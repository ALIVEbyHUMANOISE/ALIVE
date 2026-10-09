package com.projectego.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.projectego.app.R
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.PhysicalQuestRequirement
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestDifficulty
import com.projectego.app.model.TimeStatus

/**
 * What ALIVE means by a state, and the only copy for it.
 *
 * Nothing in this file reads or writes the engine. It takes values that the UI
 * has already been given and decides how they look and what they say, so the
 * whole status vocabulary can be tested without an Android runtime.
 *
 * Three rules the rest of the UI depends on:
 *  - `PENDING_VERIFICATION` is not `VERIFIED`, and `VERIFIED` is not `REWARDED`.
 *    Each gets its own headline, tone and annotation.
 *  - An engine string is never shown. Anything that arrives as a reason is
 *    classified first, then rephrased.
 *  - Color is never the only signal. Every tone here also changes surface,
 *    wording, or both.
 */

/** The character's emotional state. A display heuristic, not an engine fact. */
enum class AliveStage { DISCONNECTED, UNAWARE, CONTROLLED, AWARE, RECLAIMING, ALIVE }

/**
 * The character's face and pose, straight from the sprite sheet.
 *
 * The sheet is one vocabulary: expressions and activity poses live in the same
 * enum because every surface picks exactly one sprite. Copy about a state still
 * uses [AliveStage]; this enum only chooses how ALIVE looks.
 */
enum class AliveFace {
    NORMAL, HAPPY, SAD, SLEEPY, ANGRY, ANGRY_FULL, EXCITED, CONFUSED,
    THINKING, WORRIED, TIRED, PHONE, BOOK, LAPTOP, CAMERA, PENCIL, WAVING
}

/**
 * How a block of the interface is dressed - what happened, not how heavy it is.
 *
 * Weight (`SurfaceWeight`) is a separate axis: this enum picks the face colour
 * and whether the door is open at all. The two are described apart because a
 * screen that put every card on the heaviest edge had no hierarchy left.
 */
enum class StatusTone {
    /** No surface at all: type straight on the canvas. */
    NEUTRAL,

    /** A white face with an ink edge: something is in flight and unfinished. */
    HELD,

    /** A soft forest face: something has actually landed. */
    LANDED,

    /** A hairline and no shadow: a closed door, stated flatly. */
    QUIET
}

/** The honest families a verification failure can fall into. */
enum class VerificationReasonClass {
    RESTRICTED_APP,
    MONITORING_GAP,
    NOT_SUPPORTED,
    ACTIVITY_MISMATCH,
    TECHNICAL_LINK,
    STEPS_MISMATCH,
    STEPS_RESET,
    DISTRACTION_OPEN,
    UNKNOWN
}

/** The one action HOME offers, decided by what is already happening. */
enum class HomeAction { START_FOCUS, CONTINUE_QUEST, RETURN_TO_SESSION, CONTINUE, NONE }

/**
 * Everything ALIVE derives a mood from. All of it is a value the UI was already
 * handed; none of it is read here.
 */
data class AliveSignals(
    val timeStatus: TimeStatus,
    val dayType: DayType,
    val criticalZoneTier: Int,
    val remainingMinutes: Long,
    val currentStreak: Int,
    val reclaimedAvailableMinutes: Long,
    val attemptState: QuestAttemptState?,
    val focusActive: Boolean
)

object AliveStates {

