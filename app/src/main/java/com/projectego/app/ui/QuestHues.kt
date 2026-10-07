package com.projectego.app.ui

import androidx.annotation.ColorRes
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory

/**
 * Which hue a kind of doing wears.
 *
 * The pair of files this belongs to is [PixelMarks]: a category gets a shape and
 * a colour at the same time, so a quest card is recognisable from across the room
 * and still says what it is when the shape is read out by TalkBack.
 *
 * These hues are chrome, not meaning. They appear on a card's tab and inside its
 * icon tile and nowhere else - never on text, never on a status, never on a
 * button. `AlivePaletteTest` checks that ink stays readable on every one of them,
 * because the marks drawn inside are ink.
 */
object QuestHues {

    @ColorRes
    fun quest(category: QuestCategory): Int = when (category) {
        QuestCategory.EXERCISE -> R.color.alive_cat_exercise
        QuestCategory.STUDY -> R.color.alive_cat_study
        QuestCategory.READING -> R.color.alive_cat_reading
        QuestCategory.WALKING -> R.color.alive_cat_walking
        QuestCategory.CLEANING -> R.color.alive_cat_cleaning
        QuestCategory.CREATIVE -> R.color.alive_cat_creative
        QuestCategory.CODING -> R.color.alive_cat_coding
        QuestCategory.WRITING -> R.color.alive_cat_writing
        QuestCategory.OTHER -> R.color.alive_cat_other
    }

    @ColorRes
    fun focus(type: FocusActivityType): Int = when (type) {
        FocusActivityType.STUDY -> R.color.alive_cat_study
        // Work is the one focus activity with no quest category of its own, so it
        // gets its own tile hue rather than being sent to blue_work_mid - ink on
        // that measures 4.3:1, and a mark you cannot read is a mark that is not
        // doing its job.
        FocusActivityType.WORK -> R.color.alive_cat_work
        FocusActivityType.CODING -> R.color.alive_cat_coding
        FocusActivityType.CREATIVE -> R.color.alive_cat_creative
        FocusActivityType.WRITING -> R.color.alive_cat_writing
    }
}
