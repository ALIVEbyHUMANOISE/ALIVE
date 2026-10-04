package com.projectego.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import com.google.android.material.button.MaterialButton
import com.projectego.app.R

/**
 * ALIVE's component vocabulary.
 *
 * Structure comes from whitespace and type, not from containers. Only two
 * things in this app get a background: the one task that is currently in
 * flight (an ink outline) and something that has genuinely landed (soft
 * forest). Everything else sits directly on the paper.
 *
 * Text is dressed by applying a TextAppearance and nothing else. Setting
 * `setTextSize` afterwards silently wins over the style and breaks the scale,
 * and sizing in pixels would stop honouring the user's font-size setting, so
 * neither is done anywhere in this file.
 */
class AliveComponents(private val context: Context) {

    val palette = AlivePalette(context)
    val dimens = AliveDimens(context)

    // ---------- TEXT ----------

    private fun text(@StyleRes appearance: Int, value: CharSequence): TextView =
        TextView(context).apply {
            setTextAppearance(appearance)
            text = value
        }

    /** Small uppercase eyebrow over a section. */
    fun label(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Label, value)

    fun sectionHeader(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_SectionHeader, value)

    fun display(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Display, value)

    fun headline(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Headline, value)

    /** Serif, mid-size: the storytelling voice. */
    fun statement(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Statement, value)

    fun body(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Body, value)

    fun bodySecondary(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_BodySecondary, value)

    fun questRowTitle(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_QuestRowTitle, value)

    /**
     * The handwriting face. Cursive is ALIVE's annotation voice: short asides
     * and emphasis only, never body copy, and never more than one per section.
     */
    fun annotation(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Annotation, value)

    /** Timers and minute counts. */
    fun timer(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Timer, value)

    // ---------- STRUCTURE ----------

    fun column(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Vertical air between blocks. Spacing is content, not decoration. */
    fun vgap(heightPx: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
    }

    /** The drawn rule between sections. Deliberately not perfectly straight. */
    fun separator(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.dp(4)).apply {
            topMargin = dimens.l
            bottomMargin = dimens.l
        }
        setBackgroundResource(R.drawable.alive_hairline_ink)
    }

    /** The thinnest possible divider, for inside a block rather than between them. */
    fun hairline(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.hairline)
        setBackgroundColor(palette.inkHairline)
    }

    // ---------- SURFACES ----------

    /**
     * A block whose dress comes from its tone.
     *
     * NEUTRAL and QUIET have no container at all: a closed door and an ordinary
     * paragraph should not both turn into cards.
     */
    fun block(tone: StatusTone): LinearLayout = column().apply {
        setPadding(dimens.xl, dimens.xl, dimens.xl, dimens.xl)
        when (tone) {
            StatusTone.HELD -> setBackgroundResource(R.drawable.bg_alive_outline)
            StatusTone.LANDED -> setBackgroundResource(R.drawable.bg_alive_surface)
            StatusTone.NEUTRAL, StatusTone.QUIET -> setBackgroundColor(palette.paper)
        }
    }

    /** Text placed straight on the paper, with the fade a quiet state needs. */
    fun quietText(value: CharSequence): TextView = text(R.style.TextAppearance_ALIVE_Body, value).apply {
        setTextColor(palette.inkMuted)
    }

    // ---------- IMAGES ----------

    /**
     * The ALIVE character, inked and unscaled.
     *
     * No tint here on purpose: the poses carry their own ink strokes, and the
     * lock figure adds one forest line for the opening it is walking toward.
     * A single tint would flatten that back into one color.
     */
    fun pose(@DrawableRes res: Int, sizePx: Int, description: String): ImageView =
        ImageView(context).apply {
            setImageResource(res)
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
        }

    /** A small mark that sits inline with a value, e.g. beside the time. */
    fun mark(@DrawableRes res: Int, sizePx: Int, description: String): ImageView =
        pose(res, sizePx, description).apply {
            (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.START
        }

    // ---------- ACTIONS ----------

    /**
     * The one strong invitation on a screen. Forest fill, and nothing else on
     * the screen uses forest as a background.
     */
    fun primary(value: CharSequence): MaterialButton = MaterialButton(context).apply {
        text = value
    }

    /** Ink outline. Used when an action is real but should not shout. */
    fun secondary(value: CharSequence): MaterialButton = MaterialButton(context).apply {
        text = value
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeColor = ColorStateList.valueOf(palette.inkHairline)
        strokeWidth = dimens.hairline
        setTextColor(palette.ink)
    }

    /** No container. For anything the user may safely ignore. */
    fun quiet(value: CharSequence): MaterialButton = MaterialButton(context).apply {
        text = value
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeWidth = 0
        setTextColor(palette.inkSecondary)
    }

    // ---------- EMPTY ----------

    /**
     * An empty state that reads as rest rather than as a missing feature: one
     * relaxed figure, two short lines, no illustration added to fill a hole.
     */
    fun emptyState(heading: CharSequence, line: CharSequence, secondLine: CharSequence): LinearLayout =
        column().apply {
            addView(pose(R.drawable.ic_alive_character, dimens.characterLarge, context.getString(R.string.pose_unaware_note)))
            addView(vgap(dimens.l))
            addView(statement(heading))
            addView(vgap(dimens.s))
            addView(bodySecondary(line))
            addView(vgap(dimens.xs))
            addView(annotation(secondLine))
        }
}
