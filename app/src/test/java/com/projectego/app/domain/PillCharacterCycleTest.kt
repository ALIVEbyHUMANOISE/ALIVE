package com.projectego.app.domain

import com.projectego.app.domain.PillCharacterCycle.EyeExpression
import com.projectego.app.domain.PillCharacterCycle.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visit's arithmetic: five minutes apart, half a minute long, and unable to
 * leave the eyes on screen once it is over.
 */
class PillCharacterCycleTest {

    @Test
    fun `a visit starts every five minutes`() {
        assertEquals(5 * 60 * 1000L, PillCharacterCycle.INTERVAL_MILLIS)
        assertEquals(
            "the next visit is owed one interval after this one started",
            1_000L + PillCharacterCycle.INTERVAL_MILLIS,
            PillCharacterCycle.nextDueAt(1_000L)
        )
    }

    @Test
    fun `the visit itself lasts thirty seconds`() {
        assertEquals(30_000L, PillCharacterCycle.TOTAL_MILLIS)
        assertTrue(
            "a visit must stay a small share of the interval",
            PillCharacterCycle.TOTAL_MILLIS <= PillCharacterCycle.INTERVAL_MILLIS / 5
        )
    }

    @Test
    fun `the phases run reveal, express, sleep, fade, hidden`() {
        assertEquals(Phase.REVEAL, PillCharacterCycle.phaseAt(0L))
        assertEquals(Phase.REVEAL, PillCharacterCycle.phaseAt(PillCharacterCycle.REVEAL_MILLIS - 1))
        assertEquals(Phase.EXPRESS, PillCharacterCycle.phaseAt(PillCharacterCycle.REVEAL_MILLIS))
        assertEquals(
            Phase.SLEEP,
            PillCharacterCycle.phaseAt(PillCharacterCycle.REVEAL_MILLIS + PillCharacterCycle.EXPRESS_MILLIS)
        )
        assertEquals(
            Phase.FADE,
            PillCharacterCycle.phaseAt(
                PillCharacterCycle.REVEAL_MILLIS +
                    PillCharacterCycle.EXPRESS_MILLIS +
                    PillCharacterCycle.SLEEP_MILLIS
            )
        )
        assertEquals(Phase.HIDDEN, PillCharacterCycle.phaseAt(PillCharacterCycle.TOTAL_MILLIS))
        assertEquals("a negative clock is not a visit", Phase.HIDDEN, PillCharacterCycle.phaseAt(-1L))
    }

    @Test
    fun `the eyes fade in rather than appear`() {
        val start = PillCharacterCycle.eyesAt(0L)
        val middle = PillCharacterCycle.eyesAt(PillCharacterCycle.REVEAL_MILLIS / 2)
        val done = PillCharacterCycle.eyesAt(PillCharacterCycle.REVEAL_MILLIS)
        assertEquals(0f, start.alpha, 0f)
        assertTrue("mid-reveal is partial: ${middle.alpha}", middle.alpha in 0.1f..0.9f)
        assertEquals(1f, done.alpha, 0f)
        assertEquals(EyeExpression.OPEN, done.expression)
    }

    @Test
    fun `the eyes blink during the awake phase`() {
        val base = PillCharacterCycle.REVEAL_MILLIS
        val samples = (0 until PillCharacterCycle.EXPRESS_MILLIS.toInt() step 20)
            .map { PillCharacterCycle.eyesAt(base + it) }
        val blinks = samples.count { it.expression == EyeExpression.BLINK }
        assertTrue("the awake phase should blink, saw $blinks frames", blinks > 0)
        assertTrue("blinking must stay a punctuation, not a loop: $blinks", blinks < samples.size / 4)
    }

    @Test
    fun `the eyes look both ways`() {
        val base = PillCharacterCycle.REVEAL_MILLIS
        val gazes = (0 until PillCharacterCycle.EXPRESS_MILLIS.toInt() step 50)
            .map { PillCharacterCycle.eyesAt(base + it).gaze }
        assertTrue("must look left at some point: ${gazes.min()}", gazes.min() < -0.5f)
        assertTrue("must look right at some point: ${gazes.max()}", gazes.max() > 0.5f)
    }

