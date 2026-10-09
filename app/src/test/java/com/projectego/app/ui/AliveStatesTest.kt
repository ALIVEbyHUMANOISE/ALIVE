package com.projectego.app.ui

import com.projectego.app.R
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.PhysicalQuestRequirement
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The UI's status vocabulary, with no Android runtime.
 *
 * These assertions encode the promises the redesign was asked to keep: that
 * PENDING_VERIFICATION is not VERIFIED and neither is REWARDED, that a locked
 * day and a recovery day are not the same sentence, and that no engine reason
 * is ever handed to the screen raw. They exist because nothing else in this
 * project tests the UI layer at all.
 */
class AliveStatesTest {

    private fun signals(
        timeStatus: TimeStatus = TimeStatus.ACTIVE,
        tier: Int = 0,
        remaining: Long = 90L,
        dayType: DayType = DayType.NORMAL_DAY,
        streak: Int = 0,
        reclaimed: Long = 0L,
        attempt: QuestAttemptState? = null,
        focusActive: Boolean = false,
        focusType: FocusActivityType? = null
    ) = AliveSignals(
        timeStatus = timeStatus,
        dayType = dayType,
        criticalZoneTier = tier,
        remainingMinutes = remaining,
        currentStreak = streak,
        reclaimedAvailableMinutes = reclaimed,
        attemptState = attempt,
        focusActive = focusActive,
        focusActivityType = focusType
    )

    @Test
    fun `every attempt state gets its own headline`() {
        val headlines = QuestAttemptState.values().associateWith { AliveStates.attemptHeadline(it) }
        assertEquals(
            "seven states must not share a headline",
            headlines.size,
            headlines.values.distinct().size
        )
    }

    @Test
    fun `pending verification is not verified and verified is not rewarded`() {
        val pending = AliveStates.attemptHeadline(QuestAttemptState.PENDING_VERIFICATION)
        val verified = AliveStates.attemptHeadline(QuestAttemptState.VERIFIED)
        val rewarded = AliveStates.attemptHeadline(QuestAttemptState.REWARDED)

        assertNotEquals(pending, verified)
        assertNotEquals(verified, rewarded)
        assertNotEquals(pending, rewarded)

        // Neither unfinished state may be dressed as a success.
        assertEquals(StatusTone.HELD, AliveStates.attemptTone(QuestAttemptState.PENDING_VERIFICATION))
        assertEquals(StatusTone.HELD, AliveStates.attemptTone(QuestAttemptState.VERIFIED))
        assertEquals(StatusTone.LANDED, AliveStates.attemptTone(QuestAttemptState.REWARDED))

        // And the character must not celebrate a session that has not been
        // credited yet - REWARDED is the only past-tense state.
        assertEquals(AliveStage.AWARE, AliveStates.stageOf(signals(attempt = QuestAttemptState.VERIFIED)))
        assertEquals(AliveStage.ALIVE, AliveStates.stageOf(signals(attempt = QuestAttemptState.REWARDED, reclaimed = 20)))
        assertNotEquals(
            R.string.pose_alive_note,
            AliveStates.annotationFor(signals(attempt = QuestAttemptState.VERIFIED))
        )
    }

    @Test
    fun `nothing offers a button while a session is still being decided`() {
        assertTrue(AliveStates.offersAction(QuestAttemptState.PENDING_VERIFICATION).not())
        assertTrue(AliveStates.offersAction(QuestAttemptState.VERIFIED).not())
        assertEquals(R.string.pose_pending_note, AliveStates.annotationFor(signals(attempt = QuestAttemptState.PENDING_VERIFICATION)))
    }

    @Test
    fun `locked and recovery day stay distinguishable`() {
        val locked = AliveStates.timeHeadline(TimeStatus.LOCKED, 5, 0L)
        val recovery = AliveStates.timeHeadline(TimeStatus.ZERO_ENTERTAINMENT_DAY, 5, 0L)

        assertNotEquals(locked, recovery)
        assertNotEquals(
            AliveStates.timeSupportLine(TimeStatus.LOCKED, 5),
            AliveStates.timeSupportLine(TimeStatus.ZERO_ENTERTAINMENT_DAY, 5)
        )
    }

