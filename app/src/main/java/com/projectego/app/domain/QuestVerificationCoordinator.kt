package com.projectego.app.domain

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.model.DigitalQuestEvidence
import com.projectego.app.model.DigitalQuestVerificationResult
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.VerificationMethod
import com.projectego.app.model.VerificationOutcome

/**
 * QuestVerificationCoordinator — orchestration layer between repositories
 * and DigitalQuestVerifier.
 *
 * Responsibilities:
 * - Routes state and verification results only.
 * - Reads from QuestRepository and FocusSessionManager.
 * - Builds immutable DigitalQuestEvidence snapshot.
 * - Calls DigitalQuestVerifier for time-based evaluation.
 * - Applies results via QuestRepository methods.
 * - Does NOT implement verification rules (that is DigitalQuestVerifier's job).
 * - Does NOT award rewards directly.
 * - Does NOT handle startAttempt/linkFocusSession flow.
 */
class QuestVerificationCoordinator(
    private val questRepository: QuestRepository,
    private val focusSessionManager: FocusSessionManager,
    private val digitalQuestVerifier: DigitalQuestVerifier = DigitalQuestVerifier
) {

    /**
     * Entry point called from MainActivity.onResume().
     *
     * Evaluates the current DIGITAL quest attempt against its linked Focus session.
     *
     * @return true if a repository state transition actually succeeded, false otherwise.
     */
    fun evaluateAttemptOnResume(context: Context): Boolean {
        val attempt = questRepository.getCurrentAttempt() ?: return false

        // Only DIGITAL attempts are evaluated by this coordinator.
        if (attempt.method != VerificationMethod.DIGITAL) return false

        // Already VERIFIED but unrewarded (crash between verifyQuest() and
        // claimReward()). Do NOT re-run the verifier or verifyQuest() again -
        // claimReward() alone recovers the existing reward, and is idempotent
        // (a REWARDED attempt returns null).
        if (attempt.state == QuestAttemptState.VERIFIED) {
            return questRepository.claimReward() != null
        }

        // Monitoring-integrity check first (Section 9 of design).
        if (!isAccessibilityServiceEnabled(context)) {
            return onMonitoringLost(context)
        }

        val linkedFocusSessionId = attempt.linkedFocusSessionId ?: return false

        // Read the persisted Focus session (regardless of active status).
        val focusSession = focusSessionManager.getLastFocusSession() ?: return false

        // Structural check: the session must be the one linked to this attempt.
        if (focusSession.focusSessionId != linkedFocusSessionId) return false

        val evidence = DigitalQuestEvidence(
            attemptId = attempt.attemptId,
            questId = attempt.questId,
            questCategory = getQuestCategoryForAttempt(attempt.questId) ?: return false,
            method = attempt.method,
            committedDurationMinutes = attempt.committedDurationMinutes,
            activityStartTimestamp = attempt.activityStartTimestamp,
            linkedFocusSessionId = attempt.linkedFocusSessionId,
            focusSessionId = focusSession.focusSessionId,
            linkedQuestAttemptId = focusSession.linkedQuestAttemptId,
            focusActivityType = focusSession.activityType,
            violationCount = focusSession.violationCount,
            wasMonitoringActiveDuringAttempt = true, // already checked above
            evaluationTimestamp = System.currentTimeMillis()
        )

        return when (val result = digitalQuestVerifier.verify(evidence)) {
            is DigitalQuestVerificationResult.Verified -> {
                val verified = when (attempt.state) {
                    QuestAttemptState.IN_PROGRESS -> {
                        // Transition to PENDING_VERIFICATION, then apply VERIFIED.
                        if (!questRepository.requestVerification()) return false
                        questRepository.verifyQuest(VerificationOutcome.PASS, result.reason)
                    }
                    QuestAttemptState.PENDING_VERIFICATION -> {
                        // Already in pending verification (crash recovery); go directly to verified.
                        questRepository.verifyQuest(VerificationOutcome.PASS, result.reason)
                    }
                    else -> false // Not a valid state for this flow
                }
                // Reward only after the attempt is actually VERIFIED. claimReward()
                // itself requires state == VERIFIED and is idempotent (a REWARDED
                // attempt returns null), so this is safe on repeated resumes.
                if (verified) {
                    questRepository.claimReward()
                }
                verified
            }
            is DigitalQuestVerificationResult.NotReady -> {
                // No transition; still waiting for committed duration.
                false
            }
            is DigitalQuestVerificationResult.Failed -> {
                when (attempt.state) {
                    QuestAttemptState.IN_PROGRESS -> {
                        questRepository.failQuest(result.reason.name)
                    }
                    QuestAttemptState.PENDING_VERIFICATION -> {
                        // Already in pending verification (crash recovery); mark as failed directly.
                        questRepository.verifyQuest(VerificationOutcome.FAIL, result.reason.name)
                    }
                    else -> false // Not a valid state for this flow
                }
            }
        }
    }

    /**
     * Called by EgoAccessibilityService when a confirmed violation occurs.
     *
     * Immediate FAIL; bypasses PENDING_VERIFICATION entirely.
     *
     * @return true if failQuest() actually transitioned the attempt, false otherwise.
     */
    fun onViolationConfirmed(): Boolean {
        val attempt = questRepository.getCurrentAttempt() ?: return false
        if (attempt.method != VerificationMethod.DIGITAL) return false
        if (attempt.state != QuestAttemptState.IN_PROGRESS && attempt.state != QuestAttemptState.COMMITTED) return false
        if (attempt.linkedFocusSessionId == null) return false

        return questRepository.failQuest("1 confirmed restricted-app violation during Focus session")
    }

    /**
     * Called when monitoring integrity is lost (accessibility service disabled).
     *
     * Technical failure; distinct from violation. Never claims the user violated.
     *
     * @return true if failQuest() actually transitioned the attempt, false otherwise.
     */
    fun onMonitoringLost(context: Context): Boolean {
        val attempt = questRepository.getCurrentAttempt() ?: return false
        if (attempt.method != VerificationMethod.DIGITAL) return false
        if (attempt.state != QuestAttemptState.IN_PROGRESS && attempt.state != QuestAttemptState.COMMITTED) return false

        // If already disabled, this is a re-entrant call; still safe.
        if (isAccessibilityServiceEnabled(context)) return false

        return questRepository.failQuest("Monitoring integrity was lost, so this attempt can no longer be verified")
    }

    /**
     * Checks whether this app's accessibility service is currently enabled.
     * Mirrors the existing Settings.Secure mechanism used in MainActivity.
     */
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/com.projectego.app.service.EgoAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Resolves the quest category from the static quest catalog.
     * Returns null for unknown quest IDs (non-DIGITAL quests are already filtered).
     */
    private fun getQuestCategoryForAttempt(questId: String): QuestCategory? {
        return when (questId) {
            "quest_study_30" -> QuestCategory.STUDY
            "quest_read_30" -> QuestCategory.READING
            "quest_walk_20" -> QuestCategory.WALKING
            "quest_exercise_20" -> QuestCategory.EXERCISE
            "quest_clean_20" -> QuestCategory.CLEANING
            else -> null
        }
    }
}
