package com.projectego.app.domain

import com.projectego.app.model.Quest
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestDifficulty
import com.projectego.app.model.QuestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day's score, which is the one number on the mission board that has to be
 * right in two directions: too few counted, and a mission is missing; too many in
 * the denominator, and the bar can never be filled.
 */
class QuestDayProgressTest {

    private fun quest(id: String, status: QuestStatus) = Quest(
        id = id,
        title = id,
        description = "",
        category = QuestCategory.WALKING,
        difficulty = QuestDifficulty.EASY,
        estimatedDurationMinutes = 20,
        rewardMinutes = 15,
        status = status
    )

    @Test
    fun `the denominator is the board, not a remembered catalog size`() {
        val withoutOwnMission = (1..11).map { quest("q$it", QuestStatus.AVAILABLE) }
        val withOwnMission = withoutOwnMission + quest(CustomQuest.ID, QuestStatus.AVAILABLE)

        assertEquals(11, QuestDayProgress.of(withoutOwnMission).total)
        assertEquals(12, QuestDayProgress.of(withOwnMission).total)
    }

    @Test
    fun `only missions that paid out are counted as finished`() {
        val board = listOf(
            quest("a", QuestStatus.COMPLETED),
            quest("b", QuestStatus.COMPLETED),
            quest("c", QuestStatus.ACTIVE),
            quest("d", QuestStatus.AVAILABLE)
        )
        val progress = QuestDayProgress.of(board)

        assertEquals(2, progress.completed)
        assertEquals(4, progress.total)
        assertEquals(0.5f, progress.share, 0.0001f)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `a board with nothing on it is empty rather than infinitely full`() {
        val progress = QuestDayProgress.of(emptyList())

        assertEquals(0, progress.total)
        assertEquals(0f, progress.share, 0f)
        assertFalse("an empty board is not an accomplished day", progress.isComplete)
    }

    @Test
    fun `the day is complete only when every offered mission has paid`() {
        val board = (1..3).map { quest("q$it", QuestStatus.COMPLETED) }

        assertTrue(QuestDayProgress.of(board).isComplete)
        assertEquals(1f, QuestDayProgress.of(board).share, 0.0001f)
    }

    @Test
    fun `a mission running beside finished ones does not move the score`() {
        val board = listOf(
            quest("a", QuestStatus.COMPLETED),
            quest("b", QuestStatus.ACTIVE)
        )

        assertEquals(1, QuestDayProgress.of(board).completed)
    }
}