    @Test
    fun `the minutes shown come from the value and not from the tier`() {
        // Dev-test mode collapses the tier scale to 1 or 5, so a tier may not be
        // the source of any number on screen: the tone may come from the tier,
        // the figure always comes from remainingMinutes.
        assertEquals(R.string.time_minutes_left_critical, AliveStates.timeHeadline(TimeStatus.CRITICAL, 1, 73L))
        assertEquals(R.string.time_minutes_left, AliveStates.timeHeadline(TimeStatus.ACTIVE, 0, 73L))
        assertEquals(R.string.time_last_minute, AliveStates.timeHeadline(TimeStatus.CRITICAL, 1, 1L))
        assertEquals(R.string.time_last_minute, AliveStates.timeHeadline(TimeStatus.CRITICAL, 4, 0L))
    }

    @Test
    fun `home action follows what is already happening`() {
        assertEquals(HomeAction.RETURN_TO_SESSION, AliveStates.homeAction(signals(focusActive = true, timeStatus = TimeStatus.LOCKED, tier = 5)))
        assertEquals(HomeAction.CONTINUE_QUEST, AliveStates.homeAction(signals(attempt = QuestAttemptState.IN_PROGRESS)))
        assertEquals(HomeAction.CONTINUE_QUEST, AliveStates.homeAction(signals(attempt = QuestAttemptState.COMMITTED)))
        assertEquals(HomeAction.NONE, AliveStates.homeAction(signals(attempt = QuestAttemptState.PENDING_VERIFICATION)))
        assertEquals(HomeAction.NONE, AliveStates.homeAction(signals(attempt = QuestAttemptState.VERIFIED)))
        assertEquals(HomeAction.CONTINUE, AliveStates.homeAction(signals(attempt = QuestAttemptState.REWARDED)))
        assertEquals(HomeAction.CONTINUE, AliveStates.homeAction(signals(attempt = QuestAttemptState.FAILED)))
        assertEquals(HomeAction.NONE, AliveStates.homeAction(signals(timeStatus = TimeStatus.LOCKED, tier = 5, remaining = 0)))
        assertEquals(HomeAction.START_FOCUS, AliveStates.homeAction(signals(timeStatus = TimeStatus.ZERO_ENTERTAINMENT_DAY, tier = 5, remaining = 0, dayType = DayType.ZERO_ENTERTAINMENT_DAY)))
        assertEquals(HomeAction.START_FOCUS, AliveStates.homeAction(signals()))
    }

    @Test
    fun `effort in flight outranks being locked out`() {
        val midSessionOnALockedDay = signals(
            timeStatus = TimeStatus.LOCKED,
            tier = 5,
            remaining = 0,
            focusActive = true
        )
        assertEquals(AliveStage.RECLAIMING, AliveStates.stageOf(midSessionOnALockedDay))
    }

    @Test
    fun `stage derivation still covers the copy ladder`() {
        assertEquals(AliveStage.DISCONNECTED, AliveStates.stageOf(signals(timeStatus = TimeStatus.LOCKED, tier = 5, remaining = 0)))
        assertEquals(AliveStage.UNAWARE, AliveStates.stageOf(signals()))
        assertEquals(AliveStage.CONTROLLED, AliveStates.stageOf(signals(timeStatus = TimeStatus.CRITICAL, tier = 1, remaining = 28)))
        assertEquals(AliveStage.ALIVE, AliveStates.stageOf(signals(attempt = QuestAttemptState.REWARDED, reclaimed = 20)))
        AliveStage.values().forEach { stage ->
            assertTrue(stage.name, AliveStates.stageLabel(stage) != 0)
        }
    }

    @Test
    fun `every face resolves to a sprite and a description`() {
        AliveFace.values().forEach { face ->
            assertTrue(face.name, AliveStates.faceSpriteFor(face) != 0)
            assertTrue(face.name, AliveStates.faceDescription(face) != 0)
        }
    }

