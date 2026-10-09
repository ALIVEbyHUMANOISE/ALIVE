package com.projectego.app.domain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
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
    val limitMinutes: Long,
    val isLimitExceeded: Boolean,
    val remainingMinutes: Long,
    val criticalZoneTier: Int, // 0 = normal, 1 = 30m left, 2 = 15m left, 3 = 5m left, 4 = 1m left, 5 = locked
    val timeStatus: TimeStatus,
    val dayType: DayType,
    val currentStreak: Int,
    val timeBank: TimeBank,
    val entertainmentAppDetails: List<Pair<String, Long>> // PackageName -> Minutes
)

class UsageTracker(private val context: Context) {

    val lockStateManager = LockStateManager(context)
    val timeBankRepository = TimeBankRepository(context)

    /** Read-only archive of what this method already computed, for the chart. */
    private val usageHistory = UsageHistoryRepository(context)

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
                    val appMinutes = stat.totalTimeInForeground / 1000 / 60

                    when (category) {
                        // Only what ALIVE actually closes spends the allowance.
                        AppCategory.ENTERTAINMENT -> {
                            totalMillis += stat.totalTimeInForeground
                            if (appMinutes > 0) {
                                details.add(Pair(stat.packageName, appMinutes))
                            }
                        }

                        // Messaging and anything else ALIVE never blocks is
                        // reported, counted separately, and charged for nothing.
                        // It used to be added to the day, which meant two hours
                        // of chatting spent the entertainment allowance and then
                        // locked the apps that were only ever entertainment.
                        AppCategory.TRACKED -> unblockedMillis += stat.totalTimeInForeground

                        else -> Unit
                    }
                }
            }
        }

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
                details.toMap()
            )
        }

        return UsageReport(
            totalEntertainmentMinutes = totalMinutes,
            totalEntertainmentMillis = totalMillis,
            unblockedMinutes = unblockedMillis / 1000 / 60,
            limitMinutes = limitMinutes,
            isLimitExceeded = isExceeded || lockStateManager.isLocked(),
            remainingMinutes = remainingMinutes,
            criticalZoneTier = tier,
            timeStatus = status,
            dayType = dayType,
            currentStreak = lockStateManager.getCurrentStreak(),
            timeBank = timeBank,
            entertainmentAppDetails = details.sortedByDescending { it.second }
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
        val usageStatsManager =
            context.getSystemService(AppCompatActivity.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return LongArray(HourlyUsage.HOURS)

        val dayStart = getTodayStartTimestamp()
        val now = System.currentTimeMillis()
        val stream = usageStatsManager.queryEvents(dayStart, now) ?: return LongArray(HourlyUsage.HOURS)

        val events = mutableListOf<HourlyUsage.Event>()
        val counted = HashMap<String, Boolean>()
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

            // The screen going dark ends every session, whoever the event names.
            // Everything else is kept only if the app is one the allowance is
            // spent on - the same rule the daily figure above uses, so the hours
            // and the total cannot tell two different stories about one day.
            if (kind != HourlyUsage.Kind.SCREEN_OFF) {
                val packageName = event.packageName ?: continue
                val isCounted = counted.getOrPut(packageName) {
                    AppClassifier.categorize(context, packageName) == AppCategory.ENTERTAINMENT
                }
                if (!isCounted) continue
            }

            events.add(HourlyUsage.Event(event.timeStamp, kind, event.packageName ?: ""))
        }

        return HourlyUsage.bucket(dayStart, events, now)
    }

    private companion object {
        /** ACTIVITY_RESUMED and ACTIVITY_PAUSED: the same two events, renamed in API 29. */
        const val EVENT_RESUMED = 23
        const val EVENT_PAUSED = 24
    }
}
