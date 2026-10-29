package com.projectego.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two structural promises of the Today 2.0 card, without a screenshot:
 * the Home meter spends its width in square steps, and the four tiles share one
 * measured height even when the streak carries pips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayCardLayoutTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    @Test
    fun `the Home meter fills its width with square steps`() {
        // The Today card is MAJOR: at a 320/360/411dp screen its content box is
        // about 220/260/311dp. Test those real widths, not the screen width.
        listOf(220, 260, 311).forEach { widthDp ->
            val bar = ui.timeBar(179L, 200L, fillWidth = true)
            val width = View.MeasureSpec.makeMeasureSpec(
                ui.dimens.dp(widthDp),
                View.MeasureSpec.EXACTLY
            )
            bar.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            bar.layout(0, 0, bar.measuredWidth, bar.measuredHeight)

            assertEquals(10, bar.childCount)
            val step = bar.getChildAt(0).measuredWidth
            assertEquals(step, bar.measuredHeight)
            assertEquals(step, bar.getChildAt(bar.childCount - 1).measuredWidth)

            val used = step * bar.childCount + ui.dimens.stepGap * (bar.childCount - 1)
            assertEquals(ui.dimens.dp(widthDp), bar.measuredWidth)
            assertTrue("the meter must not overflow its width", used <= bar.measuredWidth)
            assertTrue(
                "the meter must not leave a wide strip of paper",
                bar.measuredWidth - used <= ui.dimens.dp(9)
            )
        }
    }

    @Test
    fun `a closed day still draws an empty full-width meter`() {
        val bar = ui.timeBar(0L, 0L, fillWidth = true)
        val width = View.MeasureSpec.makeMeasureSpec(
            ui.dimens.dp(220),
            View.MeasureSpec.EXACTLY
        )
        bar.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        bar.layout(0, 0, bar.measuredWidth, bar.measuredHeight)

        assertEquals(10, bar.childCount)
        val step = bar.getChildAt(0).measuredWidth
        assertEquals(step, bar.measuredHeight)
        val used = step * bar.childCount + ui.dimens.stepGap * (bar.childCount - 1)
        assertTrue("the empty meter must fit", used <= bar.measuredWidth)
    }

    @Test
    fun `four tiles share one height when the streak carries pips`() {
        val grid = ui.equalGrid()
        grid.addView(
            ui.row().apply {
                addView(tile(withPips = true), tileRowParams())
                addView(ui.hgap(ui.dimens.pairGap))
                addView(tile(withPips = false), tileRowParams())
            }
        )
        grid.addView(ui.vgap(ui.dimens.pairGap))
        grid.addView(
            ui.row().apply {
                addView(tile(withPips = false), tileRowParams())
                addView(ui.hgap(ui.dimens.pairGap))
                addView(tile(withPips = false), tileRowParams())
            }
        )

        val width = View.MeasureSpec.makeMeasureSpec(ui.dimens.dp(360), View.MeasureSpec.EXACTLY)
        grid.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)

        val heights = (0 until grid.childCount)
            .map { grid.getChildAt(it) }
            .filter { it is LinearLayout && it.orientation == LinearLayout.HORIZONTAL }
            .flatMap { row ->
                (0 until (row as LinearLayout).childCount)
                    .map { row.getChildAt(it) }
                    .filter { it is LinearLayout }
            }
            .map { it.measuredHeight }

        assertEquals(4, heights.size)
        assertTrue("every tile must be measurable", heights.all { it > 0 })
        assertEquals("the streak pips must not leave one row taller", 1, heights.distinct().size)
    }

    private fun tile(withPips: Boolean): LinearLayout = ui.statTile(
        "Fact",
        "1",
        ui.palette.workPale,
        unit = "min"
    ).apply {
        if (withPips) {
            addView(ui.vgap(ui.dimens.lineGap))
            addView(ui.streakPips(1))
        }
    }

    private fun tileRowParams() = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    )
}