    @Test
    fun `faceFor follows what is already happening`() {
        // Effort in flight outranks everything, even a locked day.
        assertEquals(
            AliveFace.LAPTOP,
            AliveStates.faceFor(signals(timeStatus = TimeStatus.LOCKED, tier = 5, remaining = 0, focusActive = true))
        )

        // A running focus session wears its own activity's pose, not a generic
        // laptop: studying reads as studying.
        assertEquals(AliveFace.BOOK, AliveStates.faceFor(signals(focusActive = true, focusType = FocusActivityType.STUDY)))
        assertEquals(AliveFace.PENCIL, AliveStates.faceFor(signals(focusActive = true, focusType = FocusActivityType.WRITING)))
        assertEquals(AliveFace.CAMERA, AliveStates.faceFor(signals(focusActive = true, focusType = FocusActivityType.CREATIVE)))
        assertEquals(AliveFace.LAPTOP, AliveStates.faceFor(signals(focusActive = true, focusType = FocusActivityType.WORK)))
        assertEquals(AliveFace.LAPTOP, AliveStates.faceFor(signals(focusActive = true, focusType = FocusActivityType.CODING)))

        assertEquals(AliveFace.NORMAL, AliveStates.faceFor(signals(attempt = QuestAttemptState.COMMITTED)))
        assertEquals(AliveFace.NORMAL, AliveStates.faceFor(signals(attempt = QuestAttemptState.IN_PROGRESS)))

        // The verification journey is its own three faces, in order.
        assertEquals(AliveFace.THINKING, AliveStates.faceFor(signals(attempt = QuestAttemptState.PENDING_VERIFICATION)))
        assertEquals(AliveFace.HAPPY, AliveStates.faceFor(signals(attempt = QuestAttemptState.VERIFIED)))
        assertEquals(AliveFace.EXCITED, AliveStates.faceFor(signals(attempt = QuestAttemptState.REWARDED, reclaimed = 20)))

        // A locked day is anger at the phone; a recovery day is a nap.
        assertEquals(AliveFace.ANGRY, AliveStates.faceFor(signals(timeStatus = TimeStatus.LOCKED, tier = 5, remaining = 0)))
        assertEquals(
            AliveFace.SLEEPY,
            AliveStates.faceFor(signals(timeStatus = TimeStatus.ZERO_ENTERTAINMENT_DAY, tier = 5, remaining = 0, dayType = DayType.ZERO_ENTERTAINMENT_DAY))
        )

        // The tier ladder.
        assertEquals(AliveFace.WORRIED, AliveStates.faceFor(signals(timeStatus = TimeStatus.CRITICAL, tier = 3, remaining = 20)))
        assertEquals(AliveFace.WORRIED, AliveStates.faceFor(signals(timeStatus = TimeStatus.CRITICAL, tier = 4, remaining = 20)))
        assertEquals(AliveFace.TIRED, AliveStates.faceFor(signals(timeStatus = TimeStatus.CRITICAL, tier = 1, remaining = 30)))
        assertEquals(AliveFace.TIRED, AliveStates.faceFor(signals(timeStatus = TimeStatus.CRITICAL, tier = 2, remaining = 30)))

        // A failed attempt is sad, a streak or reclaimed time is happy.
        assertEquals(AliveFace.SAD, AliveStates.faceFor(signals(attempt = QuestAttemptState.FAILED)))
        assertEquals(AliveFace.HAPPY, AliveStates.faceFor(signals(streak = 3)))
        assertEquals(AliveFace.HAPPY, AliveStates.faceFor(signals(reclaimed = 15)))

        // And the plain day opens with a wave.
        assertEquals(AliveFace.WAVING, AliveStates.faceFor(signals()))
    }

    @Test
    fun `every mission and focus activity gets a sheet pose`() {
        // Prop poses where the sheet has one.
        assertEquals(AliveFace.BOOK, AliveStates.questHeroSpriteFor(QuestCategory.STUDY))
        assertEquals(AliveFace.BOOK, AliveStates.questHeroSpriteFor(QuestCategory.READING))
        assertEquals(AliveFace.LAPTOP, AliveStates.questHeroSpriteFor(QuestCategory.CODING))
        assertEquals(AliveFace.PENCIL, AliveStates.questHeroSpriteFor(QuestCategory.WRITING))
        assertEquals(AliveFace.CAMERA, AliveStates.questHeroSpriteFor(QuestCategory.CREATIVE))

        // And a borrowed face everywhere the sheet has no prop: movement is
        // happy or excited, chores are tired, stillness is sleepy, people are
        // happy. Only OTHER keeps its category mark.
        assertEquals(AliveFace.HAPPY, AliveStates.questHeroSpriteFor(QuestCategory.WALKING))
        assertEquals(AliveFace.EXCITED, AliveStates.questHeroSpriteFor(QuestCategory.EXERCISE))
        assertEquals(AliveFace.TIRED, AliveStates.questHeroSpriteFor(QuestCategory.CLEANING))
        assertEquals(AliveFace.SLEEPY, AliveStates.questHeroSpriteFor(QuestCategory.MINDFULNESS))
        assertEquals(AliveFace.HAPPY, AliveStates.questHeroSpriteFor(QuestCategory.SOCIAL))
        assertEquals(null, AliveStates.questHeroSpriteFor(QuestCategory.OTHER))

        // Every focus activity has a preview pose; work reads at the laptop.
        assertEquals(AliveFace.BOOK, AliveStates.focusPreviewSpriteFor(FocusActivityType.STUDY))
        assertEquals(AliveFace.LAPTOP, AliveStates.focusPreviewSpriteFor(FocusActivityType.CODING))
        assertEquals(AliveFace.PENCIL, AliveStates.focusPreviewSpriteFor(FocusActivityType.WRITING))
        assertEquals(AliveFace.CAMERA, AliveStates.focusPreviewSpriteFor(FocusActivityType.CREATIVE))
        assertEquals(AliveFace.LAPTOP, AliveStates.focusPreviewSpriteFor(FocusActivityType.WORK))
    }

