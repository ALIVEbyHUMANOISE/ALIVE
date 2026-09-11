package com.projectego.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TimeBank.availableMinutes].
 *
 * These tests verify the CURRENT implementation as written in TimeBank.kt:
 *   availableMinutes = (earnedMinutes - spentMinutes).coerceAtLeast(0L)
 *
 * No production code (TimeBank.kt) was modified to support these tests.
 * Pure Kotlin data class, no Android framework dependency -> runs as a
 * plain JVM unit test, no Robolectric/instrumentation required.
 */
class TimeBankTest {

    // --- Required cases (exact values) ---

    @Test
    fun `earned 100, spent 30 yields available 70`() {
        val timeBank = TimeBank(earnedMinutes = 100L, spentMinutes = 30L)
        assertEquals(70L, timeBank.availableMinutes)
    }

    @Test
    fun `earned 100, spent 100 yields available 0`() {
        val timeBank = TimeBank(earnedMinutes = 100L, spentMinutes = 100L)
        assertEquals(0L, timeBank.availableMinutes)
    }

    @Test
    fun `earned 30, spent 100 yields available 0 (not negative)`() {
        val timeBank = TimeBank(earnedMinutes = 30L, spentMinutes = 100L)
        assertEquals(0L, timeBank.availableMinutes)
    }

    @Test
    fun `earned 0, spent 0 yields available 0`() {
        val timeBank = TimeBank(earnedMinutes = 0L, spentMinutes = 0L)
        assertEquals(0L, timeBank.availableMinutes)
    }

    @Test
    fun `earned 150, spent 25 yields available 125`() {
        val timeBank = TimeBank(earnedMinutes = 150L, spentMinutes = 25L)
        assertEquals(125L, timeBank.availableMinutes)
    }

    // --- Default constructor sanity check ---

    @Test
    fun `default constructor has zero earned, spent, and available`() {
        val timeBank = TimeBank()
        assertEquals(0L, timeBank.earnedMinutes)
        assertEquals(0L, timeBank.spentMinutes)
        assertEquals(0L, timeBank.availableMinutes)
    }

    // --- Invariant: availableMinutes must NEVER be negative ---
    // (Additional scenarios beyond the 5 required cases above, to stress the invariant.)

    @Test
    fun `availableMinutes is never negative when spent greatly exceeds earned`() {
        val timeBank = TimeBank(earnedMinutes = 10L, spentMinutes = 1_000_000L)
        assertTrue(timeBank.availableMinutes >= 0L)
        assertEquals(0L, timeBank.availableMinutes)
    }

    @Test
    fun `availableMinutes is never negative when nothing has been earned yet`() {
        val timeBank = TimeBank(earnedMinutes = 0L, spentMinutes = 500L)
        assertTrue(timeBank.availableMinutes >= 0L)
        assertEquals(0L, timeBank.availableMinutes)
    }
}
