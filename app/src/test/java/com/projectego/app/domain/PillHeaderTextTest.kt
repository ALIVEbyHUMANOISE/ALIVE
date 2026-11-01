package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The compact timer's text and the boundary it waits for.
 *
 * The pill hugs its reading when it is at rest, so this string's width *is* the
 * window's width. These tests pin the two rules that keep that from becoming a
 * second geometry clock: nothing is written while a morph owns the measuring, and
 * the value that lands is always the newest one - never a replay of a minute that
 * has already passed.
 */
class PillHeaderTextTest {

    private val text = PillHeaderText()

    @Test
    fun `nothing offered means nothing written`() {
        assertNull(text.flush(morphing = false))
        assertNull(text.flush(morphing = true))
    }

    @Test
    fun `the first reading is written at the first quiet boundary`() {
        text.offer("4")
        assertNull("a morph must not write it", text.flush(morphing = true))
        assertEquals("4", text.flush(morphing = false))
    }

    @Test
    fun `a reading that arrives mid-morph waits for the boundary`() {
        text.offer("9")
        assertEquals("9", text.flush(morphing = false))
        // The minute turns over while the shell is still moving.
        text.offer("10")
        assertNull(text.flush(morphing = true))
        assertNull(text.flush(morphing = true))
        assertEquals("10", text.flush(morphing = false))
    }

    @Test
    fun `only the newest reading is ever written`() {
        text.offer("4")
        text.offer("5")
        text.offer("10")
        assertEquals("10", text.flush(morphing = false))
        assertNull("and nothing older is replayed afterwards", text.flush(morphing = false))
    }

    @Test
    fun `an unchanged reading is not written twice`() {
        text.offer("5")
        assertEquals("5", text.flush(morphing = false))
        text.offer("5")
        assertNull("the same minute asks for no second write", text.flush(morphing = false))
    }

    @Test
    fun `a reading survives a burst of morphs and lands exactly once`() {
        text.offer("12")
        repeat(12) { assertNull(text.flush(morphing = true)) }
        assertEquals("12", text.flush(morphing = false))
        assertNull("a second boundary in the same minute writes nothing", text.flush(morphing = false))
    }

    @Test
    fun `a cancelled morph leaves the reading to the next quiet tick`() {
        // A morph interrupted by a tap never reaches its commit, so nothing is
        // written there; the tick that follows the next run does it instead. No
        // reading is lost and none is written while the shell is moving.
        text.offer("6")
        assertEquals("6", text.flush(morphing = false))
        text.offer("7")
        repeat(3) { assertNull(text.flush(morphing = true)) }
        assertNull(text.flush(morphing = true))
        assertEquals("7", text.flush(morphing = false))
    }

    @Test
    fun `the boundary rule is the same going out and coming back`() {
        // The service flushes at commit and on every quiet tick, whichever
        // direction the morph took; this pins that neither direction gets a
        // different rule.
        listOf("expansion", "collapse").forEach { direction ->
            val model = PillHeaderText()
            model.offer("3")
            assertEquals("$direction: written at rest", "3", model.flush(morphing = false))
            model.offer("4")
            assertNull("$direction: held while moving", model.flush(morphing = true))
            assertEquals("$direction: committed at the boundary", "4", model.flush(morphing = false))
        }
    }

    @Test
    fun `the pending reading can be read without consuming it`() {
        text.offer("10")
        assertEquals("10", text.pending())
        assertEquals("10", text.flush(morphing = false))
        assertEquals("10", text.pending())
        // A reading that arrives mid-morph is pending until the boundary, and a
        // morph that interrupts the roll must be able to snap to it.
        text.offer("11")
        assertNull(text.flush(morphing = true))
        assertEquals("11", text.pending())
        assertEquals("11", text.flush(morphing = false))
    }

    @Test
    fun `teardown leaves nothing waiting`() {
        text.offer("7")
        text.clear()
        assertNull(text.flush(morphing = false))
        // And a reading offered after teardown starts the model clean rather than
        // resurrecting the old one.
        text.offer("8")
        assertEquals("8", text.flush(morphing = false))
    }
}
