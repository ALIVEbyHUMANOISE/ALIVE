package com.projectego.app.ui

import android.content.Context
import androidx.annotation.DimenRes
import com.projectego.app.R

/**
 * ALIVE's spacing scale, in pixels, converted from dp.
 *
 * `setPadding()` and `LayoutParams` take pixels, so a bare integer makes the
 * layout density-dependent: the same number is generous on xxhdpi and cramped on
 * mdpi. Every gap in ALIVE comes from here instead.
 */
class AliveDimens(context: Context) {

    private val resources = context.resources
    private val density = resources.displayMetrics.density

    /** 4dp */
    val xs = px(R.dimen.space_xs)

    /** 8dp */
    val s = px(R.dimen.space_s)

    /** 12dp */
    val m = px(R.dimen.space_m)

    /** 16dp */
    val l = px(R.dimen.space_l)

    /** 24dp */
    val xl = px(R.dimen.space_xl)

    /** 32dp */
    val xxl = px(R.dimen.space_xxl)

    /** 48dp */
    val xxxl = px(R.dimen.space_xxxl)

    val pageHorizontal = px(R.dimen.screen_h_padding)
    val pageTop = px(R.dimen.screen_top_padding)
    val pageBottom = px(R.dimen.screen_bottom_padding)

    val hairline = px(R.dimen.hairline)
    val sectionGap = px(R.dimen.section_gap)
    val rowGap = px(R.dimen.row_gap)
    val touchMin = px(R.dimen.touch_min)
    val character = px(R.dimen.character_size)
    val characterLarge = px(R.dimen.character_size_large)

    /** For one-off measurements that do not deserve their own dimen resource. */
    fun dp(value: Int): Int = (value * density).toInt()

    private fun px(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)
}
