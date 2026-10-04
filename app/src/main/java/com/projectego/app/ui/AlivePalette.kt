package com.projectego.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.projectego.app.R

/**
 * Every color ALIVE draws with, resolved from res/values/colors.xml.
 *
 * The palette is six colors (Constitution sec.07). A missing tone is added as
 * the ink hue with alpha, never as a new color - so `inkSecondary`, `inkMuted`
 * and `inkHairline` are all still ink.
 *
 * `warmGray` is a surface color only. At #D8D2C7 on paper it sits around 1.2:1
 * contrast and must never be used for text.
 */
class AlivePalette(context: Context) {

    private val appContext = context.applicationContext

    val paper = color(R.color.alive_paper)
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

    private fun color(id: Int): Int = ContextCompat.getColor(appContext, id)
}
