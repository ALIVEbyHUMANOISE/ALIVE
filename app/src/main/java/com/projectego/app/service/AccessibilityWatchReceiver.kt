package com.projectego.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectego.app.ui.PermissionChecks

/**
 * The one place that decides whether the guard's notification should be up.
 *
 * Both triggers land here - the settle-check fired when the accessibility
 * service unbinds, and the repeating alarm that keeps the nag alive while the
 * service stays off - so the decision is made once, against the setting itself,
 * rather than at each caller's best guess. A false alarm is worse than a missed
 * one here: it would tell someone their guard is off while it is on.
 */
class AccessibilityWatchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        AccessibilityWatch.sync(
            context,
            PermissionChecks.isAccessibilityServiceEnabled(context)
        )
    }
}
