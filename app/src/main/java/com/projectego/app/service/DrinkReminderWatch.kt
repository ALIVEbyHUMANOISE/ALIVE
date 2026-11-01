package com.projectego.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.ReminderSettings
import com.projectego.app.domain.DrinkReminderPolicy

/**
 * The water reminder's clock, its note, and its two answers.
 *
 * It is deliberately not a running service. The Android answer to "remind me
 * later" is an alarm and a notification, both of which survive the app being
 * killed; a resident timer would be one more thing holding the phone awake.
 *
 * The island is a second surface, not the only one: [InfoPillService] is asked
 * to rise, but a platform that refuses a background foreground-service start
 * must still leave the person with a notification. That is why the note is
 * posted first and the pill second.
 */
object DrinkReminderWatch {

    const val ACTION_FIRE = "com.projectego.app.action.DRINK_REMINDER"
    const val ACTION_DRANK = "com.projectego.app.action.DRINK_DONE"
    const val ACTION_SNOOZE = "com.projectego.app.action.DRINK_LATER"

    private const val CHANNEL_ID = "alive_water_v2"
    private const val NOTIFICATION_ID = 7501
    private const val REQUEST_FIRE = 7502
    private const val REQUEST_DRANK = 7503
    private const val REQUEST_SNOOZE = 7504
    private const val REQUEST_OPEN = 7505

    /**
     * Puts the next alarm on the book.
     *
     * Inexact and allowed while idle: a glass of water does not need the
     * exact-alarm permission, and being asked at 14:07 instead of 14:00 costs
     * nothing. Re-arming is idempotent, so every entry point may call it.
     */
    fun arm(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!ReminderSettings.waterEnabled(context)) {
            alarm.cancel(fireIntent(context))
            return
        }
        val now = System.currentTimeMillis()
        val due = ReminderSettings.nextWaterDue(context, now)
        if (due <= 0L) return
        runCatching {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, fireIntent(context))
        }
    }

    /** The switch going off: no next alarm, and no note left behind. */
    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(fireIntent(context))
        clearNote(context)
    }

    fun clearNote(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * The alarm went off. Outside the active window it is a clock correction,
     * not a reason to speak; inside it, the note and the island are raised and
     * the next alarm is put on the book before anything can throw.
     */
    fun fire(context: Context) {
        if (!ReminderSettings.waterEnabled(context)) return
        val now = System.currentTimeMillis()
        if (!DrinkReminderPolicy.isWithinWindow(
                now,
                ReminderSettings.startHour(context),
                ReminderSettings.endHour(context)
            )
        ) {
            arm(context)
            return
        }
        ReminderSettings.markWaterFired(context, now)
        playChime(context)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            post(context)
        }
        InfoPillService.startForReminder(context, ReminderKind.DRINK)
        arm(context)
    }

    fun markDrank(context: Context) {
        ReminderSettings.markWaterDrank(context, System.currentTimeMillis())
        clearNote(context)
        arm(context)
    }

    /**
     * Shows the reminder now, because a person asked to see it.
     *
     * The schedule is left alone: this is a rehearsal, not a drink. The note
     * and the island are raised exactly as the alarm raises them, and the
     * island's own answers (drank, later) still do their real jobs.
     */
    fun preview(context: Context) {
        playChime(context)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            post(context)
        }
        InfoPillService.startForReminder(context, ReminderKind.DRINK, preview = true)
    }

    /**
     * The reminder's own voice: three short rising beeps on the alarm stream.
     *
     * Alarm, not notification: a water reminder that only whispers through a
     * notification volume someone turned down is not a reminder. The stream is
     * also the one Do Not Disturb usually leaves alone. The shade's note is
     * deliberately silent - one sound, from one place, so it never doubles.
     */
    private fun playChime(context: Context) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            val handler = Handler(Looper.getMainLooper())
            // Long enough to be an alarm, short enough not to become noise:
            // five rising beeps over about two seconds.
            listOf(0L to 170, 380L to 170, 760L to 170, 1140L to 170, 1520L to 340)
                .forEach { (delay, millis) ->
                    handler.postDelayed(
                        { runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, millis) } },
                        delay
                    )
                }
            handler.postDelayed({ runCatching { tone.release() } }, 2200L)
        }
    }

    fun snooze(context: Context) {
        val until = System.currentTimeMillis() +
            DrinkReminderPolicy.SNOOZE_MINUTES * 60_000L
        ReminderSettings.snoozeWater(context, until)
        clearNote(context)
        arm(context)
    }

    private fun post(context: Context) {
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alive_hourglass)
                .setContentTitle(context.getString(R.string.drink_notif_title))
                .setContentText(context.getString(R.string.drink_notif_body))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.drink_notif_body))
                )
                .setContentIntent(openIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(
                    R.drawable.ic_alive_hourglass,
                    context.getString(R.string.drink_action_done),
                    actionIntent(context, ACTION_DRANK, REQUEST_DRANK)
                )
                .addAction(
                    R.drawable.ic_alive_hourglass,
                    context.getString(R.string.drink_action_later),
                    actionIntent(context, ACTION_SNOOZE, REQUEST_SNOOZE)
                )
                .build()
        )
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.drink_channel_name),
                // Default, not high: a heads-up would sit on top of the island
                // that is the actual reminder. Silent, too: the reminder's
                // chime is played by the app itself (one sound, one source),
                // and the note here is only the fallback with its two buttons.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.drink_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun fireIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_FIRE,
        Intent(context, DrinkReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, DrinkReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** One receiver for the alarm and both answers, because they share one clock. */
class DrinkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            DrinkReminderWatch.ACTION_FIRE -> DrinkReminderWatch.fire(context)
            DrinkReminderWatch.ACTION_DRANK -> {
                DrinkReminderWatch.markDrank(context)
                tellTheIsland(context)
            }
            DrinkReminderWatch.ACTION_SNOOZE -> {
                DrinkReminderWatch.snooze(context)
                tellTheIsland(context)
            }
        }
    }

    /**
     * The note and the island are two surfaces over one answer. When the answer
     * comes from the shade, the island has to be told; otherwise it would keep
     * asking a question that was answered until its next refresh.
     */
    private fun tellTheIsland(context: Context) {
        context.sendBroadcast(
            Intent(InfoPillService.ACTION_REMINDER_ANSWERED)
                .setPackage(context.packageName)
        )
    }
}
