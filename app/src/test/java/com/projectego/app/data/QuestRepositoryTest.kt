package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.VerificationMethod
import com.projectego.app.model.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [QuestRepository], run under Robolectric so the real
 * Context/SharedPreferences-backed implementation can be exercised on the
 * JVM without a device/emulator.
 *
 * @Config(sdk = [36]) pins the simulated Android version Robolectric uses,
 * because this project's compileSdk/targetSdk (37) is newer than the
 * highest SDK level officially supported by Robolectric 4.16.1 (SDK 36).
 *
 * Quests are always selected through the repository's own public API
 * (getAllQuests()) - the private static INITIAL_QUESTS catalog is never
 * accessed directly.
 *
 * Phase 2.3.2: completeActiveQuest() was removed from QuestRepository (it
 * was a second reward path that bypassed verification). Tests that used
 * to set up their "quest is completed" state via completeActiveQuest()
 * now do so via the real commitQuest -> startAttempt -> requestVerification
 * -> verifyQuest -> claimReward pipeline instead - this is a migration of
 * those tests' intent, not a removal of coverage.
 *
 * No production code beyond QuestRepository.kt (and the now-inert
 * MainActivity dev button call site) was modified to support these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestRepositoryTest {

    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var questRepository: QuestRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Isolation: clear only Project EGO's own quest/time-bank preferences
        // before each test, so tests never see state left by a previous test.
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)
            .edit().clear().apply()

        timeBankRepository = TimeBankRepository(context)
        questRepository = QuestRepository(context, timeBankRepository)
    }

    // ============================================================
    // Legacy Phase 2.1 quest board - unchanged behavior, preserved as-is.
    // ============================================================

    @Test
    fun `starting an available quest makes it active`() {
        val questId = questRepository.getAllQuests().first().id

        val started = questRepository.startQuest(questId)

        assertTrue(started)
        assertEquals(questId, questRepository.getActiveQuestId())
        assertEquals(QuestStatus.ACTIVE, questRepository.getActiveQuest()?.status)
    }

    @Test
    fun `only one quest can be active at a time`() {
        val quests = questRepository.getAllQuests()
        val firstId = quests[0].id
        val secondId = quests[1].id

        val firstStarted = questRepository.startQuest(firstId)
        val secondStarted = questRepository.startQuest(secondId)

        assertTrue(firstStarted)
        assertFalse(secondStarted)
        assertEquals(firstId, questRepository.getActiveQuestId())
    }

    // ============================================================
    // Legacy tests migrated: their setup previously used
    // completeActiveQuest(); they now use the real verification pipeline
    // to reach the same "permanently completed" end state.
    // ============================================================

    @Test
    fun `a permanently completed quest cannot be started via the legacy startQuest API`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")
        questRepository.claimReward()

        val restarted = questRepository.startQuest(questId)

        assertFalse(restarted)
    }

    @Test
    fun `legacy quest board COMPLETED status persists after repository reconstruction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quest = questRepository.getAllQuests().first()
        questRepository.commitQuest(quest.id, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")
        questRepository.claimReward()

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedQuestRepository = QuestRepository(context, recreatedTimeBankRepository)

        val recreatedQuest = recreatedQuestRepository.getAllQuests().find { it.id == quest.id }
        assertEquals(QuestStatus.COMPLETED, recreatedQuest?.status)
        assertEquals(quest.rewardMinutes, recreatedTimeBankRepository.getTimeBank().earnedMinutes)
    }

    // ============================================================
    // Phase 2.3.2: QuestAttempt commit/start/verify/reward pipeline.
    // ============================================================

    @Test
    fun `committing an available quest transitions it to COMMITTED`() {
        val questId = questRepository.getAllQuests().first().id

        val committed = questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)

        assertTrue(committed)
        assertEquals(QuestAttemptState.COMMITTED, questRepository.getCurrentAttempt()?.state)
        assertEquals(questId, questRepository.getCurrentAttempt()?.questId)
    }

    @Test
    fun `committing an unknown quest id is rejected`() {
        val committed = questRepository.commitQuest("quest_does_not_exist", VerificationMethod.DIGITAL, 30L)

        assertFalse(committed)
        assertNull(questRepository.getCurrentAttempt())
    }

    @Test
    fun `committing while another attempt is already active is rejected`() {
        val quests = questRepository.getAllQuests()
        val firstId = quests[0].id
        val secondId = quests[1].id
        questRepository.commitQuest(firstId, VerificationMethod.DIGITAL, 30L)

        val secondCommitted = questRepository.commitQuest(secondId, VerificationMethod.PHYSICAL, 20L)

        assertFalse(secondCommitted)
        assertEquals(firstId, questRepository.getCurrentAttempt()?.questId)
    }

    @Test
    fun `committed attempt moves to IN_PROGRESS via startAttempt`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)

        val started = questRepository.startAttempt()

        assertTrue(started)
        assertEquals(QuestAttemptState.IN_PROGRESS, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `in-progress attempt moves to PENDING_VERIFICATION via requestVerification`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()

        val requested = questRepository.requestVerification()

        assertTrue(requested)
        assertEquals(QuestAttemptState.PENDING_VERIFICATION, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `verifyQuest with PASS moves PENDING_VERIFICATION to VERIFIED`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()

        val verified = questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")

        assertTrue(verified)
        assertEquals(QuestAttemptState.VERIFIED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `verifyQuest with FAIL moves PENDING_VERIFICATION to FAILED`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()

        val verified = questRepository.verifyQuest(VerificationOutcome.FAIL, "Test evidence failed")

        assertTrue(verified)
        assertEquals(QuestAttemptState.FAILED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `illegal transition is rejected - starting an attempt that was never committed`() {
        val started = questRepository.startAttempt()

        assertFalse(started)
        assertNull(questRepository.getCurrentAttempt())
    }

    @Test
    fun `illegal transition is rejected - requesting verification before starting the attempt`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)

        val requested = questRepository.requestVerification()

        assertFalse(requested)
        assertEquals(QuestAttemptState.COMMITTED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `reward is rejected at every state before VERIFIED`() {
        val questId = questRepository.getAllQuests().first().id

        assertNull(questRepository.claimReward()) // nothing committed yet

        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        assertNull(questRepository.claimReward()) // COMMITTED

        questRepository.startAttempt()
        assertNull(questRepository.claimReward()) // IN_PROGRESS

        questRepository.requestVerification()
        assertNull(questRepository.claimReward()) // PENDING_VERIFICATION

        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `claiming reward on a VERIFIED attempt moves it to REWARDED and grants TimeBank minutes`() {
        val quest = questRepository.getAllQuests().first()
        questRepository.commitQuest(quest.id, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")

        val reward = questRepository.claimReward()

        assertEquals(quest.rewardMinutes, reward)
        assertEquals(QuestAttemptState.REWARDED, questRepository.getCurrentAttempt()?.state)
        assertEquals(quest.rewardMinutes, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `claiming reward twice only grants the reward once`() {
        val quest = questRepository.getAllQuests().first()
        questRepository.commitQuest(quest.id, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")

        val firstClaim = questRepository.claimReward()
        val secondClaim = questRepository.claimReward()

        assertEquals(quest.rewardMinutes, firstClaim)
        assertNull(secondClaim)
        assertEquals(quest.rewardMinutes, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `a FAILED attempt cannot receive a reward`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.FAIL, "Test evidence failed")

        val reward = questRepository.claimReward()

        assertNull(reward)
        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `failQuest moves an in-progress attempt directly to FAILED`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()

        val failed = questRepository.failQuest("User abandoned the attempt")

        assertTrue(failed)
        assertEquals(QuestAttemptState.FAILED, questRepository.getCurrentAttempt()?.state)
        assertEquals(QuestStatus.AVAILABLE, questRepository.getAllQuests().find { it.id == questId }?.status)
    }

    @Test
    fun `repository reconstruction preserves the current attempt`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.PHYSICAL, 25L)
        questRepository.startAttempt()

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedQuestRepository = QuestRepository(context, recreatedTimeBankRepository)

        val reconstructed = recreatedQuestRepository.getCurrentAttempt()
        assertEquals(questId, reconstructed?.questId)
        assertEquals(VerificationMethod.PHYSICAL, reconstructed?.method)
        assertEquals(QuestAttemptState.IN_PROGRESS, reconstructed?.state)
        assertEquals(25L, reconstructed?.committedDurationMinutes)
    }

    @Test
    fun `committedDurationMinutes is unchanged through start and verification`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 45L)

        questRepository.startAttempt()
        assertEquals(45L, questRepository.getCurrentAttempt()?.committedDurationMinutes)

        questRepository.requestVerification()
        assertEquals(45L, questRepository.getCurrentAttempt()?.committedDurationMinutes)

        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")
        assertEquals(45L, questRepository.getCurrentAttempt()?.committedDurationMinutes)
    }

    @Test
    fun `verification result is persisted on the attempt`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()

        questRepository.verifyQuest(VerificationOutcome.PASS, "Focus session completed with 0 violations")

        val result = questRepository.getCurrentAttempt()?.verificationResult
        assertEquals(VerificationOutcome.PASS, result?.outcome)
        assertEquals("Focus session completed with 0 violations", result?.reason)
        assertNotNull(result?.evaluatedTimestamp)
    }

    @Test
    fun `only REWARDED permanently completes the quest on the legacy quest board`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")

        // Not yet rewarded - the legacy board must not show COMPLETED yet.
        assertEquals(QuestStatus.AVAILABLE, questRepository.getAllQuests().find { it.id == questId }?.status)

        questRepository.claimReward()

        assertEquals(QuestStatus.COMPLETED, questRepository.getAllQuests().find { it.id == questId }?.status)
    }

    @Test
    fun `a FAILED attempt does not permanently complete the quest`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.FAIL, "Test evidence failed")

        assertEquals(QuestStatus.AVAILABLE, questRepository.getAllQuests().find { it.id == questId }?.status)
    }

    @Test
    fun `after being rewarded the same quest cannot be committed again`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")
        questRepository.claimReward()

        val recommitted = questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)

        assertFalse(recommitted)
    }

    @Test
    fun `after FAILED, verification and reward operations no longer proceed`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.FAIL, "Test evidence failed")

        assertFalse(questRepository.verifyQuest(VerificationOutcome.PASS, "Retry attempt"))
        assertNull(questRepository.claimReward())
        assertEquals(QuestAttemptState.FAILED, questRepository.getCurrentAttempt()?.state)
    }

    @Test
    fun `claimed reward always equals the quest catalog reward amount, never an arbitrary amount`() {
        val quest = questRepository.getAllQuests()[2]
        questRepository.commitQuest(quest.id, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")

        val reward = questRepository.claimReward()

        assertEquals(quest.rewardMinutes, reward)
    }

    // ============================================================
    // Phase 2.3.2 correction pass: claimReward() now persists the
    // REWARDED state synchronously (commit()) before granting the
    // TimeBank reward, instead of the repository's usual apply()-based
    // persistAttempt() helper. This test cannot force commit() to fail
    // without a mocking framework or a constructor-injection refactor,
    // both out of scope here - it honestly only confirms the success
    // path: that REWARDED state and the granted minutes are durably
    // readable via a freshly reconstructed repository right after
    // claimReward() succeeds.
    // ============================================================

    @Test
    fun `after claimReward succeeds, REWARDED state and granted minutes persist across repository reconstruction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quest = questRepository.getAllQuests().first()
        questRepository.commitQuest(quest.id, VerificationMethod.DIGITAL, 30L)
        questRepository.startAttempt()
        questRepository.requestVerification()
        questRepository.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")
        questRepository.claimReward()

        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedQuestRepository = QuestRepository(context, recreatedTimeBankRepository)

        assertEquals(QuestAttemptState.REWARDED, recreatedQuestRepository.getCurrentAttempt()?.state)
        assertEquals(quest.rewardMinutes, recreatedTimeBankRepository.getTimeBank().earnedMinutes)
    }
}
