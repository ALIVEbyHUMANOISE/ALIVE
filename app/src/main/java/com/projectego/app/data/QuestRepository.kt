package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestCategory
import com.projectego.app.R
import com.projectego.app.model.QuestDifficulty
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.VerificationMethod
import com.projectego.app.model.VerificationOutcome
import com.projectego.app.model.VerificationResult
import java.util.UUID

class QuestRepository(
    context: Context,
    private val timeBankRepository: TimeBankRepository
) {

    // Kept as given, not applicationContext: below API 33 AppCompat's per-app
    // language lands on the Activity's configuration, so text resolved through an
    // application context can stay in the wrong language. This repository lives and
    // dies with the activity that owns it either way.
    private val context: Context = context

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)

    init {
        migrateLegacyQuestStateIfNeeded()
    }

    /**
     * Phase 2.3.3 one-time migration away from the legacy Phase 2.1 quest
     * lifecycle. Clears ONLY [KEY_ACTIVE_QUEST_ID] and
     * [KEY_COMPLETED_QUEST_IDS] - the old state could otherwise render a
     * quest as completed/active and block it from being started.
     *
     * Idempotent: guarded by [KEY_LEGACY_QUEST_STATE_MIGRATED] inside the
     * same synchronous commit, so it runs at most once per install.
     *
     * Deliberately does NOT touch the QuestAttempt blob, TimeBank, Focus
     * session data, or any other SharedPreferences file.
     */
    @Synchronized
    private fun migrateLegacyQuestStateIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_QUEST_STATE_MIGRATED, false)) return

        prefs.edit()
            .remove(KEY_ACTIVE_QUEST_ID)
            .remove(KEY_COMPLETED_QUEST_IDS)
            .putBoolean(KEY_LEGACY_QUEST_STATE_MIGRATED, true)
            .commit()
    }

    companion object {
        private const val KEY_ACTIVE_QUEST_ID = "key_active_quest_id"
        private const val KEY_COMPLETED_QUEST_IDS = "key_completed_quest_ids"

        // Phase 2.3.3: one-time migration marker. When this key is present,
        // the legacy Phase 2.1 quest lifecycle state (active id + completed
        // ids) has already been cleared and will never be cleared again.
        private const val KEY_LEGACY_QUEST_STATE_MIGRATED = "key_legacy_quest_state_migrated_v1"

        // Phase 2.3.2: the current QuestAttempt is persisted as a single
        // serialized string under one key, rather than scattering its
        // fields across many separate preference keys.
        private const val KEY_CURRENT_ATTEMPT = "key_current_quest_attempt"

        // ASCII Unit Separator (0x1F). Chosen over a visible delimiter like
        // '|' or ',' because it will essentially never appear inside a
        // human-authored verification "reason" string, so the manual
        // serialization below stays safe without a JSON dependency.
        private const val ATTEMPT_FIELD_SEPARATOR = "\u001F"

        // Phase 2.3.3 Step 2: field count grew from 12 to 14 to add
        // attemptId and linkedFocusSessionId. LEGACY_ATTEMPT_FIELD_COUNT
        // lets deserializeAttempt() safely migrate records persisted
        // before this step, rather than discarding them.
        private const val ATTEMPT_FIELD_COUNT = 14
        private const val LEGACY_ATTEMPT_FIELD_COUNT = 12

        // The board's quests, by id. The text lives in resources so the board can
        // speak the language the screen is speaking; only these ids ever reach
        // storage, so re-resolving the words on every read costs nothing.
        val INITIAL_QUEST_IDS = listOf(
            "quest_walk_20", "quest_study_30", "quest_read_30",
            "quest_exercise_20", "quest_clean_20", "quest_draw_20"
        )
    }

    /**
     * The quest board, built from resources.
     *
     * Titles and descriptions used to be English literals baked into the catalog,
     * which is how an Indonesian app ended up showing "Walk 20 Minutes" on every
     * card. Nothing stores a title, so this is resolved fresh on each read and
     * follows the language the moment it changes.
     */
    private fun catalog(): List<Quest> = listOf(
        Quest(
            id = "quest_walk_20",
            title = context.getString(R.string.quest_walk_title),
            description = context.getString(R.string.quest_walk_body),
            category = QuestCategory.WALKING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 15
        ),
        Quest(
            id = "quest_study_30",
            title = context.getString(R.string.quest_study_title),
            description = context.getString(R.string.quest_study_body),
            category = QuestCategory.STUDY,
            difficulty = QuestDifficulty.MEDIUM,
            estimatedDurationMinutes = 30,
            rewardMinutes = 20
        ),
        Quest(
            id = "quest_read_30",
            title = context.getString(R.string.quest_read_title),
            description = context.getString(R.string.quest_read_body),
            category = QuestCategory.READING,
            difficulty = QuestDifficulty.MEDIUM,
            estimatedDurationMinutes = 30,
            rewardMinutes = 20
        ),
        Quest(
            id = "quest_exercise_20",
            title = context.getString(R.string.quest_exercise_title),
            description = context.getString(R.string.quest_exercise_body),
            category = QuestCategory.EXERCISE,
            difficulty = QuestDifficulty.MEDIUM,
            estimatedDurationMinutes = 20,
            rewardMinutes = 25
        ),
        Quest(
            id = "quest_clean_20",
            title = context.getString(R.string.quest_clean_title),
            description = context.getString(R.string.quest_clean_body),
            category = QuestCategory.CLEANING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 15
        ),
        Quest(
            id = "quest_draw_20",
            title = context.getString(R.string.quest_draw_title),
            description = context.getString(R.string.quest_draw_body),
            category = QuestCategory.CREATIVE,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 15
        )
    )

    // ============================================================
    // Legacy Phase 2.1 quest board (AVAILABLE/ACTIVE/COMPLETED).
    // Unchanged in Phase 2.3.2 - still drives MainActivity's existing
    // "AMBIL" quest board UI, which is not being touched in this step.
    // claimReward() (below) adds to KEY_COMPLETED_QUEST_IDS so a quest
    // rewarded through the new pipeline correctly shows COMPLETED here.
    // ============================================================


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

        return catalog().map { quest ->
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

        val exists = INITIAL_QUEST_IDS.contains(questId)
        if (!exists) return false

        prefs.edit().putString(KEY_ACTIVE_QUEST_ID, questId).apply()
        return true
    }

    // ============================================================
    // Phase 2.3.2: QuestAttempt verification pipeline.
    //
    //   AVAILABLE -> COMMITTED -> IN_PROGRESS -> PENDING_VERIFICATION -> VERIFIED -> REWARDED
    //   (COMMITTED | IN_PROGRESS | PENDING_VERIFICATION) -> FAILED
    //
    // No real DIGITAL/PHYSICAL verification logic exists yet - verifyQuest()
    // takes its outcome/reason as parameters supplied by the caller. No
    // Focus/OfflineSession/UI integration exists yet.
    //
    // Removed in this phase: completeActiveQuest(questId). It directly
    // granted a TimeBank reward with no verification step at all, which is
    // now a forbidden second reward path. Reward only ever flows through
    // claimReward() after state == VERIFIED.
    // ============================================================

    /** Returns whatever QuestAttempt is currently persisted, or null if none has ever been committed. */
    @Synchronized
    fun getCurrentAttempt(): QuestAttempt? {
        val raw = prefs.getString(KEY_CURRENT_ATTEMPT, null) ?: return null
        return deserializeAttempt(raw)
    }

    /** Only these states count as an active/current attempt; REWARDED and FAILED are terminal. */
    private fun isActiveAttemptState(state: QuestAttemptState): Boolean {
        return state == QuestAttemptState.COMMITTED ||
            state == QuestAttemptState.IN_PROGRESS ||
            state == QuestAttemptState.PENDING_VERIFICATION ||
            state == QuestAttemptState.VERIFIED
    }

    /**
     * Commits to a quest attempt. Rejects:
     * - an unknown quest id
     * - a quest that is already permanently completed
     * - a non-positive committed duration
     * - committing while another attempt is still active
     *   (COMMITTED/IN_PROGRESS/PENDING_VERIFICATION/VERIFIED)
     *
     * committedDurationMinutes is authoritative for this attempt and is
     * never later derived from any linked activity's own duration.
     */
    @Synchronized
    fun commitQuest(questId: String, method: VerificationMethod, committedDurationMinutes: Long): Boolean {
        val exists = INITIAL_QUEST_IDS.contains(questId)
        if (!exists) return false

        if (getCompletedIds().contains(questId)) return false

        if (committedDurationMinutes <= 0) return false

        val current = getCurrentAttempt()
        if (current != null && isActiveAttemptState(current.state)) return false

        val attempt = QuestAttempt.commit(
            questId = questId,
            method = method,
            committedDurationMinutes = committedDurationMinutes
        )
        persistAttempt(attempt)
        return true
    }

    /** COMMITTED -> IN_PROGRESS. Only succeeds if the current attempt is COMMITTED. No reward is granted. */
    @Synchronized
    fun startAttempt(): Boolean {
        val current = getCurrentAttempt() ?: return false
        if (current.state != QuestAttemptState.COMMITTED) return false

        val updated = current.copy(
            state = QuestAttemptState.IN_PROGRESS,
            activityStartTimestamp = System.currentTimeMillis()
        )
        persistAttempt(updated)
        return true
    }

    /** IN_PROGRESS -> PENDING_VERIFICATION. Does not auto-PASS and does not grant reward. */
    @Synchronized
    fun requestVerification(): Boolean {
        val current = getCurrentAttempt() ?: return false
        if (current.state != QuestAttemptState.IN_PROGRESS) return false

        val updated = current.copy(
            state = QuestAttemptState.PENDING_VERIFICATION,
            activityEndTimestamp = System.currentTimeMillis()
        )
        persistAttempt(updated)
        return true
    }

    /**
     * PENDING_VERIFICATION -> VERIFIED (PASS) or FAILED (FAIL). Only
     * operates on PENDING_VERIFICATION. Does not grant reward - claimReward()
     * is the only operation that does that.
     *
     * There is no real verification logic in this phase; [outcome] and
     * [reason] are supplied by the caller. [reason] must describe only
     * what was actually checked, never invented evidence.
     */
    @Synchronized
    fun verifyQuest(outcome: VerificationOutcome, reason: String): Boolean {
        val current = getCurrentAttempt() ?: return false
        if (current.state != QuestAttemptState.PENDING_VERIFICATION) return false

        val result = VerificationResult(
            outcome = outcome,
            reason = reason,
            evaluatedTimestamp = System.currentTimeMillis()
        )
        val newState = if (outcome == VerificationOutcome.PASS) {
            QuestAttemptState.VERIFIED
        } else {
            QuestAttemptState.FAILED
        }
        persistAttempt(current.copy(state = newState, verificationResult = result))
        return true
    }

    /**
     * Explicit failure from COMMITTED or IN_PROGRESS (e.g. abandoning an
     * attempt before it ever reaches verification). PENDING_VERIFICATION
     * failures go through [verifyQuest] instead, so a FAIL outcome from
     * there is always paired with a VerificationResult explaining what was
     * actually checked.
     */
    @Synchronized
    fun failQuest(reason: String): Boolean {
        val current = getCurrentAttempt() ?: return false
        if (current.state != QuestAttemptState.COMMITTED &&
            current.state != QuestAttemptState.IN_PROGRESS
        ) {
            return false
        }

        val result = VerificationResult(
            outcome = VerificationOutcome.FAIL,
            reason = reason,
            evaluatedTimestamp = System.currentTimeMillis()
        )
        persistAttempt(current.copy(state = QuestAttemptState.FAILED, verificationResult = result))
        return true
    }

    /**
     * The ONLY operation that transfers a quest reward into TimeBank.
     * Requires state == VERIFIED. Grants exactly once: once the attempt is
     * REWARDED, calling this again returns null and does not add the
     * reward a second time.
     *
     * Crash-consistency (corrected in the 2.3.2 correction pass): the
     * REWARDED state is persisted using SharedPreferences.Editor.commit()
     * (synchronous) instead of this repository's usual apply()-based
     * persistAttempt() helper, and this happens BEFORE the TimeBank
     * minutes are granted. apply() only guarantees the in-memory
     * SharedPreferences is updated immediately - it does NOT guarantee the
     * write has reached disk before the next line of code runs, so a
     * process death right after an apply()-based write could still leave
     * a retry seeing the pre-crash (VERIFIED) state and granting the
     * reward twice. commit() blocks until the write is confirmed (or
     * fails) before this method is allowed to proceed to the TimeBank
     * grant, which specifically closes that window for this one
     * operation. If commit() returns false, no reward is granted, the
     * quest is not marked permanently completed, and the attempt is left
     * as VERIFIED (nothing was actually written to disk, so its
     * previous, safe state is simply unchanged) - a later retry of
     * claimReward() can try again.
     *
     * This synchronous write narrows the specific crash window before the
     * TimeBank grant. It does NOT make this operation a database
     * transaction and does NOT provide full atomicity: the quest-attempt
     * write (this preferences file) and the TimeBank write (a separate
     * preferences file) are still two independent writes with no
     * cross-file guarantee. A crash between a successful commit() here
     * and the TimeBank write below would still leave the attempt REWARDED
     * with no TimeBank minutes granted - a lost reward, not a duplicated
     * one, which remains the safer failure direction. Full transactional
     * exactly-once semantics are not claimed.
     */
    @Synchronized
    fun claimReward(): Long? {
        val current = getCurrentAttempt() ?: return null
        if (current.state != QuestAttemptState.VERIFIED) return null

        val quest = catalog().find { it.id == current.questId } ?: return null

        val rewardedAttempt = current.copy(
            state = QuestAttemptState.REWARDED,
            rewardMinutes = quest.rewardMinutes,
            rewardedTimestamp = System.currentTimeMillis()
        )

        // Critical persistence step: synchronous commit(), not the shared
        // apply()-based persistAttempt() helper used everywhere else in
        // this repository. See the crash-consistency note above.
        val persisted = prefs.edit()
            .putString(KEY_CURRENT_ATTEMPT, serializeAttempt(rewardedAttempt))
            .commit()

        if (!persisted) {
            // Persistence could not be confirmed - do not grant the
            // reward and do not mark the quest permanently completed.
            return null
        }

        timeBankRepository.addEarnedMinutes(quest.rewardMinutes)

        val completedIds = getCompletedIds().toMutableSet()
        completedIds.add(current.questId)
        prefs.edit().putStringSet(KEY_COMPLETED_QUEST_IDS, completedIds).apply()

        // A kept day is decided by whether anything was earned on it, so this is
        // the one moment that fact gets written. apply() is enough here: a lost
        // marker can cost a day, it can never grant one.
        LockStateManager.recordQuestCompleted(context)

        return quest.rewardMinutes
    }

    private fun persistAttempt(attempt: QuestAttempt) {
        prefs.edit().putString(KEY_CURRENT_ATTEMPT, serializeAttempt(attempt)).apply()
    }

    /**
     * Links a Focus session to the current Digital quest attempt.
     *
     * Succeeds ONLY when:
     * - there is a current attempt
     * - attempt state is IN_PROGRESS
     * - verification method is DIGITAL
     * - linkedFocusSessionId is currently null
     * - focusSessionId is non-blank
     *
     * Does not alter TimeBank or completed history.
     * Does not change any other state transition.
     * Repeated linking (when already linked) fails rather than overwriting.
     */
    @Synchronized
    fun linkFocusSession(focusSessionId: String): Boolean {
        if (focusSessionId.isBlank()) {
            return false
        }

        val current = getCurrentAttempt() ?: return false

        // Check eligibility
        if (current.state != QuestAttemptState.IN_PROGRESS) {
            return false
        }

        if (current.method != VerificationMethod.DIGITAL) {
            return false
        }

        if (current.linkedFocusSessionId != null) {
            // Already linked; do not overwrite
            return false
        }

        // Create updated attempt with linkage
        val updated = current.copy(linkedFocusSessionId = focusSessionId)
        persistAttempt(updated)

        return true
    }

    private fun serializeAttempt(attempt: QuestAttempt): String {
        val safeReason = attempt.verificationResult?.reason
            ?.replace(ATTEMPT_FIELD_SEPARATOR, " ") ?: ""
        val fields = listOf(
            attempt.questId,
            attempt.method.name,
            attempt.state.name,
            attempt.committedTimestamp.toString(),
            attempt.committedDurationMinutes.toString(),
            attempt.activityStartTimestamp?.toString() ?: "",
            attempt.activityEndTimestamp?.toString() ?: "",
            attempt.verificationResult?.outcome?.name ?: "",
            safeReason,
            attempt.verificationResult?.evaluatedTimestamp?.toString() ?: "",
            attempt.rewardMinutes?.toString() ?: "",
            attempt.rewardedTimestamp?.toString() ?: "",
            attempt.attemptId,
            attempt.linkedFocusSessionId ?: ""
        )
        return fields.joinToString(ATTEMPT_FIELD_SEPARATOR)
    }

    private fun deserializeAttempt(raw: String): QuestAttempt? {
        val parts = raw.split(ATTEMPT_FIELD_SEPARATOR)

        // Phase 2.3.3 Step 2: accept both the current 14-field format and
        // the legacy 12-field format persisted before Step 1 introduced
        // attemptId/linkedFocusSessionId. Anything else is corrupt or
        // unrecognized data.
        if (parts.size != ATTEMPT_FIELD_COUNT && parts.size != LEGACY_ATTEMPT_FIELD_COUNT) {
            return null
        }

        return try {
            val outcomeName = parts[7]
            val verificationResult = if (outcomeName.isEmpty()) {
                null
            } else {
                VerificationResult(
                    outcome = VerificationOutcome.valueOf(outcomeName),
                    reason = parts[8],
                    evaluatedTimestamp = parts[9].toLong()
                )
            }

            // Legacy (12-field) records have no attemptId/linkedFocusSessionId
            // at all. Generating a fresh UUID here is the one-time migration
            // fallback for a MISSING legacy field - it is NOT the same as
            // regenerating an id that was actually stored: a 14-field
            // record's stored attemptId (parts[12]) is always used as-is,
            // never replaced.
            val attemptId = if (parts.size == ATTEMPT_FIELD_COUNT) {
                parts[12]
            } else {
                UUID.randomUUID().toString()
            }
            val linkedFocusSessionId = if (parts.size == ATTEMPT_FIELD_COUNT) {
                parts[13].takeIf { it.isNotEmpty() }
            } else {
                null
            }

            QuestAttempt(
                attemptId = attemptId,
                questId = parts[0],
                method = VerificationMethod.valueOf(parts[1]),
                state = QuestAttemptState.valueOf(parts[2]),
                committedTimestamp = parts[3].toLong(),
                committedDurationMinutes = parts[4].toLong(),
                activityStartTimestamp = parts[5].takeIf { it.isNotEmpty() }?.toLong(),
                activityEndTimestamp = parts[6].takeIf { it.isNotEmpty() }?.toLong(),
                verificationResult = verificationResult,
                rewardMinutes = parts[10].takeIf { it.isNotEmpty() }?.toLong(),
                rewardedTimestamp = parts[11].takeIf { it.isNotEmpty() }?.toLong(),
                linkedFocusSessionId = linkedFocusSessionId
            )
        } catch (e: Exception) {
            // Defensive: any parse failure means "no valid attempt" rather
            // than a crash. This should not happen in normal operation.
            null
        }
    }
}
