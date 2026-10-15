package com.projectego.app.model

import java.util.UUID

enum class FocusActivityType(val displayName: String) {
    STUDY("Study"),
    WORK("Work"),
    CODING("Coding"),
    CREATIVE("Creative"),
    WRITING("Writing")
}

enum class FocusState {
    NO_FOCUS,
    ACTIVE,
    EXPIRED
}

/**
 * Phase 2.3.3 Step 1: adds [focusSessionId] (explicit unique identity),
 * [linkedQuestAttemptId] (explicit correlation to a QuestAttempt's own
 * `attemptId`), and [violationCount], per PHASE_2_3_3_DESIGN.md.
 *
 * Persistence of these three fields (actually saving/restoring them in
 * FocusSessionManager) and the increment/decrement behavior of
 * [violationCount] (a future `recordViolation()` on FocusSessionManager)
 * are NOT implemented in this step - that is explicitly deferred to a
 * later Phase 2.3.3 step. No Accessibility-specific logic is included
 * here; this file remains a pure model with no Android dependency.
 */
data class FocusSession(
    val activityType: FocusActivityType,
    val goal: String,
    val durationMinutes: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val isActive: Boolean = true,
    val focusSessionId: String = UUID.randomUUID().toString(),
    val linkedQuestAttemptId: String? = null,
    val violationCount: Int = 0,

    /**
     * The apps this session allows, when the person named them.
     *
     * Null - the default, and what every existing session carries - means the
     * session allows whatever its activity type allows, exactly as before. A
     * self-written mission fills this in instead, because its list of apps is
     * the one thing about it that cannot come from a catalog.
     */
    val approvedOverride: Set<String>? = null
) {
    fun getRemainingMillis(): Long {
        val now = System.currentTimeMillis()
        return (endTimestamp - now).coerceAtLeast(0L)
    }

    fun getRemainingMinutes(): Long {
        return getRemainingMillis() / 1000 / 60
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= endTimestamp
    }
}
