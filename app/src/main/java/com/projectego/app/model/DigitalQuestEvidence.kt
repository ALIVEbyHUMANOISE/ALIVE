package com.projectego.app.model

/**
 * Immutable snapshot of all evidence required for Digital Quest verification.
 *
 * This class represents a read-only view of the state at a specific point
 * in time. It does not contain any references to Android APIs, Context,
 * repositories, or mutable state.
 *
 * Fields correspond to locked Phase 2.3.3 design requirements:
 * - attemptId must be non-blank
 * - linkedFocusSessionId must match FocusSession.focusSessionId exactly
 * - linkedQuestAttemptId must match QuestAttempt.attemptId exactly
 * - activityStartTimestamp is authoritative for duration calculations
 * - violationCount reflects total confirmed violations during session
 * - commitment clock is independent of Time Bank extensions
 */
data class DigitalQuestEvidence(
    val attemptId: String,
    val questId: String,
    val questCategory: QuestCategory,
    val method: VerificationMethod,
    val committedDurationMinutes: Long,
    val activityStartTimestamp: Long?,
    val linkedFocusSessionId: String?,
    val focusSessionId: String?,
    val linkedQuestAttemptId: String?,
    val focusActivityType: FocusActivityType,
    val violationCount: Int,
    val wasMonitoringActiveDuringAttempt: Boolean,
    val evaluationTimestamp: Long
) {
    companion object {
        fun validateEvidence(evidence: DigitalQuestEvidence?): Pair<Boolean, String?> {
            if (evidence == null) return Pair(false, "No evidence provided")

            if (evidence.method != VerificationMethod.DIGITAL) {
                return Pair(false, "Only DIGITAL quests supported by this verifier")
            }

            if (evidence.attemptId.isBlank()) {
                return Pair(false, "Attempt ID is blank")
            }

            if (evidence.linkedFocusSessionId.isNullOrBlank()) {
                return Pair(false, "Linked Focus session ID is missing")
            }

            if (evidence.focusSessionId.isNullOrBlank()) {
                return Pair(false, "Focus session ID is missing")
            }

            if (evidence.activityStartTimestamp == null) {
                return Pair(false, "Activity start timestamp is missing")
            }

            return Pair(true, null)
        }
    }
}
