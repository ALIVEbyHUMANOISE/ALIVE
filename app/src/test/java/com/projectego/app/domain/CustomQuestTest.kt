package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of a mission somebody wrote themselves.
 *
 * Two of them carry the design: it pays like a catalog quest of the same
 * length, and it is impossible to save one that is missing the name or the
 * duration - because a mission with no words in it is the app's goal again,
 * wearing the person's jersey.
 */
class CustomQuestTest {

    @Test
    fun `the reward follows the catalog's common values, not the clock`() {
        assertEquals(10L, CustomQuest.rewardFor(10))
        assertEquals(15L, CustomQuest.rewardFor(20))
        assertEquals(20L, CustomQuest.rewardFor(30))
    }

    @Test
    fun `a mission with a name and an offered duration is complete`() {
        assertTrue(CustomQuest.isComplete("lari sore keliling blok", 20))
    }

    @Test
    fun `a blank name is not a mission`() {
        assertFalse(CustomQuest.isComplete("", 20))
        assertFalse(CustomQuest.isComplete("   ", 20))
        assertFalse(CustomQuest.isComplete(null, 20))
    }

    @Test
    fun `only the three durations the contract offers can be saved`() {
        CustomQuest.DURATIONS.forEach { assertTrue(CustomQuest.isComplete("duduk diam", it)) }
        assertFalse(CustomQuest.isComplete("duduk diam", 7))
        assertFalse(CustomQuest.isComplete("duduk diam", 0))
    }

    @Test
    fun `the id is fixed, so a rewrite is the same quest`() {
        assertEquals("custom.own", CustomQuest.ID)
    }
}
