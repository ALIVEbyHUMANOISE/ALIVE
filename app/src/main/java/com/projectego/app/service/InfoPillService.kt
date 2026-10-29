package com.projectego.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.Trace
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.domain.AppClassifier
import com.projectego.app.domain.PillArrival
import com.projectego.app.domain.PillHeaderText
import com.projectego.app.domain.PillMood
import com.projectego.app.domain.PillMorphChoreography
import com.projectego.app.domain.PillMorphEngine
import com.projectego.app.domain.PillPanelPolicy
import com.projectego.app.domain.PillPose
import com.projectego.app.domain.PillPosePolicy
import com.projectego.app.domain.PillReportSchedule
import com.projectego.app.domain.PillRowUpdate
import com.projectego.app.domain.PillTrafficPolicy
import com.projectego.app.domain.PillWatchPolicy
import com.projectego.app.domain.TrafficLight
import com.projectego.app.domain.TrafficLightState
import com.projectego.app.domain.UsageRead
import com.projectego.app.domain.UsageReport
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.AppCategory
import com.projectego.app.ui.AliveFace
import com.projectego.app.ui.AliveStates
import com.projectego.app.ui.AppLabels
import com.projectego.app.ui.Durations
import com.projectego.app.ui.PermissionChecks
import kotlin.math.abs
import kotlin.math.min

/**
 * The pill: your own time in the app that is open right now.
 *
 * It is summoned, never ambushed - a user who wants it starts it, a user who
 * does not never sees it. It is an ink island flush to the top edge and exists
 * only while an entertainment app is in front. Tapping it unfolds the day's
 * detail under the same surface; the window stays non-focusable, so it never
 * takes the keyboard or the back key from the app underneath.
 *
 * Three rules keep it honest, and each one exists because it was broken once:
 *  - Only a request raises it. A system restart with no intent is not a request.
 *  - Leaving the app ends it. The accessibility service is the fast path for
 *    that; the usage log is the answer when the service is off.
 *  - The numbers have to mean what they say. The session clock is elapsed time
 *    scoped to the day, opens are visits rather than activity resumes, and the
 *    day's report is read at a human pace instead of on every tick.
 */
class InfoPillService : Service() {

    private var window: WindowManager? = null
    private var pill: View? = null
    private var watchedPackage: String? = null

    // One view, two states. The compact reading is the header of the expanded
    // panel; the panel is built once and revealed under it, never as a second
    // window - a duplicate overlay is how two readings end up stacked.
    private var root: LinearLayout? = null
    private var header: LinearLayout? = null
    private var numberView: TextView? = null
    private var headerSpacer: View? = null
    private var redLight: TrafficLightDot? = null
    private var yellowLight: TrafficLightDot? = null
    private var greenLight: TrafficLightDot? = null
    private var trafficState = TrafficLightState(TrafficLight.NONE)
    private var pulseAnimator: ValueAnimator? = null
    private var panel: LinearLayout? = null
    private var panelCharacter: ImageView? = null
    private var panelTotal: TextView? = null
    private var panelUnit: TextView? = null
    private var panelRemaining: TextView? = null
    private var panelAppsLabel: TextView? = null
    private var panelRows: LinearLayout? = null
    private var panelMore: TextView? = null
    private var panelAction: TextView? = null
    private var progressTrack: FrameLayout? = null
    private var progressFill: View? = null
    private var lastProgress: Float? = null
    private var expanded = false
    private var sizeAnimator: ValueAnimator? = null

    // The frame the content is wearing right now. A morph starts from here, so
    // reversing direction continues the picture instead of resetting it - the
    // one clock owns the shell *and* the content, and both have to be able to
    // pick up mid-flight.
    private var currentFrame = PillMorphChoreography.rest(false)

    // The clock of the morph that owns the window right now, or null when none
    // does. It is the only place the morph's position and speed live, which is
    // what lets an interrupted run hand both to the next one.
    private var morphEngine: PillMorphEngine? = null

    // The one string whose width decides the window's, and therefore the one
    // that waits while a morph owns the measuring. See [PillHeaderText].
    private val headerText = PillHeaderText()

    // Who owns the arrival transform, and whether a tap has already shortened it.
    private val arrival = PillArrival()

    /** True while a face swap is waiting for its dip to land. See [applyCharacter]. */
    private var crossfadePlacement = false

    // The one moving part of the shape: the corner travels between the pill's
    // radius and the panel's while the window resizes, so the shape morphs
    // instead of a box growing inside another box. Tracked across interruption
    // and rotation so it can never snap.
    private var rootBackground: GradientDrawable? = null
    private var currentRadiusPx = 0

    // What the panel already shows, so a 20-second refresh does not rebuild
    // views that did not change (and cannot flicker while it is open).
    private var renderedFace: AliveFace? = null
    private var renderedRows: List<Pair<String, Long>> = emptyList()
    private var renderedCurrent: String? = null

    // The rows that are already on screen, so a minute turning over writes two
    // numbers instead of rebuilding the list. See [renderPanel].
    private var rowViews: List<RowHolder> = emptyList()

    // Two clocks for two questions: elapsed time for the session (a manual clock
    // change must not move it), wall time for the day it belongs to.
    private var startedAtElapsed = 0L
    private var startedAtWall = 0L

    // The day's report is expensive (a day of usage events plus a classification
    // per package), so it is read on its own thread: one read at a time, newest
    // wins, nothing after teardown. See [PillReportSchedule].
    private val reportSchedule = PillReportSchedule()
    private val reportExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { work ->
        Thread(work, "alive-pill-report").apply { isDaemon = true }
    }

    // The day's report is expensive; the pill needs it at a human pace, not at a
    // tick pace.
    private var lastReportAtElapsed = 0L
    private var lastReport: UsageReport? = null
    private var lastReportDay: String? = null

