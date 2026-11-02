package com.projectego.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.domain.PillAccent
import com.projectego.app.domain.PillTheme
import com.projectego.app.domain.UsageReport
import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import com.projectego.app.ui.PillEyesView
import com.projectego.app.ui.PillStatusRingView
import com.projectego.app.ui.PillWaveformView
import java.time.Duration
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

/**
 * The compact pill's one status ring, measured on the views the service builds.
 *
 * Exit criteria, not taste: exactly one ring exists, it is drawn as a circle
 * rather than a disc, the row is still a legal touch target, and the row's whole
 * content - the slot's group through the ring, `leftGroup..rightGroup` - sits on
 * the window's centre line in both compact and open states, whether or not the
 * slot has a guest. The dead space the island used to carry on its right is a
 * defect, not a mirror: nothing balances the slot from the far side any more, so
 * the slot's group and the ring are centred together and a morph cannot move them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillStatusRingTest {

    private lateinit var context: Context
    private lateinit var controller: ServiceController<InfoPillService>
    private lateinit var service: InfoPillService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildService(InfoPillService::class.java).create()
        service = controller.get()
        PillSettings.setEnabled(service, true)
        ShadowSettings.setCanDrawOverlays(true)
        service.onStartCommand(
            android.content.Intent(service, InfoPillService::class.java)
                .putExtra(InfoPillService.EXTRA_PACKAGE, "com.google.android.youtube"),
            0,
            1
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        val root = service.field<View>("root")
        root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test
    fun `the compact row is the owner's small island, not a 48dp bar`() {
        val header = service.field<View>("header")
        assertTrue(
            "header is ${header.height}px, expected the 38dp island",
            header.height in dp(38)..dp(44)
        )
    }

    @Test
    fun `the timer and the ring get equal air`() {
        val header = service.field<View>("header")
        assertEquals(
            "the timer's left edge and the ring's right edge must mirror",
            header.paddingLeft,
            header.paddingRight
        )
    }

    @Test
    fun `the row's content is placed by the layout, never by a translation`() {
        val header = service.field<View>("header")
        header.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_200))

        val row = service.field<View>("contentRow")
        assertEquals(
            "a translation in the reading row is a movement the shell cannot follow",
            0f,
            row.translationX,
            0f
        )
        assertContentCentredInRow("with the slot empty")
        setMusic(true)
        measureAndLayout(
            service.field<View>("root"),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        assertContentCentredInRow("with the ripple in the slot")
    }

    /** The row's whole content owns the row's centre line, in either mood. */
    private fun assertContentCentredInRow(state: String) {
        val row = service.field<View>("contentRow")
        assertEquals(
            "$state: the row's content must own the row's exact centre line",
            row.width / 2f,
            contentCentreInRow(),
            1f
        )
    }

    @Test
    fun `every frame of the opening keeps the reading on the centre line`() {
        setMusic(true)
        val root = service.field<View>("root")
        measureAndLayout(root, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val row = service.field<View>("contentRow")
        val slot = service.field<View>("waveSlot")

        service.javaClass.getDeclaredMethod("expand")
            .apply { isAccessible = true }
            .invoke(service)
        val animator = service.field<android.animation.ValueAnimator>("sizeAnimator")
        var elapsed = 0L
        while (elapsed <= animator.duration) {
            animator.currentPlayTime = elapsed
            val width = (service.field<View>("pill").layoutParams as android.view.WindowManager.LayoutParams).width
            measureAndLayout(
                root,
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            )
            assertEquals(
                "the row's content left the centre line at ${elapsed}ms",
                width / 2f,
                contentCentreInWindow(),
                1f
            )
            assertEquals("no glide at ${elapsed}ms", 0f, row.translationX, 0f)
            assertEquals("the slot stays at ${elapsed}ms", View.VISIBLE, slot.visibility)
            elapsed += 16L
        }
    }

    @Test
    fun `a digit roll lands before the morph starts`() {
        val number = service.field<TextView>("numberView")
        // The pose the roll wears while its ink is swapped. Set directly
        // because Robolectric's animation clock is not the subject here - the
        // rule is that a tap must clean this pose up before the shell moves.
        number.alpha = 0.35f
        number.translationY = 9f
        service.field<com.projectego.app.domain.PillHeaderText>("headerText").offer("99")

        service.field<View>("header").performClick()

        assertEquals("the roll must land on its newest reading", "99", number.text.toString())
        assertEquals("the digits must be opaque again", 1f, number.alpha, 0f)
        assertEquals("and back on the reading's baseline", 0f, number.translationY, 0f)
    }

    @Test
    fun `an idle pill keeps the slot out of the row and centres what is left`() {
        assertEquals(
            "no audio means the slot is not in the row at all",
            View.GONE,
            service.field<View>("waveSlot").visibility
        )
        // With the slot gone its group opens on the timer, so the same formula
        // measures the content: leftGroup.left is numberView.left.
        assertContentCentredInRow("with the slot gone")
        setMusic(true)
        measureAndLayout(
            service.field<View>("root"),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        assertEquals(
            "audio puts the slot in the row",
            View.VISIBLE,
            service.field<View>("waveSlot").visibility
        )
        assertContentCentredInRow("with the slot in the row")
    }

    @Test
    fun `opening the panel does not touch the reading row`() {
        val row = service.field<View>("contentRow")
        service.javaClass.getDeclaredMethod("expand")
            .apply { isAccessible = true }
            .invoke(service)

        assertEquals("the morph must not slide the reading", 0f, row.translationX, 0f)
        assertEquals(
            "the slot keeps the state the compact pill gave it",
            View.GONE,
            service.field<View>("waveSlot").visibility
        )
    }

    @Test
    fun `the tap itself keeps the row's content centred before the window grows`() {
        val root = service.field<View>("root")
        val compactWidth = root.width
        service.javaClass.getDeclaredMethod("expand")
            .apply { isAccessible = true }
            .invoke(service)

        // The first morph frame still has the compact window width. The header
        // must not have narrowed its row in the meantime: an overflow there is
        // laid out from the left and moves the content right until the window
        // catches up.
        measureAndLayout(
            root,
            View.MeasureSpec.makeMeasureSpec(compactWidth, View.MeasureSpec.EXACTLY)
        )
        assertEquals(
            "the row's content must not jump at the tap",
            compactWidth / 2f,
            contentCentreInWindow(),
            1f
        )
    }

    @Test
    fun `the row's content keeps one centre line through the morph, with audio playing`() {
        setMusic(true)
        val root = service.field<View>("root")
        measureAndLayout(root, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val compactWidth = root.width
        assertEquals(
            "the waveform's slot must join the row while audio plays",
            View.VISIBLE,
            service.field<View>("waveSlot").visibility
        )
        assertEquals(
            "the audio state still centres the row's content",
            compactWidth / 2f,
            contentCentreInWindow(),
            1f
        )

        service.javaClass.getDeclaredMethod("expand")
            .apply { isAccessible = true }
            .invoke(service)
        // The width the open window will hold. Measured without waiting on the
        // clock: the audio probe's next 2s tick would otherwise rewrite the
        // state under the assertion, and this test is about layout, not about
        // Robolectric's audio shadow.
        val expandedWidth = service.javaClass.getDeclaredMethod("panelWidthPx")
            .apply { isAccessible = true }
            .invoke(service) as Int
        measureAndLayout(root, View.MeasureSpec.makeMeasureSpec(expandedWidth, View.MeasureSpec.EXACTLY))

        assertEquals(
            "the panel must not remove the audio slot the compact pill reserved",
            View.VISIBLE,
            service.field<View>("waveSlot").visibility
        )
        assertEquals(
            "the panel keeps the slot but sends the ripple home",
            0f,
            service.field<PillWaveformView>("waveform").alpha,
            0f
        )
        assertEquals(
            "the open window must place the row's content on its own centre line",
            expandedWidth / 2f,
            contentCentreInWindow(),
            1f
        )
        // Both states are the top|centre_horizontal window's own centre line,
        // which is the screen centre in both: compact 214.5 and open 509.5 are
        // two widths of the same window, not two positions of the reading.
        assertEquals(0f, service.field<View>("contentRow").translationX, 0f)
    }

    @Test
    fun `opening the panel ends a visit instead of hiding the header`() {
        val eyes = service.field<PillEyesView>("eyesView")
        service.javaClass.getDeclaredMethod("playCharacterVisit")
            .apply { isAccessible = true }
            .invoke(service)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertTrue("the visit should be running", eyes.reveal > 0f)

        service.javaClass.getDeclaredMethod("expand")
            .apply { isAccessible = true }
            .invoke(service)

        val row = service.field<View>("contentRow")
        assertEquals("the reading row must be visible again", 1f, row.alpha, 0f)
        assertEquals("the visit must be over when the panel opens", 0f, eyes.reveal, 0f)
    }

    @Test
    fun `a swipe up waves the island away for a while`() {
        val root = service.field<View>("root")
        swipeUp(root, startY = 200f, endY = 10f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertTrue("the island should be hidden after a swipe up", EgoAccessibilityService.isPillHidden())
        EgoAccessibilityService.clearPillHidden()
    }

    @Test
    fun `the expanded panel waves away like the compact pill`() {
        val header = service.field<View>("header")
        header.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        val root = service.field<View>("root")
        // The finger starts on the panel's body, below the header, which is
        // the place the old listener could never hear.
        swipeUp(root, startY = 320f, endY = 90f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertTrue(
            "an open panel must answer the same wave-away as the compact island",
            EgoAccessibilityService.isPillHidden()
        )
        EgoAccessibilityService.clearPillHidden()
    }

    private fun swipeUp(view: View, startY: Float, endY: Float) {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 40f, startY, 0)
        val move = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_MOVE, 40f, (startY + endY) / 2f, 0)
        val up = MotionEvent.obtain(0L, 80L, MotionEvent.ACTION_UP, 40f, endY, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(move)
        view.dispatchTouchEvent(up)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `the expanded panel slides between three pages`() {
        val header = service.field<View>("header")
        header.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

        val pages = service.field<List<LinearLayout>>("panelPages")
        assertEquals("one page per surface: the day, water, the promise", 3, pages.size)

        val pager = service.field<View>("panelPager")
        assertEquals("the reading is the first page at rest", 0, pager.scrollX)

        val width = service.field<View>("panelPager").width
        assertTrue("the deck must have a width before it can slide", width > 0)
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, width - 20f, 80f, 0)
        val move = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_MOVE, 20f, 80f, 0)
        val up = MotionEvent.obtain(0L, 80L, MotionEvent.ACTION_UP, 20f, 80f, 0)
        pager.dispatchTouchEvent(down)
        pager.dispatchTouchEvent(move)
        pager.dispatchTouchEvent(up)
        down.recycle()
        move.recycle()
        up.recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

        assertTrue("a sideways drag must move the deck", pager.scrollX > 0)
        assertEquals(
            "the deck comes to rest on the water page",
            1,
            service.field<Int>("panelPageIndex")
        )
    }

    @Test
    fun `the chevrons step between pages and dim at the ends`() {
        val header = service.field<View>("header")
        header.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

        val prev = service.field<TextView>("panelPrev")
        val next = service.field<TextView>("panelNext")
        assertEquals("the first page has no page before it", 0.28f, prev.alpha, 0f)
        assertEquals("the first page has a page after it", 1f, next.alpha, 0f)

        val pager = service.field<View>("panelPager")
        val pageWidth = pager.width
        assertEquals("the deck starts on the reading", 0, pager.scrollX)
        next.performClick()
        assertEquals(
            "a page click must not teleport the deck",
            0,
            pager.scrollX
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertEquals("the weighted snap lands on the water page", pageWidth, pager.scrollX)
        assertEquals("one step lands on the water page", 1, service.field<Int>("panelPageIndex"))
        assertEquals("both ends have neighbours now", 1f, prev.alpha, 0f)

        next.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals("two steps land on the promise page", 2, service.field<Int>("panelPageIndex"))
        assertEquals(
            "the last page dims its forward chevron",
            0.28f,
            next.alpha,
            0f
        )
    }

    @Test
    fun `the pages keep the expanded shell at its old height`() {
        val header = service.field<View>("header")
        header.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_200))
        val params = service.field<View>("pill").layoutParams as android.view.WindowManager.LayoutParams
        val heightDp = params.height / context.resources.displayMetrics.density
        assertTrue(
            "the shell must not grow downward with its pages: ${heightDp}dp tall",
            heightDp <= 150
        )
    }

    @Test
    fun `the water and promise pages read the same stores as the app`() {
        val header = service.field<View>("header")
        header.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

        assertEquals(
            "water is off until the person asks for it",
            context.getString(R.string.pill_page_water_off_short),
            service.field<TextView>("panelDrinkLine").text
        )
        assertEquals(
            "nothing written yet is the promise page's honest start",
            context.getString(R.string.pill_page_promise_empty_short),
            service.field<TextView>("panelPromiseLine").text
        )
    }

    @Test
    fun `the wave-away window ends on its own`() {
        EgoAccessibilityService.hidePillFor(context, 120L)
        assertTrue(EgoAccessibilityService.isPillHidden())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertFalse("the island must come back when the window ends", EgoAccessibilityService.isPillHidden())
    }

    @Test
    fun `both light palettes are offered and the choice persists`() {
        PillSettings.setAccent(context, PillAccent.PINK)
        assertEquals(PillAccent.PINK, PillSettings.accent(context))
        PillSettings.setAccent(context, PillAccent.BLUE)
        assertEquals(PillAccent.BLUE, PillSettings.accent(context))
        assertNotEquals("the two lights must be different colours", PillAccent.BLUE.color, PillAccent.PINK.color)
        assertEquals("an absent preference is the reference blue", PillAccent.BLUE, PillAccent.of(null))
    }

    @Test
    fun `the reading row is vertically centred in the island`() {
        val header = service.field<View>("header")
        val row = service.field<View>("contentRow")
        val topGap = row.top
        val bottomGap = header.height - row.bottom
        assertTrue(
            "the row sits too high: top=$topGap bottom=$bottomGap",
            kotlin.math.abs(topGap - bottomGap) <= 2
        )
    }

    @Test
    fun `the pill carries exactly one status ring`() {
        val rings = views(PillStatusRingView::class.java)
        assertEquals("one ring, one meaning", 1, rings.size)
        assertEquals("the ring gets its own calm slot", dp(16), rings.first().width)
        assertEquals(dp(16), rings.first().height)
    }

    @Test
    fun `the ring is a circle, not a disc`() {
        val ring = views(PillStatusRingView::class.java).first()
        val bitmap = render(ring)
        val centre = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)

        // 16dp slot at any density: the outline sits 7dp from the centre.
        val onRing = maxAlphaAt(bitmap, 6.4f, 7.6f)
        val inside = maxAlphaAt(bitmap, 0f, 3f)

        assertEquals("the centre stays hollow", 0, centre ushr 24)
        assertEquals("the outline is opaque", 255, onRing)
        assertTrue("inside the ring is empty, not filled: $inside", inside < 40)
    }

    @Test
    fun `the eyes take the notch while the ripple keeps its slot`() {
        // The notch's character and the ripple are the two named layers; the
        // compact reminder's little face is a third, smaller guest and is not
        // the one that takes the notch.
        val eyes = service.field<PillEyesView>("eyesView")
        val wave = service.field<PillWaveformView>("waveform")
        assertTrue("the character must be larger than the ripple's slot", eyes.width > wave.width)
    }

    @Test
    fun `an idle pill with no audio shows neither guest`() {
        val eyes = service.field<PillEyesView>("eyesView")
        val wave = service.field<PillWaveformView>("waveform")
        assertEquals("the character is hidden between visits", 0f, eyes.reveal, 0f)
        // The ripple is a media indicator now, not decoration: with nothing
        // playing it must not animate or even show.
        assertEquals("no audio, no waveform", 0f, wave.alpha, 0f)
    }

    // ---------- the arc's direction ----------

    /**
     * The ring is a tank, not a meter: it starts full and drains as the day's
     * allowance is used. Every case below drives the real report through
     * `renderTraffic()` and reads what the service wrote to the view - never
     * `progress` itself, which would only echo the assertion back.
     */
    @Test
    fun `a day barely touched leaves the ring almost full`() {
        val remaining = arc(5L, 60L)
        assertEquals("5 of 60 minutes spent leaves 55/60 of the arc", 0.917f, remaining, 0.01f)
        assertNotEquals(
            "the arc counts what is left, not what is spent - 5/60 is the spent figure",
            0.083f, remaining, 0.01f
        )
    }

    @Test
    fun `a half spent day leaves the ring at half`() {
        // Direction-neutral by construction, kept as the midpoint the two other
        // cases are measured against.
        assertEquals(0.5f, arc(30L, 60L), 0.01f)
    }

    @Test
    fun `a spent allowance leaves an empty ring`() {
        assertEquals("nothing left means no arc", 0f, arc(60L, 60L), 0f)
    }

    @Test
    fun `going over the limit empties the ring instead of running it backwards`() {
        // The view clamps to 0..1 on its own, so this asserts the empty arc the
        // service must hand it; a negative arc is the source's business and is
        // what the coerceIn in ringProgress() is for.
        assertEquals(0f, arc(90L, 60L), 0f)
    }

    @Test
    fun `a day with no allowance reads as empty`() {
        assertEquals(
            "a zero-limit day has nothing to drain - a full ring would promise a " +
                "budget the day never had",
            0f,
            arc(0L, 0L),
            0f
        )
    }

    @Test
    fun `an unreadable day is full until the guard says it is spent`() {
        LockStateManager(service).setLocked(false)
        assertEquals("a day nobody measured still has its whole arc", 1f, arcNoReport(), 0f)
        assertEquals(
            "and it wears the unknown hue, not a status colour",
            PillTheme.RING_UNKNOWN_COLOR,
            service.field<PillStatusRingView>("statusRing").spec.color
        )

        LockStateManager(service).setLocked(true)
        assertEquals(
            "the guard knows the day is over even while the report cannot be read",
            0f,
            arcNoReport(),
            0f
        )
    }

    /** Drives one day through the service and returns the arc the ring was given. */
    private fun arc(totalMinutes: Long, limitMinutes: Long): Float {
        setField("lastReport", report(totalMinutes, limitMinutes))
        renderTraffic()
        return service.field<PillStatusRingView>("statusRing").progress
    }

    private fun arcNoReport(): Float {
        setField("lastReport", null)
        renderTraffic()
        return service.field<PillStatusRingView>("statusRing").progress
    }

    private fun renderTraffic() {
        service.javaClass.getDeclaredMethod("renderTraffic")
            .apply { isAccessible = true }
            .invoke(service)
    }

    private fun setField(name: String, value: Any?) {
        service.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private fun report(totalMinutes: Long, limitMinutes: Long) = UsageReport(
        totalEntertainmentMinutes = totalMinutes,
        totalEntertainmentMillis = totalMinutes * 60_000L,
        unblockedMinutes = 0L,
        totalScreenMinutes = totalMinutes,
        limitMinutes = limitMinutes,
        isLimitExceeded = totalMinutes > limitMinutes,
        remainingMinutes = (limitMinutes - totalMinutes).coerceAtLeast(0L),
        criticalZoneTier = 0,
        timeStatus = TimeStatus.ACTIVE,
        dayType = DayType.NORMAL_DAY,
        currentStreak = 0,
        timeBank = TimeBank(earnedMinutes = 0L, spentMinutes = 0L),
        entertainmentAppDetails = listOf("com.google.android.youtube" to totalMinutes),
        unblockedAppDetails = emptyList()
    )

    /**
     * The centre of everything the row actually holds: `leftGroup.left` to
     * `rightGroup.right`. Not the timer and the ring alone - the air that used
     * to trail the ring was the defect these tests exist to keep out.
     */
    private fun contentCentreInRow(): Float {
        val row = service.field<LinearLayout>("contentRow")
        val left = groupHolding("numberView", row)
        val right = groupHolding("statusRing", row)
        return (leftInRow(left, row) + rightInRow(right, row)) / 2f
    }

    /**
     * The row's own child that carries [name]'s view. Found by role rather than
     * by index, so a dead child trailing the ring is still counted as air on the
     * right instead of being mistaken for the row's last word.
     */
    private fun groupHolding(name: String, row: View): View {
        var node = service.field<View>(name)
        while (node.parent !== row) {
            node = node.parent as? View
                ?: throw AssertionError("$name is not inside the compact row")
        }
        return node
    }

    /** [view]'s left edge in the reading row's coordinates. */
    private fun leftInRow(view: View, row: View): Float {
        var x = view.left.toFloat()
        var parent = view.parent as? View
        while (parent != null && parent !== row) {
            x += parent.left
            parent = parent.parent as? View
        }
        return x
    }

    /** [view]'s right edge in the reading row's coordinates. */
    private fun rightInRow(view: View, row: View): Float {
        var x = view.right.toFloat()
        var parent = view.parent as? View
        while (parent != null && parent !== row) {
            x += parent.left
            parent = parent.parent as? View
        }
        return x
    }

    /** The same centre, in the window's coordinates (the row is not translated). */
    private fun contentCentreInWindow(): Float {
        val header = service.field<View>("header")
        val row = service.field<LinearLayout>("contentRow")
        return header.left + row.left + contentCentreInRow()
    }

    private fun measureAndLayout(root: View, widthSpec: Int) {
        root.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun setMusic(active: Boolean) {
        service.javaClass
            .getDeclaredMethod("setMusicActive", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(service, active)
    }

    private fun <T : View> views(
        type: Class<T>,
        root: View = service.field("root")
    ): List<T> {
        val found = mutableListOf<T>()
        fun walk(view: View) {
            if (type.isInstance(view)) found += type.cast(view) as T
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return found
    }

    private fun render(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun maxAlphaAt(bitmap: Bitmap, fromDp: Float, toDp: Float): Int {
        val density = context.resources.displayMetrics.density
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        var max = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val distance = hypot(x + 0.5f - cx, y + 0.5f - cy) / density
                if (distance in fromDp..toDp) {
                    max = maxOf(max, bitmap.getPixel(x, y) ushr 24)
                }
            }
        }
        return max
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private inline fun <reified T> Any.field(name: String): T =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
}
