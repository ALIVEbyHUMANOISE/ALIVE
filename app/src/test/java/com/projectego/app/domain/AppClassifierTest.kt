package com.projectego.app.domain

import com.projectego.app.model.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which app is treated as what, without a device.
 *
 * The two rules this file exists for both came from a real afternoon: a wallet
 * that was charged against the entertainment allowance, and a game that stayed
 * playable after the allowance was spent. The curated lists decide the first,
 * and the fallback in `categorize` - not testable here, because it asks the
 * platform - decides the second.
 */
class AppClassifierTest {

    @Test
    fun `money is never entertainment`() {
        listOf("id.dana", "com.gojek.app", "com.shopee.id", "com.jago.digitalBanking")
            .forEach { packageName ->
                assertEquals(packageName, AppCategory.TRACKED, AppClassifier.curated(packageName))
            }
    }

    @Test
    fun `the game that stayed playable is entertainment`() {
        assertEquals(
            AppCategory.ENTERTAINMENT,
            AppClassifier.curated("com.supercell.clashofclans")
        )
    }

    @Test
    fun `a package nobody named is left to the platform`() {
        assertNull(AppClassifier.curated("com.example.somethingnew"))
    }

    @Test
    fun `blank names are ignored rather than guessed`() {
        assertEquals(AppCategory.SYSTEM_IGNORED, AppClassifier.curated(null))
        assertEquals(AppCategory.SYSTEM_IGNORED, AppClassifier.curated(""))
    }

    @Test
    fun `messaging keeps its own bucket`() {
        assertEquals(AppCategory.TRACKED, AppClassifier.curated("com.whatsapp"))
    }

    @Test
    fun `no package is named by two lists`() {
        val entries = AppClassifier.curatedEntries()
        val seen = HashSet<String>()
        val duplicates = entries.map { it.first }.filter { !seen.add(it) }
        assertTrue("named twice: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `the curated lists did not all collapse into one category`() {
        // A guard against a future edit that makes every list return the same
        // thing: the chart's lenses would still render, and would be lying.
        val kinds = AppClassifier.curatedEntries().map { it.second }.toSet()
        assertTrue("expected at least three categories, got $kinds", kinds.size >= 3)
    }
}
