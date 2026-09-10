package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestDifficulty
import com.projectego.app.model.QuestStatus

class QuestRepository(
    context: Context,
    private val timeBankRepository: TimeBankRepository
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACTIVE_QUEST_ID = "key_active_quest_id"
        private const val KEY_COMPLETED_QUEST_IDS = "key_completed_quest_ids"

        // Static initial quests for Phase 2.1
        private val INITIAL_QUESTS = listOf(
            Quest(
                id = "quest_walk_20",
                title = "Walk 20 Minutes",
                description = "Go for a brisk walk outside or on a treadmill without looking at social media.",
                category = QuestCategory.WALKING,
                difficulty = QuestDifficulty.EASY,
                estimatedDurationMinutes = 20,
                rewardMinutes = 15
            ),
            Quest(
                id = "quest_study_30",
                title = "Study for 30 Minutes",
                description = "Focus on focused learning, course materials, or technical documentation.",
                category = QuestCategory.STUDY,
                difficulty = QuestDifficulty.MEDIUM,
                estimatedDurationMinutes = 30,
                rewardMinutes = 20
            ),
            Quest(
                id = "quest_read_30",
                title = "Read for 30 Minutes",
                description = "Read a physical book, kindle, or educational long-form article.",
                category = QuestCategory.READING,
                difficulty = QuestDifficulty.MEDIUM,
                estimatedDurationMinutes = 30,
                rewardMinutes = 20
            ),
            Quest(
                id = "quest_exercise_20",
                title = "Exercise for 20 Minutes",
                description = "Complete a workout routine, calisthenics, gym session, or stretching.",
                category = QuestCategory.EXERCISE,
                difficulty = QuestDifficulty.MEDIUM,
                estimatedDurationMinutes = 20,
                rewardMinutes = 25
            ),
            Quest(
                id = "quest_clean_20",
                title = "Clean Your Room for 20 Minutes",
                description = "Organize your desk, make your bed, clean your living environment.",
                category = QuestCategory.CLEANING,
                difficulty = QuestDifficulty.EASY,
                estimatedDurationMinutes = 20,
                rewardMinutes = 15
            )
        )
    }

    private fun getCompletedIds(): Set<String> {
        return prefs.getStringSet(KEY_COMPLETED_QUEST_IDS, emptySet()) ?: emptySet()
    }

    fun getActiveQuestId(): String? {
        return prefs.getString(KEY_ACTIVE_QUEST_ID, null)
    }

    @Synchronized
    fun getAllQuests(): List<Quest> {
        val activeId = getActiveQuestId()
        val completedIds = getCompletedIds()

        return INITIAL_QUESTS.map { quest ->
            val status = when {
                completedIds.contains(quest.id) -> QuestStatus.COMPLETED
                quest.id == activeId -> QuestStatus.ACTIVE
                else -> QuestStatus.AVAILABLE
            }
            quest.copy(status = status)
        }
    }

    fun getActiveQuest(): Quest? {
        val activeId = getActiveQuestId() ?: return null
        return getAllQuests().find { it.id == activeId }
    }

    /**
     * Start a quest. Only 1 quest can be active at a time.
     * Cannot start a completed quest.
     */
    @Synchronized
    fun startQuest(questId: String): Boolean {
        val currentActive = getActiveQuestId()
        if (currentActive != null) {
            // Already have an active quest!
            return false
        }

        val completed = getCompletedIds()
        if (completed.contains(questId)) {
            // Cannot restart already completed quest in Phase 2.1
            return false
        }

        val exists = INITIAL_QUESTS.any { it.id == questId }
        if (!exists) return false

        prefs.edit().putString(KEY_ACTIVE_QUEST_ID, questId).apply()
        return true
    }

    /**
     * Completes active quest (development completion mechanism for Phase 2.1).
     * Issues reward to Time Bank exactly once.
     * Prevents duplicate completions.
     * Returns reward minutes earned, or null if completion failed.
     */
    @Synchronized
    fun completeActiveQuest(questId: String): Long? {
        val activeId = getActiveQuestId()
        if (activeId == null || activeId != questId) {
            return null
        }

        val completedIds = getCompletedIds().toMutableSet()
        if (completedIds.contains(questId)) {
            // Prevent duplicate claiming
            prefs.edit().remove(KEY_ACTIVE_QUEST_ID).apply()
            return null
        }

        val quest = INITIAL_QUESTS.find { it.id == questId } ?: return null

        // 1. Mark completed
        completedIds.add(questId)
        prefs.edit()
            .putStringSet(KEY_COMPLETED_QUEST_IDS, completedIds)
            .remove(KEY_ACTIVE_QUEST_ID)
            .apply()

        // 2. Issue reward to Time Bank
        timeBankRepository.addEarnedMinutes(quest.rewardMinutes)

        return quest.rewardMinutes
    }
}
