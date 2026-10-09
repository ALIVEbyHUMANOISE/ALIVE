package com.projectego.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hour chart's arithmetic.
 *
 * The rule worth testing is the one that keeps the chart honest: an hour that has
 * not happened yet must never come out looking like an hour that was watched and
 * found empty, because those two are different facts and only one of them is
 * about the person reading it.
 */
class UsageChartStatesTest {

    private val limit = 120L

    /** 24 hours of millis, built from minutes-per-hour. */
    private fun millis(vararg perHour: Long): LongArray =
        LongArray(UsageChartStates.HOURS_PER_DAY) { (perHour.getOrElse(it) { 0L }) * 60_000L }

    @Test
    fun `an hour ahead of now is not yet, never a zero`() {
        val bars = UsageChartStates.hourBars(millis(), limit, currentHour = 9)
        assertEquals(DayBarKind.MISSING, bars[10].kind)
        assertEquals(DayBarKind.ZERO, bars[9].kind)
        assertEquals(DayBarKind.ZERO, bars[0].kind)
    }

    @Test
    fun `a bar is a share of the day's allowance, not of the busiest hour`() {
        val bars = UsageChartStates.hourBars(millis(0, 30), limit, currentHour = 5)
        assertEquals(0.25f, bars[1].fraction, 0.0001f)
    }

    @Test
    fun `an hour that spent the whole allowance is full`() {
        val bars = UsageChartStates.hourBars(millis(120), limit, currentHour = 5)
        assertEquals(DayBarKind.FULL, bars[0].kind)
        assertEquals(1f, bars[0].fraction, 0.0001f)
    }

    @Test
    fun `an hour in the past with nothing in it stays a spent zero, not a missing one`() {
        val bars = UsageChartStates.hourBars(millis(0, 0, 0, 5), limit, currentHour = 3)
        assertEquals(DayBarKind.ZERO, bars[2].kind)
        assertEquals(DayBarKind.SPENT, bars[3].kind)
    }

    @Test
    fun `shares are whole percents of the allowance`() {
        assertEquals(25, UsageChartStates.percentOf(30, 120))
        assertEquals(0, UsageChartStates.percentOf(0, 120))
        assertEquals(125, UsageChartStates.percentOf(150, 120))
        assertEquals(0, UsageChartStates.percentOf(30, 0))
    }

    @Test
    fun `the hour label is the clock, zero padded`() {
        assertEquals("09", UsageChartStates.hourLabel(9))
        assertEquals("23", UsageChartStates.hourLabel(23))
    }
}
