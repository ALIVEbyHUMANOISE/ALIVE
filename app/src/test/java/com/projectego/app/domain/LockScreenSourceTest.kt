package com.projectego.app.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level locks for the lock screen's data path.
 *
 * The lock screen used to compute the whole day's usage report inside onCreate -
 * measured at 353-785 ms of its startup, with 267-289 binder sends per creation,
 * for numbers the guard had computed one frame earlier. These checks fail if that
 * read (or the extras that replaced it) ever comes back.
 */
class LockScreenSourceTest {

    @Test
    fun `the lock screen no longer recomputes the day`() {
        val source = source("src/main/java/com/projectego/app/ui/LockActivity.kt")
        assertFalse(
            "LockActivity must not query UsageStats during creation",
            source.contains("calculateTodayEntertainmentUsage")
        )
        assertFalse(
            "and must not build a UsageTracker of its own",
            source.contains("UsageTracker")
        )
    }

    @Test
    fun `the guard hands its decision's numbers to the lock screen`() {
        val guard = source("src/main/java/com/projectego/app/service/EgoAccessibilityService.kt")
        assertTrue("the guard must send the used minutes", guard.contains("EXTRA_USED_MINUTES"))
        assertTrue("the guard must send the limit minutes", guard.contains("EXTRA_LIMIT_MINUTES"))

        val lock = source("src/main/java/com/projectego/app/ui/LockActivity.kt")
        assertTrue("the lock screen must read the used minutes", lock.contains("EXTRA_USED_MINUTES"))
        assertTrue("the lock screen must read the limit minutes", lock.contains("EXTRA_LIMIT_MINUTES"))
    }

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("source not found from ${File(".").absolutePath}: $relative")
        return file.readText()
    }
}
