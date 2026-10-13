package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * When a promise made at night starts to govern.
 *
 * The rule exists because of two clock readings that must not be confused: a
 * contract written at nine in the evening governs tomorrow, and one written at
 * half past midnight governs the day it is already part of - the same promise,
 * an hour and a half apart, and getting it backwards would make a late-night
 * contract useless for exactly the day it was written for.
 */
class NightContractTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `evening promise governs tomorrow, not tonight`() {
        val nightKey = NightContract.nightKeyOf(at(2026, 10, 12, 21, 0))
        assertEquals("2026-10-12", nightKey)
        assertFalse(NightContract.isInForce(nightKey, "2026-10-12"))
        assertTrue(NightContract.isInForce(nightKey, "2026-10-13"))
    }

    @Test
    fun `just after midnight belongs to the night that just ended`() {
        val nightKey = NightContract.nightKeyOf(at(2026, 10, 13, 0, 30))
        assertEquals("2026-10-12", nightKey)
        assertTrue(NightContract.isInForce(nightKey, "2026-10-13"))
    }

    @Test
    fun `the boundary is four in the morning`() {
        assertEquals("2026-10-12", NightContract.nightKeyOf(at(2026, 10, 13, 3, 59)))
        assertEquals("2026-10-13", NightContract.nightKeyOf(at(2026, 10, 13, 4, 1)))
    }

    @Test
    fun `a contract is a standing order, not a day pass`() {
        val nightKey = NightContract.nightKeyOf(at(2026, 10, 12, 22, 0))
        assertTrue(NightContract.isInForce(nightKey, "2026-10-14"))
        assertTrue(NightContract.isInForce(nightKey, "2026-11-01"))
    }

    @Test
    fun `the whole evening shares one night key`() {
        assertEquals(
            NightContract.nightKeyOf(at(2026, 10, 12, 21, 0)),
            NightContract.nightKeyOf(at(2026, 10, 12, 23, 59))
        )
    }
}
