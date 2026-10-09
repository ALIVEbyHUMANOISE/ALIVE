package com.projectego.app.domain

import com.projectego.app.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a claim may take from the bank onto a day, with no Android runtime.
 *
 * These tests hold the three edges that matter: the cap is per day and
 * cumulative, the bank is never overdrawn, and a recovery day refuses to be
 * bought back.
 */
class EntertainmentClaimPolicyTest {

    @Test
    fun `a fresh day claims up to the cap`() {
        assertEquals(
            EntertainmentClaimPolicy.Decision.Claim(20L),
            EntertainmentClaimPolicy.decide(
                dayType = DayType.NORMAL_DAY,
                claimedToday = 0L,
                availableMinutes = 50L
            )
        )
    }

    @Test
    fun `the bank is never overdrawn`() {
        assertEquals(
            EntertainmentClaimPolicy.Decision.Claim(7L),
            EntertainmentClaimPolicy.decide(
                dayType = DayType.NORMAL_DAY,
                claimedToday = 0L,
                availableMinutes = 7L
            )
        )
    }

    @Test
    fun `earlier claims today shrink what is left of the cap`() {
        assertEquals(
            EntertainmentClaimPolicy.Decision.Claim(5L),
            EntertainmentClaimPolicy.decide(
                dayType = DayType.NORMAL_DAY,
                claimedToday = 15L,
                availableMinutes = 50L
            )
        )
    }

    @Test
    fun `a full day of claims refuses even with a full bank`() {
        assertEquals(
            EntertainmentClaimPolicy.Decision.DailyCapReached,
            EntertainmentClaimPolicy.decide(
                dayType = DayType.NORMAL_DAY,
                claimedToday = 20L,
                availableMinutes = 50L
            )
        )
    }

    @Test
    fun `an empty bank has nothing to give`() {
        assertEquals(
            EntertainmentClaimPolicy.Decision.BankEmpty,
            EntertainmentClaimPolicy.decide(
                dayType = DayType.NORMAL_DAY,
                claimedToday = 0L,
                availableMinutes = 0L
            )
        )
    }

    @Test
    fun `a recovery day refuses the claim even with minutes in the bank`() {
        assertEquals(
            EntertainmentClaimPolicy.Decision.RecoveryDay,
            EntertainmentClaimPolicy.decide(
                dayType = DayType.ZERO_ENTERTAINMENT_DAY,
                claimedToday = 0L,
                availableMinutes = 50L
            )
        )
    }
}
