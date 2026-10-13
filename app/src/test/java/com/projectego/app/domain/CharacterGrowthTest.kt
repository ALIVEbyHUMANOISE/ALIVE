package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the character grows, and what a day is worth.
 *
 * Two properties carry the whole design and both are here because they are the
 * ones a later edit could quietly break: the form never goes backwards as
 * points are added, and a day can only pay once no matter how many times the
 * app is opened on it.
 */
class CharacterGrowthTest {

    @Test
    fun `the first threshold is earned in the first week`() {
        assertEquals(CharacterGrowth.Stage.ONE, CharacterGrowth.stageFor(0))
        assertEquals(CharacterGrowth.Stage.ONE, CharacterGrowth.stageFor(6))
        assertEquals(CharacterGrowth.Stage.TWO, CharacterGrowth.stageFor(7))
    }

    @Test
    fun `each threshold lands on its own stage`() {
        assertEquals(CharacterGrowth.Stage.TWO, CharacterGrowth.stageFor(29))
        assertEquals(CharacterGrowth.Stage.THREE, CharacterGrowth.stageFor(30))
        assertEquals(CharacterGrowth.Stage.THREE, CharacterGrowth.stageFor(99))
        assertEquals(CharacterGrowth.Stage.FOUR, CharacterGrowth.stageFor(100))
        assertEquals(CharacterGrowth.Stage.FOUR, CharacterGrowth.stageFor(5000))
    }

    @Test
    fun `a lifetime of points never sends the form backwards`() {
        var highest = CharacterGrowth.Stage.ONE
        for (points in 0..150) {
            val stage = CharacterGrowth.stageFor(points)
            assertTrue("stage fell at $points points", stage.ordinal >= highest.ordinal)
            highest = stage
        }
    }

    @Test
    fun `a day pays once, and a new day pays again`() {
        assertTrue(CharacterGrowth.owesPoint(null, "2026-10-13"))
        assertTrue(CharacterGrowth.owesPoint("2026-10-12", "2026-10-13"))
        assertFalse(CharacterGrowth.owesPoint("2026-10-13", "2026-10-13"))
    }
}