    @Test
    fun `the eyes narrow and round again`() {
        val base = PillCharacterCycle.REVEAL_MILLIS
        val shapes = (0 until PillCharacterCycle.EXPRESS_MILLIS.toInt() step 50)
            .map { PillCharacterCycle.eyesAt(base + it).heightScale }
        assertTrue("the eyes must flatten: ${shapes.min()}", shapes.min() < 0.75f)
        assertTrue("and round again: ${shapes.max()}", shapes.max() >= 0.99f)
    }

    @Test
    fun `sleeping is a distinct expression with its own phase`() {
        val sleepStart = PillCharacterCycle.REVEAL_MILLIS + PillCharacterCycle.EXPRESS_MILLIS
        val early = PillCharacterCycle.eyesAt(sleepStart + 200L)
        val late = PillCharacterCycle.eyesAt(sleepStart + PillCharacterCycle.SLEEP_MILLIS - 200L)
        assertEquals(EyeExpression.SLEEP, early.expression)
        assertEquals(1f, early.alpha, 0f)
        assertTrue("the Zzz must start low: ${early.sleepPhase}", early.sleepPhase in 0f..0.1f)
        assertTrue("and finish high: ${late.sleepPhase}", late.sleepPhase > 0.9f)
    }

    @Test
    fun `the eyes are gone when the visit ends`() {
        val end = PillCharacterCycle.eyesAt(PillCharacterCycle.TOTAL_MILLIS)
        assertEquals("a finished visit must never leave eyes on screen", 0f, end.alpha, 0f)
        assertTrue(PillCharacterCycle.isFinished(PillCharacterCycle.TOTAL_MILLIS))
        assertFalse(PillCharacterCycle.isFinished(PillCharacterCycle.TOTAL_MILLIS - 1))
    }

    @Test
    fun `the visit is the same length in every mood`() {
        val moods = listOf(
            PillCharacterCycle.moodFor(8),
            PillCharacterCycle.moodFor(15),
            PillCharacterCycle.moodFor(20),
            PillCharacterCycle.moodFor(2)
        )
        moods.forEach { mood ->
            assertTrue(
                "the last millisecond must still be inside the visit",
                PillCharacterCycle.phaseAt(PillCharacterCycle.TOTAL_MILLIS - 1, mood) != Phase.HIDDEN
            )
            assertEquals(
                "the visit must end exactly at the total",
                Phase.HIDDEN,
                PillCharacterCycle.phaseAt(PillCharacterCycle.TOTAL_MILLIS, mood)
            )
        }
    }

    @Test
    fun `the late-night character falls asleep sooner`() {
        val neutral = PillCharacterCycle.NEUTRAL_MOOD
        val night = PillCharacterCycle.moodFor(2)
        val atNeutralSleepEdge =
            PillCharacterCycle.REVEAL_MILLIS + PillCharacterCycle.EXPRESS_MILLIS - 500L
        assertEquals(
            "the neutral character is still awake here",
            Phase.EXPRESS,
            PillCharacterCycle.phaseAt(atNeutralSleepEdge, neutral)
        )
        assertEquals(
            "the late-night character is already asleep",
            Phase.SLEEP,
            PillCharacterCycle.phaseAt(atNeutralSleepEdge, night)
        )
    }

    @Test
    fun `the night eyes are narrower than the morning ones`() {
        val probe = PillCharacterCycle.REVEAL_MILLIS + 5_000L
        val morning = PillCharacterCycle.eyesAt(probe, PillCharacterCycle.moodFor(8))
        val night = PillCharacterCycle.eyesAt(probe, PillCharacterCycle.moodFor(2))
        assertTrue(
            "night ${night.heightScale} must be flatter than morning ${morning.heightScale}",
            night.heightScale < morning.heightScale
        )
    }

    @Test
    fun `a late tick past the end is still hidden`() {
        val late = PillCharacterCycle.eyesAt(PillCharacterCycle.TOTAL_MILLIS * 3)
        assertEquals(0f, late.alpha, 0f)
    }
}
