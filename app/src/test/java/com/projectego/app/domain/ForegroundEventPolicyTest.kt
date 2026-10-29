package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which window events may change what the guard is watching.
 *
 * The regression this pins: a shade, a keyboard or the screenshot preview used
 * to replace the tracked package and end the periodic tick. Closing the overlay
 * sends no new window-state event for the app underneath, so enforcement stayed
 * off while the person stayed in the entertainment app.
 */
class ForegroundEventPolicyTest {

    private val self = "com.humanoise.alive"

    @Test
    fun `our own screen is a real switch away`() {
        assertEquals(
            ForegroundEventPolicy.Target.SELF,
            ForegroundEventPolicy.targetOf(self, self)
        )
    }

    @Test
    fun `system windows are overlays, not app switches`() {
        listOf(
            "android",
            "com.android.systemui",
            "com.samsung.android.app.smartcapture"
        ).forEach { overlay ->
            assertEquals(
                "overlay $overlay",
                ForegroundEventPolicy.Target.TRANSIENT,
                ForegroundEventPolicy.targetOf(overlay, self)
            )
        }
    }

    @Test
    fun `a real app becomes the tracked package`() {
        listOf(
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.whatsapp"
        ).forEach { app ->
            assertEquals(app, ForegroundEventPolicy.Target.PACKAGE, ForegroundEventPolicy.targetOf(app, self))
        }
    }

    @Test
    fun `a transient window leaves a running entertainment tick alive`() {
        val tracked = "com.zhiliaoapp.musically"

        // What the service does with a TRANSIENT target: nothing. The tracked
        // package therefore still names the app the person is actually in, and
        // the policy keeps asking while they stay.
        val routed = ForegroundEventPolicy.targetOf("com.android.systemui", self)
        assertEquals(ForegroundEventPolicy.Target.TRANSIENT, routed)

        val tick = ForegroundEnforcementPolicy.decide(
            state = ForegroundEnforcementPolicy.State(
                packageName = tracked,
                screenInteractive = true,
                isTransient = false,
                isEntertainment = true,
                focusActive = false,
                focusAllowsPackage = false,
                focusActivityName = null,
                alreadyLocked = false,
                zeroDay = false
            ),
            nowMillis = 60_000L,
            lastReportAtMillis = 0L
        )
        assertTrue("the stay-in-app check must not be stopped by a shade", tick.keepTicking)
    }
}
