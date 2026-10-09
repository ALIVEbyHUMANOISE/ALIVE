package com.projectego.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R

/**
 * How much a surface weighs - a separate question from what happened.
 *
 * `StatusTone` answers "in flight, landed, or closed". This answers "how loudly
 * should the eye treat this box", and the two must not be collapsed: an app
 * where every card is the heaviest thing on screen is an app with no hierarchy
 * at all. A screen gets exactly one MAJOR.
 */
enum class SurfaceWeight { MAJOR, MINOR, PLAIN }

/**
 * ALIVE's component vocabulary: neo-brutalist structure, pixel soul.
 *
 * A surface is a flat rounded rectangle with an ink edge and a hard ink shadow
 * offset to the bottom-right. Nothing floats, nothing is blurred, nothing is
 * gradient. Buttons are physical: pressing one collapses its shadow.
 *
 * Weight is rationed. Most of a screen is type on canvas, one card is MAJOR, the
 * rest are MINOR, and prose that has nothing in flight gets PLAIN - if everything
 * had a border, nothing would.
 *
 * Rhythm is equally rationed, and the numbers live in res/values/dimens.xml:
 * cards are separated by air (`cardGap`), contents inside a card by a rule
 * (`groupGap` / `lineGap`), rows of one list by a hairline. Nothing inside a
 * card is `cardGap` or more apart; if two things want that much air they are two
 * cards.
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

    /** Row titles and in-flight statements: one step, not two that looked alike. */
    fun lead(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Lead, value)

    fun sectionHeader(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_SectionHeader, value)

    /** Main prose. Ink, because it is the thing being read. */
    fun body(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Body, value)

    /** Support lines and disclosures: same size as body, one step quieter. */
    fun note(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Note, value)

    /** The small grey count beside a tile: "24 quests", "2 routes". */
    fun meta(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Meta, value)

    /**
     * A tag: numbers with a unit, a weekday, `+20 MIN`. Pixel caps at 10sp.
     *
     * Ten glyphs is the limit, and it is not a stylistic preference - below that
     * size this face stops being readable text, which is what happened when it
     * carried 32-to-96-character sentences and the bottom navigation.
     */
    fun tag(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Label, value)

    /** The big number. One per screen at most. */
    fun timer(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Timer, value)

    /** A ledger number: the same terminal face, one step down. */
    fun number(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Number, value)

    fun annotation(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Annotation, value)

    /**
     * What actually went wrong, said once. Heavier and darker than the note it
     * sits under, and never a second paragraph of comfort.
     */
    fun error(value: CharSequence): TextView =
        text(R.style.TextAppearance_ALIVE_Error, value)

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
        // A row that hands its leftover width to a sentence must not align on the
        // text baseline: while it still does not know that width, it asks the text
        // how wide it would be on a single line, and the width left over afterwards
        // is what the sentence then gets drawn into. That is how a long quest step
        // arrived cut off - and a pixel tile has no baseline to share anyway.
        isBaselineAligned = false
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

    /**
     * A column that keeps exactly [gapPx] of air between its children.
     *
     * Gaps remembered by hand are gaps that get forgotten, and a card body that
     * is once in a while flush looks like a mistake rather than a choice. The
     * spacer goes before every child after the first, so a stack never ends with
     * trailing space.
     */
    class Stack(context: Context, private val gapPx: Int) : LinearLayout(context) {

        init {
            orientation = VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        override fun addView(child: View) {
            addGap()
            super.addView(child)
        }

        override fun addView(child: View, params: ViewGroup.LayoutParams) {
            addGap()
            super.addView(child, params)
        }

        private fun addGap() {
            if (childCount > 0 && gapPx > 0) {
                super.addView(
                    View(context).apply {
                        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gapPx)
                    }
                )
            }
        }
    }

    /** A column with a fixed gap. Use this for anything that holds more than one thing. */
    fun stack(gapPx: Int): Stack = Stack(context, gapPx)

    /**
     * A row whose cells end up the same height.
     *
     * `MATCH_PARENT` is how a grid usually does this, and inside a ScrollView it
     * means "the height of the screen" - which is exactly how two stat tiles once
     * ate a whole page and pushed its buttons off the bottom. So this row measures
     * its children honestly first, then pins every cell to the tallest height it
     * just saw. Pinning matters: a mere `minimumHeight` makes the box taller, but
     * a card inside it is then measured against an AT_MOST hint and never learns
     * it is allowed to spend that extra air - its name and foot line sit above
     * the bottom of the ink instead of on it.
     */
    class EqualRow(context: Context) : LinearLayout(context) {

        init {
            orientation = HORIZONTAL
            isBaselineAligned = false
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val cells = (0 until childCount).filter { index ->
                val child = getChildAt(index)
                // Only a cell that sizes itself to its content can be compared with
                // its neighbours. A spacer is MATCH_PARENT by design - and inside a
                // ScrollView that measures to the viewport, which is how two stat
                // tiles once ate a whole page.
                child.visibility != View.GONE &&
                    (child.layoutParams as? LayoutParams)?.height == LayoutParams.WRAP_CONTENT
            }
            val tallest = cells.maxOfOrNull { getChildAt(it).measuredHeight } ?: 0
            if (tallest == 0) return

            // The exact height is written into the layout params, so the next pass
            // measures every cell with an EXACTLY spec - and a cell that hands its
            // slack to a weighted spacer can then do so for real.
            var shortFound = false
            for (index in cells) {
                val lp = getChildAt(index).layoutParams as LayoutParams
                if (lp.height != tallest) {
                    lp.height = tallest
                    shortFound = true
                }
            }
            // One correction pass, then the numbers agree.
            if (shortFound) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** A row of cells that are the same height as each other. */
    fun equalRow(): EqualRow = EqualRow(context)

    /**
     * A board of horizontal rows whose cells all end up one shared height, so a
     * card in the last row is the same size as one in the first - not merely
     * even with its own neighbour.
     *
     * The cells are pinned directly, not their rows: a row refuses to take its
     * height from a MATCH_PARENT child while the board is still measuring
     * honestly, so equalising the rows instead would collapse the honest pass
     * and hide the tallest card's true size.
     */
    class EqualGrid(context: Context) : LinearLayout(context) {

        init {
            orientation = VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val cells = mutableListOf<View>()
            for (rowIndex in 0 until childCount) {
                val row = getChildAt(rowIndex) as? LinearLayout ?: continue
                if (row.orientation != HORIZONTAL) continue
                for (cellIndex in 0 until row.childCount) {
                    val cell = row.getChildAt(cellIndex)
                    // The same rule as EqualRow: only a cell that sizes itself to
                    // its content is comparable; a gap or filler has a fixed or
                    // MATCH_PARENT height on purpose.
                    if (cell.visibility == View.GONE) continue
                    val lp = cell.layoutParams as? LayoutParams ?: continue
                    if (lp.height == LayoutParams.WRAP_CONTENT) cells.add(cell)
                }
            }
            val tallest = cells.maxOfOrNull { it.measuredHeight } ?: 0
            if (tallest == 0) return

            // The exact height is written into the layout params, so the next pass
            // measures every cell with an EXACTLY spec - and a card that hands its
            // slack to a weighted spacer can then do so for real.
            var shortFound = false
            for (cell in cells) {
                val lp = cell.layoutParams as LayoutParams
                if (lp.height != tallest) {
                    lp.height = tallest
                    shortFound = true
                }
            }
            // One correction pass, then the numbers agree.
            if (shortFound) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** A column of rows whose cells can be one shared size. */
    fun equalGrid(): EqualGrid = EqualGrid(context)

    /** The thinnest ink rule, for between the rows of one list. */
    fun rowRule(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.borderThin)
        setBackgroundColor(palette.inkHairline)
    }

    // ---------- SURFACES ----------

    /**
     * What a surface looks like, decided by two independent questions: how heavy
     * (`weight`) and what happened (`tone`).
     *
     * Composed in code rather than in XML because the matrix would otherwise be
     * twelve drawables, each a copy of the next with two numbers changed - and a
     * drawable cannot express the rule that a closed door never wins the weight
     * contest, which is the whole reason QUIET overrides the shadow here.
     *
     * No face is dark: white-on-forest would force every child TextView to be
     * recoloured, and the calm reading order would go with it. Weight comes from
     * the edge and the shadow, never from an inverted fill.
     */
    private class Surface(val edge: Int, val shadow: Int, val radius: Int, @ColorInt val face: Int?)

    private fun surfaceFor(weight: SurfaceWeight, tone: StatusTone): Surface = when {
        // Ordinary content sits straight on the canvas - no edge, no shadow, and
        // no hidden padding either.
        tone == StatusTone.NEUTRAL || weight == SurfaceWeight.PLAIN -> Surface(0, 0, 0, null)

        // A closed door: a hairline and no shadow, whatever weight asked for.
        tone == StatusTone.QUIET -> Surface(dimens.borderThin, 0, dimens.radiusCard, Color.TRANSPARENT)

        else -> Surface(
            edge = if (weight == SurfaceWeight.MAJOR) dimens.borderBold else dimens.borderButton,
            shadow = if (weight == SurfaceWeight.MAJOR) dimens.shadow else dimens.shadowMinor,
            radius = dimens.radiusCard,
            face = if (tone == StatusTone.LANDED) palette.softForest else palette.white
        )
    }

    private fun drawableFor(surface: Surface): Drawable? {
        val face = surface.face ?: return null
        val radius = surface.radius.toFloat()
        // A QUIET surface asks for a hairline, a transparent face and no shadow.
        // Without the shadow it must not get the ink layer at all, because that
        // layer is what a hard shadow is made of - and an ink rectangle under a
        // transparent face is a black card, not an empty one.
        if (surface.shadow == 0) return outline(surface.edge, radius, face)
        return framed(face, surface.edge, surface.shadow, radius)
    }

    /** The face itself: a fill, an ink edge, one corner. */
    private fun outline(edge: Int, radius: Float, @ColorInt face: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(face)
            setStroke(edge, palette.ink)
            cornerRadius = radius
        }

    /**
     * A face with an ink edge and a hard ink shadow to its bottom-right - the one
     * picture every raised object in this app wears, shared by cards, tiles and
     * chips so a 10sp tag and a whole card are unmistakably the same kind of thing.
     *
     * Both layers are rounded, not just the face: a square shadow behind a round
     * card shows up as two black corners, which is the first thing the eye catches.
     */
    private fun framed(@ColorInt face: Int, edge: Int, shadow: Int, radius: Float): Drawable {
        val ink = GradientDrawable().apply {
            setColor(palette.ink)
            cornerRadius = radius
        }
        // The face covers the ink layer everywhere but bottom-right, which is
        // what leaves a hard shadow rather than a blurred one.
        return LayerDrawable(arrayOf(ink, outline(edge, radius, face))).apply {
            setLayerInset(1, 0, 0, shadow, shadow)
        }
    }

    /**
     * A block whose dress comes from its weight and its tone.
     *
     * Padding is measured from the face, not from the view's bounds: the right
     * and bottom carry the shadow's width as well, so the interior is optically
     * square and no glyph ever sits on a shadow.
     */
    fun block(
        weight: SurfaceWeight = SurfaceWeight.MINOR,
        tone: StatusTone = StatusTone.HELD
    ): LinearLayout {
        val surface = surfaceFor(weight, tone)
        return column().apply {
            background = drawableFor(surface)
            val inset = dimens.cardInset
            setPadding(
                surface.edge + inset,
                surface.edge + inset,
                surface.edge + surface.shadow + inset,
                surface.edge + surface.shadow + inset
            )
        }
    }

    /**
     * A framed notice: a MAJOR edge with a hairline ruled inside it.
     *
     * Only for the one moment ALIVE raises without being invited. Every other
     * card on every other screen is a single edge, which is what makes this one
     * read as "raised" without a colour, a sound, or a movement.
     */
    fun notice(): LinearLayout = block(SurfaceWeight.MAJOR).apply {
        setBackgroundResource(R.drawable.bg_brutal_notice)
    }

    /**
     * A named card: its title ruled off from its contents.
     *
     * MINOR by default, on purpose. A screen earns one MAJOR and everything else
     * that wanted a border gets a thinner edge and a smaller shadow - still
     * unmistakably a box, no longer competing for the top of the pile.
     */
    fun bentoCard(
        title: CharSequence,
        weight: SurfaceWeight = SurfaceWeight.MINOR,
        tone: StatusTone = StatusTone.HELD
    ): LinearLayout = block(weight, tone).apply {
        addView(cardTitle(title))
    }

    /** Uppercase, extra bold, thick ink rule underneath: a card's name. */
    fun cardTitle(value: CharSequence): LinearLayout = column().apply {
        addView(sectionHeader(value))
        addView(vgap(dimens.lineGap))
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

    /**
     * A legend chip: the slice's own fill, with the same ink edge the chart draws.
     *
     * [sizePx] is a parameter because a legend sometimes has to copy a *shape*,
     * not just a colour - a day with nothing spent is drawn as a tick, and a
     * legend that shows it as a full square is lying about the chart.
     */
    fun swatch(@ColorInt color: Int, sizePx: Int = dimens.l): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            marginEnd = dimens.m
        }
        background = GradientDrawable().apply {
            setColor(color)
            setStroke(dimens.borderButton, palette.ink)
            cornerRadius = dimens.radiusStep.toFloat()
        }
    }

    /**
     * A category's tile: the hue that kind of doing wears, an ink edge, a hard
     * shadow, and the pixel mark sitting inside it.
     *
     * This is the object the whole board is scanned by. A list of nine quests is
     * nine sentences that all start the same way, and the tile is what makes
     * "the walking one" a thing you can point at instead of a thing you read for.
     */
    fun iconTile(@ColorInt hue: Int, @DrawableRes glyph: Int, sizePx: Int = dimens.touchMin): ImageView =
        ImageView(context).apply {
            setImageResource(glyph)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = framed(hue, dimens.borderButton, dimens.shadowMinor, dimens.radiusTile.toFloat())
            val inset = (sizePx - dimens.shadowMinor) / 5
            setPadding(inset, inset, inset + dimens.shadowMinor, inset + dimens.shadowMinor)
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }

    /** Repaint a tile's face. The Focus picker swaps hue along with the mark. */
    fun tintTile(tile: ImageView, @ColorInt hue: Int) {
        tile.background = framed(hue, dimens.borderButton, dimens.shadowMinor, dimens.radiusTile.toFloat())
    }

    /**
     * A tile whose content is a short piece of text instead of a mark.
     *
     * This is the reference's logo square, drawn with what ALIVE actually knows:
     * the initial the device gives us, or the rank a row holds in a list. ALIVE
     * does not ship app icons and will not pretend to have them.
     */
    fun textTile(value: CharSequence, @ColorInt hue: Int, sizePx: Int = dimens.markSmallSize): TextView =
        TextView(context).apply {
            text = value
            setTextAppearance(R.style.TextAppearance_ALIVE_Number)
            setTextColor(palette.ink)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = framed(hue, dimens.borderButton, dimens.shadowMinor, dimens.radiusTile.toFloat())
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }

    /**
     * One proportional bar: this row's share of the whole it belongs to.
     *
     * A ranking is only readable if the gap between first and second is visible,
     * and minutes alone make someone do the arithmetic.
     */
    fun meter(share: Float, @ColorInt fill: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.s)
            background = outline(dimens.borderButton, dimens.radiusStep.toFloat(), palette.white)
            val safe = share.coerceIn(0f, 1f)
            addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, safe)
                    setBackgroundColor(fill)
                }
            )
            if (safe < 1f) {
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - safe)
                    }
                )
            }
        }

    /**
     * A block that wears a colour of its own rather than paper.
     *
     * For the one card on a screen that is raised without being asked - the
     * coverage banner - where a white face would make it just another card.
     */
    fun tintedBlock(@ColorInt face: Int, weight: SurfaceWeight = SurfaceWeight.MINOR): LinearLayout {
        val edge = if (weight == SurfaceWeight.MAJOR) dimens.borderBold else dimens.borderButton
        val shadow = if (weight == SurfaceWeight.MAJOR) dimens.shadow else dimens.shadowMinor
        val inset = dimens.cardInset
        return column().apply {
            background = framed(face, edge, shadow, dimens.radiusCard.toFloat())
            setPadding(edge + inset, edge + inset, edge + shadow + inset, edge + shadow + inset)
        }
    }

    /**
     * The flat pill on a card's top edge.
     *
     * It has no ink edge of its own, which is the one place in ALIVE that is
     * allowed: the edge belongs to the card, and a border on the tab too would
     * draw a second box around something that is meant to look tucked behind.
     */
    fun categoryTab(@ColorInt hue: Int, widthPx: Int = dimens.xxxl, heightPx: Int = dimens.l): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
            background = GradientDrawable().apply {
                setColor(hue)
                cornerRadius = dimens.radiusTile.toFloat()
            }
        }

    /**
     * One cell of the day's facts: the name in small caps, the number in the
     * number face - on its own little face.
     *
     * Four of these answer "what is this app tracking" before any sentence has
     * to. They are cards rather than ruled table cells because the grid is what
     * the rest of the screen is made of, and a ruled box in the middle of a grid
     * of boxes reads as a different app.
     *
     * [face] carries meaning, not mood: gold for something earned, blue for the
     * budget, forest for the day's own figure. A tile that is only colourful is
     * a tile nobody learns anything from.
     */
    /**
     * A ledger tile: one word, one number, one face.
     *
     * [unit] rides the baseline of the number instead of a note under the whole
     * grid, so each figure says what it is by itself - a 160 with no unit is a
     * number, 160 min is a fact.
     */
    fun statTile(
        name: CharSequence,
        value: CharSequence,
        @ColorInt face: Int = palette.white,
        unit: CharSequence? = null
    ): LinearLayout = column().apply {
        background = framed(face, dimens.borderButton, dimens.shadowMinor, dimens.radiusTile.toFloat())
        val inset = dimens.ledgerInset
        val edge = dimens.borderButton
        setPadding(
            inset + edge,
            inset + edge,
            inset + edge + dimens.shadowMinor,
            inset + edge + dimens.shadowMinor
        )
        addView(sectionHeader(name))
        addView(vgap(dimens.pairGap))
        if (unit == null) {
            addView(number(value))
        } else {
            addView(
                row().apply {
                    gravity = Gravity.BOTTOM
                    addView(number(value))
                    addView(hgap(dimens.xs))
                    addView(
                        meta(unit).apply {
                            setPadding(0, 0, 0, dimens.xs)
                        }
                    )
                }
            )
        }
    }

    /** A thick ink rule between the parts of one card. */
    fun rule(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimens.borderButton)
        setBackgroundColor(palette.ink)
    }

    /**
     * The remaining-time bar: whole blocks for what is left, hollow blocks for
     * what is spent. Segmented rather than smooth, so it reads as counted
     * minutes and not as a gauge.
     *
     * [fill] is the interior of a live block. It defaults to forest because a
     * full allowance is ALIVE's own thing; the caller passes red when the time
     * left is thin, which is the same fact the number already states - the
     * colour never carries it alone.
     */
    fun timeBar(
        remaining: Long,
        limit: Long,
        segments: Int = 10,
        @ColorInt fill: Int = palette.forest
    ): LinearLayout = row().apply {
        if (limit <= 0) return@apply
        val filled = ((remaining.toFloat() / limit.toFloat()) * segments)
            .roundToIntClamped(segments)
        for (index in 0 until segments) {
            addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dimens.stepSize, dimens.stepSize).apply {
                        marginEnd = if (index == segments - 1) 0 else dimens.stepGap
                    }
                    background = step(if (index < filled) fill else palette.white)
                }
            )
        }
    }

    /** One block of a meter: a face with an ink edge, drawn so the edge stays ink. */
    private fun step(@ColorInt face: Int): GradientDrawable = GradientDrawable().apply {
        setColor(face)
        setStroke(dimens.borderButton, palette.ink)
        cornerRadius = dimens.radiusStep.toFloat()
    }

    /**
     * A run of days, as squares: one pip per day up to seven, and after that the
     * row stays full while the sentence beside it keeps counting.
     *
     * The pips are the shape of the number, never the number itself - the text
     * next to them stays authoritative, which is what "colour is never the only
     * signal" means in practice. Gold is reserved for this and for time that was
     * actually earned.
     */
    fun streakPips(filled: Int, total: Int = 7, sizePx: Int = dimens.l): LinearLayout = row().apply {
        for (index in 0 until total) {
            addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        marginEnd = if (index == total - 1) 0 else dimens.pairGap
                    }
                    background = step(if (index < filled) palette.rewardMid else palette.white)
                }
            )
        }
    }

    /**
     * Time that was actually paid out, on paper the colour of a reward.
     *
     * A chip rather than a sentence because it is a fact with a number in it, not
     * a claim about the day: it arrives, it is small, and it never rewraps. Gold is
     * only ever used here, on the pips above, and on the sprites that mark a
     * finished quest - which is what lets it mean "earned" without a legend.
     */
    fun rewardChip(value: CharSequence): TextView = tag(value).apply {
        setTextColor(palette.rewardDark)
        background = framed(palette.rewardPale, dimens.borderButton, dimens.shadowMinor, dimens.radiusPill.toFloat())
        val inset = dimens.s
        // The shadow is inside these bounds, so the right and bottom carry its
        // width as padding or the gold sits on the glyph. Vertically the chip
        // only needs the edge, because a 10sp line already carries its own air.
        setPadding(
            dimens.borderButton + inset,
            dimens.borderButton + dimens.xs,
            dimens.borderButton + dimens.shadowMinor + inset,
            dimens.borderButton + dimens.shadowMinor + dimens.xs
        )
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
     *
     * Text colour arrives as a state list, not a colour, so a disabled button
     * cannot be left wearing the white it uses when it is live.
     */
    private fun button(
        value: CharSequence,
        @DrawableRes background: Int,
        @ColorRes textColor: Int
    ): AppCompatButton = AppCompatButton(context).apply {
        text = value
        setTextAppearance(R.style.TextAppearance_ALIVE_Button)
        // Pinned in code, not inherited from the appearance: AppCompatButton
        // resolves its font through the theme as well, and a theme-level family
        // can silently outrank the text appearance - which is how the buttons
        // dropped out of their face while every plain TextView kept it.
        typeface = ResourcesCompat.getFont(context, R.font.dm_sans_medium)
        setTextColor(ContextCompat.getColorStateList(context, textColor))
        setBackgroundResource(background)
        stateListAnimator = null
        gravity = Gravity.CENTER
        minHeight = dimens.touchMin
        // The AppCompat button style stamps every label in capitals, and a text
        // appearance cannot undo it - only the widget can. So the type ladder's
        // "say it in words, not in stamps" rule is enforced here, on every button.
        isAllCaps = false
        // Centred on the *face*, not on the bounds: the shadow is inside the
        // bounds on the right and bottom, so a label centred on the bounds sits
        // visibly low and to the left of the box it is written on.
        setPadding(
            dimens.xl + dimens.borderBold,
            dimens.m,
            dimens.xl + dimens.borderBold + dimens.shadow,
            dimens.m + dimens.shadow
        )
        includeFontPadding = false
    }

    /** The amber offer. One per screen at most. */
    fun primary(value: CharSequence): AppCompatButton =
        button(value, R.drawable.bg_brutal_button_primary, R.color.text_on_primary)
    /** Paper fill with an ink edge. */
    fun secondary(value: CharSequence): AppCompatButton =
        button(value, R.drawable.bg_brutal_button_secondary, R.color.text_on_secondary)

    /** Flat until touched. */
    fun quiet(value: CharSequence): AppCompatButton =
        button(value, R.drawable.bg_brutal_button_quiet, R.color.text_on_secondary)

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
     * A legend row you can choose.
     *
     * The swatch is drawn the way the chart draws its bars - same fill, same ink
     * edge, same corner - so a key and its picture cannot drift apart. The count
     * rides in the label, and the chosen row is marked by a thicker frame plus a
     * sentence under the group, because a highlight that only changes colour says
     * nothing to the person who cannot see the difference.
     */
    fun legendOption(value: CharSequence, @ColorInt swatchColor: Int, swatchPx: Int = dimens.l): RadioButton =
        RadioButton(context).apply {
            setTextAppearance(R.style.TextAppearance_ALIVE_Meta)
            text = value
            setTextColor(palette.ink)
            buttonDrawable = null
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dimens.touchMin
            setPadding(dimens.m, dimens.xs, dimens.m, dimens.xs)
            val swatch = GradientDrawable().apply {
                setColor(swatchColor)
                setStroke(dimens.borderButton, palette.ink)
                cornerRadius = dimens.radiusStep.toFloat()
                setBounds(0, 0, swatchPx, swatchPx)
            }
            setCompoundDrawablesRelative(swatch, null, null, null)
            compoundDrawablePadding = dimens.m
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_checked),
                    outline(dimens.borderBold, dimens.radiusTile.toFloat(), palette.white)
                )
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    /**
     * A single-choice tile: the hue it wears once it is the one picked, an
     * optional pixel mark in front of its name, and no radio circle - the tile is
     * the control. [glyph] may be 0, which is how the duration and range pickers
     * get the same object without a mark to draw.
     *
     * It stays a [RadioButton] inside the existing [RadioGroup] rather than
     * becoming a row of clickable views, because that is what makes one choice
     * exclude the others, what keeps the D-pad working, and what lets TalkBack
     * say "selected" instead of guessing from a colour. And it is built here
     * rather than bare at the call site, because a form widget that only gets a
     * text colour set silently falls back to the platform face - the one place a
     * screen then reads as someone else's app.
     */
    fun tileOption(value: CharSequence, @DrawableRes glyph: Int, @ColorInt hue: Int): RadioButton =
        RadioButton(context).apply {
            setTextAppearance(R.style.TextAppearance_ALIVE_Option)
            text = value
            buttonDrawable = null
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dimens.touchMin
            setPadding(
                dimens.l + dimens.borderBold,
                dimens.m,
                dimens.l + dimens.borderBold + dimens.shadowMinor,
                dimens.m + dimens.shadowMinor
            )
            val mark = if (glyph == 0) null else ContextCompat.getDrawable(context, glyph)?.apply {
                setBounds(0, 0, dimens.l, dimens.l)
            }
            setCompoundDrawablesRelative(mark, null, null, null)
            compoundDrawablePadding = if (mark == null) 0 else dimens.m
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_checked),
                    framed(hue, dimens.borderBold, dimens.shadowMinor, dimens.radiusTile.toFloat())
                )
                addState(intArrayOf(), outline(dimens.borderButton, dimens.radiusTile.toFloat(), palette.white))
            }
        }

    /** The goal field: an ink frame, not a bare line of hint text. */
    fun input(hint: CharSequence): EditText = EditText(context).apply {
        setTextAppearance(R.style.TextAppearance_ALIVE_Input)
        this.hint = hint
        setTextColor(palette.ink)
        setHintTextColor(palette.inkDisabled)
        setBackgroundResource(R.drawable.bg_brutal_input)
        minHeight = dimens.touchMin
        // Measured from the face: the frame is 3dp thick and its shadow 4dp, so
        // the text starts the same distance from the ink on every side.
        setPadding(
            dimens.l + dimens.borderBold,
            dimens.m,
            dimens.l + dimens.borderBold + dimens.shadowMinor,
            dimens.m + dimens.shadowMinor
        )
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
     * A short run of pixel shapes - an invader, a star, a diamond.
     *
     * One row per screen, three shapes, fixed spacing, and a seed chosen per
     * screen so the same corner always shows the same shapes. The count and the
     * margins used to be derived from the seed, which made the debris look
     * sprinkled rather than placed: pixel scatter on every component stops being
     * an accent and becomes noise.
     */
    fun accents(seed: Int, sizePx: Int = dimens.l): View = row().apply {
        for (i in 0 until ACCENT_COUNT) {
            addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        marginEnd = if (i == ACCENT_COUNT - 1) 0 else dimens.lineGap
                    }
                    setImageResource(accentShapes[(seed * 7 + i * 5) % accentShapes.size])
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
            addView(vgap(dimens.groupGap))
            addView(headline(heading))
            addView(vgap(dimens.lineGap))
            addView(note(line))
            addView(vgap(dimens.pairGap))
            addView(annotation(secondLine))
        }

    private companion object {
        /** The sprites are drawn on a 16x16 grid, so one cell is the smallest honest step. */
        const val SPRITE_GRID = 16f

        /** Three shapes, on one row, on one screen. Not a per-section decoration. */
        const val ACCENT_COUNT = 3

        const val BOB_UP_MILLIS = 240L
        const val BOB_DOWN_MILLIS = 1_500L
        const val CARET_ON_MILLIS = 620L
        const val CARET_OFF_MILLIS = 480L
    }
}
