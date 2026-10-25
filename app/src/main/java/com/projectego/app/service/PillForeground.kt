package com.projectego.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** The last app to come to the front, and when. */
data class PillForeground(val packageName: String, val at: Long)

/**
 * The pill's second opinion, from the usage log.
 *
 * The accessibility service is the fast path for "which app is in front now",
 * but it can be switched off - and with it off the pill has no other way to
 * learn that the person left. The log says the same thing without that service,
 * and it is the source the rest of the app already trusts.
 *
 * The timestamp is part of the answer on purpose: the caller has to be able to
 * tell a switch that happened after the pill was raised from an older entry that
 * merely happens to be the last one on file.
 */
object PillForegroundProbe {

    fun latest(context: Context, windowMillis: Long): PillForeground? {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val stream = manager.queryEvents(now - windowMillis, now) ?: return null

        var latestPackage: String? = null
        var latestAt = 0L
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            // MOVE_TO_FOREGROUND is ACTIVITY_RESUMED (both 1). The number 23 is
            // ACTIVITY_STOPPED: a stopped app is not a foreground app, and using
            // it here made "which app is in front" answer with the app that had
            // just been left.
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val packageName = event.packageName ?: continue
            if (latestPackage == null || event.timeStamp >= latestAt) {
                latestPackage = packageName
                latestAt = event.timeStamp
            }
        }
        return latestPackage?.let { PillForeground(it, latestAt) }
    }
}