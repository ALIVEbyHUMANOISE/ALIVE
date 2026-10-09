package com.projectego.app.domain

import com.projectego.app.model.QuestCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signal requirements per quest, as a pure rule table.
 *
 * The interesting cases are the id overrides: two quests share a category
 * yet ask for opposite things (READING holds both "read a book" and "watch
 * a podcast"), so the id must win over the category.
 */
class PhysicalQuestPolicyTest {

    @Test
    fun `walking measures movement and attention`() {
        val requirement = PhysicalQuestPolicy.requirementFor("quest_walk_20", QuestCategory.WALKING)
        assertEquals(PhysicalQuestPolicy.MIN_STEPS_PER_MINUTE_WALKING, requirement.minStepsPerMinute)
        assertNull(requirement.maxStepsPerMinute)
        assertTrue(requirement.checkDistractions)
        assertTrue(requirement.needsSteps)
        assertTrue(requirement.hasAnyCheck)
    }

    @Test
    fun `exercise asks for a low step floor, not a fitness test`() {
        val requirement = PhysicalQuestPolicy.requirementFor("quest_exercise_20", QuestCategory.EXERCISE)
        assertEquals(PhysicalQuestPolicy.MIN_STEPS_PER_MINUTE_EXERCISE, requirement.minStepsPerMinute)
        assertTrue(requirement.checkDistractions)
    }

    @Test
    fun `the run mission overrides its category with the run floor`() {
        val requirement = PhysicalQuestPolicy.requirementFor("quest_run_20", QuestCategory.EXERCISE)
        assertEquals(PhysicalQuestPolicy.MIN_STEPS_PER_MINUTE_RUN, requirement.minStepsPerMinute)
    }

    @Test
    fun `meditation asks for stillness and exempts audio apps`() {
        val requirement = PhysicalQuestPolicy.requirementFor("quest_meditate_10", QuestCategory.MINDFULNESS)
        assertEquals(PhysicalQuestPolicy.MAX_STEPS_PER_MINUTE_MEDITATION, requirement.maxStepsPerMinute)
        assertNull(requirement.minStepsPerMinute)
        assertTrue(requirement.checkDistractions)
        assertTrue(requirement.distractionExemptPackages.contains("com.spotify.music"))
        assertTrue(requirement.distractionExemptPackages.contains("com.google.android.youtube"))
    }

    @Test
    fun `social and cleaning and reading ask only for attention`() {
        listOf(
            Triple("quest_socialize_30", QuestCategory.SOCIAL, null),
            Triple("quest_kindness_15", QuestCategory.SOCIAL, null),
            Triple("quest_clean_20", QuestCategory.CLEANING, null),
            Triple("quest_read_30", QuestCategory.READING, null)
        ).forEach { (id, category, _) ->
            val requirement = PhysicalQuestPolicy.requirementFor(id, category)
            assertNull(requirement.minStepsPerMinute)
            assertNull(requirement.maxStepsPerMinute)
            assertTrue(requirement.checkDistractions)
            assertFalse(requirement.needsSteps)
        }
    }

    @Test
    fun `the podcast mission overrides reading with no checks at all`() {
        val requirement = PhysicalQuestPolicy.requirementFor("quest_podcast_30", QuestCategory.READING)
        assertEquals(com.projectego.app.model.PhysicalQuestRequirement.NONE, requirement)
        assertFalse(requirement.hasAnyCheck)
    }

    @Test
    fun `an unknown quest falls back to its category`() {
        val requirement = PhysicalQuestPolicy.requirementFor("quest_unknown_20", QuestCategory.WALKING)
        assertEquals(PhysicalQuestPolicy.MIN_STEPS_PER_MINUTE_WALKING, requirement.minStepsPerMinute)
    }
}
