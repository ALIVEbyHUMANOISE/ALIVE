package com.projectego.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.projectego.app.R

/**
 * Every color ALIVE draws with, resolved from res/values/colors.xml.
 *
 * Three families, and the difference between them is what a colour is allowed to
 * mean (Constitution sec.07):
 *
 *  - The constitution's six: paper, ink, forest, soft forest, warm gray, white.
 *    Structure and identity.
 *  - Three role hues - gold is reward, red is time running out, blue is the work
 *    itself - plus `amber`, which is the colour of an offer: the one thing on a
 *    screen you are being invited to press.
 *  - Nine category hues in `QuestHues`. Those are not status and not text: a
 *    category hue lives on a card's tab and the tile its mark sits in, nowhere
 *    else, which is what lets nine of them exist without a legend.
 *
 * A missing tone is added as the ink hue with alpha, never as a new color - so
 * `inkSecondary`, `inkMuted` and `inkHairline` are all still ink.
 *
 * `warmGray` is a surface color only. At #D8D2C7 on paper it sits around 1.2:1
 * contrast and must never be used for text.
 */
class AlivePalette(context: Context) {

    private val appContext = context.applicationContext

    val paper = color(R.color.alive_paper)
    val canvas = color(R.color.alive_canvas)
    val ink = color(R.color.alive_ink)
    val forest = color(R.color.alive_forest)
    val softForest = color(R.color.alive_soft_forest)
    val warmGray = color(R.color.alive_warm_gray)
    val white = color(R.color.alive_white)

    val inkSecondary = color(R.color.alive_ink_secondary)
    val inkMuted = color(R.color.alive_ink_muted)
    val inkDisabled = color(R.color.alive_ink_disabled)
    val inkHairline = color(R.color.alive_ink_hairline)
    val forestSoft = color(R.color.alive_forest_soft)

    /**
     * Three game roles, each with one job. Read the role, not the colour: if a
     * call site wants gold for something that is not a reward, it is using the
     * wrong token.
     *
     * `*_dark` carries text or draws an edge and is measured to clear 4.5:1 on
     * every ground (see `AlivePaletteTest`). `*_mid` is a fill only and never
     * carries text - white on gold mid is 2.2:1. `*_pale` is a surface to read
     * ink on.
     */
    val rewardDark = color(R.color.alive_gold_reward_dark)
    val rewardMid = color(R.color.alive_gold_reward_mid)
    val rewardPale = color(R.color.alive_gold_reward_pale)

    val shortDark = color(R.color.alive_red_short_dark)
    val shortMid = color(R.color.alive_red_short_mid)
    val shortPale = color(R.color.alive_red_short_pale)

    val workDark = color(R.color.alive_blue_work_dark)
    val workMid = color(R.color.alive_blue_work_mid)
    val workPale = color(R.color.alive_blue_work_pale)

    /** "Do this." Brighter and flatter than reward gold, and never on the same object. */
    val amber = color(R.color.alive_amber_action)

    private fun color(id: Int): Int = ContextCompat.getColor(appContext, id)
}
