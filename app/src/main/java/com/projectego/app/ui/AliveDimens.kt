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

    /**
     * The corner of each object family, in pixels. A pill is 999dp wide on
     * purpose: half of it is always more than its own height, so it stays a
     * capsule whatever text it ends up holding.
     */
    val radiusCard = px(R.dimen.radius_card)
    val radiusField = px(R.dimen.radius_field)
    val radiusTile = px(R.dimen.radius_tile)
    val radiusStep = px(R.dimen.radius_step)
    val radiusPill = px(R.dimen.radius_pill)

    /** One block of a meter. Square, because a stretched pixel is not a pixel. */
    val stepSize = px(R.dimen.step_size)
    val stepGap = px(R.dimen.step_gap)

    /** A full-size sprite, snapped to whole device pixels. */
    val character = sprite(R.dimen.character_size)
    val characterLarge = sprite(R.dimen.character_size_large)

    /** A category mark beside a list row, and the smaller one in the history. */
    val markSize = sprite(R.dimen.mark_size)
    val markSmallSize = sprite(R.dimen.mark_small_size)

    /** The bottom navigation icon, which used to be 24dp on a 16 grid. */
    val navIcon = sprite(R.dimen.nav_icon_size)

    /** The hourglass beside the wordmark, snapped so its 16 grid stays crisp. */
    val hourglassSize = sprite(R.dimen.hourglass_size)

    /**
     * A sprite size in pixels, rounded so one cell of its 16x16 grid is a whole
     * number of device pixels.
     *
     * Every sprite here is drawn on 16 cells. At a density of 1.75 even a "clean"
     * 64dp is 112 px, which is 7 - fine - but 48dp is 84, or 5.25 cells, and that
     * is how crisp pixel art ends up soft at the edges.
     */
    fun sprite(@DimenRes id: Int): Int = snap(px(id))

    fun spriteDp(value: Int): Int = snap(dp(value))

    private fun snap(pixels: Int): Int = (pixels + SPRITE_CELL / 2) / SPRITE_CELL * SPRITE_CELL

    /** For one-off measurements that do not deserve their own dimen resource. */
    fun dp(value: Int): Int = (value * density).toInt()

    private fun px(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)

    private companion object {
        /** Every sprite in this app is drawn on a 16x16 grid. */
        const val SPRITE_CELL = 16
    }
}
