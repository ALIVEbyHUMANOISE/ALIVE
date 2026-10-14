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
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.NightContractRepository
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.model.QuestStatus
import java.util.Calendar

/**
 * The note that says the quest you wrote down is still not done.
 *
 * One line, once, mid-afternoon - because the gap this closes is real: the
 * night contract can ask a person to name one quest, and until now nothing ever
 * mentioned it again until the day it was missed. The reminder exists for the
 * promise a person made to themselves, and it says nothing on a day with no
 * promise, or a promise whose quest is already paid.
 *
 * Same shape as [PromiseWatch]: an inexact alarm, no exact-alarm permission, and
 * armed whenever the app is opened.
 */
object QuestWatch {

    private const val CHANNEL_ID = "alive_quest"
    private const val NOTIFICATION_ID = 7501
    private const val REQUEST_CODE = 7502

    /** Mid-afternoon: late enough to have had the day, early enough to still do it. */
    const val REMINDER_HOUR = 15

    fun arm(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.RTC,
            nextReminder(),
            AlarmManager.INTERVAL_DAY,
            alarmIntent(context)
        )
    }

    fun post(context: Context, questTitle: String) {
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alive_hourglass)
                .setContentTitle(context.getString(R.string.quest_watch_title))
                .setContentText(context.getString(R.string.quest_watch_body, questTitle))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.quest_watch_body, questTitle))
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
                context.getString(R.string.quest_watch_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.quest_watch_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun nextReminder(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, QuestWatchReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE + 1,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Decides at three o'clock whether the promise still has an undone quest in it.
 *
 * Two silences are deliberate: no contract in force means nothing was promised,
 * and a quest already paid means the thing was done - in both cases the correct
 * behaviour is to say nothing at all.
 */
class QuestWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val contract = NightContractRepository(context)
            .inForceOn(LockStateManager.dayKeyOf(System.currentTimeMillis()))
        val questId = contract?.questId ?: return

        val quest = QuestRepository(context, TimeBankRepository(context))
            .getAllQuests()
            .firstOrNull { it.id == questId }
        if (quest == null || quest.status == QuestStatus.COMPLETED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        QuestWatch.post(context, quest.title)
    }
}
