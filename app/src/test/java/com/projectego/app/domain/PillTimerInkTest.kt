package com.projectego.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which colour the number is allowed to take.
 */
class PillTimerInkTest {

    @Test
    fun `an ordinary reading turns crimson only at the limit`() {
        assertTrue(PillTimerInk.isCrimson(focusShown = false, limitReached = true, warning = false))
        assertFalse(PillTimerInk.isCrimson(focusShown = false, limitReached = false, warning = false))
    }

    @Test
    fun `a focus countdown is never crimson, even when the day is spent`() {
        assertFalse(PillTimerInk.isCrimson(focusShown = true, limitReached = true, warning = false))
    }

    @Test
    fun `a battery warning wins over everything`() {
        assertTrue(PillTimerInk.isCrimson(focusShown = true, limitReached = false, warning = true))
        assertTrue(PillTimerInk.isCrimson(focusShown = false, limitReached = false, warning = true))
    }
}
