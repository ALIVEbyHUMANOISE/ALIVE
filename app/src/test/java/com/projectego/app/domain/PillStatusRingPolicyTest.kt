package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring has to say exactly what the three lights said, including with motion
 * off. These are the semantics the redesign was allowed to restyle but not to
 * change.
 *
 * The hues are locked twice on purpose: against the named constant and against
 * the literal the palette was agreed at. A constant that drifts quietly to
 * another colour would otherwise carry its own test along with it - and the
 * enum keeps the lights' old names (YELLOW is an ordinary day, GREEN is a
 * recovery day), so the state and the colour are not the same word.
 */
class PillStatusRingPolicyTest {

    @Test
    fun `an ordinary day is green and quiet`() {
        val ring = PillStatusRingPolicy.of(TrafficLightState(TrafficLight.YELLOW), true)
        assertEquals(PillTheme.RING_NORMAL_COLOR, ring.color)
        assertEquals("an ordinary day is green, not the old amber", 0xFF28C840.toInt(), ring.color)
        assertFalse(ring.pulsing)
        assertFalse(ring.ringed)
        assertTrue("an ordinary day still glows a little", ring.glow > 0f)
    }

    @Test
    fun `a recovery day is mint, never red`() {
        val ring = PillStatusRingPolicy.of(TrafficLightState(TrafficLight.GREEN), true)
        assertEquals(PillTheme.RING_RECOVERY_COLOR, ring.color)
        assertEquals("a recovery day is the mint of the palette", 0xFF2ED1A6.toInt(), ring.color)
        assertNotEquals(
            "mint must not be the limit's red",
            PillTheme.RING_LIMIT_COLOR,
            ring.color
        )
        assertFalse(ring.pulsing)
    }

    @Test
    fun `no trustworthy status claims nothing`() {
        val ring = PillStatusRingPolicy.of(TrafficLightState(TrafficLight.NONE), true)
        assertEquals(PillTheme.RING_UNKNOWN_COLOR, ring.color)
        assertEquals("an unknown day must not glow like a normal one", 0f, ring.glow, 0f)
    }

    @Test
    fun `a reached limit is steady red`() {
        val ring = PillStatusRingPolicy.of(TrafficLightState(TrafficLight.RED), true)
        assertEquals(PillTheme.RING_LIMIT_COLOR, ring.color)
        assertEquals("the limit is the one red", 0xFFFF5F57.toInt(), ring.color)
        assertFalse(ring.pulsing)
        assertFalse(ring.ringed)
    }

    @Test
    fun `approaching breathes when motion is allowed`() {
        val ring = PillStatusRingPolicy.of(
            TrafficLightState(TrafficLight.RED, pulse = true),
            animationsAllowed = true
        )
        assertEquals(PillTheme.RING_CAUTION_COLOR, ring.color)
        assertEquals("approaching is yellow, not the limit's red", 0xFFFEBC2E.toInt(), ring.color)
        assertTrue(ring.pulsing)
        assertFalse("the pulse is the cue while it runs", ring.ringed)
    }

    @Test
    fun `with motion off, approaching keeps a static ring instead of the pulse`() {
        val ring = PillStatusRingPolicy.of(
            TrafficLightState(TrafficLight.RED, pulse = true),
            animationsAllowed = false
        )
        assertEquals(PillTheme.RING_CAUTION_COLOR, ring.color)
        assertEquals("the static cue keeps the same yellow", 0xFFFEBC2E.toInt(), ring.color)
        assertFalse("reduced motion must not animate", ring.pulsing)
        assertTrue("but the state must still be visible", ring.ringed)
    }
}

/**
 * The eyes are the one deliberately adjustable part of the redesign, and the
 * ring must never inherit it: status and decoration are separate palettes.
 */
class PillThemeTest {

    @Test
    fun `the eyes are white and the bloom is not`() {
        assertEquals(0xFFFFFFFF.toInt(), PillTheme.EYE_COLOR)
        assertNotEquals(PillTheme.EYE_COLOR, PillTheme.EYE_HALO_COLOR)
        assertEquals(0xFF, PillTheme.EYE_HALO_COLOR ushr 24)
    }

    @Test
    fun `the timer is white until the day is spent`() {
        assertEquals(0xFFFFFFFF.toInt(), PillTheme.TIMER_COLOR)
        assertNotEquals(
            "the ring's ordinary green is not the timer's white",
            PillTheme.RING_NORMAL_COLOR,
            PillTheme.TIMER_COLOR
        )
    }

    @Test
    fun `the glow stays a restrained fraction`() {
        assertTrue(PillTheme.EYE_GLOW in 0f..1f)
        assertTrue("a black shell makes glow read loud", PillTheme.EYE_GLOW <= 0.5f)
    }

    @Test
    fun `status colours are never the eye palette`() {
        val ringColours = setOf(
            PillTheme.RING_NORMAL_COLOR,
            PillTheme.RING_CAUTION_COLOR,
            PillTheme.RING_LIMIT_COLOR,
            PillTheme.RING_RECOVERY_COLOR,
            PillTheme.RING_UNKNOWN_COLOR
        )
        assertFalse(PillTheme.EYE_COLOR in ringColours)
        assertFalse(PillTheme.EYE_HALO_COLOR in ringColours)
        assertEquals("five hues, five jobs", 5, ringColours.size)
        // Spelled out because a set's size alone would not name the pair that
        // could quietly collapse: the ordinary day and the recovery day are both
        // green, and they must still be two different greens.
        assertNotEquals(
            "an ordinary green and a recovery mint are two jobs",
            PillTheme.RING_NORMAL_COLOR,
            PillTheme.RING_RECOVERY_COLOR
        )
    }
}
