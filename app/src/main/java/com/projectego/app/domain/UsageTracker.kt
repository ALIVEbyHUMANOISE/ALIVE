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

        val usageStatsManager =
            context.getSystemService(AppCompatActivity.USAGE_STATS_SERVICE) as? UsageStatsManager

        val startTime = getTodayStartTimestamp()
        val endTime = System.currentTimeMillis()

        var totalMillis = 0L
        var unblockedMillis = 0L
        val details = mutableListOf<Pair<String, Long>>()
        val unblockedDetails = mutableListOf<Pair<String, Long>>()

        // Minutes today's focus sessions swallowed, per package. Computed first
        // because the loop below charges whatever is left over.
        val exemptByPackage = focusExemptedMillis()

        if (usageStatsManager != null) {
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (!usageStats.isNullOrEmpty()) {
                for (stat in usageStats) {
                    if (stat.totalTimeInForeground <= 0) continue

                    val category = AppClassifier.categorize(context, stat.packageName)

                    when (category) {
                        // Only what ALIVE actually closes spends the allowance -
                        // minus whatever a focus session that approved the app
                        // swallowed, because that time was spent focusing.
                        AppCategory.ENTERTAINMENT -> {
                            val exempt = exemptByPackage[stat.packageName] ?: 0L
                            val charged = (stat.totalTimeInForeground - exempt).coerceAtLeast(0L)
                            totalMillis += charged
                            val chargedMinutes = charged / 1000 / 60
                            if (chargedMinutes > 0) {
                                details.add(Pair(stat.packageName, chargedMinutes))
                            }
                        }

                        // Messaging and the tools a session is for are
                        // reported, counted separately, and charged for
                        // nothing. They used to be added to the day, which
                        // meant two hours of chatting spent the entertainment
                        // allowance and then locked the apps that were only
                        // ever entertainment.
                        AppCategory.TRACKED, AppCategory.WORK -> {
                            unblockedMillis += stat.totalTimeInForeground
                            val minutes = stat.totalTimeInForeground / 1000 / 60
                            if (minutes > 0) {
                                unblockedDetails.add(Pair(stat.packageName, minutes))
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        // The focus-swallowed minutes are the third kind of time that does not
        // charge, and they are reported the same way the other two are. They
        // belong to entertainment packages, so a package can honestly appear on
        // both sides of the day - and the total view adds the two rows together
        // rather than letting one of them win.
        unblockedMillis += exemptByPackage.values.sum()
        exemptByPackage.forEach { (packageName, millis) ->
            val minutes = millis / 1000 / 60
            if (minutes > 0) {
                unblockedDetails.add(Pair(packageName, minutes))
            }
        }

        // Every minute ALIVE saw. The two figures above are disjoint by
        // construction - a minute moved out of the allowance by a focus session
        // lands here exactly once, on the unblocked side - so adding them is the
        // whole day rather than a double count.
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

        val events = readEvents(dayStart, now) { packageName ->
            AppClassifier.categorize(context, packageName) == AppCategory.ENTERTAINMENT
        }
        if (events.isEmpty()) return LongArray(HourlyUsage.HOURS)

        // The same exemption the daily figure applies: the hours and the total
        // must never tell two different stories about one day.
        val spans = Foreground.spans(events, now)
        val chargeable = FocusExemption.chargeableSpans(spans, focusWindows())
        return HourlyUsage.bucketSpans(dayStart, chargeable)
    }

    /** Today's focus windows, with the packages each session said were allowed. */
    private fun focusWindows(): List<FocusExemption.Window> =
        focusSessionManager.getTodayWindows().map { window ->
            FocusExemption.Window(
                start = window.start,
                end = window.end,
                approved = FocusAppPolicy.getApprovedPackages(window.activityType)
            )
        }

    /**
     * The minutes today's focus sessions swallowed, per package.
     *
     * Only the apps a session itself approved are exempt - there is no other
     * list and no other path, so an app no focus activity approves can never be
     * excused by this, whatever the state of the accessibility service.
     */
    private fun focusExemptedMillis(): Map<String, Long> {
        val windows = focusWindows()
        if (windows.isEmpty()) return emptyMap()

        val dayStart = getTodayStartTimestamp()
        val now = System.currentTimeMillis()
        val spans = ArrayList<Foreground.Span>()
        windows.forEach { window ->
            val from = maxOf(window.start, dayStart)
            val to = minOf(window.end, now)
            if (to <= from) return@forEach
            val events = readEvents(from, to) { packageName ->
                window.approved.contains(packageName)
            }
            spans += Foreground.spans(events, to)
        }
        return FocusExemption.exemptedMillis(spans, windows)
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
    }
}