    /**
     * Which stage the character shows.
     *
     * Effort that is in flight outranks being locked out: a user who has run
     * out of entertainment time but is mid-session is reclaiming, not
     * disconnected. Stages may also move backwards, which is honest and must
     * never be worded as a loss.
     */
    fun stageOf(signals: AliveSignals): AliveStage {
        val attempt = signals.attemptState

        return when {
            signals.focusActive ||
                attempt == QuestAttemptState.COMMITTED ||
                attempt == QuestAttemptState.IN_PROGRESS -> AliveStage.RECLAIMING

            attempt == QuestAttemptState.PENDING_VERIFICATION ||
                attempt == QuestAttemptState.VERIFIED -> AliveStage.AWARE

            attempt == QuestAttemptState.REWARDED -> AliveStage.ALIVE

            signals.timeStatus == TimeStatus.LOCKED ||
                signals.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY ||
                signals.criticalZoneTier >= 5 -> AliveStage.DISCONNECTED

            signals.criticalZoneTier >= 3 -> AliveStage.AWARE

            signals.criticalZoneTier >= 1 -> AliveStage.CONTROLLED

            attempt == QuestAttemptState.FAILED -> AliveStage.AWARE

            signals.currentStreak >= 1 || signals.reclaimedAvailableMinutes > 0 -> AliveStage.AWARE

            else -> AliveStage.UNAWARE
        }
    }

    /**
     * Which face the character shows, mirroring [stageOf] priority for priority:
     * effort in flight first, then the attempt's verification journey, then the
     * day's state. Anger is aimed at the phone that ate the day - a locked day
     * gets the red burst, a recovery day gets a quiet nap instead.
     */
    fun faceFor(signals: AliveSignals): AliveFace {
        val attempt = signals.attemptState

        return when {
            signals.focusActive -> AliveFace.LAPTOP

            attempt == QuestAttemptState.COMMITTED ||
                attempt == QuestAttemptState.IN_PROGRESS -> AliveFace.NORMAL

            attempt == QuestAttemptState.PENDING_VERIFICATION -> AliveFace.THINKING

            attempt == QuestAttemptState.VERIFIED -> AliveFace.HAPPY

            attempt == QuestAttemptState.REWARDED -> AliveFace.EXCITED

            // A recovery day naps even though its tier reads high: nothing was
            // taken, so there is no phone to be angry at.
            signals.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY -> AliveFace.SLEEPY

            signals.timeStatus == TimeStatus.LOCKED ||
                signals.criticalZoneTier >= 5 -> AliveFace.ANGRY

            signals.criticalZoneTier >= 3 -> AliveFace.WORRIED

            signals.criticalZoneTier >= 1 -> AliveFace.TIRED

            attempt == QuestAttemptState.FAILED -> AliveFace.SAD

            signals.currentStreak >= 1 || signals.reclaimedAvailableMinutes > 0 -> AliveFace.HAPPY

            else -> AliveFace.WAVING
        }
    }

    @DrawableRes
    fun faceSpriteFor(face: AliveFace): Int = when (face) {
        AliveFace.NORMAL -> R.drawable.ic_alive_character
        AliveFace.HAPPY -> R.drawable.ic_alive_face_happy
        AliveFace.SAD -> R.drawable.ic_alive_face_sad
        AliveFace.SLEEPY -> R.drawable.ic_alive_face_sleepy
        AliveFace.ANGRY -> R.drawable.ic_alive_face_angry
        AliveFace.ANGRY_FULL -> R.drawable.ic_alive_face_angry_full
        AliveFace.EXCITED -> R.drawable.alive_pose_alive
        AliveFace.CONFUSED -> R.drawable.ic_alive_face_confused
        AliveFace.THINKING -> R.drawable.ic_alive_face_thinking
        AliveFace.WORRIED -> R.drawable.ic_alive_face_worried
        AliveFace.TIRED -> R.drawable.ic_alive_face_tired
        AliveFace.PHONE -> R.drawable.alive_pose_unaware
        AliveFace.BOOK -> R.drawable.ic_alive_pose_book
        AliveFace.LAPTOP -> R.drawable.alive_anim_laptop
        AliveFace.CAMERA -> R.drawable.ic_alive_pose_camera
        AliveFace.PENCIL -> R.drawable.ic_alive_pose_pencil
        AliveFace.WAVING -> R.drawable.ic_alive_pose_wave
    }

