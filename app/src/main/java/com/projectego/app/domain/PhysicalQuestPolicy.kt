package com.projectego.app.domain

import com.projectego.app.model.PhysicalQuestRequirement
import com.projectego.app.model.QuestCategory

/**
 * Which machine signals each physical quest must satisfy before its reward
 * leaves the bank. Pure rules - no Android runtime, no clock, no storage.
 *
 * The thresholds are floors, not fitness tests: the step counter exists to
 * prove the phone (and usually the person) was not left on a table. They are
 * deliberately generous to the honest side.
 */
object PhysicalQuestPolicy {

    // A slow shuffle is ~60 steps/minute. Below that the phone was probably
    // not being carried while walking.
    const val MIN_STEPS_PER_MINUTE_WALKING = 60

    // Real jogging is 140-170/minute; 100 is the floor that still defeats a
    // table and forgives every honest pace.
    const val MIN_STEPS_PER_MINUTE_RUN = 100

    // Gym work and stretching legitimately produce few steps; 20/minute is
    // the lowest floor that a phone sitting untouched cannot reach.
    const val MIN_STEPS_PER_MINUTE_EXERCISE = 20

    // Sitting still with one water break (~40 steps) passes a 10-minute
    // meditation; walking does not.
    const val MAX_STEPS_PER_MINUTE_MEDITATION = 10

    // Guided meditations legitimately play through audio apps, so those are
    // not distractions for a stillness quest.
    val AUDIO_EXEMPT_PACKAGES = setOf(
        "com.spotify.music",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music"
    )

    /**
     * The requirement for one quest, by id first and category second.
     *
     * The id overrides exist because two quests in the same category can ask
     * for opposite things: READING holds both "read a book" (phone down) and
     * "watch a podcast" (the screen IS the mission, so nothing is checked).
     */
    fun requirementFor(questId: String, category: QuestCategory): PhysicalQuestRequirement =
        when (questId) {
            "quest_run_20" ->
                PhysicalQuestRequirement(MIN_STEPS_PER_MINUTE_RUN, null, true)

            "quest_meditate_10" ->
                PhysicalQuestRequirement(null, MAX_STEPS_PER_MINUTE_MEDITATION, true, AUDIO_EXEMPT_PACKAGES)

            "quest_podcast_30" -> PhysicalQuestRequirement.NONE

            else -> when (category) {
                QuestCategory.WALKING ->
                    PhysicalQuestRequirement(MIN_STEPS_PER_MINUTE_WALKING, null, true)

                QuestCategory.EXERCISE ->
                    PhysicalQuestRequirement(MIN_STEPS_PER_MINUTE_EXERCISE, null, true)

                QuestCategory.MINDFULNESS ->
                    PhysicalQuestRequirement(null, MAX_STEPS_PER_MINUTE_MEDITATION, true, AUDIO_EXEMPT_PACKAGES)

                QuestCategory.CLEANING,
                QuestCategory.READING,
                QuestCategory.SOCIAL ->
                    PhysicalQuestRequirement(null, null, true)

                else -> PhysicalQuestRequirement.NONE
            }
        }
}
