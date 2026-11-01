package com.projectego.app.service

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The compact row must sit on the window's centre line in both moods.
 *
 * The pill once carried a 24dp `readingSpacer` as the row's last child, "mirroring"
 * the character slot from the far side. A mirror behind the ring is not a mirror:
 * it widens the row without widening the reading, so every element left of it
 * slid off centre and the island grew a dead end on the right. Two owner frames
 * show it - 12.7dp of air before the ripple against 42.0dp after the ring, the
 * ink block 14.7dp left of the capsule's centre.
 *
 * So the contract is the one the spacer broke, stated on the row rather than on
 * the pixels: the empty space between the row's inner edge and the group that
 * opens it must equal the empty space between the group that closes it and the
 * row's other inner edge - in the guest's absence, in its presence, and while a
 * morph holds the window wider than the row needs.
 *
 * Geometry only: [PillStatusRingTest] owns the ring's meaning, [PillReminderTest]
 * the reminder's, [PillRowUpdate] the reading's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillRowBalanceTest {

    /**
     * Reuses [PillFrameCapture]'s bootstrap rather than raising a second one:
     * the overlay permission, the report executor that must not read a real day,
     * the animator scale pinned to zero and the service's own start command.
     * Its helpers are private to that class, so they are reached by reflection -
     * if the harness changes shape this test fails here, loudly, and does not
     * quietly start asserting nothing.
     */
    private val harness = PillFrameCapture()
    private lateinit var service: InfoPillService
    private var opened = false

    @After
    fun tearDown() {
        if (opened) harness.tearDown()
    }

    @Test
    fun `no guest leaves the row balanced`() {
        open(guest = false)
        assertBalanced("slot gone, window wrapping the row", layOut(extraDp = 0f))
    }

    @Test
    fun `a guest in the slot leaves the row balanced`() {
        open(guest = true)
        assertBalanced("slot taken by the ripple, window wrapping the row", layOut(extraDp = 0f))
    }

    @Test
    fun `no guest stays balanced while a morph holds the window wider`() {
        open(guest = false)
        // The morph freezes the window at the previous size, so the row is laid
        // out with slack on both sides. Slack belongs to the centre line, not to
        // whichever side a dead child happens to sit on.
        assertBalanced("slot gone, window held 24dp wider", layOut(extraDp = 24f))
    }

    @Test
    fun `a guest stays balanced while a morph holds the window wider`() {
        open(guest = true)
        assertBalanced("slot taken, window held 24dp wider", layOut(extraDp = 24f))
    }

    @Test
    fun `the guest is really the only thing that moves`() {
        open(guest = false)
        val bare = layOut(extraDp = 0f)
        // Without this, a harness that could never switch the slot on would pass
        // the four balance cases twice over the same state.
        assertEquals("the row must be laid out with the slot gone", View.GONE, slot().visibility)
        assertEquals("nothing may trail the ring", 2, bare.row.childCount)
        assertTrue(
            "the timer must carry ink, or the row has no reading to centre: " +
                "width=${bare.timer.width}",
            bare.timer.width > 0
        )
        val bareWidth = bare.row.width
        setMusicPlaying(active = true)
        invoke("syncSlot")
        val guest = layOut(extraDp = 0f)
        assertEquals("the guest must occupy the slot", View.VISIBLE, slot().visibility)
        assertEquals(
            "the guest must widen the row by exactly the slot and its gap, " +
                "not by a mirror on the far side",
            slot().measuredWidth + slotMarginEnd(),
            guest.row.width - bareWidth
        )
    }

    // ---------- the measurement ----------

    private class RowRead(
        val row: ViewGroup,
        val left: View,
        val right: View,
        val timer: View,
        val airLeft: Float,
        val airRight: Float,
        val dp: Float
    )

    private fun layOut(extraDp: Float): RowRead {
        val root = field<View>("root")
        root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val density = service.resources.displayMetrics.density
        val width = root.measuredWidth + (extraDp * density).toInt()
        val height = root.measuredHeight
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, root.measuredHeight)

        val row = field<LinearLayout>("contentRow")
        val left = groupHolding(row, "numberView")
        val right = groupHolding(row, "statusRing")
        // The row's own padding, subtracted: what is measured here is the air
        // inside the row, which is where a trailing child hides.
        val airLeft = (left.left - row.paddingLeft) / density
        val airRight = (row.width - row.paddingRight - right.right) / density
        return RowRead(row, left, right, field<View>("numberView"), airLeft, airRight, density)
    }

    private fun assertBalanced(state: String, read: RowRead) {
        val gap = abs(read.airLeft - read.airRight)
        assertTrue(
            "$state: the row's air must be even, saw left=${fmt(read.airLeft)}dp " +
                "right=${fmt(read.airRight)}dp (gap ${fmt(gap)}dp, row ${read.row.width}px, " +
                "children ${read.row.childCount}, left group ${read.left.left}..${read.left.right}, " +
                "right group ${read.right.left}..${read.right.right}). A group that trails a " +
                "dead child pushes the whole reading off the centre line.",
            gap <= 1f + 0.5f / read.dp
        )
    }

    private fun fmt(value: Float) = (value * 10).toInt() / 10f

    // ---------- state and reflection ----------

    private fun open(guest: Boolean) {
        harness.setUp()
        opened = true
        PillFrameCapture::class.java
            .getDeclaredMethod("startService", ReminderKind::class.java)
            .apply { isAccessible = true }
            .invoke(harness, null as ReminderKind?)
        service = PillFrameCapture::class.java.getDeclaredField("service")
            .apply { isAccessible = true }
            .get(harness) as InfoPillService
        if (guest) {
            setMusicPlaying(active = true)
            invoke("syncSlot")
        }
        assertEquals(
            "the compact pill must be the thing under test",
            if (guest) View.VISIBLE else View.GONE,
            slot().visibility
        )
    }

    private fun setMusicPlaying(active: Boolean) = setField("musicPlaying", active)

    private fun slot(): View = field("waveSlot")

    private fun slotMarginEnd(): Int =
        (slot().layoutParams as LinearLayout.LayoutParams).marginEnd

    /** The row's child that carries [childField]'s view, found by walking up. */
    private fun groupHolding(row: ViewGroup, childField: String): View {
        val inner = field<View>(childField)
        var node: View = inner
        while (node.parent !== row) {
            node = node.parent as? View
                ?: throw AssertionError("$childField is not inside the compact row")
        }
        return node
    }

    /**
     * The service's own declared members, never the test's: the harness reaches
     * them the same way, and a private field of [InfoPillService] is not a
     * private field of this class.
     */
    private inline fun <reified T> field(name: String): T =
        service.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(service) as T

    private fun setField(name: String, value: Any?) {
        service.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private fun invoke(name: String, vararg args: Any?) {
        service.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(service, *args)
    }
}
