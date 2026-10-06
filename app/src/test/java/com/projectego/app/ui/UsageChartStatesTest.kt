package com.projectego.app.ui

import com.projectego.app.R
import com.projectego.app.model.UsageDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The chart's arithmetic, with no clock and no Android runtime.
 *
 * What these hold is the promise that a chart cannot quietly lie: a day ALIVE
 * never saw is not the same as a day with nothing spent, a full bar always means
 * that day's allowance was used up, days that went over are still counted at
 * their real minutes, and a short history is stated as short.
 */
class UsageChartStatesTest {

    private val delta = 0.0001f

    private fun day(
        date: String,
        minutes: Long,
        limit: Long = 120L,
        apps: Map<String, Long> = emptyMap()
    ) = UsageDay(date, minutes, limit, apps)

    private fun bars(vararg entries: Pair<String, UsageDay?>): List<DayBar> =
        entries.map { (date, recorded) -> UsageChartStates.barFor(date, recorded) }

    @Test
    fun `a day never recorded is not a zero`() {
        val missing = UsageChartStates.barFor("2026-10-01", null)
        val zero = UsageChartStates.barFor("2026-10-01", day("2026-10-01", 0L))

        assertEquals(DayBarKind.MISSING, missing.kind)
        assertEquals(DayBarKind.ZERO, zero.kind)
        assertEquals(0f, missing.fraction, delta)
        assertEquals(0f, zero.fraction, delta)
    }

    @Test
    fun `a stored zero limit is a recovery day rather than an empty one`() {
        val recovery = UsageChartStates.barFor("2026-10-02", day("2026-10-02", 0L, limit = 0L))
        assertEquals(DayBarKind.RECOVERY, recovery.kind)
        assertEquals(0f, recovery.fraction, delta)
    }

    @Test
    fun `a day at exactly its allowance is exactly full`() {
        val full = UsageChartStates.barFor("2026-10-03", day("2026-10-03", 120L, limit = 120L))
        assertEquals(DayBarKind.FULL, full.kind)
        assertEquals(1f, full.fraction, delta)
    }

    @Test
    fun `a day over its allowance clamps its bar but keeps its minutes`() {
        val over = UsageChartStates.barFor("2026-10-04", day("2026-10-04", 300L, limit = 120L))
        assertEquals(1f, over.fraction, delta)
        assertEquals(300L, over.minutes)
        // An over-allowance day can only be FULL: clamping is disclosure, not
        // a silent truncation of the number beside it.
        assertEquals(DayBarKind.FULL, over.kind)
    }

    @Test
    fun `a spent day is never drawn as untouched`() {
        // One minute of a two hour allowance is a hairline, but it is not zero,
        // and a chart that rounded it away would say the day was clean.
        assertTrue(UsageChartStates.fractionOf(1L, 120L) > 0f)
        assertEquals(0f, UsageChartStates.fractionOf(0L, 120L), delta)
    }

    @Test
    fun `the bar is scaled to that day's own allowance, not to the largest day`() {
        // Two days, same 60 minutes, different allowances: the 60-of-60 day is
        // full and the 60-of-120 day is half. One shared axis could not say both.
        assertEquals(0.5f, UsageChartStates.fractionOf(60L, 120L), delta)
        assertEquals(1f, UsageChartStates.fractionOf(60L, 60L), delta)
    }

    @Test
    fun `a recovery day has no share to draw`() {
        assertEquals(0f, UsageChartStates.fractionOf(0L, 0L), delta)
        assertEquals(0f, UsageChartStates.fractionOf(40L, 0L), delta)
    }

    @Test
    fun `totals never count a day that was never recorded`() {
        val recorded = bars(
            "2026-10-01" to day("2026-10-01", 40L),
            "2026-10-02" to null,
            "2026-10-03" to day("2026-10-03", 130L, limit = 120L),
            "2026-10-04" to day("2026-10-04", 0L)
        )
        val (minutes, count) = UsageChartStates.totals(recorded)

        assertEquals(170L, minutes)
        assertEquals(3, count)
        assertEquals(4, recorded.size)
    }

    @Test
    fun `a month to date list starts on the first and ends today`() {
        val today = LocalDate.of(2026, 10, 21)
        val keys = UsageChartStates.dateKeys(ChartRange.THIS_MONTH, today)

        assertEquals(21, keys.size)
        assertEquals("2026-10-01", keys.first())
        assertEquals("2026-10-21", keys.last())
    }

    @Test
    fun `the week range is the last seven days, oldest first`() {
        val today = LocalDate.of(2026, 10, 5)
        val keys = UsageChartStates.dateKeys(ChartRange.LAST_7_DAYS, today)

        assertEquals(7, keys.size)
        assertEquals("2026-09-29", keys.first())
        assertEquals("2026-10-05", keys.last())
    }

    @Test
    fun `today is one day and is never implied to be a history`() {
        val today = LocalDate.of(2026, 10, 5)
        val keys = UsageChartStates.dateKeys(ChartRange.TODAY, today)

        assertEquals(listOf("2026-10-05"), keys)
    }

    @Test
    fun `an empty store degrades by disclosure`() {
        assertEquals(R.string.chart_just_started, UsageChartStates.coverageCopy(recorded = 1, expected = 7))
        assertEquals(R.string.chart_partial, UsageChartStates.coverageCopy(recorded = 5, expected = 7))
        assertEquals(R.string.chart_complete, UsageChartStates.coverageCopy(recorded = 7, expected = 7))
        assertEquals(R.string.chart_none, UsageChartStates.coverageCopy(recorded = 0, expected = 7))
    }

    @Test
    fun `missing days keep their column instead of being dropped`() {
        val keys = listOf("2026-10-01", "2026-10-02", "2026-10-03")
        val recorded = mapOf("2026-10-02" to day("2026-10-02", 15L))
        val result = UsageChartStates.bars(keys, recorded)

        assertEquals(3, result.size)
        assertEquals(DayBarKind.MISSING, result[0].kind)
        assertEquals(DayBarKind.SPENT, result[1].kind)
        assertTrue(result.all { it.date in keys })
        assertFalse(result.any { it.fraction > 1f })
    }
}
