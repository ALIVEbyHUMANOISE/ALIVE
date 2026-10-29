package com.projectego.app.domain

/**
 * One frame of the pill's shape change, in fractions.
 *
 * The island's trick is that everything answers one clock: the window, its
 * corner radius and the content inside it all move on the same fraction, so no
 * part of the picture can be ahead of another. This object is that clock's
 * voice - the service feeds it the animation fraction and the frame the picture
 * is in right now, and it answers what every moving part should look like next.
 *
 * Pure on purpose: fractions in, a frame out, nothing kept. The choreography
 * is testable without a window, and the service cannot drift from the tests
 * because both ask the same question.
 *
 * The stagger is the reading order: the shell grows first, then the character
 * and the day's figure, then the app rows, then the way out - each element
 * arrives slightly after the one the eye is already on. On collapse the order
 * reverses and everything is gone well before the window finishes shrinking,
 * so a collapsing pill never shows an empty black shell.
 *
 * Both directions take a [from] frame because a morph can be interrupted at any
 * fraction. Starting from wherever the picture actually is - rather than from
 * the pose the transition assumes - is what keeps a reversed direction from
 * flashing content that was already half visible, or half gone.
 */
object PillMorphChoreography {

    /**
     * What every animated part should be at fraction [f].
     *
     * All values are fractions and dp offsets; the service owns pixels, which
     * is the only thing about this picture that is not unitless.
     */
    data class Frame(
        /** 0 = compact corner, 1 = expanded corner; the service lerps the radius. */
        val radius: Float,
        val panelAlpha: Float,
        val characterAlpha: Float,
        val characterScale: Float,
        val rowsAlpha: Float,
        val rowsShiftDp: Float,
        val actionAlpha: Float,
        val actionShiftDp: Float
    )

    /**
     * The way out: [from] is where the picture is now, and the content arrives
     * in reading order at the expanded rest.
     *
     * With the default start this is the expansion exactly as it has always
     * been; a start part way along the previous morph makes the same staggered
     * arrival continue instead of restart.
     */
    fun expand(f: Float, from: Frame = rest(false)): Frame =
        travel(from, EXPANDED, f, ARRIVAL)

    /**
     * The way back: [from] is where the picture is now, and everything leaves
     * early enough that the finishing shell is pure ink.
     */
    fun collapse(f: Float, from: Frame = rest(true)): Frame =
        travel(from, DEPARTURE, f, DEPARTURE_RAMPS)

    /**
     * The rest pose for each state.
     *
     * Expanded is also the exact end of [expand] - the service relies on that.
     * Collapsed is a *hidden* pose rather than the end of [collapse]: it is only
     * ever applied while the panel is invisible (at the end of a collapse, or
     * when motion is off), and because a morph now starts from whatever frame it
     * is handed, it doubles as the launch pose an arrival lifts itself out of.
     */
    fun rest(expanded: Boolean): Frame = if (expanded) EXPANDED else COMPACT

    private fun travel(from: Frame, to: Frame, f: Float, ramps: Ramps): Frame {
        val x = f.coerceIn(0f, 1f)
        return Frame(
            radius = between(from.radius, to.radius, ramps.radius.at(x)),
            panelAlpha = between(from.panelAlpha, to.panelAlpha, ramps.panel.at(x)),
            characterAlpha = between(from.characterAlpha, to.characterAlpha, ramps.character.at(x)),
            characterScale = between(from.characterScale, to.characterScale, ramps.scale.at(x)),
            rowsAlpha = between(from.rowsAlpha, to.rowsAlpha, ramps.rows.at(x)),
            rowsShiftDp = between(from.rowsShiftDp, to.rowsShiftDp, ramps.rowsShift.at(x)),
            actionAlpha = between(from.actionAlpha, to.actionAlpha, ramps.action.at(x)),
            actionShiftDp = between(from.actionShiftDp, to.actionShiftDp, ramps.actionShift.at(x))
        )
    }

    /** The one moving form of every property: the start, the target, a ramp. */
    private fun between(from: Float, to: Float, at: Float): Float = from + (to - from) * at

    /**
     * The pose a collapsed pill parks its content in - hidden, small, and
     * dropped below the panel by exactly the distance the arrival lifts it.
     * Nothing of it is visible while the panel is gone, and it is the launch
     * pose an expansion leaves from, which is why the offsets live here rather
     * than in the curve.
     */
    private val COMPACT = Frame(
        radius = 0f,
        panelAlpha = 0f,
        characterAlpha = 0f,
        characterScale = 0.72f,
        rowsAlpha = 0f,
        rowsShiftDp = ROWS_DROP_DP,
        actionAlpha = 0f,
        actionShiftDp = ACTION_DROP_DP
    )

    private val EXPANDED = Frame(
        radius = 1f,
        panelAlpha = 1f,
        characterAlpha = 1f,
        characterScale = 1f,
        rowsAlpha = 1f,
        rowsShiftDp = 0f,
        actionAlpha = 1f,
        actionShiftDp = 0f
    )

    /**
     * Where a collapse leaves the content: invisible, and already translated
     * the way a departure rides, so a reversal has somewhere to come back from.
     */
    private val DEPARTURE = Frame(
        radius = 0f,
        panelAlpha = 0f,
        characterAlpha = 0f,
        characterScale = 1f - DEPARTURE_SCALE_LOSS,
        rowsAlpha = 0f,
        rowsShiftDp = -ROWS_LIFT_DP,
        actionAlpha = 0f,
        actionShiftDp = -ACTION_LIFT_DP
    )

    /** When each property starts and finishes travelling, on the way out. */
    private val ARRIVAL = Ramps(
        radius = Span(0f, 1f),
        panel = Span(0.08f, 0.38f),
        character = Span(0.16f, 0.46f),
        scale = Span(0.14f, 0.48f),
        rows = Span(0.30f, 0.66f),
        rowsShift = Span(0.26f, 0.66f),
        action = Span(0.44f, 0.80f),
        actionShift = Span(0.38f, 0.82f)
    )

    /** On the way back everything finishes early: the shell gets the last stretch. */
    private val DEPARTURE_RAMPS = Ramps(
        radius = Span(0f, 1f),
        panel = Span(0f, 0.45f),
        character = Span(0f, 0.40f),
        scale = Span(0f, 0.45f),
        rows = Span(0f, 0.34f),
        rowsShift = Span(0f, 0.50f),
        action = Span(0f, 0.30f),
        actionShift = Span(0f, 0.44f)
    )

    private data class Ramps(
        val radius: Span,
        val panel: Span,
        val character: Span,
        val scale: Span,
        val rows: Span,
        val rowsShift: Span,
        val action: Span,
        val actionShift: Span
    )

    private data class Span(val start: Float, val end: Float) {
        fun at(x: Float): Float = ((x - start) / (end - start)).coerceIn(0f, 1f)
    }

    /** How far the rows settle from below on the way out. */
    private const val ROWS_DROP_DP = 6f

    /** How far the action settles from below on the way out. */
    private const val ACTION_DROP_DP = 10f

    /** How far the rows ride up into the header on the way back. */
    private const val ROWS_LIFT_DP = 5f

    /** How far the action rides up into the header on the way back. */
    private const val ACTION_LIFT_DP = 8f

    /** How much the character gives up as the panel closes. */
    private const val DEPARTURE_SCALE_LOSS = 0.15f
}
