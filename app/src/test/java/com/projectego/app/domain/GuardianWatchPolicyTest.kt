package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Guardian is allowed to knock.
 *
 * Two rules pull against each other and both have to hold: the knock repeats
 * while a person stays in an entertainment app (one reminder per sitting is how
 * the guard stops feeling like it is guarding), and it never becomes a flicker -
 * the general spacing and the per-app spacing are what keep it readable.
 */
class GuardianWatchPolicyTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val whatsapp = "com.whatsapp"

    private fun decide(
        now: Long = 1_000_000L,
        foreground: String? = tiktok,
        entertainment: Boolean = true,
        lastAt: Long = 0L,
        lastPackage: String? = null
    ) = GuardianWatchPolicy.decide(now, foreground, entertainment, lastAt, lastPackage)

    @Test
    fun `an entertainment app that just came forward is worth interrupting`() {
        val decision = decide()
        assertTrue(decision.interrupt)
        assertEquals(tiktok, decision.packageName)
    }

    @Test
    fun `nothing came forward, or it was not entertainment, so nothing happens`() {
        assertFalse(decide(foreground = null).interrupt)
        assertFalse(decide(foreground = whatsapp, entertainment = false).interrupt)
    }

    @Test
    fun `two popups never land inside the general spacing`() {
        val decision = decide(now = 1_000_000L, lastAt = 1_000_000L - 30_000L, lastPackage = whatsapp)
        assertFalse("30 seconds after the last one is too soon", decision.interrupt)
    }

    @Test
    fun `the same app is re-knocked while it stays open, at its own spacing`() {
        // Past the general spacing but inside the app's own: still no.
        val tooSoon = decide(
            now = 1_000_000L,
            lastAt = 1_000_000L - 60_000L,
            lastPackage = tiktok
        )
        assertFalse("one app is interrupted at most every 90 seconds", tooSoon.interrupt)

        // Past its own spacing: yes - this is the case that used to never happen,
        // so a sitting in one app heard from the guardian exactly once.
        val later = decide(
            now = 1_000_000L,
            lastAt = 1_000_000L - GuardianWatchPolicy.SAME_APP_COOLDOWN_MILLIS - 1,
            lastPackage = tiktok
        )
        assertTrue("staying in the app is a new decision once the spacing is up", later.interrupt)

        // A different app after the general spacing is a new decision too.
        val other = decide(
            now = 1_000_000L,
            lastAt = 1_000_000L - GuardianWatchPolicy.GLOBAL_COOLDOWN_MILLIS - 1,
            lastPackage = whatsapp
        )
        assertTrue(other.interrupt)
    }
}
