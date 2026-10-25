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

    @Test
    fun `the next form is the first threshold not yet reached`() {
        assertEquals(7, CharacterGrowth.nextThresholdFor(0))
        assertEquals(7, CharacterGrowth.nextThresholdFor(6))
        assertEquals(30, CharacterGrowth.nextThresholdFor(7))
        assertEquals(100, CharacterGrowth.nextThresholdFor(30))
        assertEquals(null, CharacterGrowth.nextThresholdFor(100))
        assertEquals(null, CharacterGrowth.nextThresholdFor(5000))
    }

    @Test
    fun `points to the next form count down and stop at the last`() {
        assertEquals(7, CharacterGrowth.pointsToNext(0))
        assertEquals(1, CharacterGrowth.pointsToNext(6))
        assertEquals(23, CharacterGrowth.pointsToNext(7))
        assertEquals(null, CharacterGrowth.pointsToNext(100))
    }

    @Test
    fun `each form's bar starts empty at its own threshold`() {
        // The bar measures the distance to the next form, not the distance from
        // zero: 30 points is a new bar, not a nearly-full one.
        assertEquals(0f, CharacterGrowth.shareToNext(0))
        assertEquals(0f, CharacterGrowth.shareToNext(7))
        assertEquals(0f, CharacterGrowth.shareToNext(30))
        assertEquals(1f, CharacterGrowth.shareToNext(100))
        assertEquals(1f, CharacterGrowth.shareToNext(5000))

        assertEquals(6f / 7f, CharacterGrowth.shareToNext(6), 0.0001f)
        assertEquals(22f / 23f, CharacterGrowth.shareToNext(29), 0.0001f)
        assertEquals(69f / 70f, CharacterGrowth.shareToNext(99), 0.0001f)
    }

    @Test
    fun `the bar's caption carries its own range`() {
        assertEquals(3 to 7, CharacterGrowth.progressWithin(3))
        assertEquals(0 to 7, CharacterGrowth.progressWithin(0))
        assertEquals(0 to 23, CharacterGrowth.progressWithin(7))
        assertEquals(22 to 23, CharacterGrowth.progressWithin(29))
        assertEquals(0 to 70, CharacterGrowth.progressWithin(30))
        // The last form has no next threshold, so there is no range to show.
        assertEquals(null, CharacterGrowth.progressWithin(100))
    }
}
