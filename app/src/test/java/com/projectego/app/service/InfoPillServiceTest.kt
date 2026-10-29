package com.projectego.app.service

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.domain.PillReportSchedule
import com.projectego.app.domain.UsageRead
import com.projectego.app.domain.UsageReport
import com.projectego.app.domain.UsageTracker
import java.time.Duration
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InfoPillServiceTest {
    private lateinit var controller: ServiceController<InfoPillService>
    private lateinit var service: InfoPillService
    private lateinit var executor: QueuedExecutor
    private var destroyed = false
    private val windows: List<View>
        get() = Shadow.extract<ShadowWindowManagerImpl>(
            service.getSystemService(Context.WINDOW_SERVICE)
        ).views

    @Before
    fun setUp() {
        controller = Robolectric.buildService(InfoPillService::class.java).create()
        service = controller.get()
        executor = QueuedExecutor()
        service.setField("reportExecutor", executor)
        LockStateManager(service).checkAndPerformDailyReset()
        PillSettings.setEnabled(service, true)
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        shadowOf(service.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            service.packageName,
            AppOpsManager.MODE_ALLOWED
        )
    }

    @After
    fun tearDown() {
        destroyService()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `repeated start cannot erase a queued report completion and wedge the schedule`() {
        start()
        executor.runNext()
        service.onStartCommand(request(), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(service.field<PillReportSchedule>("reportSchedule").isBusy)
        assertNotNull(service.field<Any?>("lastReport"))
    }

    @Test
    fun `screen off stops the service without waiting for a polling tick`() {
        start()
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `own activity starting immediately removes the overlay touch surface`() {
        start()
        val view = service.field<View>("pill")
        val activity = Robolectric.buildActivity(Activity::class.java).create().start()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(windows.contains(view))
        activity.stop().destroy()
    }

    @Test
    fun `destroy removes the window immediately rather than leaving an animated touch target`() {
        start()
        val view = service.field<View>("pill")
        destroyService()
        assertFalse(windows.contains(view))
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `a start while the screen is off never attaches an overlay`() {
        shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)
        service.onStartCommand(request(), 0, 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertNull(service.field<View?>("pill"))
    }

    @Test
    fun `an old report delivered after destruction cannot publish or restart work`() {
        start()
        destroyService()
        executor.runNext()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(service.field<Any?>("lastReport"))
        assertFalse(service.field<PillReportSchedule>("reportSchedule").isBusy)
    }

    @Test
    fun `a platform failure outside the query guard still releases the report slot`() {
        val failingContext = object : ContextWrapper(service) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.APP_OPS_SERVICE) throw IllegalStateException("platform unavailable")
                return super.getSystemService(name)
            }
        }
        service.setField("usageTracker\$delegate", lazy { UsageTracker(failingContext) })
        start()
        executor.runNext()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(service.field<PillReportSchedule>("reportSchedule").isBusy)
        assertNull(service.field<UsageReport?>("lastReport"))
    }

    @Test
    fun `a pill restarted after a screen-off can read the day again`() {
        start()
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        executor.runNext()
        shadowOf(Looper.getMainLooper()).idle()
        // Android has not destroyed the service yet: the restart lands on the same
        // instance, so a slot left busy by the dismissal would never read again.
        service.onStartCommand(request("com.instagram.android"), 0, 2)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        executor.runNext()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("the second reading must reach the pill", service.field<UsageReport?>("lastReport"))
    }

    @Test
    fun `a dismissed window cannot publish while Android is still scheduling destruction`() {
        start()
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        executor.runNext()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(service.field<UsageReport?>("lastReport"))
    }

    @Test
    fun `a report queued before midnight cannot become todays fresh report`() {
        start()
        val report = (UsageTracker(service).readTodayEntertainmentUsage() as UsageRead.Success).report
        val token = service.field<PillReportSchedule>("reportSchedule").field<Long>("inFlight")
        deliver(token, report, "2000-01-01", 10L)
        assertNull(service.field<UsageReport?>("lastReport"))
        assertEquals(0L, service.field<Long>("lastReportAtElapsed"))
    }

    @Test
    fun `delivery latency does not reset the age of a report`() {
        start()
        val report = (UsageTracker(service).readTodayEntertainmentUsage() as UsageRead.Success).report
        val token = service.field<PillReportSchedule>("reportSchedule").field<Long>("inFlight")
        deliver(token, report, LockStateManager.dayKeyOf(System.currentTimeMillis()), 10L)
        assertSame(report, service.field<UsageReport?>("lastReport"))
        assertEquals(10L, service.field<Long>("lastReportAtElapsed"))
    }

    private fun deliver(token: Long, report: UsageReport, day: String, readAt: Long) {
        service.javaClass.getDeclaredMethod(
            "applyReport", Long::class.javaPrimitiveType, UsageReport::class.java,
            String::class.java, Long::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, token, report, day, readAt)
    }

    private fun request(packageName: String = "com.google.android.youtube") =
        Intent(service, InfoPillService::class.java).putExtra(InfoPillService.EXTRA_PACKAGE, packageName)

    private fun start() {
        service.onStartCommand(request(), 0, 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertNotNull(service.field<View?>("pill"))
        assertTrue(windows.contains(service.field<View>("pill")))
    }

    private fun destroyService() {
        if (!destroyed) {
            destroyed = true
            controller.destroy()
        }
    }

    private inline fun <reified T> Any.field(name: String): T =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T

    private fun Any.setField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    private class QueuedExecutor : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() { tasks.removeFirst().run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> {
            stopped = true
            return mutableListOf()
        }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = stopped
    }
}
