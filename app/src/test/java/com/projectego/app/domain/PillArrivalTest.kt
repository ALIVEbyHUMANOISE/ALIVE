package com.projectego.app.domain

import com.projectego.app.domain.PillArrival.OnTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who owns the arrival transform, and what a tap may do to it.
 *
 * The arrival animates the root view's alpha, scale and translation; the morph
 * animates the window, the corner and the content. No property is shared, so the
 * rules here are about overlap, not conflict: the arrival keeps its curve when
 * nothing interrupts it, and a tap finishes it quickly from the live pose rather
 * than cancelling it into a half-done transform or snapping it to the end.
 */
class PillArrivalTest {

    private val arrival = PillArrival()

    @Test
    fun `a tap with no arrival simply lets the morph run`() {
        assertFalse(arrival.isActive)
        assertEquals(OnTap.LET_RUN, arrival.tap())
        assertFalse("and asking does not invent an arrival", arrival.isActive)
    }

    @Test
    fun `an arrival that finished before the tap cannot be shortened`() {
        arrival.started()
        arrival.finished()
        assertFalse(arrival.isActive)
        assertEquals(OnTap.LET_RUN, arrival.tap())
    }

    @Test
    fun `a tap during the arrival shortens it once`() {
        arrival.started()
        assertTrue(arrival.isActive)
        assertEquals(OnTap.SHORTEN, arrival.tap())
        assertEquals("a second tap adds no second animator", OnTap.ALREADY_SETTLING, arrival.tap())
        assertEquals(OnTap.ALREADY_SETTLING, arrival.tap())
    }

    @Test
    fun `a burst of taps while settling never asks for more work`() {
        arrival.started()
        assertEquals(OnTap.SHORTEN, arrival.tap())
        repeat(20) { assertEquals(OnTap.ALREADY_SETTLING, arrival.tap()) }
    }

    @Test
    fun `completion is idempotent`() {
        arrival.started()
        arrival.tap()
        arrival.finished()
        arrival.finished()
        assertFalse(arrival.isActive)
        assertEquals("a late second completion changes nothing", OnTap.LET_RUN, arrival.tap())
    }

    @Test
    fun `teardown hands the transform back and ignores a late completion`() {
        arrival.started()
        arrival.release()
        assertFalse(arrival.isActive)
        // The animator's end action may still arrive after the view is gone.
        arrival.finished()
        assertFalse(arrival.isActive)
        assertEquals(OnTap.LET_RUN, arrival.tap())
    }

    @Test
    fun `reduced motion ends the arrival without a trip`() {
        arrival.started()
        arrival.release()
        assertEquals("no shortening is requested when motion is off", OnTap.LET_RUN, arrival.tap())
    }

    @Test
    fun `a new summon starts a clean arrival`() {
        arrival.started()
        arrival.tap()
        // The window was rebuilt (an app switch while arriving): the new arrival
        // is a new trip and must be shorten-able again.
        arrival.started()
        assertEquals(OnTap.SHORTEN, arrival.tap())
    }
}
