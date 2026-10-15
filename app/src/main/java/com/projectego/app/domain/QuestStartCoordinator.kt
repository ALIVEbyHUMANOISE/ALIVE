package com.projectego.app.domain

import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.QuestEvidenceRepository
import com.projectego.app.data.QuestRepository
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.VerificationMethod

/**
 * QuestStartCoordinator — orchestration layer for starting a DIGITAL quest.
 *
 * Centralizes the startup sequence so no caller has to reproduce it:
 *
 *   1. validate the quest exists and is not already completed/active
 *   2. commitQuest(questId, DIGITAL, estimatedDurationMinutes)
 *   3. startAttempt()
 *   4. read the current QuestAttempt (attemptId)
 *   5. startFocusSession(matching FocusActivityType, quest.title,
 *                        estimatedDurationMinutes, attempt.attemptId)
 *   6. read the created FocusSession
 *   7. linkFocusSession(focusSession.focusSessionId)
 *
 * Only DIGITAL quests in STUDY/CODING/WRITING/CREATIVE go through
 * [startDigitalQuest]; every other category goes through
 * [startPhysicalQuest], which starts a clock and no session.
 *
 * Failure handling: once the attempt is IN_PROGRESS, any failure in steps
 * 5-7 fails the attempt via failQuest(reason) with an explicit technical
 * reason. An IN_PROGRESS DIGITAL attempt is therefore never left without a
 * linked FocusSession.
 *
 * This coordinator does NOT grant rewards and does NOT perform verification.
 */
