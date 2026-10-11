package com.projectego.app.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.projectego.app.R

/**
 * The first five seconds of the critical note: the field behind it turns red and
 * breathes, and the note refuses to be sent away.
 *
 * Two decisions worth stating, because both are measured rather than felt:
 *
 *  - The red is a *field*, and the words sit on the paper card inside it. Ink on
 *    `red_short_mid` measures 4.27:1, under the app's own floor, so a warning that
 *    wrote straight onto the red would be the one screen in ALIVE that quietly
 *    broke the rule the palette test enforces everywhere else.
 *  - The pulse is one breath per second, not a strobe: bright to deep and back,
 *    which is inside every photosensitivity guideline and still reads as movement
 *    from across a room. When the device says "remove animated transitions" the
 *    field stays the same red and simply stops moving.
 *
 * The countdown is the reason the note is not a trap: the user can see how long
 * the window lasts, and the app never claims a lock it cannot keep - HOME still
 * works, because Android does not let any app refuse it.
 */
object CriticalWarningCard {

    /** How long the note holds the screen before it hands over to the card. */
    const val LOCK_SECONDS = 5

    private const val TICK_MILLIS = 1_000L

    /** Half a breath: out and back is one second, the slow end of safe. */
    private const val BREATH_MILLIS = 500L

    /**
     * The field's colour at one point of the breath.
     *
     * Pure and public so the gate can be tested without a running animator: with
     * animations off the answer is the same red at every point, which is the whole
     * promise of respecting the system setting.
     */
    fun fieldColour(progress: Float, animationsOn: Boolean, mid: Int, deep: Int): Int =
        if (!animationsOn) mid else ArgbEvaluator().evaluate(progress, mid, deep) as Int

    /**
     * Builds the warning, already ticking. It starts and stops with its own
     * attachment, the same way the hourglass mark does, so the Activity that hosts
     * it owns no timers and nothing keeps ticking after the window is gone.
     */
    fun build(
        context: Context,
        ui: AliveComponents,
        remainingMinutes: Long,
        onContinue: () -> Unit
    ): View {
        val mid = ui.palette.shortMid
        val deep = ui.palette.shortDark

        // The MAJOR block: the one surface ALIVE raises on its own, in the colour
        // the app already reserves for time that is running out.
        val field = ui.tintedBlock(mid, SurfaceWeight.MAJOR)

        val plate = ui.block(SurfaceWeight.MINOR).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        plate.addView(ui.sectionHeader(context.getString(R.string.alert_warning_eyebrow)))
        plate.addView(ui.vgap(ui.dimens.lineGap))

        // The same figure treatment as the card that follows: the colour never
        // carries the message on its own.
        plate.addView(
            ui.row().apply {
                gravity = Gravity.BOTTOM
                addView(ui.timer(context.getString(R.string.stat_minutes, remainingMinutes)))
                addView(
                    ui.meta(context.getString(R.string.stat_min_left)).apply {
                        setPadding(ui.dimens.s, 0, 0, ui.dimens.xs)
                    }
                )
            }
        )

        plate.addView(ui.vgap(ui.dimens.lineGap))
        plate.addView(ui.note(context.getString(R.string.alert_warning_body)))

        val seconds = ui.textTile(LOCK_SECONDS.toString(), ui.palette.shortPale)
        plate.addView(ui.vgap(ui.dimens.groupGap))
        plate.addView(
            ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(seconds)
                addView(ui.hgap(ui.dimens.m))
                addView(ui.meta(context.getString(R.string.alert_warning_countdown)))
            }
        )

        plate.addView(ui.vgap(ui.dimens.groupGap))
        plate.addView(ui.accents(seed = 3))

        field.addView(plate)

        hookTimer(ui, field, seconds, mid, deep, onContinue)
        return field
    }

    private fun hookTimer(
        ui: AliveComponents,
        field: LinearLayout,
        seconds: TextView,
        mid: Int,
        deep: Int,
        onContinue: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var left = LOCK_SECONDS

        val tick = object : Runnable {
            override fun run() {
                left -= 1
                if (left <= 0) {
                    onContinue()
                    return
                }
                seconds.text = left.toString()
                handler.postDelayed(this, TICK_MILLIS)
            }
        }

        field.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private var breath: ValueAnimator? = null

            override fun onViewAttachedToWindow(view: View) {
                if (ui.animationsAllowed) {
                    breath = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = BREATH_MILLIS
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        addUpdateListener { running ->
                            faceOf(view)?.setColor(
                                fieldColour(running.animatedFraction, true, mid, deep)
                            )
                        }
                        start()
                    }
                }
                handler.postDelayed(tick, TICK_MILLIS)
            }

            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacksAndMessages(null)
                breath?.cancel()
                breath = null
                faceOf(view)?.setColor(mid)
            }
        })
    }

    /**
     * The red layer of the MAJOR block, which is the second layer of the pair the
     * app builds for every raised surface: ink underneath, face on top.
     */
    private fun faceOf(view: View): GradientDrawable? =
        ((view.background as? LayerDrawable)?.getDrawable(1) as? GradientDrawable)?.apply { mutate() }
}
