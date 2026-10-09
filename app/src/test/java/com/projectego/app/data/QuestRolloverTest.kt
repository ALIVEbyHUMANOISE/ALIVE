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
 * The daily rollover, which is what keeps the quest board a board and not a
 * collection of trophies.
 *
 * Two things have to be true at once, and they pull in opposite directions: a
 * quest finished yesterday must be offered again today, and a session that is
 * still running at midnight must not be deleted by the clock. Both are tested
 * here, along with the claim that is still waiting to be collected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestRolloverTest {

    private lateinit var context: Context
    private lateinit var timeBankRepository: TimeBankRepository

    /** Two fixed instants a day apart; the repository takes the clock as a seam. */
    private val dayOne = 1_760_000_000_000L
    private val dayTwo = dayOne + 86_400_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)
            .edit().clear().apply()
        timeBankRepository = TimeBankRepository(context)
    }

    private fun repositoryAt(now: Long) = QuestRepository(context, timeBankRepository) { now }

    /** The real pipeline, so the attempt under test is a real one. */
    private fun QuestRepository.rewardFirstQuest() {
        val questId = getAllQuests().first().id
        commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        startAttempt()
        requestVerification()
        verifyQuest(VerificationOutcome.PASS, "Test evidence passed")
        claimReward()
    }

    @Test
    fun `a quest that paid out yesterday is on offer again today`() {
        val yesterday = repositoryAt(dayOne)
        yesterday.rewardFirstQuest()
        val questId = yesterday.getAllQuests().first().id
        assertEquals(QuestStatus.COMPLETED, yesterday.getAllQuests().first { it.id == questId }.status)

        val today = repositoryAt(dayTwo)
        assertTrue(today.rolloverIfNeeded())

        assertEquals(QuestStatus.AVAILABLE, today.getAllQuests().first { it.id == questId }.status)
        assertNull(today.getCurrentAttempt())
        assertNull(today.getActiveQuestId())
    }

    @Test
    fun `an attempt that failed last night is gone this morning`() {
        val yesterday = repositoryAt(dayOne)
        val questId = yesterday.getAllQuests().first().id
        yesterday.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        yesterday.startAttempt()
        yesterday.failQuest("Test failure")
        assertEquals(QuestAttemptState.FAILED, yesterday.getCurrentAttempt()?.state)

        val today = repositoryAt(dayTwo)
        today.rolloverIfNeeded()

        assertNull(today.getCurrentAttempt())
    }

    @Test
    fun `a session still in flight is not deleted by midnight`() {
        val yesterday = repositoryAt(dayOne)
        val questId = yesterday.getAllQuests().first().id
        yesterday.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        yesterday.startAttempt()

        val today = repositoryAt(dayTwo)
        today.rolloverIfNeeded()

        val attempt = today.getCurrentAttempt()
        assertNotNull("a running session was thrown away by the rollover", attempt)
        assertEquals(QuestAttemptState.IN_PROGRESS, attempt?.state)
        assertEquals(questId, attempt?.questId)
    }

    @Test
    fun `a verdict waiting to be claimed survives too`() {
        val yesterday = repositoryAt(dayOne)
        val questId = yesterday.getAllQuests().first().id
        yesterday.commitQuest(questId, VerificationMethod.DIGITAL, 30L)
        yesterday.startAttempt()
        yesterday.requestVerification()
        yesterday.verifyQuest(VerificationOutcome.PASS, "Test evidence passed")

        val today = repositoryAt(dayTwo)
        today.rolloverIfNeeded()

        assertEquals(QuestAttemptState.VERIFIED, today.getCurrentAttempt()?.state)
        assertNotNull("the claim must still be collectable the next morning", today.claimReward())
    }

    @Test
    fun `the rollover happens once a day, not once a read`() {
        val today = repositoryAt(dayTwo)
        assertTrue(today.rolloverIfNeeded())
        assertFalse(today.rolloverIfNeeded())

        // And the state it produced is the state it keeps.
        today.commitQuest(today.getAllQuests().first().id, VerificationMethod.DIGITAL, 30L)
        assertFalse(today.rolloverIfNeeded())
        assertNotNull(today.getCurrentAttempt())
    }
}
