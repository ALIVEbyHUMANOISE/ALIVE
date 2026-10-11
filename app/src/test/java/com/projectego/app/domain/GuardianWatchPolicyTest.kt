package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Guardian is allowed to knock.
 *
 * The rule that matters most is the one about restraint: a reminder that fires on
 * every look becomes a flicker, and the whole point of this one is that it is
 * rare enough to be worth reading.
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
    fun `one app gets one interruption per sitting`() {
        // Past the general cooldown, but the same app again: still no.
        val soon = decide(
            now = 1_000_000L,
            lastAt = 1_000_000L - 100_000L,
            lastPackage = tiktok
        )
        assertFalse("closing and reopening the same app is one sitting", soon.interrupt)

        // A different app after the general cooldown is a new decision.
        val other = decide(
            now = 1_000_000L,
            lastAt = 1_000_000L - 100_000L,
            lastPackage = whatsapp
        )
        assertTrue(other.interrupt)

        // And the same app is fair game again once its own cooldown has passed.
        val later = decide(
            now = 1_000_000L,
            lastAt = 1_000_000L - GuardianWatchPolicy.SAME_APP_COOLDOWN_MILLIS - 1,
            lastPackage = tiktok
        )
        assertTrue(later.interrupt)
    }
}
