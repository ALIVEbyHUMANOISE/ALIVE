package com.projectego.app.ui

import androidx.annotation.ColorRes
import com.projectego.app.R

/**
 * The order data colours are handed out in.
 *
 * Ordered dark, bright, mid, pale so that no two neighbouring slices - and not
 * the wrap-around pair either - are ever two shades of the same cream. The old
 * series put warm gray, soft forest and paper next to each other, which meant
 * three slices of a ring were separated by nothing but a two-pixel stroke.
 *
 * Four slices is the ceiling. Beyond that a ring stops being readable and the
 * remainder is folded into the hatched aggregate, which is also the honest
 * answer: "these minutes were spent on apps ALIVE is not going to name".
 *
 * Forest is deliberately absent. It means "ALIVE is offering you a choice", and
 * a slice of someone's evening is not a choice the app is making.
 */
object AliveSeries {

    const val MAX_SLICES = 4

    @ColorRes
    val donut = listOf(
        R.color.alive_ink,
        R.color.alive_gold_reward_mid,
        R.color.alive_blue_work_mid,
        R.color.alive_soft_forest
    )

    /** Everything past the fourth app, drawn with an ink hatch over it. */
    @ColorRes
    val aggregate = R.color.alive_warm_gray

    /**
     * The same four positions, at the colour a *tile* wears.
     *
     * A ring can be solid ink; a square with an ink letter inside it cannot. These
     * are the light end of each slice's own family, so a legend row's tile and its
     * slice on the ring are unmistakably the same thing at two weights.
     */
    @ColorRes
    val tile = listOf(
        R.color.alive_white,
        R.color.alive_gold_reward_pale,
        R.color.alive_blue_work_pale,
        R.color.alive_soft_forest
    )

    @ColorRes
    fun sliceColor(index: Int): Int = donut[index % donut.size]

    @ColorRes
    fun tileColor(index: Int): Int = tile[index % tile.size]
}
