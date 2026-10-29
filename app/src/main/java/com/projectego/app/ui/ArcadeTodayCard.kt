package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.projectego.app.R
import com.projectego.app.domain.CriticalAlertPolicy
import com.projectego.app.domain.UsageReport
import com.projectego.app.model.TimeStatus

/** The parts of the Arcade card the tests and TalkBack order can find. */
internal object ArcadeTags {
    const val CARD = "arcade.card"
    const val TITLE = "arcade.title"
    const val SCOREBOARD = "arcade.scoreboard"
    const val SCORE_VALUE = "arcade.score.value"
    const val BAR = "arcade.bar"
    const val STRIP = "arcade.strip"
    const val STREAK = "arcade.strip.streak"
    const val JATAH = "arcade.strip.jatah"
    const val USED = "arcade.strip.used"
    const val BANK = "arcade.strip.bank"
    const val SUPPORT = "arcade.support"
    const val TICKET = "arcade.ticket"
    const val TICKET_TAG = "arcade.ticket.tag"
    const val USAGE = "arcade.usage"
    const val CLOSED_WORD = "arcade.closed"
    const val CLOSED_LINE = "arcade.closed.line"
}

/**
 * The Today card as the reference has it: one gold scoreboard, one meter, a
 * 2x2 of the four facts, a line about today, the tomorrow ticket, and the way
 * to the chart. No badges sit on top of it - the state is carried by the
 * scoreboard face and by TUTUP, not by a tag.
 *
 * The role colours are fixed: streak gold, allowance blue, used mint, bank
 * gold. Used uses [AlivePalette.softForest] because the reference shows it
 * mint; spending time does not get the deeper earned green of another role.
 * Nothing here changes what the numbers mean or how they are calculated.
 */
object ArcadeTodayCard {