    @StringRes
    fun faceDescription(face: AliveFace): Int = when (face) {
        AliveFace.NORMAL -> R.string.cd_face_normal
        AliveFace.HAPPY -> R.string.cd_face_happy
        AliveFace.SAD -> R.string.cd_face_sad
        AliveFace.SLEEPY -> R.string.cd_face_sleepy
        AliveFace.ANGRY -> R.string.cd_face_angry
        AliveFace.ANGRY_FULL -> R.string.cd_face_angry_full
        AliveFace.EXCITED -> R.string.cd_face_excited
        AliveFace.CONFUSED -> R.string.cd_face_confused
        AliveFace.THINKING -> R.string.cd_face_thinking
        AliveFace.WORRIED -> R.string.cd_face_worried
        AliveFace.TIRED -> R.string.cd_face_tired
        AliveFace.PHONE -> R.string.cd_face_phone
        AliveFace.BOOK -> R.string.cd_face_book
        AliveFace.LAPTOP -> R.string.cd_face_laptop
        AliveFace.CAMERA -> R.string.cd_face_camera
        AliveFace.PENCIL -> R.string.cd_face_pencil
        AliveFace.WAVING -> R.string.cd_face_waving
    }

    /**
     * The hero face for a quest detail page, or null when the sheet has no
     * pose for that category and the category's pixel mark should stay.
     */
    fun questHeroSpriteFor(category: QuestCategory): AliveFace? = when (category) {
        QuestCategory.STUDY, QuestCategory.READING -> AliveFace.BOOK
        QuestCategory.CODING -> AliveFace.LAPTOP
        QuestCategory.WRITING -> AliveFace.PENCIL
        QuestCategory.CREATIVE -> AliveFace.CAMERA
        else -> null
    }

    /**
     * The preview face on the focus setup page, or null when the sheet has no
     * pose for that activity and the pixel mark should stay.
     */
    fun focusPreviewSpriteFor(type: FocusActivityType): AliveFace? = when (type) {
        FocusActivityType.STUDY -> AliveFace.BOOK
        FocusActivityType.CODING -> AliveFace.LAPTOP
        FocusActivityType.WRITING -> AliveFace.PENCIL
        FocusActivityType.CREATIVE -> AliveFace.CAMERA
        else -> null
    }

    @StringRes
    fun stageLabel(stage: AliveStage): Int = when (stage) {
        AliveStage.DISCONNECTED -> R.string.stage_disconnected
        AliveStage.UNAWARE -> R.string.stage_unaware
        AliveStage.CONTROLLED -> R.string.stage_controlled
        AliveStage.AWARE -> R.string.stage_aware
        AliveStage.RECLAIMING -> R.string.stage_reclaiming
        AliveStage.ALIVE -> R.string.stage_alive
    }

    /**
     * The one handwritten line under the character. Says more than the stage
     * name would, and never congratulates before something has actually
     * verified.
     */
    @StringRes
    fun annotationFor(signals: AliveSignals): Int {
        return when (signals.attemptState) {
            QuestAttemptState.PENDING_VERIFICATION -> R.string.pose_pending_note
            // Counted, not yet credited: the line says when it arrives, not that
            // the time is already spent.
            QuestAttemptState.VERIFIED -> R.string.attempt_verified_support
            QuestAttemptState.FAILED -> R.string.pose_failed_note
            QuestAttemptState.COMMITTED, QuestAttemptState.IN_PROGRESS -> R.string.pose_reclaiming_note
            QuestAttemptState.REWARDED -> R.string.pose_alive_note
            else -> when {
                signals.timeStatus == TimeStatus.LOCKED ||
                    signals.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY -> R.string.pose_disconnected_note
                signals.currentStreak >= 1 || signals.reclaimedAvailableMinutes > 0 -> R.string.pose_streak_note
                else -> when (stageOf(signals)) {
                    AliveStage.UNAWARE -> R.string.pose_unaware_note
                    AliveStage.CONTROLLED -> R.string.pose_controlled_note
                    AliveStage.AWARE -> R.string.pose_aware_note
                    AliveStage.RECLAIMING -> R.string.pose_reclaiming_note
                    AliveStage.ALIVE -> R.string.pose_alive_note
                    AliveStage.DISCONNECTED -> R.string.pose_disconnected_note
                }
            }
        }
    }