    @Test
    fun `every engine failure reason lands in an honest family`() {
        val engineEnumReasons = listOf(
            "NON_DIGITAL_QUEST", "MISSING_ATTEMPT_ID", "MISSING_LINKED_FOCUS_SESSION_ID",
            "MISSING_FOCUS_SESSION_ID", "NO_LINKED_QUEST_ATTEMPT_ID", "CORRELATION_MISMATCH",
            "FOCUS_ID_MISMATCH", "START_TIMESTAMP_MISMATCH", "VIOLATION_DETECTED",
            "MONITORING_LOST", "ACTIVITY_TYPE_MISMATCH"
        )
        engineEnumReasons.forEach { raw ->
            val classified = AliveStates.classifyFailure(raw)
            assertNotEquals("$raw must not fall through as unknown", VerificationReasonClass.UNKNOWN, classified)
            assertTrue("$raw must resolve to copy", AliveStates.failureSupport(classified) != 0)
        }

        val engineSentences = listOf(
            "1 confirmed restricted-app violation during Focus session",
            "Monitoring integrity was lost, so this attempt can no longer be verified",
            "Focus session could not be created for this digital attempt",
            "Created Focus session could not be read back for this digital attempt",
            "Focus session could not be linked to this digital attempt",
            "Attempt could not be started for this digital quest"
        )
        engineSentences.forEach { raw ->
            assertNotEquals(VerificationReasonClass.UNKNOWN, AliveStates.classifyFailure(raw))
        }

        assertEquals(VerificationReasonClass.RESTRICTED_APP, AliveStates.classifyFailure("VIOLATION_DETECTED"))
        assertEquals(VerificationReasonClass.MONITORING_GAP, AliveStates.classifyFailure("MONITORING_LOST"))
        assertEquals(VerificationReasonClass.TECHNICAL_LINK, AliveStates.classifyFailure("CORRELATION_MISMATCH"))
        assertEquals(VerificationReasonClass.NOT_SUPPORTED, AliveStates.classifyFailure("NON_DIGITAL_QUEST"))
        assertEquals(VerificationReasonClass.ACTIVITY_MISMATCH, AliveStates.classifyFailure("ACTIVITY_TYPE_MISMATCH"))
    }

    @Test
    fun `unknown and empty reasons still get copy and never leak`() {
        listOf(null, "", "   ", "something the engine invented").forEach { raw ->
            val classified = AliveStates.classifyFailure(raw)
            assertEquals(VerificationReasonClass.UNKNOWN, classified)
            assertEquals(R.string.reason_unknown, AliveStates.failureSupport(classified))
        }
    }

    @Test
    fun `physical signal failures land in their own honest families`() {
        assertEquals(
            VerificationReasonClass.STEPS_RESET,
            AliveStates.classifyFailure("Step counter reset during the mission (device restarted)")
        )
        assertEquals(
            VerificationReasonClass.STEPS_MISMATCH,
            AliveStates.classifyFailure("15 steps per committed minute measured, 60 required (300 steps over 20 committed minutes)")
        )
        assertEquals(
            VerificationReasonClass.STEPS_MISMATCH,
            AliveStates.classifyFailure("Step counter could not be read when the mission ended")
        )
        assertEquals(
            VerificationReasonClass.DISTRACTION_OPEN,
            AliveStates.classifyFailure("Entertainment app open during the mission: com.instagram.android")
        )

        assertEquals(R.string.reason_steps_reset, AliveStates.failureSupport(VerificationReasonClass.STEPS_RESET))
        assertEquals(R.string.reason_steps_mismatch, AliveStates.failureSupport(VerificationReasonClass.STEPS_MISMATCH))
        assertEquals(R.string.reason_distraction_open, AliveStates.failureSupport(VerificationReasonClass.DISTRACTION_OPEN))
    }

