package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.QuestStatus
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
 * No production code was modified to support these tests.
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

    @Test
    fun `a completed quest cannot be started again`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.startQuest(questId)
        questRepository.completeActiveQuest(questId)

        val restarted = questRepository.startQuest(questId)

        assertFalse(restarted)
    }

    @Test
    fun `completing an active quest marks it completed`() {
        val questId = questRepository.getAllQuests().first().id
        questRepository.startQuest(questId)

        val reward = questRepository.completeActiveQuest(questId)

        assertNotNull(reward)
        val updatedQuest = questRepository.getAllQuests().find { it.id == questId }
        assertEquals(QuestStatus.COMPLETED, updatedQuest?.status)
        assertNull(questRepository.getActiveQuestId())
    }

    @Test
    fun `completing an active quest transfers its reward to TimeBank`() {
        val quest = questRepository.getAllQuests().first()
        questRepository.startQuest(quest.id)

        val reward = questRepository.completeActiveQuest(quest.id)

        assertNotNull(reward)
        assertEquals(quest.rewardMinutes, reward)
        assertEquals(quest.rewardMinutes, timeBankRepository.getTimeBank().earnedMinutes)
        assertEquals(quest.rewardMinutes, timeBankRepository.getTimeBank().availableMinutes)
    }

    @Test
    fun `completing the same quest again does not duplicate the reward`() {
        val quest = questRepository.getAllQuests().first()
        questRepository.startQuest(quest.id)
        val firstReward = questRepository.completeActiveQuest(quest.id)

        val secondReward = questRepository.completeActiveQuest(quest.id)

        assertNotNull(firstReward)
        assertNull(secondReward)
        assertEquals(quest.rewardMinutes, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `completing when there is no active quest does not create a reward`() {
        val someQuestId = questRepository.getAllQuests().first().id

        val reward = questRepository.completeActiveQuest(someQuestId)

        assertNull(reward)
        assertEquals(0L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `repository state persists when recreated with the same application context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quest = questRepository.getAllQuests().first()
        questRepository.startQuest(quest.id)
        questRepository.completeActiveQuest(quest.id)

        // Recreate the repositories against the SAME underlying SharedPreferences.
        val recreatedTimeBankRepository = TimeBankRepository(context)
        val recreatedQuestRepository = QuestRepository(context, recreatedTimeBankRepository)

        val recreatedQuest = recreatedQuestRepository.getAllQuests().find { it.id == quest.id }
        assertEquals(QuestStatus.COMPLETED, recreatedQuest?.status)
        assertEquals(quest.rewardMinutes, recreatedTimeBankRepository.getTimeBank().earnedMinutes)
    }
}
