package com.projectego.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.LockStateManager
import com.projectego.app.ui.Guardian

/**
 * The one notification ALIVE posts, and the only reason it has a notification
 * channel at all: to keep saying that the guard is off until it is on again.
 *
 * What is true about it, and what is not. It is an ongoing notification with no
 * auto-cancel, so on Android 12 and below it cannot be swiped away at all. From
 * Android 13 the platform hands the swipe back to the user and no app can take
 * it away, so the nag is a *repeat* rather than a lock: an inexact alarm re-posts
 * it while the service is still off, and opening ALIVE re-posts it too. It stops
 * the moment the service is back on, from either path. ALIVE never claims to be
 * un-removable, because on this platform that would be a lie.
 *
 * The channel is high importance so the note is seen rather than badged, and the
 * tap goes to the screen where the missing switch can be turned on.
 */
object AccessibilityWatch {

    const val CHANNEL_ID = "alive_guard"
    const val NOTIFICATION_ID = 41

    /** The receiver's action: one entry point, so the decision is made in one place. */
    const val ACTION_CHECK = "com.projectego.app.action.GUARD_CHECK"

    /** How often the nag comes back while the guard stays off. */
    private const val REPEAT_MILLIS = 15 * 60 * 1000L

    /**
     * How long to wait before deciding, when the only signal is the service
     * unbinding. Android updates the secure setting as it unbinds, and a check
     * that ran in the same instant could read the old value and nag someone whose
     * service was never off.
     */
    private const val SETTLE_MILLIS = 3_000L

    private const val REQUEST_CODE = 41
    private const val REQUEST_REPEAT = 42

    /** Called with the answer to "is the service on", from the places that know it. */
    fun sync(context: Context, enabled: Boolean) {
        if (enabled) {
            clear(context)
        } else {
            post(context)
            scheduleRepeat(context)
        }
    }

    /**
     * The service just unbound. That is either the user switching the guard off
     * or the system re-binding it, and the two look identical from here - so this
     * asks the receiver to look at the setting in a moment rather than deciding
     * on the spot.
     */
    fun verifySoon(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + SETTLE_MILLIS,
            checkIntent(context)
        )
    }

    fun post(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.guardian_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.guardian_channel_description)
            }
        )

        // Guardian speaks here in the pose its state calls for, and the state is
        // decided by the same facts the app shows: protection off, and whether a
        // day over the limit already went unguarded. Read, never written - this
        // path must not be able to roll a day over or touch the lock.
        val today = LockStateManager.dayKeyOf(System.currentTimeMillis())
        val state = Guardian.stateOf(
            protectionOff = true,
            gapOpen = LockStateManager(context).isGuardGapOpenOn(today),
            repairedToday = false
        )
        val headline = context.getString(Guardian.headlineFor(state))
        val body = context.getString(Guardian.bodyFor(state))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alive_hourglass)
            .setLargeIcon(Guardian.bitmap(context, state))
            .setContentTitle(headline)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .addAction(
                0,
                context.getString(
                    Guardian.actionFor(state) ?: R.string.guardian_action_restore
                ),
                settingsIntent(context)
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /** The guard is back: the nag has nothing left to say. */
    fun clear(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(checkIntent(context))
    }

    fun scheduleRepeat(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + REPEAT_MILLIS,
            REPEAT_MILLIS,
            checkIntent(context)
        )
    }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REPEAT,
        Intent(context, AccessibilityWatchReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun settingsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE + 1,
        Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
