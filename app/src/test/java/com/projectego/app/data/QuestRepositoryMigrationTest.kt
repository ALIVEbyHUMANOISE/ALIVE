package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.VerificationMethod
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
 * Phase 2.3.3 one-time legacy quest-state migration tests.
 *
 * The migration runs from QuestRepository's init block, so tests write the
 * legacy keys and then construct a fresh QuestRepository to trigger it.
 *
 * Legacy key names are intentionally repeated here as string literals: the
 * production constants are private, and these tests pin the exact on-disk
 * keys being migrated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestRepositoryMigrationTest {

    private lateinit var context: Context
    private lateinit var timeBankRepository: TimeBankRepository

    private val legacyActiveKey = "key_active_quest_id"
    private val legacyCompletedKey = "key_completed_quest_ids"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit().clear().apply()
        timeBankRepository = TimeBankRepository(context)
    }

    private fun questPrefs() =
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)

    private fun newRepository() = QuestRepository(context, timeBankRepository)

    @Test
    fun `legacy active quest id is cleared on init`() {
        questPrefs().edit().putString(legacyActiveKey, "quest_study_30").apply()

        newRepository()

        assertNull(questPrefs().getString(legacyActiveKey, null))
    }

    @Test
    fun `legacy completed quest ids are cleared on init`() {
        questPrefs().edit().putStringSet(legacyCompletedKey, setOf("quest_study_30")).apply()

        newRepository()

        assertNull(questPrefs().getStringSet(legacyCompletedKey, null))
    }

    @Test
    fun `time bank is untouched by migration`() {
        timeBankRepository.addEarnedMinutes(42L)
        questPrefs().edit()
            .putString(legacyActiveKey, "quest_read_30")
            .putStringSet(legacyCompletedKey, setOf("quest_walk_20"))
            .apply()

        newRepository()

        assertEquals(42L, timeBankRepository.getTimeBank().earnedMinutes)
    }

    @Test
    fun `quest attempt is untouched by migration`() {
        val repository = newRepository()
        assertTrue(repository.commitQuest("quest_study_30", VerificationMethod.DIGITAL, 30L))
        assertTrue(repository.startAttempt())
        val attemptIdBefore = repository.getCurrentAttempt()!!.attemptId

        // Re-seed legacy state as if it survived, then re-init.
        questPrefs().edit()
            .putString(legacyActiveKey, "quest_study_30")
            .putStringSet(legacyCompletedKey, setOf("quest_study_30"))
            .apply()
        val reinitialized = newRepository()

        val attempt = reinitialized.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(attemptIdBefore, attempt!!.attemptId)
        assertEquals(QuestAttemptState.IN_PROGRESS, attempt.state)
    }

    @Test
    fun `migration runs only once`() {
        questPrefs().edit().putString(legacyActiveKey, "quest_study_30").apply()

        newRepository() // first init: clears + sets marker

        // Re-add legacy state to prove a later init does not clear again.
        questPrefs().edit().putString(legacyActiveKey, "quest_read_30").apply()
        newRepository() // second init: marker present, must be a no-op

        assertEquals("quest_read_30", questPrefs().getString(legacyActiveKey, null))
    }

    @Test
    fun `migration is idempotent when run repeatedly`() {
        newRepository()
        val first = questPrefs().getBoolean("key_legacy_quest_state_migrated_v1", false)
        assertTrue(first)

        newRepository()
        newRepository()

        assertTrue(questPrefs().getBoolean("key_legacy_quest_state_migrated_v1", false))
        assertNull(questPrefs().getString(legacyActiveKey, null))
        assertNull(questPrefs().getStringSet(legacyCompletedKey, null))
    }

    @Test
    fun `previously blocked quest becomes AVAILABLE after migration`() {
        questPrefs().edit()
            .putStringSet(legacyCompletedKey, setOf("quest_study_30"))
            .apply()

        val repository = newRepository()

        val quest = repository.getAllQuests().find { it.id == "quest_study_30" }
        assertNotNull(quest)
        assertEquals(QuestStatus.AVAILABLE, quest!!.status)
        assertFalse(repository.getAllQuests().any { it.status == QuestStatus.COMPLETED })
    }
}