    fun build(
        context: Context,
        ui: AliveComponents,
        report: UsageReport,
        weight: SurfaceWeight,
        questDoneToday: Boolean,
        onOpenUsage: () -> Unit
    ): LinearLayout {
        val closed = report.timeStatus == TimeStatus.LOCKED ||
            report.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY
        val last = !closed && report.remainingMinutes <= 1L
        val critical = report.timeStatus == TimeStatus.CRITICAL

        val card = ui.block(weight, StatusTone.HELD).apply {
            tag = ArcadeTags.CARD
        }
        card.addView(titleRow(context, ui))
        card.addView(titleRule(context, ui))
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(scoreboard(context, ui, report, closed, critical, last))
        card.addView(ui.vgap(ui.dimens.lineGap))
        // The reference rules the scoreboard off from the facts, and the facts
        // off from today's sentence. Two weights: a full ink rule above, a
        // hairline below.
        card.addView(ui.rule())
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(factGrid(context, ui, report))
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(hairline(context, ui))
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            ui.metaSmall(
                context.getString(
                    if (last) {
                        R.string.time_last_minute
                    } else {
                        AliveStates.timeSupportLine(report.timeStatus, report.criticalZoneTier)
                    }
                )
            ).apply { tag = ArcadeTags.SUPPORT }
        )
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(ticket(context, ui, questDoneToday))
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(
            ui.secondary(context.getString(R.string.chart_open)).apply {
                tag = ArcadeTags.USAGE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { onOpenUsage() }
            }
        )
        return card
    }

    private fun titleRow(context: Context, ui: AliveComponents): LinearLayout = ui.row().apply {
        tag = ArcadeTags.TITLE
        addView(ui.sectionHeader(context.getString(R.string.today_card_title)))
    }

    private fun titleRule(context: Context, ui: AliveComponents): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ui.dimens.borderBold
        )
        setBackgroundColor(ui.palette.ink)
    }

    private fun hairline(context: Context, ui: AliveComponents): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ui.dimens.borderThin
        )
        setBackgroundColor(ui.palette.inkHairline)
    }

    private fun scoreboard(
        context: Context,
        ui: AliveComponents,
        report: UsageReport,
        closed: Boolean,
        critical: Boolean,
        last: Boolean
    ): LinearLayout {
        val face = when {
            // The reference's red face is for the last stretch, not for a
            // closed day: a zero day keeps the gold board and says TUTUP.
            critical || last -> ui.palette.shortPale
            else -> ui.palette.rewardPale
        }
        val panel = panel(
            context,
            ui,
            face = face,
            edgePx = ui.dimens.borderButton,
            shadowPx = ui.dimens.shadowMinor,
            radiusPx = ui.dimens.dp(11),
            insetPx = ui.dimens.s
        ).apply { tag = ArcadeTags.SCOREBOARD }

        panel.addView(ui.scoreLabel(context.getString(R.string.home_remaining_label)))
        panel.addView(ui.vgap(ui.dimens.pairGap))

        if (closed) {
            panel.addView(
                ui.scoreStatus(context.getString(R.string.score_state_closed)).apply {
                    isAllCaps = true
                    tag = ArcadeTags.CLOSED_WORD
                }
            )
            panel.addView(ui.vgap(ui.dimens.xs))
            panel.addView(
                ui.metaSmall(
                    context.getString(
                        if (report.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY) {
                            R.string.zero_day_heading
                        } else {
                            R.string.time_closed
                        }
                    )
                ).apply { tag = ArcadeTags.CLOSED_LINE }
            )
        } else {
            val scoreRow = ui.row().apply { gravity = Gravity.BOTTOM }
            scoreRow.addView(
                ui.timer(context.getString(R.string.stat_minutes, report.remainingMinutes)).apply {
                    tag = ArcadeTags.SCORE_VALUE
                }
            )
            scoreRow.addView(
                ui.metaTiny(context.getString(R.string.unit_minutes_suffix)).apply {
                    isAllCaps = true
                    setPadding(ui.dimens.s, 0, 0, ui.dimens.xs)
                }
            )
            scoreRow.addView(
                ui.metaTiny(context.getString(R.string.time_of_allowance, report.limitMinutes)).apply {
                    isAllCaps = true
                    setPadding(ui.dimens.s, 0, 0, ui.dimens.xs)
                }
            )
            panel.addView(scoreRow)
        }

        panel.addView(ui.vgap(ui.dimens.pairGap))
        panel.addView(
            ui.timeBar(
                remaining = report.remainingMinutes,
                limit = report.limitMinutes,
                fillWidth = true,
                fill = if (report.remainingMinutes in 1..CriticalAlertPolicy.REMAINING_WINDOW_MINUTES) {
                    ui.palette.shortMid
                } else {
                    ui.palette.forest
                }
            ).apply { tag = ArcadeTags.BAR }
        )
        return panel
    }

    /**
     * The four facts as two rows of two, each row levelled to its own taller
     * cell. The streak's pips make the top row taller; the reference does not
     * level the two rows against each other, and neither do we.
     */
    private fun factGrid(
        context: Context,
        ui: AliveComponents,
        report: UsageReport
    ): LinearLayout {
        val grid = ui.column().apply { tag = ArcadeTags.STRIP }
        grid.addView(
            factRow(
                ui,
                factCell(
                    context, ui, ArcadeTags.STREAK, R.string.stat_streak,
                    report.currentStreak.toLong(), R.string.unit_days_suffix,
                    ui.palette.rewardPale, pips = report.currentStreak.coerceIn(0, 7)
                ),
                factCell(
                    context, ui, ArcadeTags.JATAH, R.string.stat_allowance,
                    report.limitMinutes, R.string.unit_minutes_suffix,
                    ui.palette.workPale
                )
            )
        )
        grid.addView(ui.vgap(ui.dimens.s))
        grid.addView(
            factRow(
                ui,
                factCell(
                    context, ui, ArcadeTags.USED, R.string.stat_used,
                    report.totalEntertainmentMinutes, R.string.unit_minutes_suffix,
                    ui.palette.softForest
                ),
                factCell(
                    context, ui, ArcadeTags.BANK, R.string.stat_bank,
                    report.timeBank.availableMinutes, R.string.unit_minutes_suffix,
                    ui.palette.rewardPale
                )
            )
        )
        return grid
    }

    private fun factRow(ui: AliveComponents, left: View, right: View): LinearLayout =
        ui.equalRow().apply {
            listOf(left, right).forEachIndexed { index, cell ->
                if (index > 0) addView(ui.hgap(ui.dimens.s))
                addView(
                    cell.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }
                )
            }
        }

    private fun factCell(
        context: Context,
        ui: AliveComponents,
        tagValue: String,
        labelRes: Int,
        value: Long,
        unitRes: Int,
        face: Int,
        pips: Int = -1
    ): LinearLayout = ui.column().apply {
        tag = tagValue
        background = framed(ui, face, ui.dimens.borderButton, ui.dimens.dp(3), ui.dimens.dp(9))
        setPadding(ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(13), ui.dimens.dp(13))
        addView(ui.scoreLabel(context.getString(labelRes)))
        addView(ui.vgap(ui.dimens.pairGap))
        val valueRow = ui.row().apply {
            gravity = Gravity.BOTTOM
            isBaselineAligned = false
        }
        valueRow.addView(ui.scoreValue(context.getString(R.string.stat_minutes, value)))
        if (labelRes == R.string.stat_streak) {
            valueRow.addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_alive_flame)
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(16), ui.dimens.dp(16)).apply {
                        marginStart = ui.dimens.xs
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                }
            )
        }
        addView(valueRow)
        if (pips >= 0) {
            addView(ui.vgap(ui.dimens.pairGap))
            addView(pipsRow(context, ui, filled = pips))
        }
        addView(ui.vgap(ui.dimens.pairGap))
        addView(ui.meta(context.getString(unitRes)))
    }

    private fun pipsRow(context: Context, ui: AliveComponents, filled: Int): LinearLayout =
        ui.row().apply {
            for (index in 0 until 7) {
                addView(
                    View(context).apply {
                        // Twelve-dp blocks read as a progress bar; the five-dp
                        // dots looked like dust under the larger figures.
                        layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(12), ui.dimens.dp(12)).apply {
                            marginEnd = if (index == 6) 0 else ui.dimens.dp(2)
                        }
                        background = outline(
                            ui,
                            if (index < filled) ui.palette.rewardMid else ui.palette.white,
                            ui.dimens.borderThin,
                            ui.dimens.dp(2)
                        )
                    }
                )
            }
        }

    /** The reference's ticket is a tag and a sentence on the paper, not a box. */
    private fun ticket(
        context: Context,
        ui: AliveComponents,
        questDoneToday: Boolean
    ): LinearLayout = ui.row().apply {
        tag = ArcadeTags.TICKET
        gravity = Gravity.CENTER_VERTICAL
        addView(
            tagBox(
                context,
                ui,
                context.getString(R.string.home_tomorrow_label),
                ui.palette.rewardPale
            ).apply { tag = ArcadeTags.TICKET_TAG }
        )
        addView(ui.hgap(ui.dimens.s))
        addView(
            ui.weighted(
                ui.metaSmall(
                    context.getString(
                        if (questDoneToday) R.string.today_kept else R.string.today_not_kept
                    )
                )
            )
        )
    }

    private fun tagBox(
        context: Context,
        ui: AliveComponents,
        text: CharSequence,
        face: Int
    ): TextView = ui.tag(text).apply {
        setTextColor(ui.palette.ink)
        background = outline(ui, face, ui.dimens.borderThin, ui.dimens.dp(4))
        setPadding(ui.dimens.xs, ui.dimens.xs, ui.dimens.xs, ui.dimens.xs)
    }

    private fun panel(
        context: Context,
        ui: AliveComponents,
        face: Int,
        edgePx: Int,
        shadowPx: Int,
        radiusPx: Int,
        insetPx: Int
    ): LinearLayout = ui.column().apply {
        background = if (shadowPx <= 0) {
            outline(ui, face, edgePx, radiusPx)
        } else {
            framed(ui, face, edgePx, shadowPx, radiusPx)
        }
        setPadding(
            edgePx + insetPx,
            edgePx + insetPx,
            edgePx + shadowPx + insetPx,
            edgePx + shadowPx + insetPx
        )
    }

    private fun framed(
        ui: AliveComponents,
        face: Int,
        edgePx: Int,
        shadowPx: Int,
        radiusPx: Int
    ): Drawable {
        val under = GradientDrawable().apply {
            setColor(ui.palette.ink)
            cornerRadius = radiusPx.toFloat()
        }
        val faceDrawable = GradientDrawable().apply {
            setColor(face)
            setStroke(edgePx, ui.palette.ink)
            cornerRadius = radiusPx.toFloat()
        }
        return LayerDrawable(arrayOf(under, faceDrawable)).apply {
            setLayerInset(1, 0, 0, shadowPx, shadowPx)
        }
    }

    private fun outline(ui: AliveComponents, face: Int, edgePx: Int, radiusPx: Int): Drawable =
        GradientDrawable().apply {
            setColor(face)
            setStroke(edgePx, ui.palette.ink)
            cornerRadius = radiusPx.toFloat()
        }
}