    // ---------- YOUR TIME ----------

    /**
     * The head line for remaining entertainment time.
     *
     * The number always comes from `remainingMinutes`; the tier only chooses
     * the tone. That matters because dev-test mode collapses the tier scale to
     * 1 or 5, so any copy that inferred minutes from a tier would lie.
     */
    @StringRes
    fun timeHeadline(status: TimeStatus, tier: Int, remainingMinutes: Long): Int = when {
        status == TimeStatus.LOCKED -> R.string.time_closed
        status == TimeStatus.ZERO_ENTERTAINMENT_DAY -> R.string.zero_day_heading
        remainingMinutes <= 1L -> R.string.time_last_minute
        tier >= 1 -> R.string.time_minutes_left_critical
        else -> R.string.time_minutes_left
    }

    @StringRes
    fun timeSupportLine(status: TimeStatus, tier: Int): Int = when {
        status == TimeStatus.LOCKED -> R.string.time_closed_support
        status == TimeStatus.ZERO_ENTERTAINMENT_DAY -> R.string.zero_day_support
        tier >= 1 -> R.string.time_no_panic
        else -> R.string.time_allowance_support
    }

    /**
     * LOCKED and a recovery day both close the door, and both are QUIET - but
     * they never share a headline, a support line, or a surface, which is what
     * keeps them distinguishable without relying on color. An open day is HELD:
     * the allowance is still in flight, and that is the only reason its card has
     * a face at all.
     */
    fun timeTone(status: TimeStatus): StatusTone = when (status) {
        TimeStatus.LOCKED, TimeStatus.ZERO_ENTERTAINMENT_DAY -> StatusTone.QUIET
        else -> StatusTone.HELD
    }

    /**
     * Headline for the critical alert.
     *
     * Deliberately not `timeHeadline`: that one can answer with the locked or
     * recovery-day wording, and a card raised because time is *short* must
     * never print a headline that contradicts why it exists. The last-minute
     * string is reused so one minute reads identically everywhere in ALIVE.
     */
    @StringRes
    fun alertHeadline(remainingMinutes: Long): Int =
        if (remainingMinutes <= 1L) R.string.time_last_minute else R.string.alert_minutes_left

    // ---------- QUEST ATTEMPT ----------

    /**
     * Headline per attempt state. Seven states, seven distinct lines -
     * PENDING_VERIFICATION must not read like success and REWARDED is the only
     * state where reclaimed time can be spoken of in the past tense.
     *
     * `attempt_rewarded` takes one format argument (the minutes).
     */
    @StringRes
    fun attemptHeadline(state: QuestAttemptState): Int = when (state) {
        QuestAttemptState.AVAILABLE -> R.string.empty_heading
        QuestAttemptState.COMMITTED -> R.string.attempt_committed
        QuestAttemptState.IN_PROGRESS -> R.string.attempt_in_progress
        QuestAttemptState.PENDING_VERIFICATION -> R.string.attempt_pending
        QuestAttemptState.VERIFIED -> R.string.attempt_verified
        QuestAttemptState.REWARDED -> R.string.attempt_rewarded
        QuestAttemptState.FAILED -> R.string.attempt_failed
    }

