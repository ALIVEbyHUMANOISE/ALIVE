package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.DayType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LockStateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_TRACKED_DATE = "key_last_tracked_date"
        private const val KEY_IS_LOCKED = "key_is_locked"
        private const val KEY_DEV_TEST_MODE = "key_dev_test_mode"
        private const val KEY_LAST_WARNING_TIER = "key_last_warning_tier"
        private const val KEY_CURRENT_STREAK = "key_current_streak"
        private const val KEY_DAY_TYPE = "key_day_type"
        private const val KEY_LAST_DAY_FAILED = "key_last_day_failed"

        // The guard gap: a stretch during one day when the accessibility service
        // that does the closing was off. Keyed by day so it scopes itself, and so
        // no reset has to remember to clear it.
        private const val KEY_UNGUARDED_DAY = "key_unguarded_day"
        private const val KEY_GUARD_REPAIR_BASELINE = "key_guard_repair_baseline"
        private const val KEY_GUARD_REPAIRED = "key_guard_repaired"

        const val PRODUCTION_LIMIT_MINUTES = 120L
        const val DEV_TEST_LIMIT_MINUTES = 1L

        /**
         * The single `yyyy-MM-dd` key for a moment in time.
         *
         * The usage history is keyed by this, and the day-rollover check below
         * compares against it. Two separate date formatters in those two places
         * would drift silently and the chart would look empty to the engine, so
         * both use this one.
         */
        fun dayKeyOf(timeMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMillis))
    }

    private fun getTodayDateString(): String = dayKeyOf(System.currentTimeMillis())

    /**
     * Checks if a new day has arrived.
     * Evaluates previous day's success/failure before resetting state.
     * This logic is deterministic and safe even if the app was closed during midnight.
     */
    @Synchronized
    fun checkAndPerformDailyReset(evaluateUsageCallback: ((startDate: String) -> Boolean)? = null): Boolean {
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_TRACKED_DATE, null)

        if (lastDate == null) {
            // First run initialization
            prefs.edit()
                .putString(KEY_LAST_TRACKED_DATE, today)
                .putBoolean(KEY_IS_LOCKED, false)
                .putInt(KEY_LAST_WARNING_TIER, 0)
                .putInt(KEY_CURRENT_STREAK, 0)
                .putString(KEY_DAY_TYPE, DayType.NORMAL_DAY.name)
                .putBoolean(KEY_LAST_DAY_FAILED, false)
                .apply()
            return true
        }

        if (lastDate != today) {
            // Date has changed! Evaluate previous day.
            val wasLocked = prefs.getBoolean(KEY_IS_LOCKED, false)
            val currentStreak = prefs.getInt(KEY_CURRENT_STREAK, 0)
            val previousDayType = getDayType()

            // If the user reached the hard lock on the previous day, it's an immediate fail.
            // If a callback is supplied, it can also verify usage from UsageStatsManager.
            var dayFailed = wasLocked
            if (!dayFailed && evaluateUsageCallback != null) {
                dayFailed = evaluateUsageCallback(lastDate)
            }
            // A day that ended with the guard off and never repaired is not a kept
            // day, even if nothing about its usage crossed the limit. A repaired
            // gap is left alone: the price was already paid that day.
            if (!dayFailed && isGuardGapOpenOn(lastDate)) {
                dayFailed = true
            }

            val newStreak: Int
            val newDayType: DayType

            if (dayFailed) {
                // Streak resets to 0. Next day enters ZERO_ENTERTAINMENT_DAY.
                newStreak = 0
                newDayType = DayType.ZERO_ENTERTAINMENT_DAY
            } else {
                // Successful day! Streak increments by 1. Next day is NORMAL_DAY.
                newStreak = currentStreak + 1
                newDayType = DayType.NORMAL_DAY
            }

            prefs.edit()
                .putString(KEY_LAST_TRACKED_DATE, today)
                .putBoolean(KEY_IS_LOCKED, newDayType == DayType.ZERO_ENTERTAINMENT_DAY)
                .putInt(KEY_LAST_WARNING_TIER, 0)
                .putInt(KEY_CURRENT_STREAK, newStreak)
                .putString(KEY_DAY_TYPE, newDayType.name)
                .putBoolean(KEY_LAST_DAY_FAILED, dayFailed)
                .apply()

            return true
        }

        return false
    }

    fun getCurrentStreak(): Int {
        return prefs.getInt(KEY_CURRENT_STREAK, 0)
    }

    fun getDayType(): DayType {
        val name = prefs.getString(KEY_DAY_TYPE, DayType.NORMAL_DAY.name)
        return try {
            DayType.valueOf(name ?: DayType.NORMAL_DAY.name)
        } catch (_: Exception) {
            DayType.NORMAL_DAY
        }
    }

    fun wasLastDayFailed(): Boolean {
        return prefs.getBoolean(KEY_LAST_DAY_FAILED, false)
    }

    /**
     * Records that the guard was found off during [dayKey].
     *
     * [earnedAtDiscovery] is the Time Bank's lifetime earned total at that
     * moment, and it is the repair receipt: a quest reward is the only thing
     * that ever moves that number, so "finish one quest to clear this" needs no
     * new path into the reward pipeline.
     *
     * Calling this again on the same day keeps the original baseline, so a user
     * who opens ALIVE ten times while the service is off cannot push the goalpost
     * further away.
     */
    @Synchronized
    fun markGuardGap(dayKey: String, earnedAtDiscovery: Long) {
        if (dayKey.isBlank()) return
        if (prefs.getString(KEY_UNGUARDED_DAY, null) == dayKey) return
        prefs.edit()
            .putString(KEY_UNGUARDED_DAY, dayKey)
            .putLong(KEY_GUARD_REPAIR_BASELINE, earnedAtDiscovery)
            .putBoolean(KEY_GUARD_REPAIRED, false)
            .apply()
    }

    /** The day the guard was last found off, or null if it never has been. */
    @Synchronized
    fun unguardedDay(): String? = prefs.getString(KEY_UNGUARDED_DAY, null)

    @Synchronized
    fun isGuardGapOpenOn(dayKey: String): Boolean =
        prefs.getString(KEY_UNGUARDED_DAY, null) == dayKey && !prefs.getBoolean(KEY_GUARD_REPAIRED, false)

    /**
     * Closes the repair once a quest reward has actually landed.
     *
     * Strictly greater, never equal: the baseline is the total at the moment the
     * gap was noticed, and a reward that has not arrived yet must not count as
     * though it had.
     */
    @Synchronized
    fun repairGuardGapIfEarned(earnedMinutes: Long): Boolean {
        if (prefs.getString(KEY_UNGUARDED_DAY, null) == null) return false
        if (prefs.getBoolean(KEY_GUARD_REPAIRED, false)) return false
        if (earnedMinutes <= prefs.getLong(KEY_GUARD_REPAIR_BASELINE, 0L)) return false
        prefs.edit().putBoolean(KEY_GUARD_REPAIRED, true).apply()
        return true
    }

    fun isDevTestModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DEV_TEST_MODE, false)
    }

    fun setDevTestModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_TEST_MODE, enabled).apply()
    }

    fun getDailyLimitMinutes(): Long {
        if (getDayType() == DayType.ZERO_ENTERTAINMENT_DAY) {
            return 0L
        }
        return if (isDevTestModeEnabled()) DEV_TEST_LIMIT_MINUTES else PRODUCTION_LIMIT_MINUTES
    }

    fun isLocked(): Boolean {
        return prefs.getBoolean(KEY_IS_LOCKED, false)
    }

    fun setLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply()
    }

    fun getLastWarningTier(): Int {
        return prefs.getInt(KEY_LAST_WARNING_TIER, 0)
    }

    fun setLastWarningTier(tier: Int) {
        prefs.edit().putInt(KEY_LAST_WARNING_TIER, tier).apply()
    }

    /**
     * Testing aid for developer simulation of rollover.
     */
    fun simulateRollover(simulatedLastDate: String, simulateLock: Boolean) {
        prefs.edit()
            .putString(KEY_LAST_TRACKED_DATE, simulatedLastDate)
            .putBoolean(KEY_IS_LOCKED, simulateLock)
            .apply()
        checkAndPerformDailyReset()
    }
}
