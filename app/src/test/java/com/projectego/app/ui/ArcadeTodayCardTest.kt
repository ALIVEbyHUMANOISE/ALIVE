package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.domain.UsageReport
import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Arcade card's promises: a gold scoreboard with no badges, the four facts
 * as two rows of two in their role colours, a BESOK row without a box, and a
 * closed day that says TUTUP instead of printing a zero as a score.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArcadeTodayCardTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private fun report(
        remaining: Long = 179L,
        limit: Long = 200L,
        used: Long = 21L,
        streak: Int = 1,
        status: TimeStatus = TimeStatus.ACTIVE,
        dayType: DayType = DayType.NORMAL_DAY,
        bank: TimeBank = TimeBank(earnedMinutes = 45L, spentMinutes = 0L)
    ) = UsageReport(
        totalEntertainmentMinutes = used,
        totalEntertainmentMillis = used * 60_000L,
        unblockedMinutes = 0L,
        totalScreenMinutes = used,
        limitMinutes = limit,
        isLimitExceeded = status == TimeStatus.LOCKED,
        remainingMinutes = remaining,
        criticalZoneTier = 0,
        timeStatus = status,
        dayType = dayType,
        currentStreak = streak,
        timeBank = bank,
        entertainmentAppDetails = emptyList(),
        unblockedAppDetails = emptyList()
    )

    private fun build(report: UsageReport, questDone: Boolean = false): View =
        ArcadeTodayCard.build(
            context = context,
            ui = ui,
            report = report,
            weight = SurfaceWeight.MAJOR,
            questDoneToday = questDone,
            onOpenUsage = {}
        )

    private fun find(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                find(view.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
        }
    }

    private fun faceOf(view: View): Int {
        val background = view.background
        val face = when (background) {
            is GradientDrawable -> background
            is LayerDrawable -> background.getDrawable(1) as GradientDrawable
            else -> error("unexpected tile background: $background")
        }
        return face.color?.defaultColor ?: 0
    }

    @Test
    fun `the title carries the name and no score tag`() {
        val card = build(report())
        val title = requireNotNull(find(card, ArcadeTags.TITLE)) as LinearLayout

        assertEquals(1, title.childCount)
        assertEquals(context.getString(R.string.today_card_title), texts(title).single())
    }

    @Test
    fun `the scoreboard is the remaining time and the meter, with no state tag`() {
        val card = build(report())
        val scoreboard = requireNotNull(find(card, ArcadeTags.SCOREBOARD))
        val score = find(card, ArcadeTags.SCORE_VALUE) as TextView
        val bar = find(card, ArcadeTags.BAR) as LinearLayout

        assertEquals("179", score.text.toString())
        assertEquals(10, bar.childCount)
        assertEquals(
            context.getString(R.string.home_remaining_label),
            texts(scoreboard).first()
        )
        assertTrue(
            "a state tag is back on the board",
            texts(scoreboard).none { it == context.getString(R.string.score_state_normal) }
        )
    }

    @Test
    fun `the scoreboard wears the reference's faces`() {
        assertEquals(
            ui.palette.rewardPale,
            faceOf(requireNotNull(find(build(report()), ArcadeTags.SCOREBOARD)))
        )
        assertEquals(
            ui.palette.shortPale,
            faceOf(
                requireNotNull(
                    find(
                        build(report(remaining = 10L, status = TimeStatus.CRITICAL)),
                        ArcadeTags.SCOREBOARD
                    )
                )
            )
        )
        assertEquals(
            "a zero day keeps the gold board and says TUTUP",
            ui.palette.rewardPale,
            faceOf(
                requireNotNull(
                    find(
                        build(
                            report(
                                remaining = 0L,
                                limit = 0L,
                                used = 0L,
                                status = TimeStatus.ZERO_ENTERTAINMENT_DAY,
                                dayType = DayType.ZERO_ENTERTAINMENT_DAY
                            )
                        ),
                        ArcadeTags.SCOREBOARD
                    )
                )
            )
        )
    }

    @Test
    fun `the four facts sit in a 2x2 in the role colours`() {
        val card = build(report())
        val streak = requireNotNull(find(card, ArcadeTags.STREAK))
        val jatah = requireNotNull(find(card, ArcadeTags.JATAH))
        val used = requireNotNull(find(card, ArcadeTags.USED))
        val bank = requireNotNull(find(card, ArcadeTags.BANK))
        val grid = requireNotNull(find(card, ArcadeTags.STRIP)) as LinearLayout

        assertTrue(texts(streak).contains("1"))
        assertTrue(texts(streak).contains(context.getString(R.string.unit_days_suffix)))
        assertTrue(texts(jatah).contains("200"))
        assertTrue(texts(used).contains("21"))
        assertTrue(texts(bank).contains("45"))

        assertEquals(ui.palette.rewardPale, faceOf(streak))
        assertEquals(ui.palette.workPale, faceOf(jatah))
        assertEquals(ui.palette.softForest, faceOf(used))
        assertEquals(ui.palette.rewardPale, faceOf(bank))
        assertEquals(3, grid.childCount)
    }

    @Test
    fun `tomorrow is a tag and a sentence with no box`() {
        val card = build(report(), questDone = false)
        val tag = find(card, ArcadeTags.TICKET_TAG) as TextView
        val ticket = requireNotNull(find(card, ArcadeTags.TICKET))
        val usage = find(card, ArcadeTags.USAGE) as TextView

        assertEquals(context.getString(R.string.home_tomorrow_label), tag.text.toString())
        assertTrue(texts(ticket).contains(context.getString(R.string.today_not_kept)))
        assertNull("the ticket must not grow a box again", ticket.background)
        assertEquals(context.getString(R.string.chart_open), usage.text.toString())
    }

    @Test
    fun `each row levels its pair, and the streak makes the top row taller`() {
        val card = build(report())
        val width = View.MeasureSpec.makeMeasureSpec(
            ui.dimens.dp(320),
            View.MeasureSpec.EXACTLY
        )
        card.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)

        val grid = requireNotNull(find(card, ArcadeTags.STRIP)) as LinearLayout
        val top = grid.getChildAt(0) as LinearLayout
        val bottom = grid.getChildAt(2) as LinearLayout
        val topCells = listOf(ArcadeTags.STREAK, ArcadeTags.JATAH)
            .map { requireNotNull(find(card, it)) }
        val bottomCells = listOf(ArcadeTags.USED, ArcadeTags.BANK)
            .map { requireNotNull(find(card, it)) }

        assertEquals(topCells[0].measuredHeight, topCells[1].measuredHeight)
        assertEquals(bottomCells[0].measuredHeight, bottomCells[1].measuredHeight)
        assertTrue(
            "the pips must keep the top row taller than the bottom row",
            top.measuredHeight > bottom.measuredHeight
        )
        assertTrue(
            grid.measuredWidth <= card.measuredWidth - card.paddingLeft - card.paddingRight
        )
    }

    @Test
    fun `a closed day says TUTUP and never prints a zero score`() {
        val card = build(
            report(
                remaining = 0L,
                limit = 0L,
                used = 0L,
                streak = 0,
                status = TimeStatus.ZERO_ENTERTAINMENT_DAY,
                dayType = DayType.ZERO_ENTERTAINMENT_DAY,
                bank = TimeBank(earnedMinutes = 65L, spentMinutes = 0L)
            )
        )
        val closed = find(card, ArcadeTags.CLOSED_WORD) as TextView
        val bar = find(card, ArcadeTags.BAR) as LinearLayout

        assertEquals(context.getString(R.string.score_state_closed), closed.text.toString())
        assertEquals(10, bar.childCount)
        assertEquals(null, find(card, ArcadeTags.SCORE_VALUE))
        assertTrue(texts(requireNotNull(find(card, ArcadeTags.BANK))).contains("65"))
    }
}