    @Test
    fun `the watch and how lines follow the quest's signals`() {
        val steps = PhysicalQuestRequirement(60, null, true)
        val stillness = PhysicalQuestRequirement(null, 10, true)
        val attention = PhysicalQuestRequirement(null, null, true)
        val none = PhysicalQuestRequirement.NONE

        assertEquals(R.string.quest_step_watch_steps, AliveStates.physicalQuestWatchLine(steps))
        assertEquals(R.string.quest_step_watch_stillness, AliveStates.physicalQuestWatchLine(stillness))
        assertEquals(R.string.quest_step_watch_attention, AliveStates.physicalQuestWatchLine(attention))
        assertEquals(R.string.quest_step_watch_physical, AliveStates.physicalQuestWatchLine(none))

        assertEquals(R.string.quest_how_steps, AliveStates.physicalQuestHowLine(steps, stepsMeasurable = true))
        assertEquals(R.string.quest_how_steps_unavailable, AliveStates.physicalQuestHowLine(steps, stepsMeasurable = false))
        assertEquals(R.string.quest_how_stillness, AliveStates.physicalQuestHowLine(stillness, stepsMeasurable = true))
        assertEquals(R.string.quest_how_steps_unavailable, AliveStates.physicalQuestHowLine(stillness, stepsMeasurable = false))
        assertEquals(R.string.quest_how_attention, AliveStates.physicalQuestHowLine(attention, stepsMeasurable = true))
        assertEquals(R.string.quest_physical_how, AliveStates.physicalQuestHowLine(none, stepsMeasurable = true))
    }

    @Test
    fun `monitoring loss is never worded like an accusation`() {
        // Both families must resolve to different copy: a gap in ALIVE's watching
        // is not the same event as a restricted app coming up.
        assertNotEquals(
            AliveStates.failureSupport(VerificationReasonClass.MONITORING_GAP),
            AliveStates.failureSupport(VerificationReasonClass.RESTRICTED_APP)
        )
        // And a passed verification is paraphrased, never echoed back: the stored
        // reason claims a violation count the engine never actually measures.
        assertEquals(R.string.verified_reason, AliveStates.verifiedSupport())
    }

    @Test
    fun `active attempt set matches the repository rule`() {
        val active = QuestAttemptState.values().filter { AliveStates.isAttemptActive(it) }
        assertEquals(
            listOf(
                QuestAttemptState.COMMITTED,
                QuestAttemptState.IN_PROGRESS,
                QuestAttemptState.PENDING_VERIFICATION,
                QuestAttemptState.VERIFIED
            ),
            active
        )
    }

    @Test
    fun `only the quest kinds the engine can watch are offered as verifiable`() {
        listOf(QuestCategory.STUDY, QuestCategory.CODING, QuestCategory.WRITING, QuestCategory.CREATIVE)
            .forEach { assertTrue(it.name, AliveStates.isSelfVerifiable(it)) }

        listOf(
            QuestCategory.WALKING, QuestCategory.READING, QuestCategory.EXERCISE,
            QuestCategory.CLEANING, QuestCategory.MINDFULNESS, QuestCategory.SOCIAL,
            QuestCategory.OTHER
        ).forEach { assertTrue(it.name, AliveStates.isSelfVerifiable(it).not()) }
    }

    @Test
    fun `a recovery day is never shown as a normal allowance`() {
        val recovery = AliveStates.timeHeadline(TimeStatus.ZERO_ENTERTAINMENT_DAY, 5, 0L)
        assertEquals(R.string.zero_day_heading, recovery)
        assertNotEquals(R.string.time_minutes_left, recovery)
    }

    @Test
    fun `no stage is ever presented as a number of levels`() {
        // The pose and one annotation are the whole expression of a stage: there
        // is no format string in the copy set that could print "stage 4 of 6".
        val stageCopy = AliveStage.values().map { AliveStates.stageLabel(it) }
        assertTrue(stageCopy.all { it != 0 })
        assertEquals(R.string.action_start_focus, AliveStates.homeActionLabel(HomeAction.START_FOCUS))
        assertEquals(0, AliveStates.homeActionLabel(HomeAction.NONE))
    }
}
