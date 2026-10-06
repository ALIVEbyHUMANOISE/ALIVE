package com.projectego.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Package names to something a person can read, with no Android runtime.
 *
 * ALIVE declares no `<queries>` block, so `loadLabel` is subject to package
 * visibility filtering and this fallback is expected to be the common path, not
 * the rare one. The rule these tests hold is that a fallback is always a word,
 * never a raw package, and never blank - a blank caption under a bar would
 * strand the bar with nothing to identify it.
 */
class AppLabelsTest {

    @Test
    fun `a fallback is a humanised segment rather than a package`() {
        assertEquals("Musically", AppLabels.fallbackName("com.zhiliaoapp.musically"))
        assertEquals("Instagram", AppLabels.fallbackName("com.instagram.android"))
        assertFalse(AppLabels.fallbackName("com.facebook.games").contains("."))
    }

    @Test
    fun `a meaningless last segment falls back one level`() {
        // "app", "android", "client" and "lite" carry no identity, so printing
        // one would label a bar with a word that names nothing.
        assertEquals("Twitch", AppLabels.fallbackName("tv.twitch.android.app"))
        assertEquals("Facebook", AppLabels.fallbackName("com.facebook.lite"))
        assertEquals("Chrome", AppLabels.fallbackName("com.android.chrome"))
        assertEquals("Roblox", AppLabels.fallbackName("com.roblox.client"))
    }

    @Test
    fun `underscores read as spaces and the first letter is capitalised`() {
        assertEquals("My app", AppLabels.fallbackName("com.example.my_app"))
    }

    @Test
    fun `every package ALIVE already knows yields a usable word`() {
        // Mirrors AppClassifier.KNOWN_ENTERTAINMENT_PACKAGES, which is private.
        // If that list grows, this one has to grow with it: the point is that no
        // app the engine can name ends up unlabelable on the chart.
        KNOWN_ENTERTAINMENT_PACKAGES.forEach { packageName ->
            val label = AppLabels.fallbackName(packageName)
            assertTrue("blank label for $packageName", label.isNotBlank())
            assertFalse("raw package name for $packageName", label.contains("."))
        }
    }

    @Test
    fun `an odd package still yields a word instead of a crash`() {
        assertEquals("X", AppLabels.fallbackName("x"))
        // "com" is meaningless on its own, and there is nothing behind it, so
        // the last segment is still better than an empty string.
        assertEquals("Com", AppLabels.fallbackName("com"))
        assertEquals("", AppLabels.fallbackName(""))
    }

    private val KNOWN_ENTERTAINMENT_PACKAGES = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "tv.twitch.android.app",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.spotify.music",
        "com.pinterest",
        "com.bilibili.app.in",
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.mobile.legends",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.pubg.krmobile",
        "com.tencent.ig",
        "com.miHoYo.GenshinImpact",
        "com.roblox.client"
    )
}
