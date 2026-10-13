package com.projectego.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.NightContractRepository
import com.projectego.app.domain.NightContract
import java.util.Calendar

/**
 * The one note ALIVE sends about the nightly promise.
 *
 * Exactly once a night, at nine, and only when nothing has been written yet -
 * the point is to catch the evening someone forgot, not to keep asking. If the
 * promise is already written, or if the person writes it before the alarm, the
 * note never appears: silence is the correct answer to a thing already done.
 *
 * The alarm is inexact on purpose. Being told at 21:07 instead of 21:00 costs
 * nothing, and the exact-alarm permission on Android 12+ is a permission this
 * app has no other reason to hold. It is armed whenever the app is opened, so a
 * phone that only runs ALIVE when the person looks at it still behaves.
 */
object PromiseWatch {

    /** Opens the promise sheet rather than Home. */
    const val EXTRA_OPEN_PROMISE = "com.projectego.app.extra.OPEN_PROMISE"

    private const val CHANNEL_ID = "alive_promise"
    private const val NOTIFICATION_ID = 7301
    private const val REQUEST_CODE = 7302

    fun arm(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.RTC,
            nextNine(),
            AlarmManager.INTERVAL_DAY,
            alarmIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(alarmIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun post(context: Context) {
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alive_hourglass)
                .setContentTitle(context.getString(R.string.promise_notif_title))
                .setContentText(context.getString(R.string.promise_notif_body))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.promise_notif_body))
                )
                .setContentIntent(openIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
        )
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.promise_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.promise_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun nextNine(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, NightContract.NIGHT_START_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PromiseWatchReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE + 1,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_PROMISE, true)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Decides at nine whether there is anything to say at all. */
class PromiseWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val writtenTonight = NightContractRepository(context).stored()?.nightKey ==
            NightContract.nightKeyOf(System.currentTimeMillis())
        if (writtenTonight) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        PromiseWatch.post(context)
    }
}
