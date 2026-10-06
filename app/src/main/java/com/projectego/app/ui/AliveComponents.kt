package com.projectego.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.projectego.app.R

/**
 * ALIVE's component vocabulary: neo-brutalist structure, pixel soul.
 *
 * Every surface is a flat rectangle with a thick ink edge and a hard ink
 * shadow offset to the bottom-right. Nothing floats, nothing is blurred,
 * nothing is rounded. Buttons are physical: pressing one collapses its shadow.
 *
 * Not everything is a block. Most of the screen is type on paper, and the
 * blocks are reserved for the one thing that is in flight and the one thing
 * that has landed - if everything had a border, nothing would.
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

    fun display(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Display, value)

    fun headline(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Headline, value)

    fun statement(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Statement, value)

    fun sectionHeader(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_SectionHeader, value)

    fun body(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Body, value)

    fun bodySecondary(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_BodySecondary, value)

    fun questRowTitle(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_QuestRowTitle, value)

    /** Small uppercase monospace tag: YOUR TIME, TIME BANK, +20 MIN. */
    fun label(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Label, value)

    /** The big number. Monospace so digits hold their columns. */
    fun timer(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Timer, value)

    fun timerSmall(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_TimerSmall, value)

    fun annotation(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Annotation, value)

    fun quietText(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Body, value).apply {
            setTextColor(palette.inkMuted)
        }

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
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** A row child that takes whatever width is left. */
    fun <T : View> weighted(child: T, weight: Float = 1f): T = child.apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
    }

    fun vgap(heightPx: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
    }

    fun hgap(widthPx: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    /** The thinnest ink rule, for inside a block rather than between them. */
    fun hairline(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.borderThin)
        setBackgroundColor(palette.inkHairline)
    }

    /**
     * A hard rule with a square at its head: the pixel equivalent of a section
     * break, and the only divider used between major blocks.
     */
    fun separator(): View = row().apply {
        addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dimens.l, dimens.l)
                setBackgroundColor(palette.ink)
            }
        )
        addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dimens.borderThin, 1f).apply {
                    marginStart = dimens.s
                }
                setBackgroundColor(palette.ink)
            }
        )
    }

    // ---------- SURFACES ----------

    /**
     * A block whose dress comes from its tone. Padding reserves room for the
     * offset shadow so text never lands on it.
     *
     * No block uses a dark fill: white-on-forest would force every child
     * TextView to be recoloured, and the calm reading order would go with it.
     * Weight here comes from the edge and the shadow.
     */
    fun block(tone: StatusTone): LinearLayout = column().apply {
        setPadding(dimens.xl, dimens.xl, dimens.xl, dimens.xl)
        when (tone) {
            // Something is in flight: the heaviest edge on the screen.
            StatusTone.HELD -> setBackgroundResource(R.drawable.bg_brutal_block)
            // Something has landed.
            StatusTone.LANDED -> setBackgroundResource(R.drawable.bg_brutal_block_soft)
            // A closed door: a hairline and no shadow, so it competes with nothing.
            StatusTone.QUIET -> setBackgroundResource(R.drawable.bg_brutal_quiet)
            // Ordinary content sits straight on the canvas, grid and all.
            StatusTone.NEUTRAL -> setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    /**
     * A bento card: ALIVE's heaviest surface, with its name ruled off from the
     * contents the way a section header rules off a section.
     *
     * The caller gets the card with its title already in place and adds the
     * chart, the legend, or whatever else the card is about.
     */
    fun bentoCard(title: CharSequence): LinearLayout = block(StatusTone.HELD).apply {
        addView(cardTitle(title))
        addView(vgap(dimens.l))
    }

    /** Uppercase, extra bold, thick ink rule underneath: a card's name. */
    fun cardTitle(value: CharSequence): LinearLayout = column().apply {
        addView(sectionHeader(value))
        addView(vgap(dimens.s))
        addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dimens.borderBold
                )
                setBackgroundColor(palette.ink)
            }
        )
    }

    /** A legend chip: the slice's own fill, with the same ink edge the chart draws. */
    fun swatch(@ColorInt color: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dimens.l, dimens.l).apply {
            marginEnd = dimens.m
        }
        background = GradientDrawable().apply {
            setColor(color)
            setStroke(dimens.borderButton, palette.ink)
        }
    }

    /**
     * One cell of the day's facts: the name in small pixel caps, the number in
     * the number face. Four of these in a ruled grid answer "what is this app
     * tracking" before any sentence has to.
     */
    fun statTile(name: CharSequence, value: CharSequence): LinearLayout = column().apply {
        setPadding(dimens.m, dimens.m, dimens.m, dimens.m)
        addView(label(name))
        addView(vgap(dimens.xs))
        addView(timerSmall(value))
    }

    /** A thick ink rule: the horizontal half of a table's cross. */
    fun rule(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.borderButton)
        setBackgroundColor(palette.ink)
    }

    /** The vertical half of the same cross. */
    fun ruleVertical(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dimens.borderButton, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(palette.ink)
    }

    /**
     * The remaining-time bar: whole blocks for what is left, hollow blocks for
     * what is spent. Segmented rather than smooth, so it reads as counted
     * minutes and not as a gauge.
     */
    fun timeBar(remaining: Long, limit: Long, segments: Int = 10): LinearLayout = row().apply {
        if (limit <= 0) return@apply
        val filled = ((remaining.toFloat() / limit.toFloat()) * segments)
            .roundToIntClamped(segments)
        for (index in 0 until segments) {
            addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dimens.stepWidth, dimens.stepHeight).apply {
                        marginEnd = if (index == segments - 1) 0 else dimens.stepGap
                    }
                    setImageResource(
                        if (index < filled) R.drawable.alive_step_full else R.drawable.alive_step_empty
                    )
                    contentDescription = null
                }
            )
        }
    }

    private fun Float.roundToIntClamped(max: Int): Int =
        (if (this <= 0f) 0 else kotlin.math.ceil(this)).toInt().coerceIn(0, max)

    // ---------- IMAGES ----------

    /**
     * The ALIVE character. The poses are vector pixel grids, so they stay crisp
     * at any size without any bitmap filtering.
     */
    fun pose(@DrawableRes res: Int, sizePx: Int, description: String): ImageView =
        ImageView(context).apply {
            setImageResource(res)
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
        }

    /**
     * The masthead character: the same pose sprite, with a slow two-frame bob.
     *
     * Discrete rather than smooth - a figure made of pixels that glides stops
     * being made of pixels. One grid cell, held briefly, most of the time spent
     * still: it reads as breathing, not as a bounce. Nothing here touches the
     * engine or the pose mapping; the sprite is exactly the one the stage chose.
     */
    fun animatedPose(@DrawableRes res: Int, sizePx: Int, description: String): ImageView {
        val image = pose(res, sizePx, description)
        if (!animationsAllowed) return image

        val step = sizePx / SPRITE_GRID
        val handler = Handler(Looper.getMainLooper())
        var raised = false
        val bob = object : Runnable {
            override fun run() {
                raised = !raised
                image.translationY = if (raised) -step.toFloat() else 0f
                handler.postDelayed(this, if (raised) BOB_UP_MILLIS else BOB_DOWN_MILLIS)
            }
        }
        image.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                handler.postDelayed(bob, BOB_DOWN_MILLIS)
            }

            // Detached is where a re-rendered masthead ends up, and a loop that
            // outlives its view is a leak with a heartbeat.
            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(bob)
                image.translationY = 0f
            }
        })
        return image
    }

    /**
     * A block cursor beside the wordmark.
     *
     * INVISIBLE, not GONE: a wordmark that shifts half a character every second
     * is harder to read than one with a blinking tail.
     */
    fun caret(heightPx: Int): View {
        val cursor = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(heightPx / 2, heightPx).apply {
                marginStart = dimens.s
            }
            setBackgroundColor(palette.ink)
        }
        if (!animationsAllowed) return cursor

        val handler = Handler(Looper.getMainLooper())
        val blink = object : Runnable {
            override fun run() {
                val on = cursor.visibility != View.VISIBLE
                cursor.visibility = if (on) View.VISIBLE else View.INVISIBLE
                handler.postDelayed(this, if (on) CARET_ON_MILLIS else CARET_OFF_MILLIS)
            }
        }
        cursor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                handler.postDelayed(blink, CARET_ON_MILLIS)
            }

            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(blink)
            }
        })
        return cursor
    }

    /** The device decides, not us: "remove animated transitions" stops all of this. */
    private val animationsAllowed: Boolean
        get() = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f

    fun mark(@DrawableRes res: Int, sizePx: Int, description: String): ImageView =
        pose(res, sizePx, description)

    // ---------- ACTIONS ----------

    /**
     * Buttons are plain AppCompat buttons over hard-shadow drawables.
     * MaterialButton is deliberately not used: its shape drawable would fight
     * the offset shadow and soften the edge.
     */
    private fun button(
        value: CharSequence,
        @DrawableRes background: Int,
        @ColorInt textColor: Int
    ): AppCompatButton = AppCompatButton(context).apply {
        text = value
        setTextAppearance(R.style.TextAppearance_ALIVE_Button)
        setTextColor(textColor)
        setBackgroundResource(background)
        stateListAnimator = null
        gravity = Gravity.CENTER
        minHeight = dimens.touchMin
        setPadding(dimens.xl, dimens.m, dimens.xl, dimens.m - dimens.shadow)
        includeFontPadding = false
    }

    /** Forest fill. One per screen at most. */
    fun primary(value: CharSequence): AppCompatButton =
        button(value, R.drawable.bg_brutal_button_primary, palette.white)

    /** Paper fill with an ink edge. */
    fun secondary(value: CharSequence): AppCompatButton =
        button(value, R.drawable.bg_brutal_button_secondary, palette.ink)

    /** Flat until touched. */
    fun quiet(value: CharSequence): AppCompatButton =
        button(value, R.drawable.bg_brutal_button_quiet, palette.inkSecondary)

    /**
     * The way out of a panel: an ink edge, a pixel arrow and the word.
     *
     * Text alone was easy to miss on a screen made of text, and a panel with no
     * visible exit reads as a trap rather than a page.
     */
    fun backButton(label: CharSequence): AppCompatButton = secondary(label).apply {
        val arrow = ContextCompat.getDrawable(context, R.drawable.ic_arrow_back_pixel)?.apply {
            setBounds(0, 0, dimens.l, dimens.l)
        }
        setCompoundDrawablesRelative(arrow, null, null, null)
        compoundDrawablePadding = dimens.m
    }

    // ---------- FORM WIDGETS ----------

    /**
     * A radio row. These exist because the form widgets were previously built
     * bare and only had their colour set, which left them on the platform
     * default face - the one place the screen then read as someone else's app.
     */
    fun option(value: CharSequence): RadioButton = RadioButton(context).apply {
        setTextAppearance(R.style.TextAppearance_ALIVE_Option)
        text = value
        buttonTintList = ColorStateList.valueOf(palette.ink)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dimens.touchMin
        setPadding(dimens.xs, 0, 0, 0)
    }

    /** The goal field: an ink frame, not a bare line of hint text. */
    fun input(hint: CharSequence): EditText = EditText(context).apply {
        setTextAppearance(R.style.TextAppearance_ALIVE_Input)
        this.hint = hint
        setTextColor(palette.ink)
        setHintTextColor(palette.inkDisabled)
        setBackgroundResource(R.drawable.bg_brutal_input)
        minHeight = dimens.touchMin
        setPadding(dimens.l, dimens.m, dimens.l, dimens.m)
    }

    /** A disabled action loses its edge as well as its colour. */
    fun disabled(button: AppCompatButton): AppCompatButton = button.apply {
        isEnabled = false
        setTextColor(palette.inkDisabled)
    }

    // ---------- ACCENTS ----------

    private val accentShapes = intArrayOf(
        R.drawable.alive_accent_plus,
        R.drawable.alive_accent_star,
        R.drawable.alive_accent_invader,
        R.drawable.alive_accent_diamond,
        R.drawable.alive_accent_spark,
        R.drawable.alive_accent_heart
    )

    /**
     * A short run of scattered pixel shapes - an invader, a star, a diamond.
     *
     * Deterministic per seed on purpose: a section always shows the same
     * shapes, so the screen does not reshuffle every time it re-renders. Two or
     * three per section at most, because pixel debris on every component stops
     * being an accent and becomes noise.
     */
    fun accents(seed: Int, count: Int = 3, sizePx: Int = dimens.l): View = row().apply {
        for (i in 0 until count) {
            val shape = accentShapes[(seed * 7 + i * 5) % accentShapes.size]
            addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        marginEnd = dimens.m + (seed + i) % 3 * dimens.xs
                    }
                    setImageResource(shape)
                    contentDescription = null
                }
            )
        }
    }

    /**
     * An empty state that reads as rest: the figure sitting, two lines, no
     * illustration invented to fill a gap.
     */
    fun emptyState(heading: CharSequence, line: CharSequence, secondLine: CharSequence): LinearLayout =
        column().apply {
            addView(pose(R.drawable.ic_alive_character, dimens.characterLarge, context.getString(R.string.pose_unaware_note)))
            addView(vgap(dimens.l))
            addView(headline(heading))
            addView(vgap(dimens.s))
            addView(bodySecondary(line))
            addView(vgap(dimens.xs))
            addView(annotation(secondLine))
        }

    private companion object {
        /** The sprites are drawn on a 16x16 grid, so one cell is the smallest honest step. */
        const val SPRITE_GRID = 16f

        const val BOB_UP_MILLIS = 240L
        const val BOB_DOWN_MILLIS = 1_500L
        const val CARET_ON_MILLIS = 620L
        const val CARET_OFF_MILLIS = 480L
    }
}
