package com.projectego.app.domain

import com.projectego.app.model.Quest
import com.projectego.app.model.QuestStatus

/**
 * Today's mission score: how many of the board's missions have paid out.
 *
 * Kept apart from the screen on purpose. The number has to answer one question
 * and nothing else - "of what is offered today, what is done" - so the board can
 * grow, shrink, or gain a mission the person wrote, and the figure beside the bar
 * stays the truth without a second rule needing updating somewhere else.
 *
 * [total] counts the board rather than the fixed catalog: an unwritten mission is
 * not a slot waiting to be filled, and a denominator that counts it would be
 * asking for something ALIVE never offered.
 */
data class QuestDayProgress(
    val completed: Int,
    val total: Int
) {
    /** This day's share of its own board, and 0 for a board with nothing on it. */
    val share: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()

    /** True when every mission the board offered today has paid out. */
    val isComplete: Boolean
        get() = total > 0 && completed >= total

    companion object {
        fun of(quests: List<Quest>): QuestDayProgress = QuestDayProgress(
            completed = quests.count { it.status == QuestStatus.COMPLETED },
            total = quests.size
        )
    }
}
