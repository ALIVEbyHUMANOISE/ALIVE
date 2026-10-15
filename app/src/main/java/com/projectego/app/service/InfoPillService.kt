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
import android.view.animation.OvershootInterpolator
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
        watching = watchedPackage
        startForeground(NOTIFICATION_ID, ongoingNote())
        // The tap that summons it happens in the shade, and an overlay is drawn
        // below the shade - so arriving immediately would mean arriving while
        // nobody can see it, and the animation would be over before the shade
        // finished closing. Half a second is enough for that, and short enough
        // that it still feels like the press caused it.
        ticker.removeCallbacks(tick)
        ticker.postDelayed({
            if (!isRunning) return@postDelayed
            show()
            ticker.post(tick)
        }, ARRIVE_DELAY_MILLIS)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        watching = null
        ticker.removeCallbacks(tick)
        // It leaves the way it arrived: shrinking back towards the top edge,
        // rather than vanishing between two frames.
        val leaving = pill
        pill = null
        if (leaving != null) {
            val density = resources.displayMetrics.density
            leaving.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationY(-10 * density)
                .setDuration(160L)
                .withEndAction { runCatching { window?.removeView(leaving) } }
                .start()
        }
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
            // Centred and tucked under the status bar, the way the phone's own
            // chrome sits. The camera hole is in the middle on this device, so
            // "in the status bar" would mean covering the clock - a few pixels
            // lower is the closest this platform lets anything get.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (TUCK_DP * resources.displayMetrics.density).toInt()
        }

        manager.addView(view, params)

        // It arrives the way an island does: growing out of the top rather than
        // appearing at full size. Overshoot, not ease - the arrival is a moment,
        // and the same curve the navbar's circle travels on.
        view.apply {
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
            translationY = -(12 * resources.displayMetrics.density)
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(OvershootInterpolator(1.05f))
                .start()
        }
    }

    /** The status bar's own height, so the pill lands under it on any device. */
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fallback = (28 * resources.displayMetrics.density).toInt()
        return if (id > 0) resources.getDimensionPixelSize(id) else fallback
    }

    private fun build(): View {
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        // Ink, not paper, and no edge and no shadow. Every other surface ALIVE
        // draws is a bright card with a bold border, and that language is right
        // inside the app and wrong here: an overlay over somebody's video has to
        // disappear into the phone's own chrome, exactly the way the home-screen
        // widget had to be allowed to be translucent. The colour that survives
        // is the one the eye should land on - the number.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16), px(9), px(18), px(9))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.alive_ink))
                cornerRadius = px(26).toFloat()
            }
        }

        row.addView(text("0", 15f, R.font.silkscreen_bold, R.color.alive_amber_action))
        row.addView(
            text("", 11f, R.font.manrope_medium, R.color.alive_paper).apply {
                setPadding(px(10), 0, 0, 0)
                alpha = 0.9f
            }
        )
        return row
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
        val row = pill as? LinearLayout ?: return
        val app = watchedPackage ?: return
        val sessionMinutes = (System.currentTimeMillis() - startedAt) / 60_000
        val opens = runCatching { UsageTracker(this).opensToday(app) }.getOrDefault(0)
        val report = runCatching { UsageTracker(this).calculateTodayEntertainmentUsage() }.getOrNull()
        val spent = report?.isLimitExceeded == true
        val label = AppLabels.labelFor(this, app)

        // Two facts and a colour. The number is the loud one; the line beside it
        // is the one that changes; and a spent day turns the number crimson
        // instead of adding a sentence - a pill that grows when things go wrong
        // is a pill that shouts.
        (row.getChildAt(0) as? TextView)?.apply {
            text = Durations.of(this@InfoPillService, sessionMinutes)
            setTextColor(
                ContextCompat.getColor(
                    this@InfoPillService,
                    if (spent) R.color.alive_red_short_mid else R.color.alive_amber_action
                )
            )
        }
        (row.getChildAt(1) as? TextView)?.text = when {
            spent -> getString(R.string.pill_line_spent, label)
            report != null && report.remainingMinutes <= NEAR_MINUTES ->
                getString(
                    R.string.pill_line_left,
                    label,
                    Durations.of(this, report.remainingMinutes)
                )
            else -> getString(R.string.pill_line_opens, label, opens)
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

        /** Which app the running pill is watching, so nothing restarts it twice. */
        @Volatile
        var watching: String? = null
            private set

        private const val CHANNEL_ID = "alive_pill"
        private const val NOTIFICATION_ID = 7401
        private const val TICK_MILLIS = 20_000L
        private const val ARRIVE_DELAY_MILLIS = 800L
        private const val PROBE_MILLIS = 60_000L
        private const val NEAR_MINUTES = 15L
        /** How far down from the very top edge the pill hangs. Flush, near enough:
         *  the camera hole on this device is centred and 64px tall, and a black
         *  pill over a black hole is how the hole stops existing. */
        private const val TUCK_DP = 2

        fun start(context: Context, packageName: String? = null) {
            context.startForegroundService(
                Intent(context, InfoPillService::class.java)
                    .putExtra(EXTRA_PACKAGE, packageName)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, InfoPillService::class.java))
        }
    }
}
