package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cutting a day out of the foreground log.
 *
 * The bug this file exists for: the platform's own daily figure was taken as
 * "today", and on a device whose daily buckets roll at a time other than
 * midnight it carries the previous evening with it. A day is now whatever of the
 * event log lies after midnight, which makes the trim below the whole
 * difference between ALIVE's number and the one on the phone's own screen.
 */
class ForegroundTest {

    private val minute = 60_000L
    private val midnight = 1000 * minute

    private fun span(id: String, from: Long, to: Long) = Foreground.Span(id, from, to)

    @Test
    fun `a session that began before midnight pays only for the minutes it spent today`() {
        val clipped = Foreground.clip(
            listOf(span("com.instagram.android", midnight - 120 * minute, midnight + 60 * minute)),
            midnight,
            midnight + 300 * minute
        )
        assertEquals(listOf(span("com.instagram.android", midnight, midnight + 60 * minute)), clipped)
    }

    @Test
    fun `a session that ended before midnight is not part of today at all`() {
        assertTrue(
            Foreground.clip(
                listOf(span("com.instagram.android", midnight - 90 * minute, midnight - 10 * minute)),
                midnight,
                midnight + 300 * minute
            ).isEmpty()
        )
    }

    @Test
    fun `a session inside the day is handed over untouched`() {
        val inside = span("com.whatsapp", midnight + 10 * minute, midnight + 25 * minute)
        assertEquals(listOf(inside), Foreground.clip(listOf(inside), midnight, midnight + 300 * minute))
    }

    @Test
    fun `the hour being lived in is trimmed to now, not left open`() {
        val clipped = Foreground.clip(
            listOf(span("com.whatsapp", midnight + 50 * minute, midnight + 200 * minute)),
            midnight,
            midnight + 80 * minute
        )
        assertEquals(listOf(span("com.whatsapp", midnight + 50 * minute, midnight + 80 * minute)), clipped)
    }

    @Test
    fun `spans are still spans after the trim, so the sums cannot drift`() {
        val day = Foreground.clip(
            listOf(
                span("com.a", midnight - 30 * minute, midnight + 30 * minute),
                span("com.b", midnight + 10 * minute, midnight + 40 * minute)
            ),
            midnight,
            midnight + 100 * minute
        )
        assertEquals(30 * minute + 30 * minute, day.sumOf { it.end - it.start })
    }
}
