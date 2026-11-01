package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.domain.DrinkReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two reminders' memory. Scheduling is [DrinkReminderPolicy]'s job; this
 * pins what the stores mean, especially the anchor that keeps a switch from
 * firing on the finger that turned it on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ReminderSettings.disableWater(context)
        ReminderSettings.markWaterDrank(context, 0L)
        ReminderSettings.snoozeWater(context, 0L)
        ReminderSettings.setWaterSchedule(
            context,
            DrinkReminderPolicy.DEFAULT_INTERVAL_MINUTES,
            DrinkReminderPolicy.DEFAULT_START_HOUR,
            DrinkReminderPolicy.DEFAULT_END_HOUR
        )
        ReminderSettings.setPromiseEnabled(context, true)
    }

    @Test
    fun `both reminders start off safe`() {
        assertFalse("water is opt-in", ReminderSettings.waterEnabled(context))
        assertTrue("the promise note already existed and stays on", ReminderSettings.promiseEnabled(context))
    }

    @Test
    fun `turning water on anchors the next reminder to now`() {
        val now = System.currentTimeMillis()
        ReminderSettings.enableWater(context, now)
        assertEquals(now, ReminderSettings.lastDrinkAt(context))
        assertEquals(0L, ReminderSettings.lastFiredAt(context))
        assertTrue(ReminderSettings.nextWaterDue(context, now) > now)
    }

    @Test
    fun `a schedule survives the next read`() {
        ReminderSettings.setWaterSchedule(context, 90, 8, 22)
        assertEquals(90, ReminderSettings.intervalMinutes(context))
        assertEquals(8, ReminderSettings.startHour(context))
        assertEquals(22, ReminderSettings.endHour(context))
    }

    @Test
    fun `a glass moves the anchor and clears the snooze`() {
        ReminderSettings.enableWater(context, System.currentTimeMillis() - 60_000L)
        ReminderSettings.snoozeWater(context, System.currentTimeMillis() + 60_000L)
        val now = System.currentTimeMillis()
        ReminderSettings.markWaterDrank(context, now)
        assertEquals(now, ReminderSettings.lastDrinkAt(context))
        assertEquals(0L, ReminderSettings.snoozeUntil(context))
        assertEquals(0L, ReminderSettings.lastFiredAt(context))
    }

    @Test
    fun `off means no next due at all`() {
        ReminderSettings.enableWater(context, System.currentTimeMillis())
        ReminderSettings.disableWater(context)
        assertEquals(0L, ReminderSettings.nextWaterDue(context, System.currentTimeMillis()))
    }
}
