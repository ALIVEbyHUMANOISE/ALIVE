package com.projectego.app.ui

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque

/**
 * The four Guardian poses, measured rather than admired.
 *
 * Three of these rules are the ones the critter's sheet already lives by, moved
 * onto a second character: a body is one connected piece, nothing decorative
 * floats off it, and the figure has to read against the surface it is drawn on.
 * The fourth is the brief's own requirement made checkable: idle, alert,
 * recovery and restored must be four different drawings, because a state a
 * person cannot tell apart from the last one is not a state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuardianSpriteTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val poses = linkedMapOf(
        "idle" to R.drawable.ic_guardian_idle,
        "alert" to R.drawable.ic_guardian_alert,
        "recovery" to R.drawable.ic_guardian_recovery,
        "restored" to R.drawable.ic_guardian_restored
    )

    @Test
    fun `the whole figure is one connected shape in every pose`() {
        poses.forEach { (name, id) ->
            // Every painted cell, whatever colour: the helm is joined to the body
            // through the visor band, and a floating limb is a floating limb
            // whichever ink it is drawn in.
            val drawn = paintedCells(id)
            assertTrue("$name draws nothing at all", drawn.isNotEmpty())

            val seen = mutableSetOf(drawn.first())
            val queue = ArrayDeque(listOf(drawn.first()))
            while (queue.isNotEmpty()) {
                val (x, y) = queue.remove()
                listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).forEach { step ->
                    if (step in drawn && seen.add(step)) queue.add(step)
                }
            }

            val orphans = (drawn - seen).sortedBy { (x, y) -> y * 16 + x }
            assertTrue("$name has pixels floating off the figure: $orphans", orphans.isEmpty())
        }
    }

    @Test
    fun `no accent floats off the body`() {
        val accents = listOf(
            "crimson" to R.color.alive_red_short_dark,
            "crimson bright" to R.color.alive_red_short_mid,
            "amber eyes" to R.color.alive_amber_action,
            "metal" to R.color.alive_warm_gray
        )

        poses.forEach { (name, id) ->
            val body = cells(id, R.color.alive_ink)
            accents.forEach { (label, colour) ->
                val cluster = cells(id, colour)
                if (cluster.isEmpty()) return@forEach

                // Every accent cluster - the eyes, the visor, the shield - has to
                // touch the body, or it is decoration sitting in mid-air.
                val seen = mutableSetOf<Pair<Int, Int>>()
                val queue = ArrayDeque(listOf(cluster.first()))
                seen += cluster.first()
                var touchesBody = false
                while (queue.isNotEmpty()) {
                    val (x, y) = queue.remove()
                    listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).forEach { step ->
                        if (step in body) touchesBody = true
                        if (step in cluster && seen.add(step)) queue.add(step)
                    }
                }
                assertTrue("$name: $label is not attached to the body", touchesBody)
            }
        }
    }

    @Test
    fun `the eyes read on the helmet, and the figure reads on the card`() {
        val eyes = colour(R.color.alive_amber_action)
        val helmet = colour(R.color.alive_ink)
        val paper = colour(R.color.alive_paper)
        val white = colour(R.color.alive_white)

        // A face whose eyes cannot be seen is the one thing this character is
        // for. 3:1 is the bar for a non-text graphic that carries meaning.
        val onHelmet = contrast(eyes, helmet)
        assertTrue("amber on charcoal = $onHelmet", onHelmet >= 3.0)

        // And the whole figure has to be a figure on the surfaces it is drawn on,
        // rather than a smudge the colour of the card.
        listOf("paper" to paper, "white" to white).forEach { (name, ground) ->
            val ratio = contrast(helmet, ground)
            assertTrue("charcoal on $name = $ratio", ratio >= 8.0)
        }
    }

    @Test
    fun `every pose is a different drawing`() {
        val drawings = poses.mapValues { (_, id) -> paintedCells(id).toString() }
        val distinct = drawings.values.toSet()
        assertEquals("two poses draw the same pixels", drawings.size, distinct.size)
    }

    /** Cells of one colour, read straight out of the compiled vector. */
    private fun cells(id: Int, colourRes: Int): Set<Pair<Int, Int>> {
        val cells = linkedSetOf<Pair<Int, Int>>()
        val parser = context.resources.getXml(id)
        try {
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                var fill: String? = null
                var path: String? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "fillColor" -> fill = parser.getAttributeValue(i)
                        "pathData" -> path = parser.getAttributeValue(i)
                    }
                }
                if (path == null || !isColour(fill, colourRes)) continue
                rectangles(path).forEach { (x, y, w, h) ->
                    for (dy in 0 until h) for (dx in 0 until w) cells += x + dx to y + dy
                }
            }
        } finally {
            parser.close()
        }
        return cells
    }

    /** Every cell the sprite paints, whatever colour it is painted. */
    private fun paintedCells(id: Int): Set<Pair<Int, Int>> {
        val drawn = linkedSetOf<Pair<Int, Int>>()
        val parser = context.resources.getXml(id)
        try {
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                var path: String? = null
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "pathData") path = parser.getAttributeValue(i)
                }
                if (path == null) continue
                rectangles(path).forEach { (x, y, w, h) ->
                    for (dy in 0 until h) for (dx in 0 until w) drawn += x + dx to y + dy
                }
            }
        } finally {
            parser.close()
        }
        return drawn
    }

    /**
     * A compiled vector stores its fill as `@2131034166`, not as a colour name,
     * so the reference has to be read as an id. The name form is kept for the
     * day a sprite is authored without a colour token.
     */
    private fun isColour(fill: String?, colourRes: Int): Boolean {
        val token = fill?.removePrefix("@") ?: return false
        token.toIntOrNull()?.let { return it == colourRes }
        return false
    }

    /** The bounding box of each `Mx,yhwvh-wz` run in a pathData string. */
    private fun rectangles(path: String): List<IntArray> =
        Regex("M(-?\\d+),(-?\\d+)((?:[hv]-?\\d+)+)z").findAll(path).map { run ->
            var x = run.groupValues[1].toInt()
            var y = run.groupValues[2].toInt()
            var left = x
            var top = y
            var right = x
            var bottom = y
            Regex("[hv](-?\\d+)").findAll(run.groupValues[3]).forEach { step ->
                val amount = step.groupValues[1].toInt()
                if (step.value[0] == 'h') x += amount else y += amount
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
            intArrayOf(left, top, right - left, bottom - top)
        }.toList()

    private fun colour(id: Int): Int = androidx.core.content.ContextCompat.getColor(context, id)

    /** WCAG 2.1 relative luminance ratio; every token here is fully opaque. */
    private fun contrast(foreground: Int, background: Int): Double {
        fun channel(value: Int): Double {
            val ratio = value / 255.0
            return if (ratio <= 0.03928) ratio / 12.92 else Math.pow((ratio + 0.055) / 1.055, 2.4)
        }
        fun luminance(rgb: Int): Double =
            0.2126 * channel(rgb shr 16 and 0xFF) +
                0.7152 * channel(rgb shr 8 and 0xFF) +
                0.0722 * channel(rgb and 0xFF)

        val first = luminance(foreground)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return ((lighter + 0.05) / (darker + 0.05) * 100).toInt() / 100.0
    }
}
