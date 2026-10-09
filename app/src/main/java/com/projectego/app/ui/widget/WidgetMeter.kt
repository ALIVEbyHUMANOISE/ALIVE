package com.projectego.app.ui.widget

import androidx.annotation.ColorRes
import com.projectego.app.ui.AliveSeries

/**
 * One day's allowance, drawn as a single bar.
 *
 * The widget has no room for a second chart, so the meter carries what the ring
 * carries: each named app's slice in the colour the ring gave it, everything
 * past the named apps folded into the aggregate colour, and the rest of the bar
 * left empty - which is the day that is still there to spend.
 *
 * A day past its allowance fills the bar instead of bursting it. The slices keep
 * their share of *each other*, and the percentage beside the figure is what says
 * how far past the line the day actually went.
 */
object WidgetMeter {

    data class Slice(@param:ColorRes val colour: Int, val share: Float)

    /**
     * [namedMinutes] are the apps the card lists, already ranked; [foldedMinutes]
     * is every other app the day spent, and [limitMinutes] is that day's own
     * allowance - never a shared axis, so a full bar always means the same thing.
     */
    fun slices(namedMinutes: List<Long>, foldedMinutes: Long, limitMinutes: Long): List<Slice> {
        if (limitMinutes <= 0L) return emptyList()

        val parts = namedMinutes.mapIndexed { index, minutes ->
            AliveSeries.sliceColor(index) to minutes
        } + (AliveSeries.aggregate to foldedMinutes)

        val counted = parts.filter { it.second > 0L }
        val total = counted.sumOf { it.second }
        if (total <= 0L) return emptyList()

        val scale = minOf(1f, limitMinutes.toFloat() / total)
        return counted.map { Slice(it.first, it.second * scale / limitMinutes) }
    }
}
