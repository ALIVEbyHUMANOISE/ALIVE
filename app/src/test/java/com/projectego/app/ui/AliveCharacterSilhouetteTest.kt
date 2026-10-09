package com.projectego.app.ui

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque

/**
 * The character has to be one piece, not a pile of confetti.
 *
 * Two rounds of feedback were the same complaint wearing different words: arms
 * flush against the body read as "no arms", and arms lifted a pixel off the body
 * read as "no arms" again, because a floating square is decoration, not a limb.
 * The rule that survives both is that every pink cell touches the torso, so the
 * silhouette is one connected shape and the limbs are bumps on it.
 *
 * The laptop frames are exempt: there the keyboard is what separates the hands
 * from the torso, and that is the point of the frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveCharacterSilhouetteTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val standingSprites = listOf(
        "ic_alive_character" to R.drawable.ic_alive_character,
        "ic_alive_character_aware" to R.drawable.ic_alive_character_aware,
        "alive_pose_disconnected" to R.drawable.alive_pose_disconnected,
        "alive_pose_unaware" to R.drawable.alive_pose_unaware,
        "alive_pose_aware" to R.drawable.alive_pose_aware,
        "alive_pose_controlled" to R.drawable.alive_pose_controlled,
        "alive_pose_alive" to R.drawable.alive_pose_alive,
        "alive_pose_reclaiming" to R.drawable.alive_pose_reclaiming
    )

    @Test
    fun `every pink cell of every pose touches the body`() {
        standingSprites.forEach { (name, id) ->
            val cells = pinkCells(id)
            assertTrue("$name draws no pink at all", cells.isNotEmpty())

            val seen = mutableSetOf(cells.first())
            val queue = ArrayDeque(listOf(cells.first()))
            while (queue.isNotEmpty()) {
                val (x, y) = queue.remove()
                listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).forEach { step ->
                    if (step in cells && seen.add(step)) queue.add(step)
                }
            }

            val orphans = (cells - seen).sortedBy { (x, y) -> y * 16 + x }
            assertTrue("$name has pink pixels floating off the silhouette: $orphans", orphans.isEmpty())
        }
    }

    /** Pink cells read straight out of the compiled vector, in grid coordinates. */
    private fun pinkCells(id: Int): Set<Pair<Int, Int>> {
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
                if (!isBodyPink(fill) || path == null) continue
                rectangles(path).forEach { (x, y, w, h) ->
                    for (dy in 0 until h) for (dx in 0 until w) cells += x + dx to y + dy
                }
            }
        } finally {
            parser.close()
        }
        return cells
    }

    /**
     * A compiled vector stores its fill as `@2131034166`, not as a colour name,
     * so the reference has to be read as an id. The name form is kept for the
     * day a sprite is authored without a colour token.
     */
    private fun isBodyPink(fill: String?): Boolean {
        val token = fill?.removePrefix("@") ?: return false
        token.toIntOrNull()?.let { return it == R.color.alive_pink_soft }
        return token == "color/alive_pink_soft"
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
}
