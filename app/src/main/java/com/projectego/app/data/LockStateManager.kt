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

        const val PRODUCTION_LIMIT_MINUTES = 120L
        const val DEV_TEST_LIMIT_MINUTES = 1L
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

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
