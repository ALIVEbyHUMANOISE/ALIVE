package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.TimeBank

class TimeBankRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_EARNED_MINUTES = "key_earned_minutes"
        private const val KEY_SPENT_MINUTES = "key_spent_minutes"
    }

    @Synchronized
    fun getTimeBank(): TimeBank {
        val earned = prefs.getLong(KEY_EARNED_MINUTES, 0L)
        val spent = prefs.getLong(KEY_SPENT_MINUTES, 0L)
        return TimeBank(earnedMinutes = earned, spentMinutes = spent)
    }

    @Synchronized
    fun addEarnedMinutes(minutes: Long): TimeBank {
        if (minutes <= 0) return getTimeBank()
        val current = getTimeBank()
        val newEarned = current.earnedMinutes + minutes
        prefs.edit().putLong(KEY_EARNED_MINUTES, newEarned).apply()
        return TimeBank(earnedMinutes = newEarned, spentMinutes = current.spentMinutes)
    }

    @Synchronized
    fun spendMinutes(minutes: Long): Boolean {
        if (minutes <= 0) return false
        val current = getTimeBank()
        if (current.availableMinutes < minutes) {
            // Cannot spend more than available
            return false
        }
        val newSpent = current.spentMinutes + minutes
        prefs.edit().putLong(KEY_SPENT_MINUTES, newSpent).apply()
        return true
    }
}
