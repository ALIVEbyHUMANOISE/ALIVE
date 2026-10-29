package com.projectego.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.domain.UsageAccessNotePolicy

/**
 * The one thing ALIVE can say when its guard is running but cannot see.
 *
 * Accessibility can be on and the guard still unable to close anything, because
 * Usage Access is what says how much was used. Nothing outside the app announced
 * that state: a person could revoke it, keep the guard apparently on, and only
 * find out by opening ALIVE. This is the same kind of note the accessibility
 * switch already gets - same channel, next id - and it is posted on the
 * transition only, so a tick that keeps failing cannot turn it into a stream.
 *
 * Tapping the note opens ALIVE; the note's own action opens the system's Usage
 * Access screen directly, which is where the fix actually lives.
 */
object UsageAccessWatch {

    private const val NOTIFICATION_ID = 42

    /** What was last posted, so a repeated tick is not a repeated alert. */
    @Volatile
    private var shown = false

    fun sync(context: Context, granted: Boolean) {
        when (UsageAccessNotePolicy.action(shown = shown, granted = granted)) {
            UsageAccessNotePolicy.Action.SHOW -> {
                shown = true
                post(context)
            }

            UsageAccessNotePolicy.Action.CLEAR -> {
                shown = false
                clear(context)
            }

            UsageAccessNotePolicy.Action.NONE -> Unit
        }
    }

    private fun post(context: Context) {
        AccessibilityWatch.ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Codes 44/45: never 0 or 1, which the pill and the widget own for their
        // own MainActivity tokens (filterEquals ignores extras).
        val open = PendingIntent.getActivity(
            context,
            44,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val usageSettings = PendingIntent.getActivity(
            context,
            45,
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, AccessibilityWatch.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alive_hourglass)
                .setContentTitle(context.getString(R.string.permissions_heading))
                .setContentText(context.getString(R.string.permissions_usage_action))
                .setContentIntent(open)
                .addAction(
                    0,
                    context.getString(R.string.permissions_usage_action),
                    usageSettings
                )
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
        )
    }

    private fun clear(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
