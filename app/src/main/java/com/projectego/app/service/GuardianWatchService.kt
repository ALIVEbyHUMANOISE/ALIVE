package com.projectego.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.projectego.app.domain.AppClassifier
import com.projectego.app.domain.GuardianWatchPolicy
import com.projectego.app.model.AppCategory
import com.projectego.app.ui.GuardianAlertActivity
import com.projectego.app.ui.PermissionChecks

/**
 * Watches the usage log while the guard is off, and taps the user on the
 * shoulder when an entertainment app comes to the front.
 *
 * Why a service at all: with the accessibility service switched off there is no
 * window-change event to listen to, so the only way to notice "he just opened
 * TikTok" is to look at the usage log on a timer. The timer is the whole reason
 * this exists - and the whole reason it is built to be small: it runs only while
 * the guard is off, it stops itself the moment the guard is back, it does nothing
 * at all with the screen off, and each look is one bounded query.
 *
 * What it is not: a lock. It can only start a popup, the popup can be dismissed,
 * and Android will not let any app do more than that from here. The copy in
 * [GuardianAlertActivity] says so, and this comment exists to stop a future
 * reader from tightening the wording into a promise the platform does not keep.
 *
 * It is a foreground service for two reasons, both required: the process has to
 * stay alive to keep looking, and Android 8+ only lets a background process look
 * at the usage log while it is foreground. Its notification is the guard's own -
 * the same one, the same id - so the shade still holds exactly one ALIVE note.
 */
class GuardianWatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastInterruptAt = 0L
    private var lastInterruptedPackage: String? = null

    private val look = object : Runnable {
        override fun run() {
            // The guard came back: the reason this service exists is gone.
            if (PermissionChecks.isAccessibilityServiceEnabled(this@GuardianWatchService)) {
                stopSelf()
                return
            }
            if (isInteractive()) watchOnce()
            handler.postDelayed(this, GuardianWatchPolicy.POLL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            AccessibilityWatch.NOTIFICATION_ID,
            AccessibilityWatch.notification(this)
        )
        handler.postDelayed(look, GuardianWatchPolicy.POLL_MILLIS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Not sticky on purpose: a service that came back after being killed
        // without knowing why is a watcher nobody asked for. The app starts it
        // again the next time it sees the guard is off.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** One look: what came to the front since the last one, and is it entertainment? */
    private fun watchOnce() {
        val now = System.currentTimeMillis()
        val foreground = lastForegroundPackage(now - GuardianWatchPolicy.LOOKBACK_MILLIS, now)
            ?: return
        if (foreground == packageName) return

        val decision = GuardianWatchPolicy.decide(
            now = now,
            foregroundPackage = foreground,
            isEntertainment = AppClassifier.categorize(this, foreground) == AppCategory.ENTERTAINMENT,
            lastInterruptAt = lastInterruptAt,
            lastInterruptedPackage = lastInterruptedPackage
        )
        if (!decision.interrupt) return

        lastInterruptAt = now
        lastInterruptedPackage = decision.packageName
        startActivity(
            Intent(this, GuardianAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
        )
    }

    /**
     * The last app that came to the front inside the window, from the same event
     * stream the tracker reads - the event log has no opinion about which hour it
     * is, which is why it is used here too.
     */
    private fun lastForegroundPackage(from: Long, to: Long): String? {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager ?: return null
        val stream = usage.queryEvents(from, to) ?: return null

        var latest: String? = null
        var latestAt = 0L
        val event = android.app.usage.UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            if (event.eventType != EVENT_RESUMED && event.eventType != EVENT_MOVE_TO_FOREGROUND) continue
            if (event.timeStamp < latestAt) continue
            latest = event.packageName ?: continue
            latestAt = event.timeStamp
        }
        return latest
    }

    private fun isInteractive(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: false

    private companion object {
        /** ACTIVITY_RESUMED and its pre-29 name: the same two events the tracker reads. */
        const val EVENT_RESUMED = 23
        const val EVENT_MOVE_TO_FOREGROUND = 1
    }
}
