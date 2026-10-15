package com.projectego.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.CustomQuestRepository
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
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
 * The one mission a person writes, from the board to the payout.
 *
 * Three things have to hold together for this to be more than decoration: the
 * board must show the words that are on file right now, the id must be fixed so
 * a rewrite is not a second quest, and the payout must be the catalog's value
 * for the length - decided from the attempt, so editing afterwards cannot void
 * a reward that was already earned. What the person does NOT decide is the app
 * list, so the session it starts is an ordinary one for its kind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomMissionFlowTest {

    private lateinit var context: Context
    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var mission: CustomQuestRepository
    private lateinit var focusSessionManager: FocusSessionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(
            "project_ego_quests",
            "project_ego_time_bank",
            "project_ego_custom_quest",
            "project_ego_focus_session"
        ).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
        }
        timeBankRepository = TimeBankRepository(context)
        mission = CustomQuestRepository(context)
        focusSessionManager = FocusSessionManager(context, timeBankRepository)
    }

    private fun board(): QuestRepository =
        QuestRepository(context, timeBankRepository) { mission.stored() }

    @Test
    fun `with nothing written the board has no mission on it`() {
        assertTrue(board().getAllQuests().none { it.id == CustomQuest.ID })
    }

    @Test
    fun `a written mission stands on the board in the author's words and kind`() {
        mission.save("ulang bab empat statistika", 20, QuestCategory.STUDY)

        val entry = board().getAllQuests().find { it.id == CustomQuest.ID }
        assertNotNull(entry)
        assertEquals("ulang bab empat statistika", entry!!.title)
        assertEquals(QuestCategory.STUDY, entry.category)
        assertEquals(20, entry.estimatedDurationMinutes)
        assertEquals(CustomQuest.rewardFor(20), entry.rewardMinutes)
        assertEquals(QuestStatus.AVAILABLE, entry.status)
    }

    @Test
    fun `rewriting a mission changes its words and never adds a second quest`() {
        mission.save("ulang statistika", 20, QuestCategory.STUDY)
        assertEquals(1, board().getAllQuests().count { it.id == CustomQuest.ID })

        mission.save("nulis catatan bab empat", 10, QuestCategory.WRITING)
        val board = board()
        assertEquals(1, board.getAllQuests().count { it.id == CustomQuest.ID })
        val entry = board.getAllQuests().find { it.id == CustomQuest.ID }!!
        assertEquals("nulis catatan bab empat", entry.title)
        assertEquals(QuestCategory.WRITING, entry.category)
    }

    @Test
    fun `clearing a mission takes its entry off the board with it`() {
        mission.save("ulang statistika", 20, QuestCategory.STUDY)
        mission.clear()

        assertTrue(board().getAllQuests().none { it.id == CustomQuest.ID })
        // And the id is no longer attemptable, so a stale screen cannot start it.
        assertFalse(board().commitQuest(CustomQuest.ID, VerificationMethod.DIGITAL, 20L))
    }

    @Test
    fun `a mission starts an ordinary session of the kind it was given`() {
        mission.save("ulang statistika", 20, QuestCategory.STUDY)
        val board = board()
        val starter = QuestStartCoordinator(board, focusSessionManager)

        assertTrue(starter.startDigitalQuest(CustomQuest.ID))

        val attempt = board.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(20L, attempt!!.committedDurationMinutes)

        val session = focusSessionManager.getLastFocusSession()
        assertNotNull(session)
        assertEquals(FocusActivityType.STUDY, session!!.activityType)
        assertEquals(20L, session.durationMinutes)
        // Null, not a list of the person's own: a mission runs on ALIVE's apps
        // for its kind, exactly like a catalog quest. This is the line that
        // closes the hole where naming TikTok paid the person for scrolling it.
        assertNull(session.approvedOverride)
    }

    @Test
    fun `a mission that passed pays the catalog value for its length, once`() {
        mission.save("duduk diam sepuluh menit", 10, QuestCategory.STUDY)
        val board = board()

        assertTrue(board.commitQuest(CustomQuest.ID, VerificationMethod.DIGITAL, 10L))
        assertTrue(board.startAttempt())
        assertTrue(board.requestVerification())
        assertTrue(board.verifyQuest(VerificationOutcome.PASS, "Watcher saw 10 minutes with 0 violations"))

        assertEquals(10L, board.claimReward())
        assertEquals(10L, timeBankRepository.getTimeBank().availableMinutes)
        // Paid once: the second claim finds a REWARDED attempt and grants nothing.
        assertNull(board.claimReward())
        assertEquals(10L, timeBankRepository.getTimeBank().availableMinutes)
    }

    @Test
    fun `a reward already earned survives the mission being cleared`() {
        mission.save("duduk diam sepuluh menit", 10, QuestCategory.STUDY)
        val board = board()

        board.commitQuest(CustomQuest.ID, VerificationMethod.DIGITAL, 10L)
        board.startAttempt()
        board.requestVerification()
        board.verifyQuest(VerificationOutcome.PASS, "Watcher saw 10 minutes with 0 violations")

        // The attempt is the record of what was agreed; the words on the board
        // are not. Deleting them must not delete the minutes that were spent.
        mission.clear()
        assertEquals(10L, board.claimReward())
    }
}