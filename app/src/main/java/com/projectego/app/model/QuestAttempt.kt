package com.projectego.app.model

/**
 * Phase 2.3 quest verification lifecycle - PURE MODEL LAYER ONLY (step 2.3.1).
 *
 * No repository, persistence, Focus, OfflineSession, verification, or
 * reward LOGIC is implemented in this file. It only defines the shape of
 * a single quest attempt so later steps (repository wiring, Focus
 * integration, Offline Session, real verification, real reward issuance)
 * have a concrete data model to build on.
 */

/**
 * How a committed quest attempt will be verified.
 * Digital and Physical are deliberately separate strategies - there is
 * no single generic verifier that claims to prove every kind of activity.
 */
enum class VerificationMethod {
    DIGITAL,
    PHYSICAL
}

/**
 * Lifecycle of a single quest attempt under Phase 2.3:
 *
 *   AVAILABLE -> COMMITTED -> IN_PROGRESS -> PENDING_VERIFICATION -> VERIFIED -> REWARDED
 *
 * Failure can occur from COMMITTED, IN_PROGRESS, or PENDING_VERIFICATION -> FAILED.
 *
 * AVAILABLE represents a quest that has not been committed yet.
 * [QuestAttempt.commit] always starts a new attempt at COMMITTED - nothing
 * in this file enforces which transitions between states are legal; that
 * belongs to a later (repository) step.
 */
enum class QuestAttemptState {
    AVAILABLE,
    COMMITTED,
    IN_PROGRESS,
    PENDING_VERIFICATION,
    VERIFIED,
    REWARDED,
    FAILED
}

/** Outcome of evaluating a quest attempt's evidence. */
enum class VerificationOutcome {
    PASS,
    FAIL
}

/**
 * Record of a verification evaluation.
 *
 * [reason] must describe only what was actually checked (e.g. "Focus
 * session completed with 0 violations" or "Reflection was empty") and
 * never claim stronger proof than the system can actually know - EGO
 * must not claim it knows the user actually studied.
 */
data class VerificationResult(
    val outcome: VerificationOutcome,
    val reason: String,
    val evaluatedTimestamp: Long
)

/**
 * A single quest attempt moving through the Phase 2.3 verification pipeline.
 *
 * [committedDurationMinutes] is fixed at commit time and is intentionally
 * independent from any linked activity's own duration (e.g. a Focus
 * Session's duration). If a linked Focus Session is later extended via
 * Time Bank, that must NOT change what this attempt considers its
 * committed duration - the two are separate fields by design.
 */
data class QuestAttempt(
    val questId: String,
    val method: VerificationMethod,
    val state: QuestAttemptState,
    val committedTimestamp: Long,
    val committedDurationMinutes: Long,
    val activityStartTimestamp: Long? = null,
    val activityEndTimestamp: Long? = null,
    val verificationResult: VerificationResult? = null,
    val rewardMinutes: Long? = null,
    val rewardedTimestamp: Long? = null
) {
    companion object {
        /**
         * Creates a freshly committed attempt. No activity, verification,
         * or reward has happened yet - all of those fields are null and
         * [state] is [QuestAttemptState.COMMITTED].
         *
         * [now] defaults to the real clock but can be injected for
         * deterministic tests (the same lesson learned from
         * LockStateManager's hardcoded Date() call being untestable).
         */
        fun commit(
            questId: String,
            method: VerificationMethod,
            committedDurationMinutes: Long,
            now: Long = System.currentTimeMillis()
        ): QuestAttempt {
            return QuestAttempt(
                questId = questId,
                method = method,
                state = QuestAttemptState.COMMITTED,
                committedTimestamp = now,
                committedDurationMinutes = committedDurationMinutes
            )
        }
    }

    /** True only once verification has actually run and passed. */
    val isVerified: Boolean
        get() = verificationResult?.outcome == VerificationOutcome.PASS

    /** True only once a reward has actually been recorded for this attempt. */
    val isRewarded: Boolean
        get() = rewardMinutes != null
}
