package com.projectego.app.ui

import androidx.annotation.DrawableRes
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory

/**
 * Which pixel mark stands for which kind of doing.
 *
 * A quest list and a focus form are both lists of verbs, and a verb reads faster
 * as a shape than as a word: the book says "reading" before the title finishes
 * rendering. The marks are drawn on the same 16 grid as the character, so a row
 * of them still looks like one hand drew it.
 *
 * `OTHER` deliberately borrows the star accent rather than getting a mark of its
 * own: a picture for "none of the above" would claim a shape the category does
 * not have.
 */
object PixelMarks {

    @DrawableRes
    fun focus(type: FocusActivityType): Int = when (type) {
        FocusActivityType.STUDY -> R.drawable.ic_pixel_book
        FocusActivityType.WORK -> R.drawable.ic_pixel_briefcase
        FocusActivityType.CODING -> R.drawable.ic_pixel_terminal
        FocusActivityType.CREATIVE -> R.drawable.ic_pixel_brush
        FocusActivityType.WRITING -> R.drawable.ic_pixel_pen
    }

    @DrawableRes
    fun quest(category: QuestCategory): Int = when (category) {
        QuestCategory.EXERCISE -> R.drawable.ic_pixel_dumbbell
        QuestCategory.STUDY -> R.drawable.ic_pixel_book
        QuestCategory.READING -> R.drawable.ic_pixel_books
        QuestCategory.WALKING -> R.drawable.ic_pixel_footprints
        QuestCategory.CLEANING -> R.drawable.ic_pixel_broom
        QuestCategory.CREATIVE -> R.drawable.ic_pixel_brush
        QuestCategory.CODING -> R.drawable.ic_pixel_terminal
        QuestCategory.WRITING -> R.drawable.ic_pixel_pen
        QuestCategory.MINDFULNESS -> R.drawable.ic_pixel_lotus
        QuestCategory.SOCIAL -> R.drawable.ic_pixel_people
        QuestCategory.OTHER -> R.drawable.alive_accent_star
    }

    /** Spoken description, so the mark is never the only carrier of meaning. */
    fun description(category: QuestCategory): Int = when (category) {
        QuestCategory.EXERCISE -> R.string.cd_mark_exercise
        QuestCategory.STUDY -> R.string.cd_mark_study
        QuestCategory.READING -> R.string.cd_mark_reading
        QuestCategory.WALKING -> R.string.cd_mark_walking
        QuestCategory.CLEANING -> R.string.cd_mark_cleaning
        QuestCategory.CREATIVE -> R.string.cd_mark_creative
        QuestCategory.CODING -> R.string.cd_mark_coding
        QuestCategory.WRITING -> R.string.cd_mark_writing
        QuestCategory.MINDFULNESS -> R.string.cd_mark_mindfulness
        QuestCategory.SOCIAL -> R.string.cd_mark_social
        QuestCategory.OTHER -> R.string.cd_mark_other
    }
}
