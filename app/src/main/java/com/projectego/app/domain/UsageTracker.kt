package com.projectego.app.domain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.data.UsageHistoryRepository
import com.projectego.app.model.AppCategory
import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import java.util.Calendar

data class UsageReport(
    val totalEntertainmentMinutes: Long,
    val totalEntertainmentMillis: Long,
    /** Time ALIVE sees in apps it never blocks. Reported, never charged. */
    val unblockedMinutes: Long,
    /**
     * The whole day ALIVE can see: what spent the allowance plus what did not.
     * The entertainment figure alone can say "0 menit" on a day someone spent
     * four hours in Word and WhatsApp, and a card that shows only the diet
     * number is not a picture of their day.
     */
    val totalScreenMinutes: Long,
    val limitMinutes: Long,
    val isLimitExceeded: Boolean,
    val remainingMinutes: Long,
    val criticalZoneTier: Int, // 0 = normal, 1 = 30m left, 2 = 15m left, 3 = 5m left, 4 = 1m left, 5 = locked
    val timeStatus: TimeStatus,
    val dayType: DayType,
    val currentStreak: Int,
    val timeBank: TimeBank,
    val entertainmentAppDetails: List<Pair<String, Long>>, // PackageName -> Minutes
    /**
     * The apps the allowance is never spent on, by name: everything ALIVE does
     * not block, plus the minutes a focus session swallowed. The same shape as
     * [entertainmentAppDetails] so one chart can swap between the two - and the
     * two together are one day, which is what the third view shows.
     */
    val unblockedAppDetails: List<Pair<String, Long>>
)

class UsageTracker(private val context: Context) {

    val lockStateManager = LockStateManager(context)
    val timeBankRepository = TimeBankRepository(context)

    /** Read-only archive of what this method already computed, for the chart. */
    private val usageHistory = UsageHistoryRepository(context)

    /**
     * What each package was last classified as.
     *
     * Classifying asks the platform two questions per package, and one day of
     * events names the same twenty packages over and over - which is what made
     * opening the statistics tab feel like a freeze. Concurrent because the
     * chart reads its hours on a background thread while Home reads its total
     * on the main one.
     */
    private val categories = java.util.concurrent.ConcurrentHashMap<String, AppCategory>()

    private fun categoryOf(packageName: String): AppCategory =
        categories.getOrPut(packageName) { AppClassifier.categorize(context, packageName) }

    /** Today's focus windows, for the exemption; a second instance over the same prefs. */
    private val focusSessionManager = FocusSessionManager(context, timeBankRepository)

    fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun calculateTodayEntertainmentUsage(): UsageReport {
        // Ensure day rollover is checked and evaluated
        lockStateManager.checkAndPerformDailyReset()

        val startTime = getTodayStartTimestamp()
        val endTime = System.currentTimeMillis()

        val details = mutableListOf<Pair<String, Long>>()
        val unblockedDetails = mutableListOf<Pair<String, Long>>()

        // The day, as intervals, from the same event log the hourly chart reads.
        //
        // This used to ask the platform for a number instead: one
        // queryUsageStats(INTERVAL_DAILY, midnight, now) per app, with its
        // totalTimeInForeground taken as today. Measured on a real phone that
        // answer was nearly two hours bigger than Digital Wellbeing's, because
        // the query returns whichever stored bucket overlaps the window, and on
        // this device the buckets roll at about a quarter to seven in the
        // evening - so yesterday's prime time was billed as today's. The events
        // have no such opinion: they are a list of what came to the front and
        // when, and today is the part of that which lies after midnight.
        val windows = focusWindows()
        val spans = todaysSpans(startTime, endTime)

        val entertainment = spans.filter { categoryOf(it.id) == AppCategory.ENTERTAINMENT }

        // Only what ALIVE actually closes spends the allowance - minus whatever
        // a focus session that approved the app swallowed, because that time was
        // spent focusing.
        val charged = FocusExemption.chargeableSpans(entertainment, windows)
        val totalMillis = charged.sumOf { it.end - it.start }
        minutesByPackage(charged).forEach { (packageName, minutes) ->
            if (minutes > 0) details.add(Pair(packageName, minutes))
        }

        // Messaging and the tools a session is for are reported, counted
        // separately, and charged for nothing. They used to be added to the day,
        // which meant two hours of chatting spent the entertainment allowance and
        // then locked the apps that were only ever entertainment.
        val free = spans.filter {
            val category = categoryOf(it.id)
            category == AppCategory.TRACKED || category == AppCategory.WORK
        }

        // The focus-swallowed minutes are the third kind of time that does not
        // charge, and they are reported the same way the other two are. They
        // belong to entertainment packages, so a package can honestly appear on
        // both sides of the day - and the total view adds the two rows together
        // rather than letting one of them win.
        val exempt = FocusExemption.exemptedMillis(entertainment, windows)
        val unblockedMillis = free.sumOf { it.end - it.start } + exempt.values.sum()
        minutesByPackage(free).forEach { (packageName, minutes) ->
            if (minutes > 0) unblockedDetails.add(Pair(packageName, minutes))
        }
        exempt.forEach { (packageName, millis) ->
            val minutes = millis / 1000 / 60
            if (minutes > 0) unblockedDetails.add(Pair(packageName, minutes))
        }

        // Every minute ALIVE saw. Each span lands in exactly one of the three
        // figures above, so adding them is the whole day rather than a double
        // count - which the per-app totals were not: an app a focus session
        // approved was counted once as a tool and again as exempt time.
        val screenMillis = totalMillis + unblockedMillis

        val totalMinutes = totalMillis / 1000 / 60
        val dayType = lockStateManager.getDayType()
        val limitMinutes = lockStateManager.getDailyLimitMinutes()
        val timeBank = timeBankRepository.getTimeBank()

        val isExceeded: Boolean
        val tier: Int
        val status: TimeStatus

        if (dayType == DayType.ZERO_ENTERTAINMENT_DAY) {
            // In Recovery / Zero-time day, entertainment is strictly 0 min base allowance.
            isExceeded = true
            lockStateManager.setLocked(true)
            tier = 5
            status = TimeStatus.ZERO_ENTERTAINMENT_DAY
        } else {
            isExceeded = totalMinutes >= limitMinutes

            if (isExceeded) {
                lockStateManager.setLocked(true)
            }

            tier = if (lockStateManager.isDevTestModeEnabled()) {
                if (isExceeded) 5 else 1
            } else {
                // Rungs are minutes-left, never absolute usage: the allowance
                // grows when minutes are claimed from the Time Bank, and a
                // threshold like ">= 120" would silently mean something else
                // at every other limit.
                val minutesLeft = limitMinutes - totalMinutes
                when {
                    isExceeded -> 5 // Locked
                    minutesLeft <= 1L -> 4 // 1 min left
                    minutesLeft <= 5L -> 3 // 5 min left
                    minutesLeft <= 15L -> 2 // 15 min left
                    minutesLeft <= 30L -> 1 // 30 min left (Critical Zone Start)
                    else -> 0
                }
            }

            status = when {
                isExceeded || lockStateManager.isLocked() -> TimeStatus.LOCKED
                tier > 0 -> TimeStatus.CRITICAL
                else -> TimeStatus.ACTIVE
            }
        }

        val remainingMinutes = if (limitMinutes > totalMinutes) limitMinutes - totalMinutes else 0L

        // Archived last, and wrapped so a history write can never stop the
        // report - and therefore the lock - from being returned. The key comes
        // from the window that was actually queried rather than a fresh clock
        // read: if the query straddled midnight, a new reading would stamp
        // today with yesterday's number.
        runCatching {
            usageHistory.recordDay(
                LockStateManager.dayKeyOf(startTime),
                totalMillis,
                limitMinutes,
                details.toMap(),
                screenMillis
            )
        }

        return UsageReport(
            totalEntertainmentMinutes = totalMinutes,
            totalEntertainmentMillis = totalMillis,
            unblockedMinutes = unblockedMillis / 1000 / 60,
            totalScreenMinutes = screenMillis / 1000 / 60,
            limitMinutes = limitMinutes,
            isLimitExceeded = isExceeded || lockStateManager.isLocked(),
            remainingMinutes = remainingMinutes,
            criticalZoneTier = tier,
            timeStatus = status,
            dayType = dayType,
            currentStreak = lockStateManager.getCurrentStreak(),
            timeBank = timeBank,
            entertainmentAppDetails = details.sortedByDescending { it.second },
            unblockedAppDetails = unblockedDetails.sortedByDescending { it.second }
        )
    }

