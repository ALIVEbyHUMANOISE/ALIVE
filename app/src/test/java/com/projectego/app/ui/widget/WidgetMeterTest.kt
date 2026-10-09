package com.projectego.app.ui.widget

import com.projectego.app.ui.AliveSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's meter, as arithmetic rather than as a drawing.
 *
 * The bar is the only place on the home screen where a day's shape is shown, so
 * the two ways it can lie are the two things locked here: slices that do not
 * match the rows they sit above, and a day over its allowance drawn as though
 * the allowance had grown.
 */
class WidgetMeterTest {

    @Test
    fun `a day inside its allowance leaves the rest of the bar empty`() {
        val slices = WidgetMeter.slices(listOf(60L, 30L), foldedMinutes = 0L, limitMinutes = 120L)

        assertEquals(2, slices.size)
        assertEquals(0.5f, slices[0].share, TOLERANCE)
        assertEquals(0.25f, slices[1].share, TOLERANCE)
        assertEquals(0.75f, slices.sumOf { it.share.toDouble() }.toFloat(), TOLERANCE)
    }

    @Test
    fun `each slice wears the colour of the row it stands for`() {
        val slices = WidgetMeter.slices(listOf(50L, 40L, 30L), foldedMinutes = 20L, limitMinutes = 200L)

        assertEquals(AliveSeries.donut[0], slices[0].colour)
        assertEquals(AliveSeries.donut[1], slices[1].colour)
        assertEquals(AliveSeries.donut[2], slices[2].colour)
        // Everything the card does not name is still the day's own minutes, so
        // it gets the aggregate colour rather than a fifth made-up one.
        assertEquals(AliveSeries.aggregate, slices[3].colour)
    }

    @Test
    fun `a day past its allowance fills the bar instead of bursting it`() {
        val slices = WidgetMeter.slices(listOf(120L, 60L), foldedMinutes = 0L, limitMinutes = 120L)

        assertEquals(1f, slices.sumOf { it.share.toDouble() }.toFloat(), TOLERANCE)
        // Scaled back together, so the bar still says which app ate the day.
        assertEquals(0.667f, slices[0].share, TOLERANCE)
        assertEquals(0.333f, slices[1].share, TOLERANCE)
    }

    @Test
    fun `a day with no allowance has no share of one`() {
        assertTrue(WidgetMeter.slices(listOf(45L), foldedMinutes = 0L, limitMinutes = 0L).isEmpty())
    }

    @Test
    fun `a recorded zero draws no slices`() {
        assertTrue(WidgetMeter.slices(listOf(0L), foldedMinutes = 0L, limitMinutes = 120L).isEmpty())
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
