package com.projectego.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.domain.UsageReport
import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import java.io.File
import java.time.Duration
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

/** Evidence generator: draws the real pill hierarchy to PNG frames. Not a behavioural test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillFrameCapture {

    private lateinit var service: InfoPillService
    private lateinit var controller: ServiceController<InfoPillService>
    private var originalScale = 1f

    @Before
    fun setUp() {
        originalScale = scale()
        scale(0f)
    }

    @After
    fun tearDown() {
        scale(originalScale)
        runCatching { controller.destroy() }
    }

    @Test
    fun capture() {
        val out = File("build/living-render").apply { mkdirs() }

        captureState(out, "1-compact-normal.png", normal(used = 30))
        captureState(out, "2-compact-approaching.png", approaching())
        captureState(out, "3-compact-limit.png", limit())
        captureState(out, "4-expanded-normal.png", normal(used = 42), expand = true)
        captureState(out, "5-expanded-approaching.png", approaching(), expand = true)
        captureState(out, "6-expanded-limit.png", limit(), expand = true)
    }

    private fun captureState(out: File, name: String, report: UsageReport, expand: Boolean = false) {
        startService()
        service.setField("lastReport", report)
        service.setField("lastReportDay", LockStateManager.dayKeyOf(System.currentTimeMillis()))
        service.setField("lastReportAtElapsed", SystemClock.elapsedRealtime())
        service.setField(
            "startedAtElapsed",
            SystemClock.elapsedRealtime() - report.totalEntertainmentMinutes * 60_000L
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(21_000))
        service.invoke("renderTraffic")
        service.invoke("writeHeaderText")
        if (expand) {
            service.field<View>("header").performClick()
            service.invoke("renderPanel", report, false)
            service.invoke("resizePanelToContent")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        }
        val root = service.field<View>("root")
        val params = root.layoutParams as WindowManager.LayoutParams
        val density = service.resources.displayMetrics.density
        val width = if (params.width > 0) params.width else 0
        val height = if (params.height > 0) params.height else 0
        if (width > 0 && height > 0) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, width, height)
        } else {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        org.junit.Assert.assertEquals(
            "the island's ground must be opaque black in $name",
            android.graphics.Color.BLACK,
            bitmap.getPixel(bitmap.width / 2, bitmap.height - 3)
        )
        val file = File(out, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        org.junit.Assert.assertTrue("$name was not written", file.length() > 1024)
        println("CAPTURE $name ${root.width}x${root.height} density=$density")
        controller.destroy()
    }

    private fun startService() {
        PillSettings.setEnabled(ApplicationProvider.getApplicationContext(), true)
        ShadowSettings.setCanDrawOverlays(true)
        controller = Robolectric.buildService(InfoPillService::class.java).create()
        service = controller.get()
        // A real read would overwrite the injected day mid-capture.
        service.setField("reportExecutor", object : java.util.concurrent.AbstractExecutorService() {
            override fun execute(command: Runnable) = Unit
            override fun shutdown() = Unit
            override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: java.util.concurrent.TimeUnit) = false
        })
        LockStateManager(service).checkAndPerformDailyReset()
        service.onStartCommand(
            Intent(service, InfoPillService::class.java)
                .putExtra(InfoPillService.EXTRA_PACKAGE, "com.google.android.youtube"),
            0,
            1
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
    }

    private fun report(
        used: Long, limit: Long, tier: Int, status: TimeStatus, exceeded: Boolean,
        dayType: DayType = DayType.NORMAL_DAY
    ) = UsageReport(
        totalEntertainmentMinutes = used,
        totalEntertainmentMillis = used * 60_000L,
        unblockedMinutes = 18L,
        totalScreenMinutes = used + 18L,
        limitMinutes = limit,
        isLimitExceeded = exceeded,
        remainingMinutes = (limit - used).coerceAtLeast(0L),
        criticalZoneTier = tier,
        timeStatus = status,
        dayType = dayType,
        currentStreak = 4,
        timeBank = TimeBank(earnedMinutes = 30L, spentMinutes = 0L),
        entertainmentAppDetails = listOf(
            "com.google.android.youtube" to (used * 2 / 3),
            "com.zhiliaoapp.musically" to (used / 4),
            "com.instagram.android" to (used / 12)
        ),
        unblockedAppDetails = listOf("com.whatsapp" to 18L)
    )

    private fun normal(used: Long) =
        report(used, 120L, 0, TimeStatus.ACTIVE, exceeded = false)

    private fun approaching() =
        report(100L, 120L, 2, TimeStatus.CRITICAL, exceeded = false)

    private fun limit() =
        report(120L, 120L, 5, TimeStatus.LOCKED, exceeded = true)

    private fun scale(): Float =
        ValueAnimator::class.java.getDeclaredMethod("getDurationScale")
            .apply { isAccessible = true }.invoke(null) as Float

    private fun scale(value: Float) {
        ValueAnimator::class.java.getDeclaredMethod(
            "setDurationScale", Float::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(null, value)
    }

    private inline fun <reified T> Any.field(name: String): T =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T

    private fun Any.setField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    private fun Any.invoke(name: String, vararg args: Any?) {
        javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(this, *args)
    }
}
