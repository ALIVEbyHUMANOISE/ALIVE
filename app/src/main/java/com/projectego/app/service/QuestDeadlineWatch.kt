package com.projectego.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.CustomQuestRepository
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.QuestEvidenceRepository
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.domain.AndroidPhysicalQuestEvidenceProvider
import com.projectego.app.domain.QuestSettlePolicy
import com.projectego.app.domain.QuestVerificationCoordinator
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.VerificationMethod

/**
 * The ten minutes that close a mission nobody pressed done on.
 *
 * [QuestWatch] asks about the mission at three in the afternoon. This does the
 * quieter job: when a physical mission's own window has run out and the grace has
 * gone with it, the mission is finished, paid, and said - without the person
 * having to remember a button. Their word is still what starts the counting; the
 * clock only stops it, and it stops it generously late.
 *
 * The same shape as every other note ALIVE keeps: an inexact alarm, no exact-alarm
 * permission, re-armed whenever the app is opened. A phone in deep sleep fires late,
 * which costs nothing but when the note arrives - and [MainActivity] runs the same
 * check on every resume, so the rule is not owed to the alarm alone.
 */
object QuestDeadlineWatch {

    private const val REQUEST_CODE = 7504
    private const val NOTIFICATION_ID = 7505

    /**
     * Points the alarm at the open window's deadline, or clears it.
     *
     * Called when a mission starts and on every resume, so an attempt that ended,
     * failed, or was declared by hand never leaves an alarm behind waiting to
     * speak over a mission that is already settled.
     */
    fun arm(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = alarmIntent(context)
        val dueAt = QuestSettlePolicy.dueAt(openAttempt(context))
        if (dueAt == null) {
            alarm.cancel(intent)
        } else {
            alarm.set(AlarmManager.RTC, dueAt, intent)
        }
    }

    /** The board as the person's own mission stands on it right now. */
    fun questRepository(context: Context): QuestRepository {
        val customQuests = CustomQuestRepository(context)
        return QuestRepository(
            context,
            TimeBankRepository(context),
            customQuestProvider = { customQuests.stored() }
        )
    }

    /**
     * The running physical window, or null.
     *
     * Read from the repository rather than remembered here, because the attempt on
     * file is the record of the commitment - and a watch holding its own copy would
     * be a second answer to what is running.
     */
    fun openAttempt(context: Context): QuestAttempt? =
        questRepository(context).getCurrentAttempt()?.takeIf {
            it.method == VerificationMethod.PHYSICAL && it.state == QuestAttemptState.IN_PROGRESS
        }

    /**
     * The one route this watch takes to a verdict: the coordinator's, which is the
     * same door a hand-pressed declaration goes through. No rule lives here.
     */
    fun coordinator(context: Context): QuestVerificationCoordinator {
        val timeBank = TimeBankRepository(context)
        val quests = questRepository(context)
        val evidence = QuestEvidenceRepository(context)
        return QuestVerificationCoordinator(
            quests,
            FocusSessionManager(context, timeBank),
            physicalEvidenceProvider = AndroidPhysicalQuestEvidenceProvider(context),
            questEvidenceRepository = evidence
        )
    }

    fun post(context: Context, questTitle: String, minutes: Long) {
        QuestWatch.ensureChannel(context)
        val body = context.getString(R.string.quest_deadline_body, questTitle, minutes)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, QuestWatch.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alive_hourglass)
                .setContentTitle(context.getString(R.string.quest_deadline_title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, QuestDeadlineReceiver::class.java),
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
 * Closes the window whose grace ran out, then says so once.
 *
 * The title is read before the verdict and the reward after, because the attempt
 * is rewritten by the settle it goes through - and a note that named the wrong
 * mission would be worse than no note at all.
 */
class QuestDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val quests = QuestDeadlineWatch.questRepository(context)
        val running = QuestDeadlineWatch.openAttempt(context)
        val settled = QuestDeadlineWatch.coordinator(context).autoSettleDueAttempt()

        // Re-armed whatever happened: a settled window has no deadline left to
        // point at, and the alarm must not outlive the attempt it was set for.
        QuestDeadlineWatch.arm(context)

        if (!settled || running == null) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val reward = quests.getCurrentAttempt()?.rewardMinutes ?: return
        val title = quests.getAllQuests().find { it.id == running.questId }?.title ?: return
        QuestDeadlineWatch.post(context, title, reward)
    }
}
