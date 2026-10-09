package com.projectego.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hour chart's arithmetic.
 *
 * Two rules are worth testing. The day must divide into whole, evenly-sized
 * blocks that sum back to the day, and an hour that has not happened yet must
 * never come out looking like an hour that was watched and found empty - those
 * are different facts and only one of them is about the person reading it.
 */
class UsageChartStatesTest {

    private val limit = 120L

    /** 24 hours of millis, built from minutes-per-hour. */
    private fun millis(vararg perHour: Long): LongArray =
        LongArray(UsageChartStates.HOURS_PER_DAY) { (perHour.getOrElse(it) { 0L }) * 60_000L }

    @Test
    fun `the day is drawn in eight blocks of three hours`() {
        val bars = UsageChartStates.hourBars(millis(), limit, currentHour = 9)
        assertEquals(8, bars.size)
        assertEquals(0, bars.first().startHour)
        assertEquals(21, bars.last().startHour)
        assertEquals(
            UsageChartStates.HOURS_PER_DAY,
            bars.sumOf { UsageChartStates.HOURS_PER_BAR }
        )
    }

    @Test
    fun `a block adds up the hours inside it`() {
        val bars = UsageChartStates.hourBars(millis(0, 10, 5, 5), limit, currentHour = 10)
        assertEquals(15L, bars[0].minutes)
        assertEquals(5L, bars[1].minutes)
        assertEquals(0L, bars[2].minutes)
    }

    @Test
    fun `a block still ahead is not yet, never a zero`() {
        val bars = UsageChartStates.hourBars(millis(), limit, currentHour = 4)
        assertEquals(DayBarKind.ZERO, bars[0].kind)
        assertEquals(DayBarKind.ZERO, bars[1].kind)
        assertEquals(DayBarKind.MISSING, bars[2].kind)
        assertEquals(DayBarKind.MISSING, bars[7].kind)
    }

    @Test
    fun `a bar is a share of the day's allowance, not of the busiest block`() {
        val bars = UsageChartStates.hourBars(millis(0, 30), limit, currentHour = 5)
        assertEquals(0.25f, bars[0].fraction, 0.0001f)
    }

    @Test
    fun `a block that spent the whole allowance is full`() {
        val bars = UsageChartStates.hourBars(millis(120), limit, currentHour = 5)
        assertEquals(DayBarKind.FULL, bars[0].kind)
        assertEquals(1f, bars[0].fraction, 0.0001f)
    }

    @Test
    fun `a block in the past with nothing in it stays a zero, not a missing one`() {
        val bars = UsageChartStates.hourBars(millis(0, 0, 0, 5), limit, currentHour = 3)
        assertEquals(DayBarKind.ZERO, bars[0].kind)
        assertEquals(DayBarKind.SPENT, bars[1].kind)
    }

    @Test
    fun `blocks are named by the hours they cover`() {
        assertEquals("00–03", UsageChartStates.hourBlockLabel(0))
        assertEquals("21–24", UsageChartStates.hourBlockLabel(21))
        assertEquals("03.00–06.00", UsageChartStates.hourBlockDetail(3))
    }

    @Test
    fun `shares are whole percents of the allowance`() {
        assertEquals(25, UsageChartStates.percentOf(30, 120))
        assertEquals(0, UsageChartStates.percentOf(0, 120))
        assertEquals(125, UsageChartStates.percentOf(150, 120))
        assertEquals(0, UsageChartStates.percentOf(30, 0))
    }
}
