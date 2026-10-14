package com.projectego.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.domain.UsageTracker
import com.projectego.app.ui.AppLabels
import com.projectego.app.ui.Durations

/**
 * The pill: your own time in the app that is open right now.
 *
 * It is summoned, never ambushed - a user who wants it starts it, a user who
 * does not never sees it. It sits under the status bar rather than at the top
 * edge, because the top edge is where the camera hole lives, and it takes no
 * focus and no touches: it is a reading, not a control, so a thumb scrolling a
 * feed goes straight through it.
 *
 * Its style is the app's own card language, at the smallest size that language
 * works at: paper, a 3px ink edge, a hard shadow, and the duration in the pixel
 * face because that is the number the eye should land on.
 */
class InfoPillService : Service() {

    private var window: WindowManager? = null
    private var pill: View? = null
    private var watchedPackage: String? = null
    private var startedAt = 0L

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ticker.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val asked = intent?.getStringExtra(EXTRA_PACKAGE)
        watchedPackage = asked ?: recentForegroundPackage()
        if (watchedPackage == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startedAt = System.currentTimeMillis()
        isRunning = true
        startForeground(NOTIFICATION_ID, ongoingNote())
        show()
        ticker.removeCallbacks(tick)
        ticker.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        ticker.removeCallbacks(tick)
        pill?.let { window?.removeView(it) }
        pill = null
        super.onDestroy()
    }

    // ---------- the window ----------

    private fun show() {
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        window = manager
        val view = build()
        pill = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // No focus, no touches, no dimming: a reading, not a control.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin()
            y = margin()
        }

        manager.addView(view, params)
    }

    private fun margin(): Int = (MARGIN_DP * resources.displayMetrics.density).toInt()

    private fun build(): View {
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(10), px(14), px(12))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.alive_paper))
                setStroke(px(3), ContextCompat.getColor(context, R.color.alive_ink))
                cornerRadius = px(12).toFloat()
            }
            elevation = px(6).toFloat()
        }

        column.addView(text("", 22f, R.font.silkscreen_bold, R.color.alive_ink))
        column.addView(text("", 13f, R.font.manrope_medium, R.color.alive_ink_secondary))
        column.addView(text("", 13f, R.font.manrope_medium, R.color.alive_ink_secondary))
        return column
    }

    private fun text(value: String, sizeSp: Float, font: Int, colour: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(ContextCompat.getColor(context, colour))
            typeface = ResourcesCompat.getFont(context, font)
            includeFontPadding = false
            setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
        }

    // ---------- the words ----------

    private fun refresh() {
        val column = pill as? LinearLayout ?: return
        val app = watchedPackage ?: return
        val sessionMinutes = (System.currentTimeMillis() - startedAt) / 60_000
        val opens = runCatching { UsageTracker(this).opensToday(app) }.getOrDefault(0)
        val report = runCatching { UsageTracker(this).calculateTodayEntertainmentUsage() }.getOrNull()

        (column.getChildAt(0) as? TextView)?.text = Durations.of(this, sessionMinutes)
        (column.getChildAt(1) as? TextView)?.text =
            getString(R.string.pill_on_app, AppLabels.labelFor(this, app))
        (column.getChildAt(2) as? TextView)?.text = when {
            report == null -> getString(R.string.pill_opens, opens)
            report.isLimitExceeded -> getString(R.string.pill_spent)
            report.remainingMinutes <= NEAR_MINUTES ->
                getString(R.string.pill_left, Durations.of(this, report.remainingMinutes))
            else -> getString(R.string.pill_opens, opens)
        }
    }

    /** The app in front a moment ago, so the tile does not have to know. */
    private fun recentForegroundPackage(): String? {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val stream = manager.queryEvents(now - PROBE_MILLIS, now) ?: return null
        val event = UsageEvents.Event()
        var found: String? = null
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                found = event.packageName
            }
        }
        return found
    }

    private fun ongoingNote(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pill_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = getString(R.string.pill_channel_description) }
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alive_hourglass)
            .setContentTitle(getString(R.string.pill_channel_name))
            .setContentText(getString(R.string.pill_channel_description))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_PACKAGE = "com.projectego.app.extra.PILL_PACKAGE"

        /** For the tile's own state: the pill is up, so its next tap takes it down. */
        @Volatile
        var isRunning = false
            private set

        private const val CHANNEL_ID = "alive_pill"
        private const val NOTIFICATION_ID = 7401
        private const val TICK_MILLIS = 20_000L
        private const val PROBE_MILLIS = 60_000L
        private const val NEAR_MINUTES = 15L
        private const val MARGIN_DP = 44

        fun start(context: Context) {
            context.startForegroundService(Intent(context, InfoPillService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, InfoPillService::class.java))
        }
    }
}
