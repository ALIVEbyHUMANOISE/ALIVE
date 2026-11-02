package com.projectego.app.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level locks for the paths where enforcement used to stop for good.
 *
 * Three defects, one shape: the guard answers "is the person in a blocked app",
 * acts once, and then stops asking. They live in the service rather than in a
 * policy because the policy already says the right thing - `LOCK_NOW` carries
 * `keepTicking = true` - and the service is what ignored it.
 *
 * These are text checks on purpose. The behaviour needs an accessibility service
 * and a real window stack, which no unit test here can provide; a Robolectric
 * service harness would mostly assert its own mocks. What can be locked cheaply
 * is that the shape of the code cannot silently go back to the losing version -
 * the same trade [LockScreenSourceTest] already makes in this package.
 */
class EnforcementReArmSourceTest {

    private fun guard(): String =
        source("src/main/java/com/projectego/app/service/EgoAccessibilityService.kt")

    private fun lock(): String =
        source("src/main/java/com/projectego/app/ui/LockActivity.kt")

    @Test
    fun `a launched lock no longer ends the tick`() {
        val source = guard()

        assertFalse(
            "a fire-and-forget startActivity must not be read as 'the lock covered the app'",
            source.contains("if (launched || !tick.keepTicking)")
        )
        assertTrue(
            "the tick must stop only when the policy says there is nothing left to watch",
            source.contains("if (!tick.keepTicking)")
        )
    }

    @Test
    fun `a rebound service keeps watching what was already in front`() {
        val source = guard()
        val connectBody = source.substringAfter("override fun onServiceConnected()")
            .substringBefore("override fun onAccessibilityEvent")

        assertTrue(
            "the tick must be re-armed on a rebind, or the rest of the sitting is unguarded",
            connectBody.contains("reArmEnforcement")
        )

        // The package has to survive the trip. onUnbind runs before the next
        // onServiceConnected and used to call stopEnforcementTick(), which
        // cleared trackedForeground - so the re-arm it was written for always
        // received null and returned on its first line.
        val unbindBody = source.substringAfter("override fun onUnbind")
            .substringBefore("override fun onInterrupt")
        assertFalse(
            "a rebind must not throw away the package it was already watching",
            unbindBody.contains("stopEnforcementTick()")
        )
    }

    @Test
    fun `every lock launch passes the retry gate, not only the tick`() {
        val source = guard()

        // Both enforce* are reached from the event path as well as from the
        // tick, and window-state events fire far more often than every 5 s. A
        // gate that lives only in the policy leaves those callers unbounded.
        for (name in listOf("enforceNormalLock", "enforceFocusLock")) {
            val body = source.substringAfter("private fun $name(")
                .substringBefore("private fun ")
            assertTrue(
                "$name must refuse inside the retry window, wherever it is called from",
                body.contains("lockRetryBusy")
            )
        }
    }

    @Test
    fun `the lock screen re-reads its extras when a second lock arrives`() {
        val source = lock()

        assertTrue(
            "LockActivity is singleTask: without onNewIntent the second lock keeps the first app's name",
            source.contains("override fun onNewIntent")
        )
        assertTrue(
            "the second lock must render from its own intent",
            source.contains("setIntent(intent)")
        )
    }

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("source not found from ${File(".").absolutePath}: $relative")
        return file.readText()
    }
}
