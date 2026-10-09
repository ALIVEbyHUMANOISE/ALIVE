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
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.FocusActivityType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestStartCoordinatorTest {

    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var questRepository: QuestRepository
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var evidenceRepository: QuestEvidenceRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_quest_evidence", Context.MODE_PRIVATE).edit().clear().apply()
        timeBankRepository = TimeBankRepository(context)
        questRepository = QuestRepository(context, timeBankRepository)
        focusSessionManager = FocusSessionManager(context, timeBankRepository)
        evidenceRepository = QuestEvidenceRepository(context)
    }

    private fun coordinator(
        startFocus: ((FocusActivityType, String, Long, String) -> Boolean)? = null,
        linkFocus: ((String) -> Boolean)? = null
    ): QuestStartCoordinator {
        return if (startFocus == null && linkFocus == null) {
            QuestStartCoordinator(questRepository, focusSessionManager)
        } else {
            QuestStartCoordinator(
                questRepository,
                focusSessionManager,
                startFocusSession = startFocus
                    ?: { t, g, d, a -> focusSessionManager.startFocusSession(t, g, d, a) },
                linkFocusSession = linkFocus ?: { id -> questRepository.linkFocusSession(id) }
            )
        }
    }

    @Test
    fun `successful DIGITAL quest startup links attempt and focus session`() {
        val result = coordinator().startDigitalQuest("quest_study_30")
        assertTrue(result)

        val attempt = questRepository.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(QuestAttemptState.IN_PROGRESS, attempt!!.state)

        val focusSession = focusSessionManager.getFocusSession()
        assertNotNull(focusSession)

        assertEquals(focusSession!!.focusSessionId, attempt.linkedFocusSessionId)
        assertEquals(attempt.attemptId, focusSession.linkedQuestAttemptId)
        assertEquals(FocusActivityType.STUDY, focusSession.activityType)
    }

    @Test
    fun `unsupported category returns failure and leaves no active attempt`() {
        val result = coordinator().startDigitalQuest("quest_exercise_20")
        assertFalse(result)
        assertNull(questRepository.getCurrentAttempt())
    }

    @Test
    fun `stale legacy active quest id does not block DIGITAL start`() {
        // Simulate a leftover pre-2.3.3 KEY_ACTIVE_QUEST_ID. It must no
        // longer gate the Quest Board / coordinator, since the AMBIL flow
        // no longer writes or clears it.
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)
            .edit()
            .putString("key_active_quest_id", "quest_study_30")
            .apply()

        val result = coordinator().startDigitalQuest("quest_study_30")
        assertTrue(result)

        val attempt = questRepository.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(QuestAttemptState.IN_PROGRESS, attempt!!.state)
    }

    @Test
    fun `focus creation failure fails the started attempt`() {
        val result = coordinator(startFocus = { _, _, _, _ -> false })
            .startDigitalQuest("quest_study_30")
        assertFalse(result)

        val attempt = questRepository.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(QuestAttemptState.FAILED, attempt!!.state)
        assertTrue(attempt.verificationResult!!.reason.contains("Focus"))
    }

    @Test
    fun `focus linkage failure fails the started attempt`() {
        val result = coordinator(linkFocus = { _ -> false })
            .startDigitalQuest("quest_study_30")
        assertFalse(result)

        val attempt = questRepository.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(QuestAttemptState.FAILED, attempt!!.state)
        assertTrue(attempt.verificationResult!!.reason.contains("Focus"))
    }

    @Test
    fun `focus session read-back not bound to this attempt fails the started attempt`() {
        // startFocusSession "succeeds" but persists a session NOT bound to the
        // attempt (linkedQuestAttemptId = null), e.g. an unrelated session.
        val result = coordinator(
            startFocus = { type, goal, _, _ ->
                focusSessionManager.startFocusSession(type, goal, 30L, linkedQuestAttemptId = null)
            }
        ).startDigitalQuest("quest_study_30")
        assertFalse(result)

        val attempt = questRepository.getCurrentAttempt()
        assertNotNull(attempt)
        assertEquals(QuestAttemptState.FAILED, attempt!!.state)
        assertNull(attempt!!.linkedFocusSessionId)
    }

    @Test
    fun `no IN_PROGRESS attempt remains without linked FocusSession after failure`() {
        // Focus creation failure
        coordinator(startFocus = { _, _, _, _ -> false }).startDigitalQuest("quest_study_30")
        assertNoUnlinkedInProgress()

        // Reset and exercise linkage failure
        context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit().clear().apply()
        coordinator(linkFocus = { _ -> false }).startDigitalQuest("quest_study_30")
        assertNoUnlinkedInProgress()
    }

    private fun assertNoUnlinkedInProgress() {
        val attempt = questRepository.getCurrentAttempt() ?: return
        val unlinkedInProgress =
            attempt.state == QuestAttemptState.IN_PROGRESS && attempt.linkedFocusSessionId == null
        assertFalse(
            "IN_PROGRESS DIGITAL attempt must never remain without a linked FocusSession",
            unlinkedInProgress
        )
    }

    @Test
    fun `focus activity type mapping supports only the four digital categories`() {
        assertEquals(FocusActivityType.STUDY, QuestStartCoordinator.focusActivityTypeFor(QuestCategory.STUDY))
        assertEquals(FocusActivityType.CODING, QuestStartCoordinator.focusActivityTypeFor(QuestCategory.CODING))
        assertEquals(FocusActivityType.WRITING, QuestStartCoordinator.focusActivityTypeFor(QuestCategory.WRITING))
        assertEquals(FocusActivityType.CREATIVE, QuestStartCoordinator.focusActivityTypeFor(QuestCategory.CREATIVE))
        assertNull(QuestStartCoordinator.focusActivityTypeFor(QuestCategory.EXERCISE))
        assertNull(QuestStartCoordinator.focusActivityTypeFor(QuestCategory.WALKING))
        assertNull(QuestStartCoordinator.focusActivityTypeFor(QuestCategory.CLEANING))
    }

    private fun physicalCoordinator(provider: PhysicalQuestEvidenceProvider): QuestStartCoordinator =
        QuestStartCoordinator(
            questRepository,
            focusSessionManager,
            physicalEvidenceProvider = provider,
            questEvidenceRepository = evidenceRepository
        )

    @Test
    fun `startPhysicalQuest records the opening step snapshot`() {
        val provider = object : PhysicalQuestEvidenceProvider {
            override fun readStepCount(): Int? = 42
            override fun entertainmentPackagesBetween(startMillis: Long, endMillis: Long): List<String>? = null
        }

        val started = physicalCoordinator(provider).startPhysicalQuest("quest_walk_20")
        assertTrue(started)

        val attemptId = questRepository.getCurrentAttempt()!!.attemptId
        assertEquals(com.projectego.app.data.PhysicalStepSnapshot(42, null), evidenceRepository.snapshotFor(attemptId))
    }

    @Test
    fun `startPhysicalQuest records the unavailable marker when steps cannot be read`() {
        val started = physicalCoordinator(PhysicalQuestEvidenceProvider.Unavailable)
            .startPhysicalQuest("quest_walk_20")
        assertTrue(started)

        val attemptId = questRepository.getCurrentAttempt()!!.attemptId
        val snapshot = evidenceRepository.snapshotFor(attemptId)
        assertNotNull("an unavailable reading is still a record", snapshot)
        assertNull(snapshot!!.startSteps)
    }

    @Test
    fun `startPhysicalQuest leaves no evidence for a quest without step signals`() {
        val provider = object : PhysicalQuestEvidenceProvider {
            override fun readStepCount(): Int? = 42
            override fun entertainmentPackagesBetween(startMillis: Long, endMillis: Long): List<String>? = null
        }

        val started = physicalCoordinator(provider).startPhysicalQuest("quest_podcast_30")
        assertTrue(started)

        val attemptId = questRepository.getCurrentAttempt()!!.attemptId
        assertNull(evidenceRepository.snapshotFor(attemptId))
    }
}
