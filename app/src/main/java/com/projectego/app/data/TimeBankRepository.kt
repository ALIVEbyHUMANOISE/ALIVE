package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.TimeBank

/**
 * The bank's two totals.
 *
 * Every read-modify-write runs under [TimeBankLedger], so the same minutes
 * cannot be seen as available by two writers at once, regardless of how many
 * repository instances exist. A debit is one key write; the claimed-minute
 * credit that pays for it lives in another file and is written by the claim
 * under the same monitor - see [com.projectego.app.domain.EntertainmentClaimCoordinator].
 */
class TimeBankRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_EARNED_MINUTES = "key_earned_minutes"
        private const val KEY_SPENT_MINUTES = "key_spent_minutes"
    }

    fun getTimeBank(): TimeBank = TimeBankLedger.run {
        val earned = prefs.getLong(KEY_EARNED_MINUTES, 0L)
        val spent = prefs.getLong(KEY_SPENT_MINUTES, 0L)
        TimeBank(earnedMinutes = earned, spentMinutes = spent)
    }

    fun addEarnedMinutes(minutes: Long): TimeBank = TimeBankLedger.run {
        if (minutes <= 0) return@run getTimeBank()
        val current = getTimeBank()
        val newEarned = current.earnedMinutes + minutes
        prefs.edit().putLong(KEY_EARNED_MINUTES, newEarned).apply()
        TimeBank(earnedMinutes = newEarned, spentMinutes = current.spentMinutes)
    }

    fun spendMinutes(minutes: Long): Boolean = TimeBankLedger.run {
        if (minutes <= 0) return@run false
        val current = getTimeBank()
        if (current.availableMinutes < minutes) {
            // Cannot spend more than available
            return@run false
        }
        val newSpent = current.spentMinutes + minutes
        prefs.edit().putLong(KEY_SPENT_MINUTES, newSpent).apply()
        true
    }
}
