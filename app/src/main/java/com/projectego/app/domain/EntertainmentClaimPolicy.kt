package com.projectego.app.domain

import com.projectego.app.model.DayType

/**
 * How a Time Bank claim may add minutes to today's entertainment allowance.
 *
 * The rules that are not obvious:
 *
 *  - A recovery day has no allowance at all, so a claim there would create
 *    exactly the entertainment time that day exists to withhold.
 *  - The cap is per day, and "per day" is the claim's own day: the count is
 *    stored with the day it was made on (the guard-gap pattern in
 *    `LockStateManager`), so tomorrow starts at zero without a reset.
 *  - What may be claimed is the smaller of the cap's leftover and the bank
 *    balance - this policy never grants minutes that do not exist.
 *
 * No `Context` and no clock: the numbers come in, one decision goes out, and
 * the same inputs always produce the same answer. Testable on plain JVM.
 */
object EntertainmentClaimPolicy {

    /** The most that may ever be added to one day's allowance. */
    const val DAILY_CAP_MINUTES = 20L

    sealed interface Decision {
        /** Claim [minutes] now. */
        data class Claim(val minutes: Long) : Decision

        /** A recovery day has no allowance, so there is nothing to add to. */
        object RecoveryDay : Decision

        /** The day's [DAILY_CAP_MINUTES] are used up. */
        object DailyCapReached : Decision

        /** The bank has nothing to give. */
        object BankEmpty : Decision
    }

    fun decide(dayType: DayType, claimedToday: Long, availableMinutes: Long): Decision {
        if (dayType == DayType.ZERO_ENTERTAINMENT_DAY) return Decision.RecoveryDay

        val capLeft = DAILY_CAP_MINUTES - claimedToday.coerceAtLeast(0L)
        if (capLeft <= 0L) return Decision.DailyCapReached
        if (availableMinutes <= 0L) return Decision.BankEmpty

        return Decision.Claim(minOf(capLeft, availableMinutes))
    }
}
