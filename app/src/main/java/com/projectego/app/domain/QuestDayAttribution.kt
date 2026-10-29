package com.projectego.app.domain

import com.projectego.app.data.LockStateManager
import com.projectego.app.model.QuestAttempt

/**
 * Which day a finished mission counts for.
 *
 * Not the day the reward button was pressed. A 23:40 exercise window that closes
 * itself at 00:00 was lived on the evening before, and stamping it with the claim
 * moment cost a person their next day: the rollover judged "yesterday" by a marker
 * that had already flipped to "today", decided nothing had been done, and turned
 * the next day into a no-entertainment day.
 *
 * The honest anchor is the window itself: the finish the person declared, when
 * there is one; otherwise the end of the window they committed to; otherwise its
 * start; otherwise the moment the attempt was committed.
 */
object QuestDayAttribution {

    private const val MILLIS_PER_MINUTE = 60_000L

    fun livedAt(attempt: QuestAttempt): Long {
        attempt.activityEndTimestamp?.let { return it }

        val startedAt = attempt.activityStartTimestamp
        if (startedAt != null) {
            val minutes = attempt.committedDurationMinutes
            return if (minutes > 0L) startedAt + minutes * MILLIS_PER_MINUTE else startedAt
        }

        return attempt.committedTimestamp
    }

    fun dayKeyOf(attempt: QuestAttempt): String = LockStateManager.dayKeyOf(livedAt(attempt))
}
