package com.projectego.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.PillSettings
import java.time.Duration
import kotlin.math.hypot
import org.junit.Assert.assertEquals
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
 * The compact pill's three lights, measured on the views the service builds.
 *
 * Exit criteria, not taste: every light stays at full colour, each carries a
 * halo that reads as lit, and the compact row is a legal touch target with
 * symmetric air on both sides of the reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillLightsTest {

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
    fun `the compact row is a 48dp touch target`() {
        val header = service.field<View>("header")
        assertTrue(
            "header is ${header.height}px, below the 48dp minimum",
            header.height >= dp(48)
        )
    }

    @Test
    fun `the timer and the lights get equal air`() {
        val header = service.field<View>("header")
        assertEquals(
            "the reading's left edge and the lights' right edge must mirror",
            header.paddingLeft,
            header.paddingRight
        )
    }

    @Test
    fun `every light is lit, not matte`() {
        lights().forEach { dot ->
            assertEquals("a light gets room for its halo, not a 2dp ring", dp(18), dot.width)
            val bitmap = render(dot)
            val core = bitmap.getPixel(dot.width / 2, dot.height / 2)
            assertEquals("the core must be drawn at full alpha", 255, core ushr 24)
            assertEquals(
                "the core must be the light's own colour",
                dot.coreColor and 0x00FFFFFF,
                core and 0x00FFFFFF
            )
            // The core is 10dp across, so everything past 5dp is halo. Sampling in
            // dp keeps the band meaning the same thing at any density.
            val inside = ringAlpha(bitmap, 4.0f, 4.9f)
            val outside = ringAlpha(bitmap, 5.6f, 6.4f)
            assertEquals("the disc out to the core's edge is solid", 255, inside)
            assertTrue(
                "past the core the light must glow, not stop: $outside/255",
                outside in 30 until 255
            )
        }
    }

    @Test
    fun `a dimmed pulse never dims the core`() {
        val red = lights().first()
        val full = ringAlpha(render(red), 5.6f, 6.4f)
        red.glowFraction = 0.35f
        val dimmed = render(red)
        val pulsing = ringAlpha(dimmed, 5.6f, 6.4f)
        assertTrue("the pulse must move the halo: $full -> $pulsing", pulsing < full)
        assertEquals(
            "the core is full colour through the whole pulse",
            255,
            dimmed.getPixel(red.width / 2, red.height / 2) ushr 24
        )
    }

    private fun lights(): List<TrafficLightDot> {
        val found = mutableListOf<TrafficLightDot>()
        fun walk(view: View) {
            if (view is TrafficLightDot) found += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(service.field("root"))
        assertEquals("the pill carries exactly three lights", 3, found.size)
        return found
    }

    private fun render(dot: View): Bitmap {
        val bitmap = Bitmap.createBitmap(dot.width, dot.height, Bitmap.Config.ARGB_8888)
        dot.draw(Canvas(bitmap))
        return bitmap
    }

    private fun ringAlpha(bitmap: Bitmap, fromDp: Float, toDp: Float): Int {
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
