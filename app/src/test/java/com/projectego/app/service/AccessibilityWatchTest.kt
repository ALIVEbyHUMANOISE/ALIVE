package com.projectego.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.ui.PermissionChecks
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The guard's one notification, and the two promises it makes.
 *
 * First, that it is not a notification anyone can let slide off the shade
 * without noticing: it is posted ongoing, with no auto-cancel.
 *
 * Second, and the one that matters more, that it is never wrong. The nag is
 * driven by one question - is the service actually on - and every trigger,
 * including the repeating alarm that only exists because the service was off
 * fifteen minutes ago, has to ask it again before it says anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccessibilityWatchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val notifications: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Writes the setting [PermissionChecks] reads, the way Android stores it. */
    private fun setServiceEnabled(enabled: Boolean) {
        val component = "${context.packageName}/${EgoAccessibilityService::class.java.canonicalName}"
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            if (enabled) component else ""
        )
    }

    private fun syncWithTheSetting() {
        AccessibilityWatch.sync(context, PermissionChecks.isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `the nag is ongoing, and it disappears the moment the guard is on`() {
        setServiceEnabled(false)
        syncWithTheSetting()

        val posted = shadowOf(notifications).getNotification(AccessibilityWatch.NOTIFICATION_ID)
        assertNotNull("the guard is off, so it must say so", posted)
        assertTrue(
            "a notification that can be swiped is a nag that gets swiped",
            posted!!.flags and Notification.FLAG_ONGOING_EVENT != 0
        )
        assertTrue(
            "a nag that dismisses itself is not doing its job",
            posted.flags and Notification.FLAG_AUTO_CANCEL == 0
        )

        setServiceEnabled(true)
        syncWithTheSetting()
        assertNull(
            "the guard is back, so the nag has nothing left to say",
            shadowOf(notifications).getNotification(AccessibilityWatch.NOTIFICATION_ID)
        )
    }

    @Test
    fun `the alarm re-asks the question instead of trusting why it was set`() {
        // Set while the guard was off, delivered while it is on: the alarm must
        // not manage to post anything.
        setServiceEnabled(true)
        AccessibilityWatchReceiver().onReceive(context, Intent(AccessibilityWatch.ACTION_CHECK))
        assertNull(shadowOf(notifications).getNotification(AccessibilityWatch.NOTIFICATION_ID))

        // And the other direction: it does post, from outside the app.
        setServiceEnabled(false)
        AccessibilityWatchReceiver().onReceive(context, Intent(AccessibilityWatch.ACTION_CHECK))
        assertNotNull(shadowOf(notifications).getNotification(AccessibilityWatch.NOTIFICATION_ID))
    }

    @Test
    fun `the repeat runs while the guard is off and is called off when it returns`() {
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

        setServiceEnabled(false)
        syncWithTheSetting()
        assertTrue("nothing would bring the nag back", alarms.scheduledAlarms.isNotEmpty())

        setServiceEnabled(true)
        syncWithTheSetting()
        assertTrue("the repeat outlived the guard it was nagging about", alarms.scheduledAlarms.isEmpty())
    }
}
