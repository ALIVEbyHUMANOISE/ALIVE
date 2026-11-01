package com.projectego.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import java.time.Duration
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The expanded island: a panel, not a page.
 *
 * Pinned here: the panel's content is laid out once at its destination width
 * and clipped by the growing shell (no per-frame reflow while the morph runs),
 * the island never widens past its budget, and a finished transition leaves no
 * animator behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PillPanelTest {

    private lateinit var context: Context
    private lateinit var controller: ServiceController<InfoPillService>
    private lateinit var service: InfoPillService
    private var originalDurationScale = 1f

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalDurationScale = durationScale()
        durationScale(1f)
        controller = Robolectric.buildService(InfoPillService::class.java).create()
        service = controller.get()
        // A real read would publish during the idles below and resize the panel
        // underneath the assertions.
        setField("reportExecutor", object : AbstractExecutorService() {
            override fun execute(command: Runnable) = Unit
            override fun shutdown() = Unit
            override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        })
        LockStateManager(service).checkAndPerformDailyReset()
        PillSettings.setEnabled(service, true)
        ShadowSettings.setCanDrawOverlays(true)
        service.onStartCommand(
            Intent(service, InfoPillService::class.java)
                .putExtra(InfoPillService.EXTRA_PACKAGE, "com.google.android.youtube"),
            0,
            1
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
    }

    @After
    fun tearDown() {
        durationScale(originalDurationScale)
        controller.destroy()
    }

    @Test
    fun `the panel keeps its destination width while the shell grows`() {
        val panel = service.field<View>("panel")
        header().performClick()
        assertEquals(
            "the content must be laid out once, not reflowed every frame",
            panelWidth(),
            panel.layoutParams.width
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(
            "an open panel stays pinned at the window's width, so a refresh cannot re-wrap it",
            panelWidth(),
            panel.layoutParams.width
        )
    }

    @Test
    fun `the island never widens past its budget`() {
        durationScale(0f)
        header().performClick()
        val windowWidth = (pill().layoutParams as WindowManager.LayoutParams).width
        assertTrue(
            "the panel is ${windowWidth / context.resources.displayMetrics.density}dp wide",
            windowWidth <= dp(340)
        )
    }

    @Test
    fun `a settled collapse is compact again and leaves no animator behind`() {
        durationScale(0f)
        val panel = service.field<View>("panel")
        header().performClick()
        header().performClick()
        val params = pill().layoutParams as WindowManager.LayoutParams
        assertEquals(View.GONE, panel.visibility)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width)
        assertEquals(
            "the closed island hugs its reading again",
            WindowManager.LayoutParams.MATCH_PARENT,
            panel.layoutParams.width
        )
        assertEquals(null, service.field<ValueAnimator?>("sizeAnimator"))
    }

    @Test
    fun `rapid taps end in the state of the last tap with one settled shell`() {
        val panel = service.field<View>("panel")
        repeat(5) {
            header().performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(24))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
        assertEquals("five taps leave the panel open", View.VISIBLE, panel.visibility)
        assertEquals(null, service.field<ValueAnimator?>("sizeAnimator"))
        assertEquals(
            "the settled panel sits at its destination width",
            panelWidth(),
            panel.layoutParams.width
        )
    }

    @Test
    fun `the panel carries no second character`() {
        // Direction A: the character belongs to the notch. A 64dp copy inside
        // the panel made the expanded state read as a second app.
        assertEquals(null, service.field<ImageView?>("panelCharacter"))
    }

    @Test
    fun `the panel is warmed while the pill idles, without showing itself`() {
        val panel = service.field<View>("panel")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_400))
        assertEquals("the warm-up must not reveal the panel", View.GONE, panel.visibility)
        assertTrue(
            "the panel must be measured before the first tap, was ${panel.measuredHeight}px",
            panel.measuredHeight > 0
        )
    }

    private fun header(): View = service.field("header")

    private fun pill(): View = service.field("pill")

    private fun panelWidth(): Int = service.run {
        javaClass.getDeclaredMethod("panelWidthPx").apply { isAccessible = true }
            .invoke(this) as Int
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun setField(name: String, value: Any) {
        service.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private fun durationScale(): Float =
        ValueAnimator::class.java.getDeclaredMethod("getDurationScale")
            .apply { isAccessible = true }.invoke(null) as Float

    private fun durationScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod(
            "setDurationScale", Float::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(null, scale)
    }

    private inline fun <reified T> Any.field(name: String): T =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
}
