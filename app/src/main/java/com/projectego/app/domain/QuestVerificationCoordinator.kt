package com.projectego.app.domain

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.QuestEvidenceRepository
import com.projectego.app.data.QuestRepository
import com.projectego.app.model.DigitalQuestEvidence
import com.projectego.app.model.DigitalQuestVerificationResult
import com.projectego.app.model.PhysicalQuestEvidence
import com.projectego.app.model.PhysicalQuestRequirement
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestAttempt
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
    private val digitalQuestVerifier: DigitalQuestVerifier = DigitalQuestVerifier,
    // Physical-quest evidence and clock. The defaults measure nothing and use
    // the real clock, so every existing call site stays untouched; MainActivity
    // wires the real provider and repository.
    private val physicalEvidenceProvider: PhysicalQuestEvidenceProvider =
        PhysicalQuestEvidenceProvider.Unavailable,
    private val questEvidenceRepository: QuestEvidenceRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis
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

        // A PHYSICAL attempt is judged on the clock and the person's own word,
        // never on which apps were open, so it never touches the monitoring or
        // Focus checks below.
        if (attempt.method == VerificationMethod.PHYSICAL) return evaluatePhysicalAttempt(attempt)

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
     * The person said they finished a quest ALIVE cannot watch.
     *
     * The declaration is recorded first - `requestVerification()` stamps the end
     * of the window - and only then judged, so the measurement is of the time
     * they actually spent rather than of whenever the app next gets opened. The
     * gate is checked here as well as in the screen, because a button that should
     * have been disabled is exactly what a modified clock produces.
     *
     * @return true when the attempt reached VERIFIED and its reward was claimed.
     */
    fun declarePhysicalAttemptDone(): Boolean {
        val attempt = questRepository.getCurrentAttempt() ?: return false
        if (attempt.method != VerificationMethod.PHYSICAL) return false
        if (attempt.state != QuestAttemptState.IN_PROGRESS) return false
        if (PhysicalQuestVerifier.remainingMinutes(
                attempt.activityStartTimestamp,
                clock(),
                attempt.committedDurationMinutes
            ) > 0L
        ) return false

        // Snapshot the step counter at the moment the person declares done,
        // before the window is stamped closed by requestVerification().
        val evidenceRepository = questEvidenceRepository
        if (evidenceRepository != null &&
            requirementFor(attempt)?.needsSteps == true
        ) {
            evidenceRepository.recordStepsAtDeclare(
                attempt.attemptId,
                physicalEvidenceProvider.readStepCount()
            )
        }

        if (!questRepository.requestVerification()) return false
        return settlePhysicalAttempt(questRepository.getCurrentAttempt() ?: attempt)
    }

    /**
     * On resume, finish work a previous session left half-done: a declaration
     * that never reached a verdict, or a verdict that never reached its reward.
     *
     * A running physical attempt is deliberately left alone. It ends when the
     * person says it does, and nothing here may say it for them.
     */
    private fun evaluatePhysicalAttempt(attempt: QuestAttempt): Boolean = when (attempt.state) {
        QuestAttemptState.VERIFIED -> questRepository.claimReward() != null
        QuestAttemptState.PENDING_VERIFICATION -> settlePhysicalAttempt(attempt)
        else -> false
    }

    private fun settlePhysicalAttempt(attempt: QuestAttempt): Boolean {
        val timeResult = PhysicalQuestVerifier.verify(
            startedAt = attempt.activityStartTimestamp,
            declaredAt = attempt.activityEndTimestamp ?: clock(),
            committedMinutes = attempt.committedDurationMinutes
        )

        val requirement = requirementFor(attempt)
        val signalsResult =
            if (timeResult is PhysicalQuestVerifier.Result.Failed ||
                requirement == null || !requirement.hasAnyCheck
            ) {
                PhysicalQuestSignalsVerifier.Result.NotApplicable
            } else {
                PhysicalQuestSignalsVerifier.verify(
                    requirement,
                    gatherPhysicalEvidence(attempt, requirement),
                    attempt.committedDurationMinutes
                )
            }

        val outcome: Pair<VerificationOutcome, String> = when {
            timeResult is PhysicalQuestVerifier.Result.Waiting -> return false
            timeResult is PhysicalQuestVerifier.Result.Failed ->
                VerificationOutcome.FAIL to timeResult.reason

            signalsResult is PhysicalQuestSignalsVerifier.Result.Failed ->
                VerificationOutcome.FAIL to signalsResult.reason

            else -> VerificationOutcome.PASS to composePassReason(timeResult, signalsResult)
        }

        val verified = questRepository.verifyQuest(outcome.first, outcome.second)
        // The reward leaves through the one door every quest uses: claimReward()
        // after VERIFIED, and nothing else.
        if (verified) questRepository.claimReward()
        // Terminal in both directions: evidence belongs to one attempt and
        // must never outlive its verdict.
        questEvidenceRepository?.clear(attempt.attemptId)
        return verified
    }

    private fun gatherPhysicalEvidence(
        attempt: QuestAttempt,
        requirement: PhysicalQuestRequirement
    ): PhysicalQuestEvidence {
        val snapshot = questEvidenceRepository?.snapshotFor(attempt.attemptId)
        val distractions =
            if (requirement.checkDistractions && attempt.activityStartTimestamp != null) {
                physicalEvidenceProvider.entertainmentPackagesBetween(
                    attempt.activityStartTimestamp!!,
                    attempt.activityEndTimestamp ?: clock()
                )
            } else {
                null
            }
        return PhysicalQuestEvidence(
            stepsAtStart = snapshot?.startSteps,
            stepsAtDeclare = snapshot?.declareSteps,
            distractionPackages = distractions
        )
    }

    private fun composePassReason(
        time: PhysicalQuestVerifier.Result,
        signals: PhysicalQuestSignalsVerifier.Result
    ): String = listOf(
        (time as? PhysicalQuestVerifier.Result.Passed)?.reason,
        (signals as? PhysicalQuestSignalsVerifier.Result.Passed)?.reason
    ).filterNotNull().joinToString("; ")

    private fun requirementFor(attempt: QuestAttempt): PhysicalQuestRequirement? {
        val quest = questRepository.getAllQuests().find { it.id == attempt.questId }
            ?: return null
        return PhysicalQuestPolicy.requirementFor(quest.id, quest.category)
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
     * Resolves the quest category from the catalog the repository actually
     * serves, rather than a list of identifiers kept here.
     *
     * The list used to be hardcoded, and it had already fallen behind the board:
     * a quest added to the catalog without a matching line here would build
     * evidence with a null category and never verify, silently, forever.
     */
    private fun getQuestCategoryForAttempt(questId: String): QuestCategory? =
        questRepository.getAllQuests().find { it.id == questId }?.category
}
