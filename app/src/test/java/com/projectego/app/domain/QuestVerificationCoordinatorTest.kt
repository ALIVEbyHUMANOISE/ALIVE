package com.projectego.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.VerificationMethod
import com.projectego.app.model.VerificationOutcome
import com.projectego.app.model.QuestAttemptState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestVerificationCoordinatorTest {

    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var questRepository: QuestRepository
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var coordinator: QuestVerificationCoordinator
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE).edit().clear().apply()
        timeBankRepository = TimeBankRepository(context)
        questRepository = QuestRepository(context, timeBankRepository)
        focusSessionManager = FocusSessionManager(context, timeBankRepository)
        coordinator = QuestVerificationCoordinator(questRepository, focusSessionManager)
    }

    @Test
    fun `evaluateAttemptOnResume returns false when no current attempt exists`() {
        val result = coordinator.evaluateAttemptOnResume(context)
        assertFalse(result)
    }

    @Test
    fun `onViolationConfirmed returns true when active DIGITAL attempt has linked focus session`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        focusSessionManager.startTestFocusSession(FocusActivityType.STUDY, "Test", 1L)
        val attemptId = questRepository.getCurrentAttempt()!!.attemptId
        val focusSessionId = focusSessionManager.getFocusSession()!!.focusSessionId
        questRepository.linkFocusSession(focusSessionId)
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit().putString("key_linked_quest_attempt_id", attemptId).apply()
        val result = coordinator.onViolationConfirmed()
        assertTrue(result)
        assertEquals(QuestAttemptState.FAILED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `onViolationConfirmed returns false when no linked focus session`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        val result = coordinator.onViolationConfirmed()
        assertFalse(result)
    }

    @Test
    fun `violation reason is distinct technical message`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        focusSessionManager.startTestFocusSession(FocusActivityType.STUDY, "Test", 1L)
        val attemptId = questRepository.getCurrentAttempt()!!.attemptId
        val focusSessionId = focusSessionManager.getFocusSession()!!.focusSessionId
        questRepository.linkFocusSession(focusSessionId)
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE)
            .edit().putString("key_linked_quest_attempt_id", attemptId).apply()
        val violationResult = coordinator.onViolationConfirmed()
        assertTrue(violationResult)
        val reason = questRepository.getCurrentAttempt()?.verificationResult?.reason
        assertNotNull(reason)
        assertTrue(reason!!.contains("violation"))
    }

    @Test
    fun `IN_PROGRESS transition through PENDING_VERIFICATION to VERIFIED works`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        assertEquals(QuestAttemptState.IN_PROGRESS, questRepository.getCurrentAttempt()?.state)
        assertTrue(questRepository.requestVerification())
        assertEquals(QuestAttemptState.PENDING_VERIFICATION, questRepository.getCurrentAttempt()?.state)
        assertTrue(questRepository.verifyQuest(VerificationOutcome.PASS, "Duration satisfied"))
        assertEquals(QuestAttemptState.VERIFIED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `PENDING_VERIFICATION recovery goes straight to verifyQuest PASSED`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        assertTrue(questRepository.requestVerification())
        assertEquals(QuestAttemptState.PENDING_VERIFICATION, questRepository.getCurrentAttempt()?.state)
        assertTrue(questRepository.verifyQuest(VerificationOutcome.PASS, "Duration satisfied (recovered)"))
        assertEquals(QuestAttemptState.VERIFIED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `failQuest rejects PENDING_VERIFICATION and verifyQuest accepts it`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        assertTrue(questRepository.requestVerification())
        assertEquals(QuestAttemptState.PENDING_VERIFICATION, questRepository.getCurrentAttempt()?.state)
        val failResult = questRepository.failQuest("test failure")
        assertFalse(failResult)
        assertEquals(QuestAttemptState.PENDING_VERIFICATION, questRepository.getCurrentAttempt()?.state)
        val verifyFailResult = questRepository.verifyQuest(VerificationOutcome.FAIL, "Verification failed")
        assertTrue(verifyFailResult)
        assertEquals(QuestAttemptState.FAILED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `failQuest from IN_PROGRESS succeeds`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        assertEquals(QuestAttemptState.IN_PROGRESS, questRepository.getCurrentAttempt()?.state)
        val result = questRepository.failQuest("User abandoned")
        assertTrue(result)
        assertEquals(QuestAttemptState.FAILED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `VERIFIED unrewarded attempt recovers reward on resume via claimReward`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        assertTrue(questRepository.requestVerification())
        assertTrue(questRepository.verifyQuest(VerificationOutcome.PASS, "pass"))
        assertEquals(QuestAttemptState.VERIFIED, questRepository.getCurrentAttempt()?.state)
        assertNull(questRepository.getCurrentAttempt()?.rewardMinutes)
        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)

        val result = coordinator.evaluateAttemptOnResume(context)

        assertTrue(result)
        assertEquals(QuestAttemptState.REWARDED, questRepository.getCurrentAttempt()?.state)
        assertEquals(20L, questRepository.getCurrentAttempt()?.rewardMinutes)
        assertNotNull(questRepository.getCurrentAttempt()?.rewardedTimestamp)
        assertEquals(20L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `second evaluateAttemptOnResume after VERIFIED recovery is idempotent`() {
        assertTrue(questRepository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(questRepository.startAttempt())
        assertTrue(questRepository.requestVerification())
        assertTrue(questRepository.verifyQuest(VerificationOutcome.PASS, "pass"))

        assertTrue(coordinator.evaluateAttemptOnResume(context))
        assertEquals(20L, timeBankRepository.getTimeBank().earnedMinutes)

        val second = coordinator.evaluateAttemptOnResume(context)

        assertFalse(second)
        assertEquals(QuestAttemptState.REWARDED, questRepository.getCurrentAttempt()?.state)
        assertEquals(20L, timeBankRepository.getTimeBank().earnedMinutes)
    }
}
