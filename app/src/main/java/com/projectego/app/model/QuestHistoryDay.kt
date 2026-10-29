package com.projectego.app.model

/**
 * One day of the mission log.
 *
 * [recorded] is the field that keeps this honest. A day ALIVE has no entry for
 * is not a day on which nothing was finished - it is a day the app was never
 * opened, or one that happened before this log existed - and saying "empty"
 * about it would be the same lie the usage chart already refuses, where a
 * missing day is drawn apart from a zero one.
 *
 * [completions] names what was finished, in the order it was finished.
 */
data class QuestHistoryDay(
    val dayKey: String,
    val completedCount: Int,
    val earnedMinutes: Long,
    val recorded: Boolean,
    val completions: List<QuestCompletion> = emptyList()
)
