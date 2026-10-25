package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the pill decides from the app in front.
 *
 * Three properties matter: evidence older than the pill is not evidence of a
 * change (or the pill closes the moment it opens), a system window over the app
 * is not a place the person went, and a different app has to stay in front long
 * enough to be a switch rather than a dialog.
 */
class PillWatchPolicyTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val youtube = "com.google.android.youtube"
    private val whatsapp = "com.whatsapp"
    private val systemUi = "com.android.systemui"
    private val startedAt = 1_000_000L

    private fun decide(
        watched: String = tiktok,
        foreground: String? = tiktok,
        foregroundAt: Long = startedAt + 1_000L,
        started: Long = startedAt,
        now: Long = foregroundAt + 10_000L,
        entertainment: Boolean = true,
        transient: Boolean = false
    ) = PillWatchPolicy.decide(
        watchedPackage = watched,
        foregroundPackage = foreground,
        foregroundAt = foregroundAt,
        startedAtWall = started,
        nowWall = now,
        isEntertainment = entertainment,
        isTransientWindow = transient
    )

    @Test
    fun `nothing recent keeps the pill where it is`() {
        assertEquals(PillWatchPolicy.Decision.Keep, decide(foreground = null))
    }

    @Test
    fun `the same app is not a change`() {
        assertEquals(PillWatchPolicy.Decision.Keep, decide(foreground = tiktok))
    }

    @Test
    fun `another entertainment app is followed`() {
        assertEquals(
            PillWatchPolicy.Decision.Follow(youtube),
            decide(foreground = youtube, entertainment = true)
        )
    }

    @Test
    fun `a non-entertainment app ends the reading`() {
        assertEquals(
            PillWatchPolicy.Decision.Stop,
            decide(foreground = whatsapp, entertainment = false)
        )
    }

    @Test
    fun `an entry older than the pill is not evidence of a change`() {
        assertEquals(
            PillWatchPolicy.Decision.Keep,
            decide(
                foreground = "com.sec.android.app.launcher",
                foregroundAt = startedAt - 5_000L,
                now = startedAt + 20_000L,
                entertainment = false
            )
        )
    }

    @Test
    fun `an event at the moment the pill was raised still counts`() {
        assertEquals(
            PillWatchPolicy.Decision.Follow(youtube),
            decide(foreground = youtube, foregroundAt = startedAt, now = startedAt + 10_000L)
        )
    }

    @Test
    fun `the notification shade over the app is not the person leaving`() {
        // The shade belongs to the system UI; treating it as an app switch is
        // what made the pill vanish on every pull and restart the timer after.
        assertEquals(
            PillWatchPolicy.Decision.Keep,
            decide(foreground = systemUi, entertainment = false, transient = true)
        )
    }

    @Test
    fun `a different app seen for two seconds is not a switch yet`() {
        val seenAt = startedAt + 1_000L
        assertEquals(
            PillWatchPolicy.Decision.Keep,
            decide(
                foreground = whatsapp,
                foregroundAt = seenAt,
                now = seenAt + 2_000L,
                entertainment = false
            )
        )
    }

    @Test
    fun `a real switch acts once it has settled`() {
        assertEquals(
            PillWatchPolicy.Decision.Stop,
            decide(
                foreground = whatsapp,
                foregroundAt = startedAt + 1_000L,
                now = startedAt + 1_000L + PillWatchPolicy.SETTLE_MILLIS,
                entertainment = false
            )
        )
    }

    @Test
    fun `a window that did not resume an activity is not a switch`() {
        // The 2026-10-23 report: a keyboard/booster window over TikTok stopped
        // the pill, and TikTok's next window state restarted it at 0. The log
        // still says TikTok resumed, so the window is only a window.
        assertEquals(false, PillWatchPolicy.isConfirmedSwitch("com.samsung.android.honeyboard", tiktok))
        assertEquals(true, PillWatchPolicy.isConfirmedSwitch(whatsapp, whatsapp))
    }

    @Test
    fun `an unreadable log leaves the decision to the periodic probe`() {
        // Null is not evidence of leaving: the service's own 20s probe still
        // takes the pill down if the app really changed.
        assertEquals(false, PillWatchPolicy.isConfirmedSwitch(whatsapp, null))
    }
}