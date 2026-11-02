package com.projectego.app.domain

import com.projectego.app.domain.ForegroundEnforcementPolicy.Action
import com.projectego.app.domain.ForegroundEnforcementPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the guard does while a package stays in front.
 *
 * The policy exists because enforcement used to fire only on a window change: a
 * person under the limit at entry who then crossed it inside the app was never
 * asked again. These tests hold the ordering - cheap gates first, the expensive
 * report only at its own cadence - and the places where the tick must simply
 * stop.
 */
class ForegroundEnforcementPolicyTest {

    private val tick = ForegroundEnforcementPolicy.TICK_MILLIS
    private val interval = ForegroundEnforcementPolicy.REPORT_INTERVAL_MILLIS

    private fun state(
        packageName: String? = "com.zhiliaoapp.musically",
        interactive: Boolean = true,
        transient: Boolean = false,
        entertainment: Boolean = true,
        focusActive: Boolean = false,
        focusAllows: Boolean = false,
        locked: Boolean = false,
        zeroDay: Boolean = false
    ) = State(
        packageName = packageName,
        screenInteractive = interactive,
        isTransient = transient,
        isEntertainment = entertainment,
        focusActive = focusActive,
        focusAllowsPackage = focusAllows,
        focusActivityName = if (focusActive) "Belajar" else null,
        alreadyLocked = locked,
        zeroDay = zeroDay
    )

    private fun decide(
        s: State,
        now: Long = 10 * tick,
        lastReport: Long = now,
        lastLaunch: Long = 0L
    ) = ForegroundEnforcementPolicy.decide(
        s,
        nowMillis = now,
        lastReportAtMillis = lastReport,
        lastLockLaunchAtMillis = lastLaunch
    )

    @Test
    fun `no tracked package means nothing to do and nothing to keep`() {
        val result = decide(state(packageName = null))
        assertEquals(Action.IDLE, result.action)
        assertFalse(result.keepTicking)
    }

    @Test
    fun `a transient system window never keeps the tick alive`() {
        val result = decide(state(transient = true))
        assertEquals(Action.IDLE, result.action)
        assertFalse("a shade or keyboard is not a place to enforce in", result.keepTicking)
    }

    @Test
    fun `a dark screen ends the tick`() {
        val result = decide(state(interactive = false, locked = true))
        assertEquals("nothing may be enforced over a screen nobody is looking at", Action.IDLE, result.action)
        assertFalse(result.keepTicking)
    }

    @Test
    fun `leaving for a non-entertainment app ends the tick`() {
        val result = decide(state(packageName = "com.whatsapp", entertainment = false))
        assertEquals(Action.IDLE, result.action)
        assertFalse(result.keepTicking)
    }

    @Test
    fun `a focus session that allows the app keeps waiting`() {
        val result = decide(state(focusActive = true, focusAllows = true))
        assertEquals(Action.IDLE, result.action)
        assertTrue("the session can end while the app stays open", result.keepTicking)
    }

    @Test
    fun `a focus session that stops allowing the app locks it`() {
        val result = decide(state(focusActive = true, focusAllows = false))
        assertEquals(Action.FOCUS_LOCK, result.action)
        assertTrue("if the launch is debounced the next tick retries", result.keepTicking)
    }

    @Test
    fun `a persisted lock is enforced without asking the report`() {
        val result = decide(state(locked = true), now = 99 * tick, lastReport = 0L)
        assertEquals(Action.LOCK_NOW, result.action)
        assertTrue(result.keepTicking)
    }

    @Test
    fun `a zero-entertainment day is enforced without asking the report`() {
        val result = decide(state(zeroDay = true), now = 99 * tick, lastReport = 0L)
        assertEquals(Action.LOCK_NOW, result.action)
    }

    @Test
    fun `the report is only asked for at its own cadence`() {
        val now = 120 * tick
        val early = decide(state(), now = now, lastReport = now - interval + 1)
        assertEquals("one minute is the floor, not every tick", Action.IDLE, early.action)
        assertTrue(early.keepTicking)

        val due = decide(state(), now = now, lastReport = now - interval)
        assertEquals(Action.EVALUATE_REPORT, due.action)
        assertTrue(due.keepTicking)
    }

    @Test
    fun `a claim that clears the lock stops the cheap lock on the next tick`() {
        val now = 60 * tick
        val beforeClaim = decide(state(locked = true), now = now, lastReport = now)
        assertEquals(Action.LOCK_NOW, beforeClaim.action)

        // EntertainmentClaimCoordinator clears the persisted lock; under the
        // limit again, the tick goes back to waiting for the report cadence.
        val afterClaim = decide(state(locked = false), now = now + tick, lastReport = now)
        assertEquals(Action.IDLE, afterClaim.action)
    }

    @Test
    fun `a focus session without an entertainment app never reports`() {
        val result = decide(
            state(packageName = "com.whatsapp", entertainment = false, focusActive = true, focusAllows = false),
            now = 500 * tick,
            lastReport = 0L
        )
        assertEquals("focus decides before category, and locks", Action.FOCUS_LOCK, result.action)
    }

    @Test
    fun `a lock that was just launched is not issued again on the next tick`() {
        val now = 100 * tick
        val result = decide(state(locked = true), now = now, lastLaunch = now - tick)

        assertEquals(
            "the tick keeps running so a trimmed lock can be retried, but not by relaunching every 5 s",
            Action.IDLE,
            result.action
        )
        assertTrue("the guard is still watching while it waits", result.keepTicking)
    }

    @Test
    fun `the lock is retried once the retry window has passed`() {
        val now = 100 * tick
        val due = decide(
            state(locked = true),
            now = now,
            lastLaunch = now - ForegroundEnforcementPolicy.LOCK_RETRY_MILLIS
        )
        assertEquals("a lock that never came to the front gets another attempt", Action.LOCK_NOW, due.action)
    }

    @Test
    fun `a focus lock obeys the same retry window`() {
        val now = 100 * tick
        val busy = decide(
            state(focusActive = true, focusAllows = false),
            now = now,
            lastLaunch = now - tick
        )
        assertEquals(Action.IDLE, busy.action)
        assertTrue(busy.keepTicking)

        val due = decide(
            state(focusActive = true, focusAllows = false),
            now = now,
            lastLaunch = now - ForegroundEnforcementPolicy.LOCK_RETRY_MILLIS
        )
        assertEquals(Action.FOCUS_LOCK, due.action)
    }

    @Test
    fun `the retry window belongs to the app that was locked, not to the process`() {
        val now = 100 * tick
        val justLocked = now - tick

        assertEquals(
            "the same app keeps its window",
            justLocked,
            ForegroundEnforcementPolicy.retryAnchorFor("com.a", "com.a", justLocked)
        )
        assertEquals(
            "another app is a fresh sitting and must not inherit the wait",
            Long.MIN_VALUE,
            ForegroundEnforcementPolicy.retryAnchorFor("com.b", "com.a", justLocked)
        )
        assertEquals(
            "nothing locked yet means no wait at all",
            Long.MIN_VALUE,
            ForegroundEnforcementPolicy.retryAnchorFor("com.a", null, justLocked)
        )
        assertEquals(
            "an unknown foreground cannot inherit a window",
            Long.MIN_VALUE,
            ForegroundEnforcementPolicy.retryAnchorFor(null, "com.a", justLocked)
        )
    }
}
