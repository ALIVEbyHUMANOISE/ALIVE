package com.projectego.app.service

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.data.ReminderSettings
import com.projectego.app.domain.PillHeaderText
import java.time.Duration
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.robolectric.shadows.ShadowSettings

/**
 * The island's other job: a reminder it was asked to show, not a reading.
 *
 * The important assertions are the ones a person sees: the reminder opens by
 * itself, the usage panel is not behind it, and the two answers are real
 * controls rather than decoration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PillReminderTest {

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
        ReminderSettings.disableWater(service)
        ReminderSettings.markWaterDrank(service, 0L)
        ReminderSettings.snoozeWater(service, 0L)
        ReminderSettings.setPromiseEnabled(service, true)
    }

    @After
    fun tearDown() {
        durationScale(originalDurationScale)
        controller.destroy()
        InfoPillService.stop(context)
    }

    @Test
    fun `a tap on a reminder never replaces its label with a stale reading`() {
        // A gate from an earlier pill may still hold a usage string. The
        // reminder has no digit roll, so the tap must leave its word alone.
        service.field<PillHeaderText>("headerText").offer("42 min")
        start(ReminderKind.DRINK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))
        val label = service.field<TextView>("numberView").text.toString()

        service.field<View>("header").performClick()

        assertEquals(
            "the reminder's label is not a timer reading",
            label,
            service.field<TextView>("numberView").text.toString()
        )
    }

    @Test
    fun `a drink reminder opens itself and offers two answers`() {
        start(ReminderKind.DRINK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))

        val reminder = service.field<View>("reminderPanel")
        val usage = service.field<View>("panel")
        assertEquals(View.VISIBLE, reminder.visibility)
        assertEquals("the usage panel has no meaning for a reminder", View.GONE, usage.visibility)
        assertEquals(View.VISIBLE, service.field<TextView>("reminderPrimary").visibility)
        assertEquals(View.VISIBLE, service.field<TextView>("reminderSecondary").visibility)
        assertEquals(context.getString(R.string.pill_drink_done), service.field<TextView>("reminderPrimary").text)
        assertEquals(context.getString(R.string.pill_drink_later), service.field<TextView>("reminderSecondary").text)
        assertEquals(
            "the card's kicker names the surface",
            context.getString(R.string.pill_reminder_kicker),
            service.field<TextView>("reminderKicker").text
        )
        val params = service.field<View>("pill").layoutParams as WindowManager.LayoutParams
        assertNotEquals(
            "the reminded island must actually be open, not merely marked expanded",
            WindowManager.LayoutParams.WRAP_CONTENT,
            params.width
        )
        val heightDp = params.height / context.resources.displayMetrics.density
        assertTrue(
            "the reminder must keep the normal expanded height: ${heightDp}dp",
            heightDp <= 140
        )
    }

    @Test
    fun `drank the glass records it and leaves the island`() {
        start(ReminderKind.DRINK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))

        service.field<TextView>("reminderPrimary").performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        assertTrue(
            "the glass is the fact the next reminder counts from",
            ReminderSettings.lastDrinkAt(service) > 0L
        )
        assertEquals("the island leaves after the answer", null, service.field<View>("pill"))
    }

    @Test
    fun `the promise reminder's action opens the promise, not a settings page`() {
        start(ReminderKind.PROMISE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))

        val primary = service.field<TextView>("reminderPrimary")
        assertEquals(context.getString(R.string.pill_promise_action), primary.text)
        assertEquals(context.getString(R.string.pill_promise_later), service.field<TextView>("reminderSecondary").text)
        primary.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .nextStartedActivity
        assertTrue("the tap must open ALIVE", started != null)
        assertTrue(
            "it must open the promise sheet, not Home",
            started?.getBooleanExtra(PromiseWatch.EXTRA_OPEN_PROMISE, false) == true
        )
    }

    @Test
    fun `an answer from the shade takes the island down at once`() {
        start(ReminderKind.DRINK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))

        context.sendBroadcast(
            Intent(InfoPillService.ACTION_REMINDER_ANSWERED)
                .setPackage(context.packageName)
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        assertEquals(
            "the island must not wait for the next 20s tick to stop asking",
            null,
            service.field<View>("pill")
        )
    }

    @Test
    fun `a reminder answered over a reading asks for the reading again`() {
        service.onStartCommand(
            Intent(service, InfoPillService::class.java)
                .putExtra(InfoPillService.EXTRA_PACKAGE, "com.google.android.youtube"),
            0,
            1
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))

        start(ReminderKind.DRINK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))
        service.field<TextView>("reminderPrimary").performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

        val restore = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .nextStartedService
        assertTrue("the interrupted reading must be asked for again", restore != null)
        assertEquals(
            "the focus or reading that the reminder covered comes back",
            "com.google.android.youtube",
            restore?.getStringExtra(InfoPillService.EXTRA_PACKAGE)
        )
    }

    @Test
    fun `the page's rehearsal raises the island exactly like the alarm`() {
        DrinkReminderWatch.preview(context)

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .nextStartedService
        assertTrue("a rehearsal must ask for the island", started != null)
        assertEquals(
            "and it must be the drink reminder",
            ReminderKind.DRINK.name,
            started?.getStringExtra(InfoPillService.EXTRA_REMINDER)
        )
    }

    @Test
    fun `an old snooze does not kill a fresh reminder in its first frame`() {
        // The person snoozed an earlier reminder; tapping "try now" must not
        // be read as that old snooze answering the new one.
        ReminderSettings.snoozeWater(service, System.currentTimeMillis() + 10 * 60_000L)
        start(ReminderKind.DRINK, preview = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))

        val reminder = service.field<View>("reminderPanel")
        assertEquals("the preview must stay on screen", View.VISIBLE, reminder.visibility)
    }

    private fun start(kind: ReminderKind, preview: Boolean = false) {
        service.onStartCommand(
            Intent(service, InfoPillService::class.java)
                .putExtra(InfoPillService.EXTRA_REMINDER, kind.name)
                .putExtra(InfoPillService.EXTRA_REMINDER_PREVIEW, preview),
            0,
            1
        )
    }

    private fun <T> InfoPillService.field(name: String): T {
        val field = InfoPillService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as T
    }

    private fun setField(name: String, value: Any) {
        InfoPillService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    private fun durationScale(): Float =
        android.animation.ValueAnimator::class.java.getDeclaredMethod("getDurationScale")
            .apply { isAccessible = true }.invoke(null) as Float

    private fun durationScale(scale: Float) {
        android.animation.ValueAnimator::class.java.getDeclaredMethod(
            "setDurationScale", Float::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(null, scale)
    }
}
