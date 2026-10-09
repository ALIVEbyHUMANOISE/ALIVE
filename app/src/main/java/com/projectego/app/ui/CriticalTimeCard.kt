package com.projectego.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.projectego.app.R

/**
 * The critical-time card, built once and hostable twice: by
 * [CriticalAlertActivity] over another app, and inside HOME's YOUR TIME block
 * if cross-app delivery ever has to be dropped.
 *
 * It uses the same figure treatment, the same pixel bar and the same held
 * surface as the dashboard, so the note reads as ALIVE speaking rather than as
 * the system interrupting.
 */
object CriticalTimeCard {

    fun build(
        context: Context,
        ui: AliveComponents,
        remainingMinutes: Long,
        limitMinutes: Long,
        onDismiss: () -> Unit
    ): View {
        // A framed notice, not a card: this is the one surface ALIVE raises on
        // its own, so it carries a second rule inside its edge instead of a
        // colour, a sound or a movement.
        val block = ui.notice()

        // The one surface where ALIVE is visibly angry - at the phone, not the
        // user - so the alert reads as ALIVE speaking rather than the system
        // interrupting.
        block.addView(
            ui.mark(
                AliveStates.faceSpriteFor(AliveFace.ANGRY_FULL),
                ui.dimens.character,
                context.getString(AliveStates.faceDescription(AliveFace.ANGRY_FULL))
            )
        )
        block.addView(ui.vgap(ui.dimens.lineGap))

        block.addView(ui.sectionHeader(context.getString(R.string.chart_alert_eyebrow)))
        block.addView(ui.vgap(ui.dimens.lineGap))

        val figure = ui.row().apply { gravity = Gravity.BOTTOM }
        figure.addView(ui.timer(context.getString(R.string.stat_minutes, remainingMinutes)))
        figure.addView(
            ui.meta(context.getString(R.string.stat_min_left)).apply {
                setPadding(ui.dimens.s, 0, 0, ui.dimens.xs)
            }
        )
        block.addView(figure)

        block.addView(ui.vgap(ui.dimens.lineGap))
        block.addView(ui.timeBar(remainingMinutes, limitMinutes))

        block.addView(ui.vgap(ui.dimens.groupGap))
        block.addView(
            ui.headline(
                context.getString(AliveStates.alertHeadline(remainingMinutes), remainingMinutes)
            )
        )

        block.addView(ui.vgap(ui.dimens.lineGap))
        block.addView(ui.note(context.getString(R.string.alert_support)))

        block.addView(ui.vgap(ui.dimens.groupGap))
        block.addView(ui.accents(seed = 6))

        block.addView(ui.vgap(ui.dimens.lineGap))
        block.addView(
            ui.quiet(context.getString(R.string.action_back)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { onDismiss() }
            }
        )

        return block
    }
}
