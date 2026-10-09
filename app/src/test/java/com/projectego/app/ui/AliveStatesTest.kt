package com.projectego.app.ui

import com.projectego.app.R
import com.projectego.app.model.DayType
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
        focusActive: Boolean = false
    ) = AliveSignals(
        timeStatus = timeStatus,
        dayType = dayType,
        criticalZoneTier = tier,
        remainingMinutes = remaining,
        currentStreak = streak,
        reclaimedAvailableMinutes = reclaimed,
        attemptState = attempt,
        focusActive = focusActive
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
    fun `stage derivation covers all six poses`() {
        val stages = listOf(
            AliveStage.DISCONNECTED, AliveStage.UNAWARE, AliveStage.CONTROLLED,
            AliveStage.AWARE, AliveStage.RECLAIMING, AliveStage.ALIVE
        )
        stages.forEach { stage ->
            assertTrue(stage.name, AliveStates.poseFor(stage) != 0)
            assertTrue(stage.name, AliveStates.stageLabel(stage) != 0)
        }
        assertEquals(AliveStage.DISCONNECTED, AliveStates.stageOf(signals(timeStatus = TimeStatus.LOCKED, tier = 5, remaining = 0)))
        assertEquals(AliveStage.UNAWARE, AliveStates.stageOf(signals()))
        assertEquals(AliveStage.CONTROLLED, AliveStates.stageOf(signals(timeStatus = TimeStatus.CRITICAL, tier = 1, remaining = 28)))
        assertEquals(AliveStage.ALIVE, AliveStates.stageOf(signals(attempt = QuestAttemptState.REWARDED, reclaimed = 20)))
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
