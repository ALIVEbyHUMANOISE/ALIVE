package com.projectego.app.model

/**
 * One mission that paid out, recorded when it paid.
 *
 * [title] is a snapshot, not a lookup. A catalog mission can be renamed in a
 * later release, and the person's own mission can be rewritten or deleted the
 * moment they open the editor - but the thing they finished had the words it had,
 * and a history that renamed it afterwards would be rewriting the past. The board
 * still shows live titles; this is the record.
 */
data class QuestCompletion(
    val questId: String,
    val title: String,
    val minutes: Long,
    val completedAt: Long
)
