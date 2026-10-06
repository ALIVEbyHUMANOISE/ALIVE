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
        val block = ui.block(StatusTone.HELD)

        block.addView(ui.label(context.getString(R.string.chart_alert_eyebrow)))
        block.addView(ui.vgap(ui.dimens.s))

        val figure = ui.row().apply { gravity = Gravity.BOTTOM }
        figure.addView(ui.timer(context.getString(R.string.stat_minutes, remainingMinutes)))
        figure.addView(
            ui.label(context.getString(R.string.stat_min_left)).apply {
                setPadding(ui.dimens.m, 0, 0, ui.dimens.l)
            }
        )
        block.addView(figure)

        block.addView(ui.vgap(ui.dimens.s))
        block.addView(ui.timeBar(remainingMinutes, limitMinutes))

        block.addView(ui.vgap(ui.dimens.xl))
        block.addView(
            ui.headline(
                context.getString(AliveStates.alertHeadline(remainingMinutes), remainingMinutes)
            )
        )

        block.addView(ui.vgap(ui.dimens.s))
        block.addView(ui.bodySecondary(context.getString(R.string.alert_support)))

        block.addView(ui.vgap(ui.dimens.l))
        block.addView(ui.accents(seed = 4, count = 3))

        block.addView(ui.vgap(ui.dimens.m))
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
