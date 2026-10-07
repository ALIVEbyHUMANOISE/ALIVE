package com.projectego.app.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Where a chart's columns go: how many share a row, and how many rows there are.
 *
 * [columnCount] is carried because the last row of a wrapped month is usually
 * short, and a grid that only knows its own shape would happily hit-test a
 * column that was never drawn.
 */
data class ChartLayout(val columnsPerRow: Int, val rowCount: Int, val columnCount: Int) {

    val isEmpty: Boolean get() = columnsPerRow <= 0 || rowCount <= 0

    fun rowOf(index: Int): Int = if (isEmpty) 0 else index / columnsPerRow

    /** The column at one grid position, or -1 in the empty tail of a short row. */
    fun indexAt(row: Int, column: Int): Int {
        val index = row * columnsPerRow + column
        return if (!isEmpty && index < columnCount) index else -1
    }

    companion object {
        val EMPTY = ChartLayout(0, 0, 0)
    }
}

/**
 * How a bar chart's columns fit the width it was actually given.
 *
 * Pure arithmetic on purpose: this decides whether a month is one row of
 * hairlines nobody can read or three rows of bars you can point at, and a rule
 * with that much say should be testable without a screen.
 */
object ChartGeometry {

    /**
     * The narrowest slot a bar may stand in. Below this a column is neither
     * readable as a height nor findable by a thumb, so the chart wraps instead.
     */
    const val MIN_SLOT_DP: Float = 18f

    /**
     * Fits [columnCount] columns into [availablePx] of canvas.
     *
     * Rows are balanced rather than filled: 31 days at 13 per row is
     * 13 + 13 + 5, and a ragged last row reads like a chart that ran out of
     * data. 11 + 11 + 9 says the same thing with rows that match.
     */
    fun layout(availablePx: Float, columnCount: Int, density: Float): ChartLayout {
        if (columnCount <= 0 || availablePx <= 0f || density <= 0f) return ChartLayout.EMPTY
        val perRow = floor(availablePx / (MIN_SLOT_DP * density)).toInt().coerceIn(1, columnCount)
        val rows = ceil(columnCount.toFloat() / perRow).toInt()
        return ChartLayout(ceil(columnCount.toFloat() / rows).toInt(), rows, columnCount)
    }

    /** A label is printed only when it fits its own column and the air beside it. */
    fun labelFits(textWidthPx: Float, slotPx: Float, marginPx: Float): Boolean =
        textWidthPx + marginPx <= slotPx

    /** The gap a label keeps from its column edges, in dp - small, but never zero. */
    const val LABEL_MARGIN_DP: Float = 2f

    /** One column's bar width: most of the slot, capped so a week does not look like a plank. */
    fun barWidth(slotPx: Float, maxPx: Float, minPx: Float): Float =
        min(slotPx * 0.62f, maxPx).coerceAtLeast(min(minPx, slotPx))
}