    /**
     * Second line per attempt state.
     *
     * PENDING and VERIFIED deliberately offer no reassurance beyond what the
     * engine has actually done.
     */
    @StringRes
    fun attemptSupport(state: QuestAttemptState): Int = when (state) {
        QuestAttemptState.COMMITTED -> R.string.attempt_committed_support
        QuestAttemptState.IN_PROGRESS -> R.string.attempt_in_progress_support
        QuestAttemptState.PENDING_VERIFICATION -> R.string.attempt_pending_support
        QuestAttemptState.VERIFIED -> R.string.attempt_verified_support
        QuestAttemptState.REWARDED -> R.string.attempt_rewarded_total
        QuestAttemptState.FAILED -> R.string.attempt_failed_neutral
        QuestAttemptState.AVAILABLE -> R.string.empty_body
    }

    /**
     * VERIFIED stays HELD: the session has counted, but the minutes have not
     * reached the Time Bank yet - claimReward runs on the next resume. Giving it
     * the success surface would say "reclaimed" before anything was reclaimed.
     */
    fun attemptTone(state: QuestAttemptState): StatusTone = when (state) {
        QuestAttemptState.COMMITTED,
        QuestAttemptState.IN_PROGRESS,
        QuestAttemptState.PENDING_VERIFICATION,
        QuestAttemptState.VERIFIED -> StatusTone.HELD

        QuestAttemptState.REWARDED -> StatusTone.LANDED

        QuestAttemptState.FAILED -> StatusTone.QUIET

        QuestAttemptState.AVAILABLE -> StatusTone.NEUTRAL
    }

    /** Whether the attempt is still taking up the user's one open slot. */
    fun isAttemptActive(state: QuestAttemptState?): Boolean = when (state) {
        QuestAttemptState.COMMITTED,
        QuestAttemptState.IN_PROGRESS,
        QuestAttemptState.PENDING_VERIFICATION,
        QuestAttemptState.VERIFIED -> true

        else -> false
    }

    /** PENDING and VERIFIED must not offer a button; nothing here should. */
    fun offersAction(state: QuestAttemptState?): Boolean = when (state) {
        QuestAttemptState.COMMITTED,
        QuestAttemptState.IN_PROGRESS,
        QuestAttemptState.REWARDED,
        QuestAttemptState.FAILED -> true

        else -> false
    }

    // ---------- FAILURE REASONS ----------

    /**
     * Maps a persisted verification reason onto an honest family.
     *
     * The engine writes either a raw `FailureReason` enum name
     * (QuestVerificationCoordinator passes `result.reason.name` straight to
     * failQuest) or one of a handful of its own sentences. Both are matched
     * here, and neither is ever displayed: showing the raw value would leak
     * SCREAMING_SNAKE into a journal-style screen and would let a technical
     * fault read like an accusation.
     *
     * Anything unrecognised becomes UNKNOWN, which is still the shortest honest
     * sentence - never the raw string.
     */
    fun classifyFailure(raw: String?): VerificationReasonClass {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return VerificationReasonClass.UNKNOWN

        return when {
            value.equals("VIOLATION_DETECTED", ignoreCase = true) ->
                VerificationReasonClass.RESTRICTED_APP

            value.equals("MONITORING_LOST", ignoreCase = true) ->
                VerificationReasonClass.MONITORING_GAP

            value.equals("NON_DIGITAL_QUEST", ignoreCase = true) ->
                VerificationReasonClass.NOT_SUPPORTED

            value.equals("ACTIVITY_TYPE_MISMATCH", ignoreCase = true) ->
                VerificationReasonClass.ACTIVITY_MISMATCH

            value.equals("CORRELATION_MISMATCH", ignoreCase = true) ||
                value.equals("FOCUS_ID_MISMATCH", ignoreCase = true) ||
                value.equals("NO_LINKED_QUEST_ATTEMPT_ID", ignoreCase = true) ||
                value.equals("MISSING_ATTEMPT_ID", ignoreCase = true) ||
                value.equals("MISSING_LINKED_FOCUS_SESSION_ID", ignoreCase = true) ||
                value.equals("MISSING_FOCUS_SESSION_ID", ignoreCase = true) ||
                value.equals("START_TIMESTAMP_MISMATCH", ignoreCase = true) ->
                VerificationReasonClass.TECHNICAL_LINK

            value.contains("violation", ignoreCase = true) ->
                VerificationReasonClass.RESTRICTED_APP

            value.contains("monitoring integrity", ignoreCase = true) ->
                VerificationReasonClass.MONITORING_GAP

            value.contains("could not be created", ignoreCase = true) ||
                value.contains("could not be read back", ignoreCase = true) ||
                value.contains("could not be linked", ignoreCase = true) ||
                value.contains("could not be started", ignoreCase = true) ->
                VerificationReasonClass.TECHNICAL_LINK

            // Physical-quest signals. Reset before generic step: the reset
            // sentence contains the word "step" too.
            value.contains("step counter reset", ignoreCase = true) ->
                VerificationReasonClass.STEPS_RESET

            value.contains("step", ignoreCase = true) ->
                VerificationReasonClass.STEPS_MISMATCH

            value.contains("entertainment app", ignoreCase = true) ->
                VerificationReasonClass.DISTRACTION_OPEN

            else -> VerificationReasonClass.UNKNOWN
        }
    }

