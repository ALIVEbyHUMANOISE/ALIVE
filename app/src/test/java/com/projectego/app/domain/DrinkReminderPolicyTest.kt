package com.projectego.app.domain

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The water reminder's arithmetic: inside the window, never outside it, and a
 * snooze that outranks the regular cadence.
 */
class DrinkReminderPolicyTest {

    private val start = 7
    private val end = 22

    @Test
    fun `a due time inside the window is left alone`() {
        val now = at(hour = 8)
        val due = DrinkReminderPolicy.nextDueMillis(now, now, 60, start, end)
        assertEquals(at(hour = 9), due)
    }

    @Test
    fun `a cadence that lands before the window waits for its opening`() {
        val now = at(hour = 6)
        val due = DrinkReminderPolicy.nextDueMillis(now, now, 60, start, end)
        assertEquals(at(hour = 7), due)
    }

    @Test
    fun `a cadence that lands after the window moves to tomorrow morning`() {
        val now = at(hour = 21, minute = 30)
        val due = DrinkReminderPolicy.nextDueMillis(now, now, 60, start, end)
        assertEquals(at(dayOffset = 1, hour = 7), due)
    }

    @Test
    fun `a snooze later than the cadence wins and stays inside the window`() {
        val now = at(hour = 8)
        val snooze = at(hour = 9, minute = 30)
        val due = DrinkReminderPolicy.nextDueMillis(
            nowMillis = now,
            anchorMillis = now,
            intervalMinutes = 30,
            startHour = start,
            endHour = end,
            snoozeUntilMillis = snooze
        )
        assertEquals(snooze, due)
    }

    @Test
    fun `a snooze past the end waits for tomorrow instead of speaking at night`() {
        val now = at(hour = 21, minute = 50)
        val snooze = at(hour = 22, minute = 5)
        val due = DrinkReminderPolicy.nextDueMillis(
            nowMillis = now,
            anchorMillis = now,
            intervalMinutes = 30,
            startHour = start,
            endHour = end,
            snoozeUntilMillis = snooze
        )
        assertEquals(at(dayOffset = 1, hour = 7), due)
    }

    @Test
    fun `the window is half-open so the closing hour is already tomorrow`() {
        assertTrue(DrinkReminderPolicy.isWithinWindow(at(hour = 21, minute = 59), start, end))
        assertFalse(DrinkReminderPolicy.isWithinWindow(at(hour = 22), start, end))
        assertFalse(DrinkReminderPolicy.isWithinWindow(at(hour = 6, minute = 59), start, end))
        assertTrue(DrinkReminderPolicy.isWithinWindow(at(hour = 7), start, end))
    }

    @Test
    fun `a malformed stored window falls back to the default instead of throwing`() {
        val now = at(hour = 8)
        val due = DrinkReminderPolicy.nextDueMillis(now, now, 60, 23, 5)
        assertEquals(at(hour = 9), due)
    }

    /** A local-time instant, so the policy's Calendar reads the same fields. */
    private fun at(dayOffset: Int = 0, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 12, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis
}
