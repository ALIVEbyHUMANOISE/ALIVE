package com.projectego.app.domain

import com.projectego.app.domain.ForegroundVisits.Event
import com.projectego.app.domain.ForegroundVisits.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the pill's "N× today" is counted.
 *
 * The rule that makes it honest: one visit is one entry, not one activity
 * resume, and a screen-off ends whatever visit was open.
 */
class ForegroundVisitsTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val youtube = "com.google.android.youtube"
    private val whatsapp = "com.whatsapp"

    private fun resumed(packageName: String?) = Event(Kind.RESUMED, packageName)
    private val screenOff = Event(Kind.SCREEN_OFF)

    @Test
    fun `several resumes inside one visit count once`() {
        val events = listOf(resumed(tiktok), resumed(tiktok), resumed(tiktok))
        assertEquals(1, ForegroundVisits.count(events, tiktok))
    }

    @Test
    fun `leaving and coming back is a second visit`() {
        val events = listOf(resumed(tiktok), resumed(youtube), resumed(tiktok))
        assertEquals(2, ForegroundVisits.count(events, tiktok))
    }

    @Test
    fun `a screen-off splits a visit even in the same app`() {
        val events = listOf(resumed(tiktok), screenOff, resumed(tiktok))
        assertEquals(2, ForegroundVisits.count(events, tiktok))
    }

    @Test
    fun `other apps do not add to this app's count`() {
        val events = listOf(resumed(whatsapp), resumed(youtube), resumed(whatsapp))
        assertEquals(0, ForegroundVisits.count(events, tiktok))
    }

    @Test
    fun `a resume with no package is skipped, not counted as a break`() {
        val events = listOf(resumed(tiktok), resumed(null), resumed(tiktok))
        assertEquals(1, ForegroundVisits.count(events, tiktok))
    }

    @Test
    fun `deduping another app does not merge two visits to this one`() {
        val events = listOf(resumed(tiktok), resumed(whatsapp), resumed(whatsapp), resumed(tiktok))
        assertEquals(2, ForegroundVisits.count(events, tiktok))
    }

    @Test
    fun `the shade does not split a visit`() {
        // Pulling the notification shade resumes the system UI between two
        // resumes of the app; that is not a visit anywhere and it must not turn
        // one sitting into two.
        val events = listOf(
            resumed(tiktok),
            resumed("com.android.systemui"),
            resumed(tiktok)
        )
        assertEquals(
            1,
            ForegroundVisits.count(
                events,
                tiktok,
                ignoredPackages = AppClassifier.TRANSIENT_WINDOW_PACKAGES
            )
        )
    }
}