    @StringRes
    fun failureSupport(reason: VerificationReasonClass): Int = when (reason) {
        VerificationReasonClass.RESTRICTED_APP -> R.string.reason_restricted_app
        VerificationReasonClass.MONITORING_GAP -> R.string.reason_monitoring_gap
        VerificationReasonClass.NOT_SUPPORTED -> R.string.reason_not_supported
        VerificationReasonClass.ACTIVITY_MISMATCH -> R.string.reason_activity_mismatch
        VerificationReasonClass.TECHNICAL_LINK -> R.string.reason_technical_link
        VerificationReasonClass.STEPS_MISMATCH -> R.string.reason_steps_mismatch
        VerificationReasonClass.STEPS_RESET -> R.string.reason_steps_reset
        VerificationReasonClass.DISTRACTION_OPEN -> R.string.reason_distraction_open
        VerificationReasonClass.UNKNOWN -> R.string.reason_unknown
    }

    /**
     * A successful verification gets a paraphrase, never the stored reason.
     *
     * DigitalQuestVerifier writes "Committed duration satisfied with 0
     * violations", and that trailing clause is not something the engine
     * established: the counter it refers to is never incremented in production.
     * Repeating it would claim more than ALIVE knows.
     */
    @StringRes
    fun verifiedSupport(): Int = R.string.verified_reason

    // ---------- QUESTS ----------

    /**
     * Which quest kinds the engine can currently verify on its own.
     *
     * Mirrors QuestStartCoordinator.focusActivityTypeFor(): anything else returns
     * null there and startDigitalQuest() fails. This is a UI-side mirror, so if
     * that map ever grows, this one has to grow with it or the board will mark a
     * startable quest as unverifiable.
     */
    fun isSelfVerifiable(category: QuestCategory): Boolean = when (category) {
        QuestCategory.STUDY,
        QuestCategory.CODING,
        QuestCategory.WRITING,
        QuestCategory.CREATIVE -> true

        else -> false
    }

    /**
     * The "how ALIVE decides" step line for a physical quest, decided by the
     * quest's own signal requirement.
     */
    @StringRes
    fun physicalQuestWatchLine(requirement: PhysicalQuestRequirement): Int = when {
        requirement.maxStepsPerMinute != null -> R.string.quest_step_watch_stillness
        requirement.minStepsPerMinute != null -> R.string.quest_step_watch_steps
        requirement.checkDistractions -> R.string.quest_step_watch_attention
        else -> R.string.quest_step_watch_physical
    }

