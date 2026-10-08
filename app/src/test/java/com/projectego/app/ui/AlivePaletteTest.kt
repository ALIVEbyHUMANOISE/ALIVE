package com.projectego.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contrast, measured from the real resources rather than from a spreadsheet.
 *
 * The palette grew from six colours to nine hues on purpose - the screen read as
 * flat - and the cheapest way for that to go wrong is a colour that is only
 * legible against the one background it was eyeballed on. So every token that is
 * allowed to carry text or draw an edge is checked against every ground in the
 * file, alpha included, and the build fails if it does not clear 4.5:1.
 *
 * Fills (`*_mid`) are deliberately not tested as text grounds, because they are
 * not allowed to be one: white on gold_mid measures 2.2:1. A filled shape gets
 * its label from outside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlivePaletteTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)

    /** Every surface a token may end up sitting on. */
    private val grounds by lazy {
        listOf(
            "canvas" to color(R.color.alive_canvas),
            "paper" to color(R.color.alive_paper),
            "white" to color(R.color.alive_white),
            "softForest" to color(R.color.alive_soft_forest),
            "warmGray" to color(R.color.alive_warm_gray),
            "goldPale" to color(R.color.alive_gold_reward_pale),
            "redPale" to color(R.color.alive_red_short_pale),
            "bluePale" to color(R.color.alive_blue_work_pale)
        )
    }

    /** Tokens that carry text or draw an edge. */
    private val textAndEdgeTokens by lazy {
        listOf(
            "ink" to color(R.color.alive_ink),
            "forest" to color(R.color.alive_forest),
            "goldDark" to color(R.color.alive_gold_reward_dark),
            "redDark" to color(R.color.alive_red_short_dark),
            "blueDark" to color(R.color.alive_blue_work_dark)
        )
    }

    @Test
    fun `every text token clears 4_5 against every ground`() {
        val failures = mutableListOf<String>()

        textAndEdgeTokens.forEach { (inkName, ink) ->
            grounds.forEach { (groundName, ground) ->
                val ratio = contrast(ink, ground)
                if (ratio < 4.5) failures += "$inkName on $groundName = $ratio"
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the secondary text tone still clears 4_5 on the page grounds`() {
        // ink_secondary is 70% ink, so it has to be composited before it is
        // measured - the raw alpha value means nothing on its own.
        listOf(
            "canvas" to color(R.color.alive_canvas),
            "paper" to color(R.color.alive_paper),
            "white" to color(R.color.alive_white)
        ).forEach { (name, ground) ->
            val ratio = contrast(color(R.color.alive_ink_secondary), ground)
            assertTrue("$name = $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `no role colour is so close to its own pale that a swatch disappears`() {
        // A slice or a step has to be visibly a fill, not a smudge of the page.
        val pairs = listOf(
            "gold" to (color(R.color.alive_gold_reward_mid) to color(R.color.alive_gold_reward_pale)),
            "red" to (color(R.color.alive_red_short_mid) to color(R.color.alive_red_short_pale)),
            "blue" to (color(R.color.alive_blue_work_mid) to color(R.color.alive_blue_work_pale))
        )
        pairs.forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$name mid on its pale = $ratio", ratio >= 1.5)
        }
    }

    @Test
    fun `warm gray never carries text`() {
        // The house rule, as a number: warm gray on paper is about 1.2:1, which
        // is why it is allowed to be a surface and not a sentence.
        val ratio = contrast(color(R.color.alive_warm_gray), color(R.color.alive_paper))
        assertTrue("warmGray on paper = $ratio", ratio < 1.6)
    }

    @Test
    fun `ink is readable on every tile hue and on the action amber`() {
        // A category tile holds an ink pixel mark and a card's tab sits under ink
        // text, so the same 4.5:1 that governs a sentence governs a hue here.
        // This is the test that decides whether a category colour may exist.
        val fills = listOf(
            R.color.alive_cat_study,
            R.color.alive_cat_reading,
            R.color.alive_cat_exercise,
            R.color.alive_cat_walking,
            R.color.alive_cat_cleaning,
            R.color.alive_cat_creative,
            R.color.alive_cat_coding,
            R.color.alive_cat_writing,
            R.color.alive_cat_work,
            R.color.alive_cat_other,
            R.color.alive_amber_action
        )
        val ink = color(R.color.alive_ink)
        fills.forEach { id ->
            val name = context.resources.getResourceName(id)
            val ratio = contrast(ink, color(id))
            assertTrue("$name = $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `the colour of an offer is not the colour of a reward`() {
        // Amber invites, gold pays out. Two yellows that measure the same are one
        // yellow with two names, which is how a palette starts lying.
        val ratio = contrast(color(R.color.alive_amber_action), color(R.color.alive_gold_reward_mid))
        assertTrue("amber against reward gold = $ratio", ratio >= 1.3)
    }

    @Test
    fun `the two kinds that share a bar shape are told apart by more than height`() {
        // A tall bar is either a day that ran its allowance out or a day that spent
        // part of it, and those say opposite things about the same evening. Every
        // other kind in the chart is separated by shape - a tick, a hatched stub, a
        // hollow one - so this is the one pair colour has to carry on its own.
        val ratio = contrast(color(R.color.alive_red_short_mid), color(R.color.alive_soft_forest))
        assertTrue("shortMid against softForest = $ratio", ratio >= 1.3)
    }

    /** WCAG 2.1 relative luminance, after compositing any alpha onto the ground. */
    private fun contrast(foreground: Int, background: Int): Double {
        val blended = blend(foreground, background)
        val first = luminance(blended)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return ((lighter + 0.05) / (darker + 0.05) * 100).toInt() / 100.0
    }

    private fun blend(foreground: Int, background: Int): Int {
        val alpha = (foreground ushr 24 and 0xFF) / 255.0
        if (alpha >= 1.0) return foreground and 0xFFFFFF

        fun channel(shift: Int): Int =
            ((foreground ushr shift and 0xFF) * alpha + (background ushr shift and 0xFF) * (1 - alpha)).toInt()

        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val value = (rgb ushr shift and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
