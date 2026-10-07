package com.projectego.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's column maths, at a density of 1 so the numbers in these tests are
 * the dp values the design says.
 */
class ChartGeometryTest {

    private fun layout(availableDp: Float, columns: Int) =
        ChartGeometry.layout(availableDp, columns, 1f)

    @Test
    fun aMonthWrapsInsteadOfTurningIntoHairlines() {
        val grid = layout(250f, 31)
        assertEquals(11, grid.columnsPerRow)
        assertEquals(3, grid.rowCount)
        assertEquals(31, grid.columnCount)
    }

    @Test
    fun rowsAreBalancedRatherThanFilled() {
        // 13 + 13 + 5 would be the greedy answer; a short last row looks like a
        // chart that ran out of data, so the columns are shared instead.
        val grid = layout(250f, 31)
        assertTrue(grid.columnsPerRow * grid.rowCount - grid.columnCount < grid.columnsPerRow)
    }

    @Test
    fun aWideScreenKeepsOneRow() {
        val grid = layout(600f, 31)
        assertEquals(31, grid.columnsPerRow)
        assertEquals(1, grid.rowCount)
    }

    @Test
    fun aVeryNarrowScreenWrapsHarderButNeverBelowTheMinSlot() {
        val grid = layout(100f, 31)
        assertEquals(5, grid.columnsPerRow)
        assertEquals(7, grid.rowCount)
        assertTrue(100f / grid.columnsPerRow >= ChartGeometry.MIN_SLOT_DP)
    }

    @Test
    fun aWeekFitsInOneRowOfSeven() {
        val grid = layout(250f, 7)
        assertEquals(7, grid.columnsPerRow)
        assertEquals(1, grid.rowCount)
    }

    @Test
    fun aSingleDayIsOneColumn() {
        assertEquals(ChartLayout(1, 1, 1), layout(250f, 1))
    }

    @Test
    fun nothingToFitIsNotALayout() {
        assertEquals(ChartLayout.EMPTY, layout(250f, 0))
        assertEquals(ChartLayout.EMPTY, layout(0f, 12))
        assertEquals(ChartLayout.EMPTY, ChartGeometry.layout(250f, 12, 0f))
    }

    @Test
    fun theEmptyTailOfAShortRowHasNoColumnToHit() {
        val grid = layout(250f, 31)
        assertEquals(30, grid.indexAt(row = 2, column = 8))
        assertEquals(-1, grid.indexAt(row = 2, column = 9))
        assertEquals(0, grid.rowOf(0))
        assertEquals(2, grid.rowOf(30))
    }

    @Test
    fun aLabelIsPrintedOnlyWhenItsOwnColumnCanHoldIt() {
        assertTrue(ChartGeometry.labelFits(textWidthPx = 16f, slotPx = 20f, marginPx = 2f))
        assertFalse(ChartGeometry.labelFits(textWidthPx = 19f, slotPx = 20f, marginPx = 2f))
    }

    @Test
    fun aBarIsNeverWiderThanItsSlot() {
        assertEquals(30f, ChartGeometry.barWidth(slotPx = 300f, maxPx = 30f, minPx = 3f))
        // Most of the slot, so a month's bars keep a gap between them.
        assertEquals(14.0, ChartGeometry.barWidth(slotPx = 22.58f, maxPx = 30f, minPx = 3f).toDouble(), 0.05)
        // A slot narrower than the smallest bar still cannot be overprinted by it.
        assertEquals(2f, ChartGeometry.barWidth(slotPx = 2f, maxPx = 30f, minPx = 3f))
    }
}
