package com.projectego.app.ui

import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The data series, as a rule rather than a list someone can extend casually.
 *
 * The ring used to hand out warm gray, soft forest and paper to neighbouring
 * slices, which meant three of them were separated by nothing but a two-pixel
 * stroke. These assertions keep the fix in place: four slices, no repeats, no
 * agency colour in the data, and the leftovers folded into one honest aggregate.
 */
class AliveSeriesTest {

    @Test
    fun `the ring shows four slices and folds the rest`() {
        assertEquals(4, AliveSeries.MAX_SLICES)
        assertEquals(AliveSeries.MAX_SLICES, AliveSeries.donut.size)
    }

    @Test
    fun `no two slices share a colour, including the wrap-around pair`() {
        val distinct = AliveSeries.donut.distinct()
        assertEquals(AliveSeries.donut.size, distinct.size)
        // The last slice touches the first one in a ring, so that seam matters
        // as much as any neighbour in the middle.
        assertFalse(AliveSeries.donut.first() == AliveSeries.donut.last())
    }

    @Test
    fun `forest never colours somebody's evening`() {
        // Forest means "ALIVE is offering a choice". Minutes spent on an app are
        // not a choice the app made, so the identity colour stays out of data.
        assertFalse(AliveSeries.donut.contains(R.color.alive_forest))
    }

    @Test
    fun `the aggregate is the inert colour, not a fifth value`() {
        assertEquals(R.color.alive_warm_gray, AliveSeries.aggregate)
    }

    @Test
    fun `the index wraps instead of running off the end`() {
        assertEquals(AliveSeries.donut[0], AliveSeries.sliceColor(0))
        assertEquals(AliveSeries.donut[0], AliveSeries.sliceColor(4))
        assertEquals(AliveSeries.donut[3], AliveSeries.sliceColor(7))
    }
}
