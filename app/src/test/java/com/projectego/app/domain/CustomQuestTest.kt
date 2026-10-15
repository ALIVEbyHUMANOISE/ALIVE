package com.projectego.app.domain

import com.projectego.app.model.QuestCategory
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
    fun `a mission with a name, an offered duration and a kind is complete`() {
        assertTrue(CustomQuest.isComplete("ulang bab empat statistika", 20, QuestCategory.STUDY))
    }

    @Test
    fun `a blank name is not a mission`() {
        assertFalse(CustomQuest.isComplete("", 20, QuestCategory.STUDY))
        assertFalse(CustomQuest.isComplete("   ", 20, QuestCategory.STUDY))
        assertFalse(CustomQuest.isComplete(null, 20, QuestCategory.STUDY))
    }

    @Test
    fun `only the three durations the contract offers can be saved`() {
        CustomQuest.DURATIONS.forEach {
            assertTrue(CustomQuest.isComplete("duduk diam", it, QuestCategory.STUDY))
        }
        assertFalse(CustomQuest.isComplete("duduk diam", 7, QuestCategory.STUDY))
        assertFalse(CustomQuest.isComplete("duduk diam", 0, QuestCategory.STUDY))
    }

    @Test
    fun `only the kinds ALIVE can watch can be saved`() {
        CustomQuest.KINDS.forEach {
            assertTrue(CustomQuest.isComplete("duduk diam", 20, it))
        }
        // A walk cannot be watched, so a mission that says "walk" could never be
        // verified - and an unverifiable mission is the one thing this design
        // refuses outright.
        assertFalse(CustomQuest.isComplete("lari sore", 20, QuestCategory.WALKING))
        assertFalse(CustomQuest.isComplete("duduk diam", 20, QuestCategory.MINDFULNESS))
        assertFalse(CustomQuest.isComplete("duduk diam", 20, QuestCategory.OTHER))
    }

    @Test
    fun `the id is fixed, so a rewrite is the same quest`() {
        assertEquals("custom.own", CustomQuest.ID)
    }

    @Test
    fun `the board entry keeps the words, the length, the kind and the catalog's pay`() {
        val entry = CustomQuest.quest("ulang bab empat statistika", "body", 20, QuestCategory.STUDY)

        assertEquals(CustomQuest.ID, entry.id)
        assertEquals("ulang bab empat statistika", entry.title)
        assertEquals(20, entry.estimatedDurationMinutes)
        assertEquals(CustomQuest.rewardFor(20), entry.rewardMinutes)
        // The kind IS the category: nothing downstream has to know this quest was
        // written by hand.
        assertEquals(QuestCategory.STUDY, entry.category)
    }
}
