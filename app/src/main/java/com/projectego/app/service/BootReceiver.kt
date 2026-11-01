package com.projectego.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the app's own alarms back after a restart.
 *
 * Android keeps an enabled accessibility service across a reboot - the system
 * rebinds it, so the guard itself needs nothing from this receiver. What does not
 * survive is everything ALIVE scheduled for itself: the nightly promise note, the
 * mid-afternoon quest note, and the ten-minute close of a mission nobody declared.
 * Without this they were silently skipped until the person next opened the app,
 * which is the opposite of what a note at 21:00 promises.
 *
 * It deliberately does nothing else: no usage query, no service start, no
 * activity, and it never touches the accessibility setting - a reboot is not a
 * reason to open anything. Arming the same alarm twice is the same as arming it
 * once, so a repeated broadcast cannot duplicate work.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { PromiseWatch.arm(context) }
        runCatching { QuestWatch.arm(context) }
        runCatching { QuestDeadlineWatch.arm(context) }
        runCatching { DrinkReminderWatch.arm(context) }
    }
}
