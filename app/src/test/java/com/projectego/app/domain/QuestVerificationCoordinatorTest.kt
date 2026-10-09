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
import com.projectego.app.data.QuestEvidenceRepository
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.VerificationMethod
import com.projectego.app.model.VerificationOutcome
import com.projectego.app.model.QuestAttemptState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestVerificationCoordinatorTest {

    private class FakePhysicalEvidenceProvider : PhysicalQuestEvidenceProvider {
        var stepCount: Int? = null
        var distractionPackages: List<String>? = emptyList()
        override fun readStepCount(): Int? = stepCount
        override fun entertainmentPackagesBetween(startMillis: Long, endMillis: Long): List<String>? =
            distractionPackages
    }

    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var questRepository: QuestRepository
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var evidenceRepository: QuestEvidenceRepository
    private lateinit var provider: FakePhysicalEvidenceProvider
    private lateinit var coordinator: QuestVerificationCoordinator
    private lateinit var context: Context

    // The one clock every repository and coordinator in these tests shares.
    private var now = 1_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_quest_evidence", Context.MODE_PRIVATE).edit().clear().apply()
        now = 1_000_000L
        timeBankRepository = TimeBankRepository(context)
        questRepository = QuestRepository(context, timeBankRepository, clock = { now })
        focusSessionManager = FocusSessionManager(context, timeBankRepository)
        evidenceRepository = QuestEvidenceRepository(context)
        provider = FakePhysicalEvidenceProvider()
        coordinator = QuestVerificationCoordinator(
            questRepository,
            focusSessionManager,
            physicalEvidenceProvider = provider,
            questEvidenceRepository = evidenceRepository,
            clock = { now }
        )
    }

    private fun startPhysical(questId: String): Boolean =
        QuestStartCoordinator(
            questRepository,
            focusSessionManager,
            physicalEvidenceProvider = provider,
            questEvidenceRepository = evidenceRepository
        ).startPhysicalQuest(questId)

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

    // ---------- PHYSICAL SIGNALS ----------

    @Test
    fun `a walk with honest steps verifies, rewards, and clears its evidence`() {
        provider.stepCount = 0
        assertTrue(startPhysical("quest_walk_20"))
        val attemptId = questRepository.getCurrentAttempt()!!.attemptId

        now += 21 * 60_000L
        provider.stepCount = 1200
        assertTrue(coordinator.declarePhysicalAttemptDone())

        assertEquals(QuestAttemptState.REWARDED, questRepository.getCurrentAttempt()?.state)
        assertEquals(15L, timeBankRepository.getTimeBank().earnedMinutes)
        assertNull("evidence must not outlive its verdict", evidenceRepository.snapshotFor(attemptId))
    }

    @Test
    fun `a walk with table steps fails and pays nothing`() {
        provider.stepCount = 0
        assertTrue(startPhysical("quest_walk_20"))

        now += 21 * 60_000L
        provider.stepCount = 300
        assertTrue(coordinator.declarePhysicalAttemptDone())

        val attempt = questRepository.getCurrentAttempt()!!
        assertEquals(QuestAttemptState.FAILED, attempt.state)
        assertTrue(attempt.verificationResult!!.reason.contains("steps"))
        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)
        assertNull(evidenceRepository.snapshotFor(attempt.attemptId))
    }

    @Test
    fun `a counter reset mid-mission fails`() {
        provider.stepCount = 900
        assertTrue(startPhysical("quest_walk_20"))

        now += 21 * 60_000L
        provider.stepCount = 200
        assertTrue(coordinator.declarePhysicalAttemptDone())

        val attempt = questRepository.getCurrentAttempt()!!
        assertEquals(QuestAttemptState.FAILED, attempt.state)
        assertTrue(attempt.verificationResult!!.reason.contains("reset"))
        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `an entertainment app during a social quest fails with the distraction reason`() {
        provider.distractionPackages = listOf("com.instagram.android")
        assertTrue(startPhysical("quest_socialize_30"))

        now += 31 * 60_000L
        assertTrue(coordinator.declarePhysicalAttemptDone())

        val attempt = questRepository.getCurrentAttempt()!!
        assertEquals(QuestAttemptState.FAILED, attempt.state)
        assertTrue(attempt.verificationResult!!.reason.contains("Entertainment app"))
        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `the podcast mission passes on the clock alone`() {
        provider.stepCount = null
        provider.distractionPackages = null
        assertTrue(startPhysical("quest_podcast_30"))

        now += 31 * 60_000L
        assertTrue(coordinator.declarePhysicalAttemptDone())

        assertEquals(QuestAttemptState.REWARDED, questRepository.getCurrentAttempt()?.state)
        assertEquals(20L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `a declaration interrupted before settling recovers on resume from stored evidence`() {
        provider.stepCount = 0
        assertTrue(startPhysical("quest_walk_20"))
        val attemptId = questRepository.getCurrentAttempt()!!.attemptId

        now += 21 * 60_000L
        provider.stepCount = 1200
        // The declare steps were recorded, then the process died before the
        // settle could run - the next resume must judge the stored evidence.
        evidenceRepository.recordStepsAtDeclare(attemptId, provider.readStepCount())
        assertTrue(questRepository.requestVerification())

        assertTrue(coordinator.evaluateAttemptOnResume(context))

        assertEquals(QuestAttemptState.REWARDED, questRepository.getCurrentAttempt()?.state)
        assertEquals(15L, timeBankRepository.getTimeBank().earnedMinutes)
        assertNull(evidenceRepository.snapshotFor(attemptId))
    }
}