    /**
     * Today, hour by hour, for the apps the allowance is spent on.
     *
     * Read from the event stream rather than as twenty-four one-hour queries: a
     * daily query clipped to a single hour comes back on several devices holding
     * the whole day, which would paint twenty-four identical bars and call it
     * data. The event log has no such opinion - it is a list of what came to the
     * front and when, and the hours fall out of it.
     *
     * The same set the daily figure charges for, so the two charts can never
     * disagree about the same day.
     */
    fun hourlyUsedMillisToday(): LongArray {
        val dayStart = getTodayStartTimestamp()
        val now = System.currentTimeMillis()

        // The same spans the daily figure charges for, so the two charts can
        // never disagree about the same day - and a session that began before
        // midnight pays for the minutes it spent after it here too.
        val spans = todaysSpans(dayStart, now).filter {
            categoryOf(it.id) == AppCategory.ENTERTAINMENT
        }
        return HourlyUsage.bucketSpans(dayStart, FocusExemption.chargeableSpans(spans, focusWindows()))
    }

    /**
     * Today's foreground time, as spans that lie inside today.
     *
     * The log is read from before midnight on purpose: a session that started
     * yesterday has no resume event after it, so a query that began at midnight
     * would not know the app was in front at all and would report the first
     * hours of the day as empty. [Foreground.clip] then keeps only today's part
     * of whatever survived.
     */
    private fun todaysSpans(dayStart: Long, now: Long): List<Foreground.Span> =
        Foreground.clip(
            Foreground.spans(readEvents(dayStart - SESSION_LOOKBACK_MILLIS, now) { true }, now),
            dayStart,
            now
        )

    /** Today's focus windows, with the packages each session said were allowed. */
    private fun focusWindows(): List<FocusExemption.Window> =
        focusSessionManager.getTodayWindows().map { window ->
            FocusExemption.Window(
                start = window.start,
                end = window.end,
                approved = FocusAppPolicy.getApprovedPackages(window.activityType)
            )
        }

    /** Whole minutes per package, in the order the spans first name each one. */
    private fun minutesByPackage(spans: List<Foreground.Span>): Map<String, Long> {
        val millis = LinkedHashMap<String, Long>()
        spans.forEach { span ->
            millis[span.id] = (millis[span.id] ?: 0L) + (span.end - span.start)
        }
        return millis.mapValues { it.value / 1000 / 60 }
    }

    /**
     * The foreground log between two instants, filtered to what [keep] accepts.
     *
     * Screen-off and keyguard events always pass: they close sessions, and the
     * session they close belongs to whoever the event names.
     */
    private fun readEvents(from: Long, to: Long, keep: (String) -> Boolean): List<HourlyUsage.Event> {
        val usageStatsManager =
            context.getSystemService(AppCompatActivity.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()
        val stream = usageStatsManager.queryEvents(from, to) ?: return emptyList()

        val events = mutableListOf<HourlyUsage.Event>()
        val kept = HashMap<String, Boolean>()
        val event = UsageEvents.Event()

        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND, EVENT_RESUMED -> HourlyUsage.Kind.RESUMED
                UsageEvents.Event.MOVE_TO_BACKGROUND, EVENT_PAUSED -> HourlyUsage.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> HourlyUsage.Kind.SCREEN_OFF
                else -> continue
            }

            if (kind != HourlyUsage.Kind.SCREEN_OFF) {
                val packageName = event.packageName ?: continue
                if (!kept.getOrPut(packageName) { keep(packageName) }) continue
            }

            events.add(HourlyUsage.Event(event.timeStamp, kind, event.packageName ?: ""))
        }
        return events
    }

    private companion object {
        /** ACTIVITY_RESUMED and ACTIVITY_PAUSED: the same two events, renamed in API 29. */
        const val EVENT_RESUMED = 23
        const val EVENT_PAUSED = 24

        /**
         * How far before midnight the event log is read, to catch the session
         * that was still running when the day changed. Longer than any plausible
         * unbroken session, short enough that the log has certainly kept it.
         */
        const val SESSION_LOOKBACK_MILLIS = 12 * 3_600_000L
    }
}
