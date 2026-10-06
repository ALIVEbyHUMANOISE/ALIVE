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

    /**
     * Rhythm, one value per relationship. See res/values/dimens.xml for why
     * `cardGap` is as wide as it is and why nothing inside a card reaches it.
     */
    val cardGap = px(R.dimen.card_gap)
    val groupGap = px(R.dimen.group_gap)
    val lineGap = px(R.dimen.line_gap)
    val pairGap = px(R.dimen.pair_gap)
    val cardInset = px(R.dimen.card_inset)
    val ledgerInset = px(R.dimen.ledger_inset)

    val touchMin = px(R.dimen.touch_min)
    val character = px(R.dimen.character_size)
    val characterLarge = px(R.dimen.character_size_large)

    /** Thick ink edge on a MAJOR block. */
    val borderBold = px(R.dimen.border_bold)

    /** Edge of a button face, and of a MINOR card - the same object family. */
    val borderButton = px(R.dimen.border_button)

    /** Hairline edge on a quiet surface. */
    val borderThin = px(R.dimen.border_hairline)

    /** How far a MAJOR shadow sits from its face. */
    val shadow = px(R.dimen.shadow_offset)

    /** How far a MINOR shadow sits: present, so the face still lifts, quiet. */
    val shadowMinor = px(R.dimen.shadow_minor)

    val stepWidth = px(R.dimen.step_width)
    val stepHeight = px(R.dimen.step_height)
    val stepGap = px(R.dimen.step_gap)

    /** For one-off measurements that do not deserve their own dimen resource. */
    fun dp(value: Int): Int = (value * density).toInt()

    private fun px(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)
}