    /**
     * The annotation under the start button: what ALIVE will actually check.
     * [stepsMeasurable] says whether the step signal can be read on this
     * phone right now (sensor present and permission granted).
     */
    @StringRes
    fun physicalQuestHowLine(
        requirement: PhysicalQuestRequirement,
        stepsMeasurable: Boolean
    ): Int = when {
        requirement.maxStepsPerMinute != null ->
            if (stepsMeasurable) R.string.quest_how_stillness else R.string.quest_how_steps_unavailable

        requirement.minStepsPerMinute != null ->
            if (stepsMeasurable) R.string.quest_how_steps else R.string.quest_how_steps_unavailable

        requirement.checkDistractions -> R.string.quest_how_attention
        else -> R.string.quest_physical_how
    }

    @StringRes
    fun categoryLabel(category: QuestCategory): Int = when (category) {
        QuestCategory.EXERCISE -> R.string.category_exercise
        QuestCategory.STUDY -> R.string.category_study
        QuestCategory.READING -> R.string.category_reading
        QuestCategory.WALKING -> R.string.category_walking
        QuestCategory.CLEANING -> R.string.category_cleaning
        QuestCategory.CREATIVE -> R.string.category_creative
        QuestCategory.CODING -> R.string.category_coding
        QuestCategory.WRITING -> R.string.category_writing
        QuestCategory.MINDFULNESS -> R.string.category_mindfulness
        QuestCategory.SOCIAL -> R.string.category_social
        QuestCategory.OTHER -> R.string.category_other
    }

    /** Difficulty stays one quiet word. Never a badge, chip, or star rating. */
    @StringRes
    fun difficultyLabel(difficulty: QuestDifficulty): Int = when (difficulty) {
        QuestDifficulty.EASY -> R.string.difficulty_easy
        QuestDifficulty.MEDIUM -> R.string.difficulty_medium
        QuestDifficulty.HARD -> R.string.difficulty_hard
    }

    @StringRes
    fun focusActivityLabel(type: FocusActivityType): Int = when (type) {
        FocusActivityType.STUDY -> R.string.focus_activity_study
        FocusActivityType.WORK -> R.string.focus_activity_work
        FocusActivityType.CODING -> R.string.focus_activity_coding
        FocusActivityType.CREATIVE -> R.string.focus_activity_creative
        FocusActivityType.WRITING -> R.string.focus_activity_writing
    }

    // ---------- HOME ACTION ----------

    /**
     * The single thing HOME invites.
     *
     * Pending verification intentionally offers nothing: there is no useful
     * interaction while ALIVE is still deciding, and prompting one would imply
     * the outcome depends on a press.
     */
    fun homeAction(signals: AliveSignals): HomeAction {
        val attempt = signals.attemptState

        return when {
            signals.focusActive -> HomeAction.RETURN_TO_SESSION

            attempt == QuestAttemptState.COMMITTED ||
                attempt == QuestAttemptState.IN_PROGRESS -> HomeAction.CONTINUE_QUEST

            attempt == QuestAttemptState.PENDING_VERIFICATION ||
                attempt == QuestAttemptState.VERIFIED -> HomeAction.NONE

            attempt == QuestAttemptState.REWARDED ||
                attempt == QuestAttemptState.FAILED -> HomeAction.CONTINUE

            // A recovery day still has productive things to do, so focus stays
            // available; a plain locked evening does not get an action.
            signals.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY -> HomeAction.START_FOCUS

            signals.timeStatus == TimeStatus.LOCKED -> HomeAction.NONE

            else -> HomeAction.START_FOCUS
        }
    }

    @StringRes
    fun homeActionLabel(action: HomeAction): Int = when (action) {
        HomeAction.START_FOCUS -> R.string.action_start_focus
        HomeAction.CONTINUE_QUEST -> R.string.action_continue_quest
        HomeAction.RETURN_TO_SESSION -> R.string.action_return_session
        HomeAction.CONTINUE -> R.string.action_finish_life_mission
        HomeAction.NONE -> 0
    }
}