class QuestStartCoordinator(
    private val questRepository: QuestRepository,
    private val focusSessionManager: FocusSessionManager,
    // ponytail: test seams (default = real collaborators). Remove if a
    // collaborator interface is introduced later; exists only so the
    // Focus-creation / Focus-link failure paths are unit-testable.
    private val startFocusSession: (
        type: FocusActivityType,
        goal: String,
        durationMinutes: Long,
        linkedQuestAttemptId: String
    ) -> Boolean = { type, goal, durationMinutes, linkedQuestAttemptId ->
        focusSessionManager.startFocusSession(
            activityType = type,
            goal = goal,
            durationMinutes = durationMinutes,
            linkedQuestAttemptId = linkedQuestAttemptId
        )
    },
    private val linkFocusSession: (focusSessionId: String) -> Boolean = { focusSessionId ->
        questRepository.linkFocusSession(focusSessionId)
    },
    // Physical-quest evidence, recorded the moment a steps-quest starts. The
    // defaults measure nothing, which keeps every existing call site untouched.
    private val physicalEvidenceProvider: PhysicalQuestEvidenceProvider =
        PhysicalQuestEvidenceProvider.Unavailable,
    private val questEvidenceRepository: QuestEvidenceRepository? = null
) {

    companion object {
        const val REASON_FOCUS_CREATION_FAILED =
            "Focus session could not be created for this digital attempt"
        const val REASON_FOCUS_READ_FAILED =
            "Created Focus session could not be read back for this digital attempt"
        const val REASON_FOCUS_LINK_FAILED =
            "Focus session could not be linked to this digital attempt"
        const val REASON_ATTEMPT_START_FAILED =
            "Attempt could not be started for this digital quest"

        /** Maps a quest category to its matching Focus activity type, or null if unsupported. */
        fun focusActivityTypeFor(category: QuestCategory): FocusActivityType? = when (category) {
            QuestCategory.STUDY -> FocusActivityType.STUDY
            QuestCategory.CODING -> FocusActivityType.CODING
            QuestCategory.WRITING -> FocusActivityType.WRITING
            QuestCategory.CREATIVE -> FocusActivityType.CREATIVE
            else -> null
        }
    }

    /**
     * Runs the full DIGITAL startup flow for [questId].
     *
     * @return true only when the entire startup flow succeeded, i.e. the
     *         attempt is IN_PROGRESS and linked to the created Focus session.
     */
    @Synchronized
    fun startDigitalQuest(questId: String): Boolean {
        // 1. Quest must exist and not be permanently completed.
        // NOTE: QuestStatus.ACTIVE is a legacy KEY_ACTIVE_QUEST_ID artifact
        // and is deliberately NOT used here - the authoritative "another
        // attempt is active" guard is commitQuest()'s active-attempt check.
        val quest = questRepository.getAllQuests().find { it.id == questId } ?: return false
        if (quest.status == QuestStatus.COMPLETED) return false

        val activityType = focusActivityTypeFor(quest.category) ?: return false

        val durationMinutes = quest.estimatedDurationMinutes.toLong()

        // 2. Commit the attempt.
        if (!questRepository.commitQuest(questId, VerificationMethod.DIGITAL, durationMinutes)) {
            return false
        }

        // 3. Start the attempt.
        if (!questRepository.startAttempt()) {
            questRepository.failQuest(REASON_ATTEMPT_START_FAILED)
            return false
        }

        // 4. Read the attempt for its attemptId.
        val attempt = questRepository.getCurrentAttempt()
        if (attempt == null) {
            questRepository.failQuest(REASON_ATTEMPT_START_FAILED)
            return false
        }

        // 5. Create the Focus session bound to this attempt.
        val created = startFocusSession(
            activityType,
            quest.title,
            durationMinutes,
            attempt.attemptId
        )
        if (!created) {
            questRepository.failQuest(REASON_FOCUS_CREATION_FAILED)
            return false
        }

        // 6. Read the created Focus session. Use the active-status-agnostic
        // read (DESIGN §5/§6): the just-created session is the most recent
        // persisted one, and must be read back even if it is not currently
        // reported as "active" (e.g. an already-expired edge).
        val focusSession = focusSessionManager.getLastFocusSession()
        if (focusSession == null) {
            questRepository.failQuest(REASON_FOCUS_READ_FAILED)
            return false
        }

        // The read-back session must be the one created for THIS attempt;
        // never link a stale/unrelated Focus session.
        if (focusSession.linkedQuestAttemptId != attempt.attemptId) {
            questRepository.failQuest(REASON_FOCUS_READ_FAILED)
            return false
        }

        // 7. Link the Focus session to the attempt.
        if (!linkFocusSession(focusSession.focusSessionId)) {
            questRepository.failQuest(REASON_FOCUS_LINK_FAILED)
            return false
        }

        return true
    }

    /**
     * Runs the full PHYSICAL startup flow for [questId]: commit, start, and
     * nothing else. No Focus session is created, because a Focus session watches
     * a screen and a walk does not happen on one.
     *
     * The attempt is left IN_PROGRESS with its start timestamp written, which is
     * the only clock [PhysicalQuestVerifier] can measure a later self-report
     * against - so starting is what commits ALIVE to the time, not just the
     * person.
     *
     * @return true only when the attempt is IN_PROGRESS and can be declared done.
     */
    @Synchronized
    fun startPhysicalQuest(questId: String): Boolean {
        val quest = questRepository.getAllQuests().find { it.id == questId } ?: return false
        if (quest.status == QuestStatus.COMPLETED) return false

        // A category ALIVE can watch does not come through here. Two paths for the
        // same quest would mean two answers to whether it was earned.
        if (focusActivityTypeFor(quest.category) != null) return false

        val durationMinutes = quest.estimatedDurationMinutes.toLong()

        if (!questRepository.commitQuest(questId, VerificationMethod.PHYSICAL, durationMinutes)) {
            return false
        }
        if (!questRepository.startAttempt()) {
            questRepository.failQuest(REASON_ATTEMPT_START_FAILED)
            return false
        }

        // Snapshot the step counter at the moment the attempt's clock starts,
        // but only for quests whose policy measures steps. Recording the
        // unavailable marker (null) is still a record: a missing snapshot is
        // what the verifier treats as "never recorded".
        val evidenceRepository = questEvidenceRepository
        if (evidenceRepository != null &&
            PhysicalQuestPolicy.requirementFor(quest.id, quest.category).needsSteps
        ) {
            val attempt = questRepository.getCurrentAttempt()
            if (attempt != null) {
                evidenceRepository.recordStepsAtStart(
                    attempt.attemptId,
                    physicalEvidenceProvider.readStepCount()
                )
            }
        }
        return true
    }
}
