package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focus-session exemption.
 *
 * The rule worth dying for is the last test in this file: whatever a window
 * covers, an app that no focus activity approves can never be excused - that
 * is the whole difference between "focus time is free" and "start a session
 * and scroll". Every other test is the arithmetic around it.
 */
class FocusExemptionTest {

    private val minute = 60_000L

    private fun span(id: String, from: Long, to: Long) = Foreground.Span(id, from, to)

    private fun window(from: Long, to: Long, approved: Set<String>) =
        FocusExemption.Window(from, to, approved)

    @Test
    fun `an approved app inside its own window is exempt`() {
        val exempt = FocusExemption.exemptedMillis(
            listOf(span("com.notion.id", 10 * minute, 40 * minute)),
            listOf(window(0L, 60 * minute, setOf("com.notion.id")))
        )
        assertEquals(30 * minute, exempt["com.notion.id"])
    }

    @Test
    fun `only the overlap inside the window is exempt`() {
        val exempt = FocusExemption.exemptedMillis(
            listOf(span("com.notion.id", 10 * minute, 40 * minute)),
            listOf(window(20 * minute, 50 * minute, setOf("com.notion.id")))
        )
        assertEquals(20 * minute, exempt["com.notion.id"])
    }

    @Test
    fun `an app no window approves is never exempt`() {
        val exempt = FocusExemption.exemptedMillis(
            listOf(span("com.instagram.android", 10 * minute, 40 * minute)),
            listOf(window(0L, 60 * minute, setOf("com.notion.id")))
        )
        assertTrue(exempt.isEmpty())
    }

    @Test
    fun `chargeable spans keep the time around the window`() {
        val chargeable = FocusExemption.chargeableSpans(
            listOf(span("com.notion.id", 10 * minute, 60 * minute)),
            listOf(window(20 * minute, 50 * minute, setOf("com.notion.id")))
        )
        assertEquals(
            listOf(
                Foreground.Span("com.notion.id", 10 * minute, 20 * minute),
                Foreground.Span("com.notion.id", 50 * minute, 60 * minute)
            ),
            chargeable
        )
    }

    @Test
    fun `a span swallowed whole leaves nothing chargeable`() {
        val chargeable = FocusExemption.chargeableSpans(
            listOf(span("com.notion.id", 10 * minute, 40 * minute)),
            listOf(window(0L, 60 * minute, setOf("com.notion.id")))
        )
        assertTrue(chargeable.isEmpty())
    }

    @Test
    fun `the social app stays chargeable even when the window covers it entirely`() {
        val chargeable = FocusExemption.chargeableSpans(
            listOf(span("com.instagram.android", 10 * minute, 40 * minute)),
            listOf(window(0L, 60 * minute, setOf("com.notion.id")))
        )
        assertEquals(
            listOf(Foreground.Span("com.instagram.android", 10 * minute, 40 * minute)),
            chargeable
        )
    }

    /**
     * The invariant the daily total now stands on.
     *
     * The day is built by adding the chargeable minutes, the exempt minutes and
     * the unblocked apps' minutes together, so a minute that appeared on two of
     * those sides would be billed twice - which is exactly what the per-app
     * totals used to do to an app a focus session had approved.
     */
    @Test
    fun `chargeable and exempt minutes are the same time once, never twice`() {
        val spans = listOf(
            span("com.notion.id", 10 * minute, 40 * minute),
            span("com.instagram.android", 45 * minute, 60 * minute)
        )
        val windows = listOf(window(20 * minute, 50 * minute, setOf("com.notion.id")))
        val charged = FocusExemption.chargeableSpans(spans, windows).sumOf { it.end - it.start }
        val exempt = FocusExemption.exemptedMillis(spans, windows).values.sum()

        assertEquals(spans.sumOf { it.end - it.start }, charged + exempt)
        assertEquals(20 * minute, exempt)
    }
}