    private val usageTracker by lazy { UsageTracker(this) }

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            trace("pill.refresh") { refresh() }
            if (isRunning && pill != null) ticker.postDelayed(this, TICK_MILLIS)
        }
    }
    private val arrive = Runnable {
        if (isRunning) {
            if (screenInteractive()) {
                show()
                if (pill != null) ticker.post(tick)
            } else {
                dismiss()
            }
        }
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) dismiss()
        }
    }
    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) { dismiss() }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        application.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    private fun screenInteractive(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false

    private fun dismiss() {
        isRunning = false
        watching = null
        // Only the pill's own clocks are stopped. Sweeping the whole queue here used
        // to drop a report completion that was already on its way, and the schedule
        // would keep the slot busy for the rest of the service's life - so a pill
        // restarted after a screen-off never read the day again.
        ticker.removeCallbacks(tick)
        ticker.removeCallbacks(arrive)
        removeWindow()
        stopSelf()
    }

    private fun removeWindow() {
        stopMorph()
        morphEngine = null
        arrival.release()
        stopTrafficPulse()
        panel?.animate()?.cancel()
        panelCharacter?.animate()?.cancel()
        crossfadePlacement = false
        val leaving = pill
        pill = null
        leaving?.animate()?.cancel()
        if (leaving != null) runCatching { window?.removeViewImmediate(leaving) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Only a request raises the pill. A null intent means the system restarted
        // the service on its own, and a restart is not a request - answering one
        // by re-showing the pill is how a reading nobody asked for appeared on
        // the home screen.
        val asked = intent?.getStringExtra(EXTRA_PACKAGE)
        if (asked == null || !PillSettings.isEnabled(this) || !Settings.canDrawOverlays(this) ||
            !screenInteractive()
        ) {
            dismiss()
            return START_NOT_STICKY
        }
        // The number belongs to the app named beside it: moving to another app
        // starts that app's reading at zero, because a just-opened Instagram
        // showing TikTok's twenty minutes would be a lie. What was wrong before
        // was the *spurious* reset - the shade, a dialog, a stop event - not a
        // real switch.
        discardPreviousDayReport()
        val previousPackage = watchedPackage
        watchedPackage = asked
        if (previousPackage == null || previousPackage != asked) {
            startSession()
        }

        val foregroundStarted = runCatching {
            startForeground(NOTIFICATION_ID, ongoingNote())
        }.isSuccess
        if (!foregroundStarted) {
            dismiss()
            return START_NOT_STICKY
        }

        isRunning = true
        watching = watchedPackage

        // Only this run's own callbacks are cancelled: a report completion already
        // queued on this handler belongs to the reading, not to the arrival, and
        // clearing the whole queue used to strand the schedule with a read that
        // could never publish.
        ticker.removeCallbacks(tick)
        ticker.removeCallbacks(arrive)
        if (pill != null) {
            // A switch between entertainment apps changes the label, not the
            // arrival - so it is not animated in again and the number never blinks.
            ticker.post(tick)
        } else {
            // The press that summons it happens in the shade, and an overlay is
            // drawn below the shade, so arriving at once would mean arriving while
            // nobody can see it. Any earlier arrival is cleared first, so a quick
            // app switch cannot queue two.
            ticker.postDelayed(arrive, ARRIVE_DELAY_MILLIS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        watching = null
        ticker.removeCallbacksAndMessages(null)
        unregisterReceiver(screenOff)
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        reportSchedule.close()
        reportExecutor.shutdownNow()
        removeWindow()
        headerText.clear()
        expanded = false
        root = null
        header = null
        numberView = null
        headerSpacer = null
        redLight = null
        yellowLight = null
        greenLight = null
        trafficState = TrafficLightState(TrafficLight.NONE)
        panel = null
        panelCharacter = null
        panelTotal = null
        panelUnit = null
        panelRemaining = null
        panelAppsLabel = null
        panelRows = null
        panelMore = null
        panelAction = null
        progressTrack = null
        progressFill = null
        lastProgress = null
        rootBackground = null
        currentRadiusPx = 0
        renderedFace = null
        renderedRows = emptyList()
        renderedCurrent = null
        rowViews = emptyList()
        window = null
        super.onDestroy()
    }

    // ---------- the window ----------

    private fun show() {
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        window = manager

        // A start can arrive while the pill is already up - moving from one
        // entertainment app to another does exactly that. The old view used to be
        // left attached: it kept the previous app's name, and the two rows drew
        // on top of each other.
        pill?.let { old -> runCatching { manager.removeView(old) } }
        pill = null

        val view = build()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No focus and no dimming: the pill can now be tapped, but it must
            // never take the keyboard or the back key. NOT_TOUCH_MODAL keeps
            // every touch outside its own bounds flowing to the app below.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topOffsetPx()
        }

        // A window-manager refusal (permission revoked in the last moment, an OEM
        // that says no) must end the pill, not the process: the accessibility
        // service lives in the same process, and it is the thing this app exists
        // for.
        val added = runCatching { manager.addView(view, params) }.isSuccess
        if (!added) {
            dismiss()
            return
        }
        pill = view

        // Read the day while the pill is still arriving, so the numbers are
        // usually there before anyone taps - and the first tap never waits.
        requestReport()

        // It arrives the way an island does: growing out of the top rather than
        // appearing at full size. Overshoot, not ease - the arrival is a moment,
        // and the same curve the navbar's circle travels on. These three
        // properties belong to the arrival alone; the morph owns the window and
        // the content, so the only thing a tap can do is shorten the trip.
        view.apply {
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
            translationY = -(12 * resources.displayMetrics.density)
            arrival.started()
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(OvershootInterpolator(1.05f))
                .withEndAction { arrival.finished() }
                .start()
        }
    }

    /**
     * Finishes an arrival a tap interrupted, from the pose it is actually in. The
     * transform stays with its owner - this is the same properties, a shorter
     * trip - so nothing is cancelled mid-fade and nothing is snapped.
     */
    private fun shortenArrival() {
        val view = pill ?: return
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(ARRIVAL_FINISH_MILLIS)
            .withEndAction { arrival.finished() }
            .start()
    }

    /** Reduced motion: the arrival's answer without the trip. */
    private fun settleArrivalNow() {
        arrival.release()
        val view = pill ?: return
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationY = 0f
    }

    /**
     * How far from the very top edge the pill hangs.
     *
     * A centred camera hole is where an island belongs: the ink sits in the hole
     * and the hole stops existing, which is the whole trick. A hole anywhere else
     * is the clock's space, so on those phones the pill starts below the safe
     * inset instead of covering it.
     */
    private fun topOffsetPx(): Int {
        val density = resources.displayMetrics.density
        val tuck = (TUCK_DP * density).toInt()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return tuck

        val display = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return tuck
        val cutout = runCatching { display.cutout }.getOrNull() ?: return tuck
        val bounds = cutout.boundingRects.firstOrNull() ?: return tuck

        val width = resources.displayMetrics.widthPixels
        val centered = abs(bounds.exactCenterX() - width / 2f) < width / 8f
        return if (centered) tuck else maxOf(tuck, cutout.safeInsetTop)
    }

    private fun build(): LinearLayout {
        // Pure black, not the app's ink: the island is its own surface, and on
        // an OLED the camera hole and the pill become one uninterrupted dark.
        // The expanded panel and the compact reading share it, so the state
        // change never flips the colour under the header. The drawable is held
        // separately: its corner is the one part of the shape that moves.
        val surface = rounded(Color.BLACK, px(COMPACT_RADIUS_DP))
        rootBackground = surface
        currentRadiusPx = px(COMPACT_RADIUS_DP)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            background = surface
        }
        this.root = root

        val number = text("0", 15f, R.font.silkscreen_bold, R.color.alive_amber_action)
        numberView = number
        // The timer is its own measured width, not a reserved two-digit box:
        // the pill hugs [14dp][timer][12dp][lights][14dp], so the air on both
        // sides is equal. The cost is that a digit change widens the pill and
        // moves the lights by half a digit - the symmetric trade.
        number.gravity = Gravity.CENTER
        number.maxLines = 1
        applyTimerInkPadding()

        // Three status lights, not three buttons. Only the row is decorative;
        // the header remains the single touch target.
        val red = trafficDot(R.color.alive_traffic_red)
        val yellow = trafficDot(R.color.alive_traffic_yellow)
        val green = trafficDot(R.color.alive_traffic_green)
        redLight = red
        yellowLight = yellow
        greenLight = green
        val rightGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(TRAFFIC_GAP_DP), 0, 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(red)
            addView(yellow)
            addView(green)
        }
        val leftGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(number)
        }

        // Zero in the compact pill, weight 1 once expanded: the lights sit
        // right after the timer when the pill hugs its content, and travel to
        // the panel's upper-right when the header grows. A weight of 1 in a
        // wrap_content row would measure against the whole screen, which is
        // exactly how the pill became a bar once.
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 0f)
        }
        headerSpacer = spacer

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Equal air on both sides: the timer's ink starts 14dp in, and the
            // last light's halo ends 14dp before the pill's edge.
            setPadding(px(14), px(8), px(14), px(8))
            // The whole island is the control; a 36dp-tall one is below the
            // platform's touch target and a thumb misses it at the screen edge.
            minimumHeight = px(48)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.pill_panel_expand)
            setOnClickListener { toggleExpanded() }
            addView(leftGroup)
            addView(spacer)
            addView(rightGroup)
        }
        this.header = header
        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val expandedPanel = buildPanel()
        panel = expandedPanel
        root.addView(
            expandedPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    /**
     * The expanded state: the character, the day's real figures, and the way
     * back into the app. Black continues the pill; the character keeps its
     * paper tile because its ink features would disappear on the dark ground.
     */
    private fun buildPanel(): LinearLayout {
        val paper = colour(R.color.alive_paper)
        val amber = colour(R.color.alive_amber_action)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(14))
            visibility = View.GONE
        }
        panel.addView(View(this).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(paper, 34))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                maxOf(1, px(1))
            )
        })

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(12), 0, 0)
        }
        val tile = FrameLayout(this).apply {
            // Paper, not the critter's own rose: its body is pink, so a pink
            // tile would erase everything except the ink details. The pale tile
            // is the window the character is drawn in.
            background = rounded(colour(R.color.alive_paper), px(14))
            // 64dp against the day's 24sp figure: the character is a companion
            // to the number, not a poster above it.
            layoutParams = LinearLayout.LayoutParams(px(64), px(64))
        }
        val character = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(spritePx(48), spritePx(48), Gravity.CENTER)
        }
        panelCharacter = character
        tile.addView(character)
        head.addView(tile)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        column.addView(smallText(getString(R.string.pill_panel_total_label), 11f, 0.68f))

        val totalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val total = text("", 24f, R.font.silkscreen_bold, R.color.alive_amber_action)
        panelTotal = total
        val unit = text(
            getString(R.string.unit_minutes_suffix),
            11f,
            R.font.manrope_medium,
            R.color.alive_paper
        ).apply {
            setPadding(px(8), 0, 0, px(4))
            alpha = 0.75f
        }
        panelUnit = unit
        totalRow.addView(total)
        totalRow.addView(unit)
        column.addView(totalRow)

        val remaining = smallText("", 12f, 0.82f)
        panelRemaining = remaining
        column.addView(remaining)
        head.addView(column)
        panel.addView(
            head,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // The day's own used share. No label, no percentage: the remaining
        // sentence above already carries the number, and a bar with a caption
        // would say it twice.
        val track = FrameLayout(this).apply {
            background = rounded(ColorUtils.setAlphaComponent(paper, 30), px(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                px(4)
            ).apply { topMargin = px(10) }
        }
        val fill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        track.addView(fill)
        progressTrack = track
        progressFill = fill
        panel.addView(track)

        val apps = smallText(getString(R.string.pill_panel_apps_label), 11f, 0.55f).apply {
            setPadding(0, px(14), 0, px(2))
        }
        panelAppsLabel = apps
        panel.addView(apps)

        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panelRows = rows
        panel.addView(
            rows,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val more = smallText("", 11f, 0.55f).apply {
            setPadding(0, px(4), 0, 0)
            visibility = View.GONE
        }
        panelMore = more
        panel.addView(more)

        val action = TextView(this).apply {
            text = getString(R.string.pill_panel_open)
            textSize = 12f
            typeface = ResourcesCompat.getFont(this@InfoPillService, R.font.manrope_medium)
            setTextColor(colour(R.color.alive_ink))
            setPadding(px(18), px(10), px(18), px(10))
            background = rounded(amber, px(20))
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            gravity = Gravity.CENTER
            // A control in an overlay is still a control: 48dp tall whatever
            // its label measures.
            minHeight = px(48)
            minimumHeight = px(48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(14) }
            setOnClickListener { openDashboard() }
        }
        panelAction = action
        panel.addView(action)
        return panel
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

    /**
     * The timer's ink, not its font box, is what the eye centres.
     *
     * A TextView's line box includes descender space the digits and "MENIT"
     * never use, so centring the view leaves the visible glyphs low. This
     * pads the view until the measured ink box is exactly centred; the header
     * then centres the view, and the timer lines up with the LEDs' cores.
     */
    private fun applyTimerInkPadding() {
        val number = numberView ?: return
        val value = number.text ?: return
        if (value.isEmpty()) return
        val bounds = Rect()
        number.paint.getTextBounds(value.toString(), 0, value.length, bounds)
        val metrics = number.paint.getFontMetricsInt()
        // Solve paddingTop - ascent + inkCentre = viewHeight / 2.
        val top = metrics.descent + metrics.ascent - bounds.top - bounds.bottom
        number.setPadding(0, maxOf(0, top), 0, maxOf(0, -top))
    }

    /**
     * Writes the newest timer reading unless a morph owns the measuring, and
     * re-inks the digits when it does. Called on every tick and again the instant
     * a morph commits, so a reading that arrived mid-morph is on screen the
     * moment the shell is at rest - never a frame earlier.
     */
    private fun writeHeaderText() {
        val text = headerText.flush(morphing = sizeAnimator != null) ?: return
        numberView?.text = text
        applyTimerInkPadding()
        updateHeaderDescription()
    }

    private fun smallText(value: String, sizeSp: Float, alpha: Float): TextView =
        text(value, sizeSp, R.font.manrope_medium, R.color.alive_paper).apply {
            this.alpha = alpha
        }

    private fun trafficDot(colourRes: Int): TrafficLightDot = TrafficLightDot(this).apply {
        coreColor = colour(colourRes)
        // Every light stays at full colour in every state; the state is told
        // by motion (and, with motion off, by a ring), never by dimming.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(
            px(TRAFFIC_SLOT_DP),
            px(TRAFFIC_SLOT_DP)
        )
    }

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /**
     * A sprite size snapped to whole 16-cell device pixels, so the character
     * and the panel's arrow keep the same crisp grid as every other ALIVE
     * sprite instead of being resampled into blur.
     */
    private fun spritePx(dp: Int): Int {
        val raw = px(dp)
        return (raw + SPRITE_HALF) / SPRITE_CELL * SPRITE_CELL
    }

    private fun colour(id: Int): Int = ContextCompat.getColor(this, id)

    private fun rounded(fill: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusPx.toFloat()
        }

    // ---------- compact <-> expanded ----------

    private fun toggleExpanded() {
        if (root == null) return
        // A tap during the arrival does not cancel it: cancelling freezes the root
        // at a half-done scale and alpha, and snapping to the end jumps. It
        // finishes from where it is instead. With motion off it is simply over.
        if (!animationsAllowed()) {
            settleArrivalNow()
        } else if (arrival.tap() == PillArrival.OnTap.SHORTEN) {
            shortenArrival()
        }
        // An interrupted animation is not stacked: the next state starts from
        // wherever the window and the content currently are.
        stopMorph()
        // The morph owns the content's alpha from here, so a face swap that is
        // still dipping finishes now rather than fading in behind the shell.
        settleCharacterCrossfade()
        if (expanded) trace("pill.collapse") { collapse() } else trace("pill.expand") { expand() }
    }

    /**
     * A named slice in the system trace, and nothing else.
     *
     * Coarse on purpose - one slice per interaction or per tick, never per frame:
     * when nobody is tracing, a section costs two native flag checks, and when
     * somebody is, it is what separates the pill's own work from the process's.
     */
    private inline fun <T> trace(section: String, block: () -> T): T {
        Trace.beginSection(section)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Stops the running morph without letting it finish.
     *
     * The reference is dropped *before* the cancel: [ValueAnimator.cancel] calls
     * onAnimationEnd on the spot, and with the old animator still registered as
     * the current one an interrupted run used to commit the end state of a
     * transition nobody wanted any more - a half-open pill snapped fully open
     * before it started closing.
     */
    private fun stopMorph() {
        val running = sizeAnimator
        sizeAnimator = null
        running?.cancel()
    }

    private fun expand() {
        val root = root ?: return
        val header = header ?: return
        val panel = panel ?: return
        if (expanded) return
        discardPreviousDayReport()
        expanded = true
        // A reading is requested, never waited for: the first frame of the morph
        // must not sit behind a UsageStats query. Until it lands the panel shows
        // the last good report, or the honest "no numbers yet" state.
        requestReport()
        // The opening sets the pose directly and lets the entrance carry it;
        // only changes while open crossfade. This is the first-use-heavy part:
        // labels, the sprite and the row views are all built here, on the tap.
        trace("pill.expand.content") {
            renderTraffic()
            renderedFace = null
            renderPanel(lastReport, usageTracker.lockStateManager.isLocked())
        }
        panel.visibility = View.VISIBLE

        // The whole picture answers one clock. The opening frame is the frame the
        // content is already wearing, and animateWindow carries it to rest - so a
        // reversal continues where the eye left it, and a fresh expansion still
        // starts with everything hidden where the choreography wants it. The
        // independent view animators this used to run each kept their own watch,
        // which is how the content and the shell fell out of step.
        applyFrame(PillMorphChoreography.expand(0f, currentFrame))

        val windowParams = root.layoutParams as? WindowManager.LayoutParams
        // The size the last morph frame wrote when it was interrupted, ahead of
        // the size the layout pass has caught up with: an expansion that starts
        // out of a half-collapsed pill has to leave from the half.
        val fromW = morphSize(windowParams?.width, root.width, header.measuredWidth)
        val fromH = morphSize(windowParams?.height, root.height, header.measuredHeight)

        // Freeze the window at that size *before* the spacer gains its
        // weight: a weighted child in a wrap_content window measures against
        // the whole screen, which is how the pill became a bar once.
        if (windowParams != null) {
            windowParams.width = fromW
            windowParams.height = fromH
            window?.let { manager ->
                runCatching { manager.updateViewLayout(root, windowParams) }
            }
        }
        (headerSpacer?.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f
        headerSpacer?.requestLayout()

        val toW = panelWidthPx()
        // The content is laid out once at the width it will rest at and clipped
        // by the growing shell. Left as MATCH_PARENT it would re-measure and
        // re-wrap its labels on every morph frame, which is exactly the drift
        // between the shell and its content this morph exists to prevent.
        holdPanelWidth(panel, toW)
        // The target is the header plus the panel it carries, measured the same
        // way [resizePanelToContent] measures a settled one - not the current
        // window height plus the panel, which would count a half-open shell's
        // leftover twice and hand back a panel with a strip of empty ink under it.
        val headerHeight = header.height.takeIf { it > 0 } ?: header.measuredHeight
        val toH = headerHeight + trace("pill.expand.measure") { measurePanel(panel, toW) }

        animateWindow(fromW, fromH, toW, toH, expanding = true)
    }

    private fun collapse() {
        val root = root ?: return
        val header = header ?: return
        val panel = panel ?: return
        if (!expanded) return
        expanded = false
        updateHeaderDescription()
        // Fraction zero of a collapse is the frame the content is wearing now,
        // not the full panel: the same clock that shrinks the window takes what
        // is actually on screen from there to gone.
        applyFrame(PillMorphChoreography.collapse(0f, currentFrame))

        val params = root.layoutParams as? WindowManager.LayoutParams
        val fromW = morphSize(params?.width, root.width, header.measuredWidth)
        val fromH = morphSize(params?.height, root.height, header.measuredHeight)
        trace("pill.collapse.measure") {
            header.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }
        animateWindow(
            fromW, fromH, header.measuredWidth, header.measuredHeight,
            expanding = false
        ) {
            panel.visibility = View.GONE
            panel.alpha = 1f
            // Only now does the panel give its width back: the compact row ends at
            // the timer + lights, and the spacer exists solely to pin them to the
            // expanded panel's right edge.
            releasePanelWidth()
            (headerSpacer?.layoutParams as? LinearLayout.LayoutParams)?.weight = 0f
            headerSpacer?.requestLayout()
            val back = root.layoutParams as? WindowManager.LayoutParams ?: return@animateWindow
            // Back to wrapping the reading: the timer changes width as its
            // digits change, and nothing else is in the row.
            back.width = WindowManager.LayoutParams.WRAP_CONTENT
            back.height = WindowManager.LayoutParams.WRAP_CONTENT
            window?.let { manager -> runCatching { manager.updateViewLayout(root, back) } }
        }
    }

    /**
     * The window itself is resized, not just the content: the ink ground has to
     * grow with the panel or the island would be drawn at its new size in one
     * frame. The panel's content is already laid out at the final width and is
     * simply clipped by the smaller window on the way there.
     *
     * One animator owns the whole transition: its fraction moves the window size,
     * the corner radius and every piece of content together, so nothing can
     * finish before the surface it lives on. Every start is read from where the
     * picture is now - [fromW]/[fromH] and [currentRadiusPx] for the shell,
     * [currentFrame] for the content, both captured here so a running morph
     * cannot feed on its own output - which is what lets a reversed direction
     * carry on instead of jumping. The fraction comes from [PillMorphEngine]:
     * the pill's own curve and duration when the morph starts from rest, and a
     * continuation that keeps the interrupted run's speed when a tap cuts in.
     */
    private fun animateWindow(
        fromW: Int,
        fromH: Int,
        toW: Int,
        toH: Int,
        expanding: Boolean,
        onEnd: () -> Unit = {}
    ) {
        val root = root ?: return
        val manager = window ?: return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        val fromRadius = currentRadiusPx
        val startFrame = currentFrame
        val toRadius = px(if (expanding) EXPANDED_RADIUS_DP else COMPACT_RADIUS_DP)
        val targetMorph = if (expanding) 1f else 0f
        if (!animationsAllowed()) {
            stopMorph()
            morphEngine = null
            params.width = toW
            params.height = toH
            runCatching { manager.updateViewLayout(root, params) }
            // Reduced motion goes to the answer, not through the morph, and never
            // waits for a callback that will not come.
            applyFrame(PillMorphChoreography.rest(expanding))
            applyRadius(1f, fromRadius, toRadius)
            writeHeaderText()
            onEnd()
            return
        }
        // [morphEngine] is only ever set while a run owns the window, and every
        // path that stops owning it - completion, reduced motion, teardown -
        // clears it, so a live one here means this tap interrupted a morph. That
        // run's position and speed carry into the new one, which is what keeps a
        // reversal from restarting at the curve's maximum slope. A run from rest
        // keeps the pill's own curve and duration, unchanged.
        val interrupted = morphEngine?.takeUnless { it.isSettled }
        val engine = interrupted?.let {
            PillMorphEngine.springRun(it.morph, it.velocity, targetMorph, SPRING_MILLIS)
        } ?: if (expanding) {
            // An open starts from rest on a spring: it has to be accelerated, and
            // that is what gives it weight. The collapse keeps the ease - leaving
            // should feel decided, not heavy.
            PillMorphEngine.launchRun(EXPAND_CAP_MILLIS, targetMorph)
        } else {
            PillMorphEngine.easeRun(COLLAPSE_MILLIS, 1f, targetMorph)
        }
        morphEngine = engine
        val duration = engine.durationMillis
        var discarded = false
        var committed = false
        // The run's answer, written once. Both the animator's own end and a
        // continuation that arrives early go through here, and the flag is what
        // keeps a deferred callback from writing it a second time.
        fun commit() {
            if (committed) return
            committed = true
            sizeAnimator = null
            morphEngine = null
            params.width = toW
            params.height = toH
            runCatching { manager.updateViewLayout(root, params) }
            applyFrame(PillMorphChoreography.rest(expanding))
            applyRadius(1f, fromRadius, toRadius)
            // The boundary the deferred reading was waiting for: whatever minute
            // arrived while the shell was moving is written now, at rest.
            writeHeaderText()
            onEnd()
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { value ->
                val f = engine.tick(value.currentPlayTime)
                // The launch runs past its target on its own, so the window follows
                // the run's fraction directly; the corner keeps the same clock but
                // is held at its target so the radius never exceeds its token.
                params.width = (fromW + (toW - fromW) * f).toInt()
                params.height = (fromH + (toH - fromH) * f).toInt()
                runCatching { manager.updateViewLayout(root, params) }
                applyFrame(
                    if (expanding) PillMorphChoreography.expand(f, startFrame)
                    else PillMorphChoreography.collapse(f, startFrame)
                )
                applyRadius(f.coerceIn(0f, 1f), fromRadius, toRadius)
                // A continuation is over when its spring arrives, not when its cap
                // runs out. Ending it here with end() would be the elegant move and
                // is a crash: ValueAnimator.end() dispatches another update frame
                // from inside this one, the frame calls end() again, and the stack
                // runs out (measured on the phone: StackOverflowError, the whole
                // process taking the pill and the guard down with it). So the run
                // commits its own answer, then cancels the ticker, whose callbacks
                // arrive after this frame and find nothing left to write.
                if (engine.isSettled && value.currentPlayTime < value.duration) {
                    commit()
                    value.cancel()
                }
            }
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                discarded = true
            }

            override fun onAnimationEnd(animation: Animator) {
                // A cancelled run belongs to a state that is already gone, and a
                // run that is no longer the current one must not overwrite the
                // next: either way it commits nothing.
                if (discarded || sizeAnimator !== anim) return
                commit()
            }
        })
        sizeAnimator = anim
        anim.start()
    }

    /**
     * The pixel size the morph last wrote, or the laid-out size once the window
     * is back to wrapping its content, or the measured size before a layout pass.
     */
    private fun morphSize(paramsSize: Int?, layoutSize: Int, measureSize: Int): Int =
        paramsSize?.takeIf { it > 0 } ?: layoutSize.takeIf { it > 0 } ?: measureSize

    /** Sets every animated part of the content to the frame the clock says. */
    private fun applyFrame(frame: PillMorphChoreography.Frame) {
        currentFrame = frame
        panel?.alpha = frame.panelAlpha
        panelCharacter?.apply {
            alpha = frame.characterAlpha
            scaleX = frame.characterScale
            scaleY = frame.characterScale
        }
        val density = resources.displayMetrics.density
        panelRows?.apply {
            alpha = frame.rowsAlpha
            translationY = (frame.rowsShiftDp * density).toInt().toFloat()
        }
        panelAction?.apply {
            alpha = frame.actionAlpha
            translationY = (frame.actionShiftDp * density).toInt().toFloat()
            // A control that cannot be seen is not a control. The panel only
            // leaves the window at the end of a collapse, so through every morph -
            // and through a chain of reversals - this button is invisible and
            // still the first thing a stray tap finds. Tapping it opens ALIVE,
            // opening ALIVE stops the pill, and that is how a burst of taps made
            // the pill disappear.
            val presented = expanded && sizeAnimator == null && frame.actionAlpha >= 1f
            isClickable = presented
            isFocusable = presented
        }
    }

    private fun applyRadius(fraction: Float, from: Int, to: Int) {
        val surface = rootBackground ?: return
        currentRadiusPx = (from + (to - from) * fraction).toInt()
        surface.cornerRadius = currentRadiusPx.toFloat()
        root?.invalidate()
    }

    private fun animationsAllowed(): Boolean =
        runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)

    private fun holdPanelWidth(panel: View, width: Int) {
        val params = panel.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.width == width) return
        params.width = width
        panel.layoutParams = params
    }

    /**
     * Gives the panel's width back to the window. Called when the island closes:
     * while it is open the panel stays pinned at the window's own width, which is
     * what keeps a refresh from re-wrapping the rows.
     */
    private fun releasePanelWidth() {
        val panel = panel ?: return
        holdPanelWidth(panel, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun measurePanel(panel: View, width: Int): Int {
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return panel.measuredHeight
    }

    private fun panelWidthPx(): Int {
        val screen = resources.displayMetrics.widthPixels
        val capped = min(screen - px(32), px(PANEL_MAX_WIDTH_DP))
        return capped.coerceAtLeast(px(PANEL_MIN_WIDTH_DP))
    }

    /**
     * Starts a background read of the day's report, unless one is already out.
     *
     * The tracker is touched here, on the main thread, so its lazy construction
     * never happens on the worker; only the read itself is moved. The executor is
     * single-threaded on purpose: the schedule already refuses a second read, and
     * one thread means a slow query cannot turn into a queue.
     */
    private fun requestReport() {
        val token = reportSchedule.request() ?: return
        val tracker = usageTracker
        val day = LockStateManager.dayKeyOf(System.currentTimeMillis())
        val readAt = SystemClock.elapsedRealtime()
        runCatching {
            reportExecutor.execute {
                val report = try {
                    trace("pill.refresh.report") {
                        (tracker.readTodayEntertainmentSnapshot() as? UsageRead.Success)?.report
                    }
                } catch (failure: RuntimeException) {
                    Log.w(TAG, "Report unavailable: ${failure.javaClass.simpleName}")
                    null
                }
                // Published on the main thread only: the schedule is not
                // synchronised, and this worker must never be the one to retire a
                // slot the UI thread is also checking.
                ticker.post { applyReport(token, report, day, readAt) }
            }
        }.onFailure { reportSchedule.complete(token) }
    }

    /**
     * Publishes a finished read on the main thread - if it is still the newest and
     * the pill is still up.
     *
     * A failed read keeps the last good report instead of blanking the pill: "no
     * data yet" and "no usage" are different sentences, and the second one is a
     * lie the person could act on. Nothing here touches enforcement: the guard
     * keeps its own reads, and the pill's numbers never decide anything.
     */
    private fun applyReport(token: Long, report: UsageReport?, day: String, readAt: Long) {
        if (!reportSchedule.complete(token) || !isRunning) return
        discardPreviousDayReport()
        if (day != LockStateManager.dayKeyOf(System.currentTimeMillis())) {
            requestReport()
            return
        }
        if (report != null) {
            lastReport = report
            lastReportDay = day
            lastReportAtElapsed = readAt
        }
        if (pill == null) return
        trace("pill.refresh.apply") {
            renderTraffic()
            writeHeaderText()
            if (expanded) {
                renderPanel(lastReport, usageTracker.lockStateManager.isLocked())
                if (sizeAnimator == null) resizePanelToContent()
            }
        }
    }

    private fun discardPreviousDayReport() {
        if (lastReportDay != LockStateManager.dayKeyOf(System.currentTimeMillis())) {
            lastReport = null
            lastReportDay = null
            lastReportAtElapsed = 0L
        }
    }

    private fun renderPanel(report: UsageReport?, locked: Boolean) {
        val model = PillPanelPolicy.from(report, locked)
        val face = faceFor(
            PillPosePolicy.poseFor(report, locked, watchedPackage)
        )
        // During the expansion the face is set directly and the panel's own
        // entrance carries it. Only a pose change while the panel is already
        // open crossfades.
        trace("pill.face") { applyCharacter(face, crossfade = expanded && sizeAnimator == null) }

        lastProgress = model.progress
        trace("pill.bar") { updateProgressBar(model.progress, model.mood) }
        val hasTotal = model.totalMinutes != null
        panelTotal?.apply {
            text = model.totalMinutes?.let { getString(R.string.stat_minutes, it) }.orEmpty()
            visibility = if (hasTotal) View.VISIBLE else View.GONE
            // The same signal the compact number uses when the day is spent -
            // the large figure, not the sentence, carries the state.
            setTextColor(
                colour(
                    if (model.mood == PillMood.LIMIT) R.color.alive_red_short_mid
                    else R.color.alive_amber_action
                )
            )
        }
        panelUnit?.visibility = if (hasTotal) View.VISIBLE else View.GONE
        panelRemaining?.apply {
            text = when {
                !hasTotal -> getString(R.string.pill_panel_unavailable)
                model.mood == PillMood.RECOVERY -> getString(R.string.time_bank_claim_recovery)
                model.mood == PillMood.LIMIT -> getString(R.string.pill_panel_spent)
                else -> getString(R.string.pill_panel_remaining, model.remainingMinutes ?: 0L)
            }
            setTextColor(colour(R.color.alive_paper))
            alpha = 0.82f
        }
        val rowKeys = model.rows.map { it.packageName to it.minutes }
        when (PillRowUpdate.action(
            shownPackages = renderedRows.map { it.first },
            nextPackages = model.rows.map { it.packageName },
            shownCurrent = renderedCurrent,
            nextCurrent = watchedPackage,
            shownMinutes = renderedRows.map { it.second },
            nextMinutes = model.rows.map { it.minutes }
        )) {
            PillRowUpdate.Action.REBUILD -> trace("pill.rows") {
                renderedCurrent = watchedPackage
                val container = panelRows ?: return@trace
                container.removeAllViews()
                rowViews = model.rows.map { row ->
                    appRow(row.packageName, row.minutes, row.packageName == watchedPackage).also {
                        container.addView(it.view)
                    }
                }
            }

            PillRowUpdate.Action.NUMBERS -> trace("pill.rows.update") {
                model.rows.forEachIndexed { index, row ->
                    rowViews.getOrNull(index)?.minutes?.text = minutesText(row.minutes)
                }
            }

            PillRowUpdate.Action.NOTHING -> Unit
        }
        renderedRows = rowKeys
        panelAppsLabel?.visibility = if (model.rows.isEmpty()) View.GONE else View.VISIBLE
        panelMore?.apply {
            visibility = if (model.hiddenApps > 0) View.VISIBLE else View.GONE
            text = getString(R.string.pill_panel_more, model.hiddenApps)
        }
    }

    private fun applyCharacter(face: AliveFace, crossfade: Boolean) {
        val image = panelCharacter ?: return
        if (renderedFace == face) return
        val hadFace = renderedFace != null
        renderedFace = face
        if (!crossfade || !hadFace || !animationsAllowed()) {
            crossfadePlacement = false
            image.animate().cancel()
            placeFace(image, face)
            // The morph owns this view's alpha. A swap that is not a crossfade
            // hands it back to whatever the clock says now, never to 1 - which
            // is how a refresh landing mid-morph used to light the character up
            // inside a closing pill.
            image.alpha = currentFrame.characterAlpha
            return
        }
        // The dip, the swap, the fade back in. A morph that starts mid-dip takes
        // the swap over in [settleCharacterCrossfade], so this end action only
        // runs while the crossfade still owns the character.
        crossfadePlacement = true
        image.animate().cancel()
        image.animate()
            .alpha(0f)
            .setDuration(70L)
            .withEndAction {
                if (!crossfadePlacement) return@withEndAction
                crossfadePlacement = false
                placeFace(image, face)
                image.animate().alpha(1f).setDuration(110L).start()
            }
            .start()
    }

    private fun placeFace(image: ImageView, face: AliveFace) {
        image.setImageResource(AliveStates.faceSpriteFor(face))
        image.contentDescription = getString(AliveStates.faceDescription(face))
    }

    /**
     * Called when a morph takes the content in hand: the character's own
     * animator is retired so it cannot keep animating alpha behind the shell's
     * clock, and a swap that had not landed yet lands now.
     */
    private fun settleCharacterCrossfade() {
        val image = panelCharacter ?: return
        val waiting = crossfadePlacement
        crossfadePlacement = false
        image.animate().cancel()
        if (waiting) renderedFace?.let { placeFace(image, it) }
        image.alpha = currentFrame.characterAlpha
    }

    private fun updateProgressBar(progress: Float?, mood: PillMood) {
        val track = progressTrack ?: return
        if (progress == null) {
            track.visibility = View.GONE
            return
        }
        track.visibility = View.VISIBLE
        val fillColour = if (mood == PillMood.LIMIT) {
            R.color.alive_red_short_mid
        } else {
            R.color.alive_amber_action
        }
        progressFill?.background = rounded(colour(fillColour), px(2))
        setProgressFillWidth(progress)
    }

    private fun setProgressFillWidth(progress: Float) {
        val fill = progressFill ?: return
        // The track lives inside the panel's 16dp side padding; its width is
        // known without waiting for a layout pass, including during the
        // expansion animation.
        val inner = (panelWidthPx() - px(32)).coerceAtLeast(1)
        val width = (inner * progress.coerceIn(0f, 1f)).toInt().coerceIn(0, inner)
        fill.layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.MATCH_PARENT)
        fill.requestLayout()
    }

    private fun faceFor(pose: PillPose): AliveFace = when (pose) {
        // VIDEO and SOCIAL share the phone pose: the app name is the only
        // context the system can prove, and the sprite set has no separate
        // activity for either. It is the character holding a phone, not a claim
        // about scrolling or watching.
        PillPose.VIDEO, PillPose.SOCIAL -> AliveFace.PHONE
        PillPose.GAMING -> AliveFace.EXCITED
        PillPose.BROWSING -> AliveFace.THINKING
        PillPose.APPROACHING -> AliveFace.WORRIED
        PillPose.LIMIT -> AliveFace.ANGRY
        PillPose.RECOVERY -> AliveFace.SLEEPY
        PillPose.IDLE -> AliveFace.NORMAL
    }

    private data class RowHolder(val view: View, val minutes: TextView)

    private fun minutesText(minutes: Long): String =
        getString(R.string.stat_minutes, minutes) + " " + getString(R.string.unit_minutes_suffix)

    private fun appRow(packageName: String, minutes: Long, current: Boolean): RowHolder {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(5), 0, px(5))
            // Explicit full width: the weighted name must be able to push the
            // value to the right edge, not collapse into a wrapped row.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(
            smallText(
                trace("pill.label") { AppLabels.labelFor(this, packageName) },
                12.5f,
                if (current) 1f else 0.8f
            ).apply {
                if (current) setTextColor(colour(R.color.alive_amber_action))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        val minutesView = smallText(minutesText(minutes), 12f, 0.8f)
            .apply { setPadding(px(10), 0, 0, 0) }
        row.addView(minutesView)
        return RowHolder(row, minutesView)
    }

    private fun resizePanelToContent() {
        val root = root ?: return
        val header = header ?: return
        val panel = panel ?: return
        if (panel.visibility != View.VISIBLE) return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        // The width is recomputed, not reused: a rotation or a split-screen
        // resize changes the panel's whole budget, and the y offset follows the
        // cutout on the new orientation.
        val width = panelWidthPx()
        val y = topOffsetPx()
        holdPanelWidth(panel, width)
        val headerHeight = header.height.takeIf { it > 0 } ?: header.measuredHeight
        val height = headerHeight + measurePanel(panel, width)
        if (params.width == width && params.height == height && params.y == y) return
        params.width = width
        params.height = height
        params.y = y
        window?.let { manager -> runCatching { manager.updateViewLayout(root, params) } }
        // The corner came along for the ride through the morph; a rotation that
        // resizes the panel must not leave it at whatever fraction the animation
        // was interrupted at.
        applyRadius(1f, currentRadiusPx, px(EXPANDED_RADIUS_DP))
        lastProgress?.let { setProgressFillWidth(it) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTimerInkPadding()
        if (expanded && sizeAnimator == null) resizePanelToContent()
    }

    private fun openDashboard() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        dismiss()
    }

    // ---------- the words ----------

    private fun refresh() {
        if (pill == null) return
        var app = watchedPackage ?: return

        // A pill that cannot draw or cannot read must not stay up. Both grants can
        // be revoked while the service runs, and neither sends a callback.
        if (!Settings.canDrawOverlays(this) || !PermissionChecks.hasUsageAccess(this)) {
            dismiss()
            return
        }

        // Screen off means the app is gone: there is nothing to read over, and a
        // pill that waits through the night wakes up as a ghost over the launcher.
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false) {
            dismiss()
            return
        }

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        discardPreviousDayReport()

        // A session does not outlive its day: at midnight the number and the
        // allowance beside it would otherwise describe two different days.
        if (LockStateManager.dayKeyOf(startedAtWall) != LockStateManager.dayKeyOf(nowWall)) {
            val midnight = usageTracker.getTodayStartTimestamp()
            startedAtWall = midnight
            startedAtElapsed = nowElapsed - (nowWall - midnight)
            lastReportAtElapsed = 0
            lastReport = null
        }

        // What is actually in front, read from the usage log rather than assumed.
        // The accessibility service is the fast path for this, but it can be off -
        // and with it off nothing told this service that the person had left, so a
        // pill started for YouTube stayed on screen for fifteen hours.
        val front = PillForegroundProbe.latest(this, FOREGROUND_WINDOW_MILLIS)
        if (front != null) {
            val decision = PillWatchPolicy.decide(
                watchedPackage = app,
                foregroundPackage = front.packageName,
                foregroundAt = front.at,
                startedAtWall = startedAtWall,
                nowWall = nowWall,
                isEntertainment = AppClassifier.categorize(this, front.packageName) ==
                    AppCategory.ENTERTAINMENT,
                isTransientWindow = AppClassifier.isTransientWindow(front.packageName)
            )
            when (decision) {
                PillWatchPolicy.Decision.Stop -> {
                    dismiss()
                    return
                }

                is PillWatchPolicy.Decision.Follow -> {
                    // The person moved to another entertainment app. The timer
                    // starts at zero: it counts this app, and a freshly opened
                    // app has not been used yet. The no-reset case was about
                    // the shade, not about a real switch.
                    app = decision.packageName
                    watchedPackage = app
                    watching = app
                    startSession()
                }

                PillWatchPolicy.Decision.Keep -> Unit
            }
        }

        val sessionMinutes = (nowElapsed - startedAtElapsed) / 60_000

        // The day's report is refreshed at most once a minute, on the same
        // tracker instance so the classification cache survives between reads.
        if (lastReport == null || nowElapsed - lastReportAtElapsed >= REPORT_REFRESH_MILLIS) {
            // Read on the worker: asking the platform about a day of events
            // measured 218-499 ms on the phone, longer than a whole morph. Until
            // the answer lands, the last good report keeps the numbers on screen.
            requestReport()
        }

        val report = lastReport
        renderTraffic()
        // The number is the session's own clock; the lights carry the day's
        // state. The only time the number itself turns crimson is a reached
        // limit - a recovery day is green, not spent. The reading is offered on
        // every tick but written only when no morph owns the window: it is the
        // one string whose width decides the window's own.
        headerText.offer(Durations.of(this, sessionMinutes))
        numberView?.let { number ->
            val limitReached = trafficState.active == TrafficLight.RED && !trafficState.pulse
            number.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (limitReached) R.color.alive_red_short_mid else R.color.alive_amber_action
                )
            )
        }
        writeHeaderText()

        // The expanded state re-reads the same report, never a second one. A
        // refresh during the size animation is skipped; the next one lands.
        if (expanded) {
            renderPanel(report, usageTracker.lockStateManager.isLocked())
            if (sizeAnimator == null) resizePanelToContent()
        }
    }

    /**
     * The lights are status, so they are recomputed from the same report the
     * guard uses, never from a second calculation. Only the pulse is animation;
     * everything else is a steady alpha.
     */
    private fun renderTraffic() {
        val state = PillTrafficPolicy.from(
            lastReport,
            usageTracker.lockStateManager.isLocked()
        )
        trafficState = state
        val motion = animationsAllowed()
        // All three lights keep their full colour. The only moving part is the
        // red halo; with motion off, the red ring is the static stand-in for
        // the approaching state.
        redLight?.ringed = PillTrafficPolicy.shouldRing(state, motion)
        if (PillTrafficPolicy.shouldPulse(state, motion)) {
            startTrafficPulse()
        } else {
            stopTrafficPulse()
        }
        updateHeaderDescription()
    }

    private fun startTrafficPulse() {
        if (pulseAnimator != null) return
        val red = redLight ?: return
        pulseAnimator = ValueAnimator.ofFloat(1f, TRAFFIC_PULSE_MIN_GLOW).apply {
            duration = TRAFFIC_PULSE_HALF_MILLIS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { red.glowFraction = it.animatedValue as Float }
            start()
        }
    }

    private fun stopTrafficPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        // A cancelled pulse can leave the halo mid-fade; the core never moved.
        redLight?.glowFraction = 1f
    }

    private fun trafficDescription(state: TrafficLightState): String = getString(
        when (state.active) {
            TrafficLight.RED ->
                if (state.pulse) R.string.pill_traffic_approaching
                else R.string.pill_traffic_limit

            TrafficLight.YELLOW -> R.string.pill_traffic_normal
            TrafficLight.GREEN -> R.string.pill_traffic_recovery
            TrafficLight.NONE -> R.string.pill_traffic_unknown
        }
    )

    /**
     * The compact reading is also the button that opens the day's detail. Its
     * description carries the timer, the lights' status and the action, because
     * a clickable header with decorative children would otherwise be announced
     * as a nameless button.
     */
    private fun updateHeaderDescription() {
        val number = numberView?.text?.toString().orEmpty()
        val action = getString(
            if (expanded) R.string.pill_panel_collapse else R.string.pill_panel_expand
        )
        header?.contentDescription = resources.getString(
            R.string.pill_panel_description,
            number,
            trafficDescription(trafficState),
            action
        )
    }

    /**
     * A new sitting for the app in front: both clocks restart, and the per-app
     * visit count is re-read.
     *
     * Called when the pill is raised and when a real switch moves it to another
     * entertainment app - never for the shade or a dialog, because those are
     * windows over the app rather than visits. The rule that keeps it honest is
     * that the number always describes the app named beside it.
     */
    private fun startSession() {
        startedAtWall = System.currentTimeMillis()
        startedAtElapsed = SystemClock.elapsedRealtime()
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

        private const val TAG = "InfoPillService"
        private const val CHANNEL_ID = "alive_pill"
        private const val NOTIFICATION_ID = 7401
        private const val TICK_MILLIS = 20_000L
        private const val ARRIVE_DELAY_MILLIS = 800L

        /**
         * The morph's own clock: a slightly longer open than the old 230ms, so
         * the staggered content has room to settle, and a shorter close, because
         * leaving should never feel like it is asking permission. The open is the
         * one with a settle at the end - the close lands and stops.
         */
        private const val EXPAND_CAP_MILLIS = 740L
        private const val COLLAPSE_MILLIS = 210L

        /**
         * How long a continuation may be ticked for. It is a ceiling, not a
         * duration: the spring reports arrival and the run ends there, and this
         * only decides when the animator stops supplying frames if it never does.
         */
        private const val SPRING_MILLIS = 900L

        /**
         * The rest of an arrival a tap interrupted: about a third of the arrival
         * itself, so the shell is done presenting itself before the morph has
         * read a quarter of its own first frame's worth of movement.
         */
        private const val ARRIVAL_FINISH_MILLIS = 90L

        /**
         * The corner's two homes. Compact is the pill it has always been; the
         * expanded panel keeps its corner softer so the shape reads as one
         * continuous surface instead of a box that grew inside another box.
         */
        private const val COMPACT_RADIUS_DP = 26
        private const val EXPANDED_RADIUS_DP = 34

        /**
         * One light is an 18dp slot holding a 10dp core and the 4dp of halo
         * that makes it read as lit. The slots touch, and the group carries the
         * same 14dp from the timer that the pill carries on its outer edges.
         */
        private const val TRAFFIC_SLOT_DP = 18
        private const val TRAFFIC_GAP_DP = 14

        /**
         * One full pulse is 1.2s: 600ms down, 600ms back. The halo dims, the
         * core stays at full colour.
         */
        private const val TRAFFIC_PULSE_HALF_MILLIS = 600L
        private const val TRAFFIC_PULSE_MIN_GLOW = 0.35f

        /** Every sprite is drawn on 16 cells, exactly like the app's sprites. */
        private const val SPRITE_CELL = 16
        private const val SPRITE_HALF = 8

        /** The expanded island stays a panel, not a page. */
        private const val PANEL_MAX_WIDTH_DP = 320
        private const val PANEL_MIN_WIDTH_DP = 280

        /** How old the day's report may be before the pill reads it again. */
        private const val REPORT_REFRESH_MILLIS = 60_000L

        /** How far back the foreground check reads. See [PillForegroundProbe]. */
        const val FOREGROUND_WINDOW_MILLIS = 30 * 60_000L

        /** How far down from the very top edge the pill hangs on a centred hole. */
        private const val TUCK_DP = 2

        fun start(context: Context, packageName: String? = null) {
            // A foreground-service start can be refused by the platform (background
            // restrictions, OEM policy). The pill is decoration next to the guard
            // that shares this process, so a refusal must not take that guard down
            // with it - the next real request can try again.
            runCatching {
                context.startForegroundService(
                    Intent(context, InfoPillService::class.java)
                        .putExtra(EXTRA_PACKAGE, packageName)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, InfoPillService::class.java)) }
        }
    }
}