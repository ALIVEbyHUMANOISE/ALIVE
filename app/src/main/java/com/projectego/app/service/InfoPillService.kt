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
import android.media.AudioManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.BatteryManager
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.NightContractRepository
import com.projectego.app.data.PillSettings
import com.projectego.app.data.ReminderSettings
import com.projectego.app.domain.AppClassifier
import com.projectego.app.domain.NightContract
import com.projectego.app.domain.PillArrival
import com.projectego.app.domain.PillHeaderText
import com.projectego.app.domain.PillMood
import com.projectego.app.domain.PillMorphChoreography
import com.projectego.app.domain.PillMorphEngine
import com.projectego.app.domain.PillPanelPolicy
import com.projectego.app.domain.BatteryWarningPolicy
import com.projectego.app.domain.PillAccent
import com.projectego.app.domain.PillCharacterCycle
import com.projectego.app.domain.PillPose
import com.projectego.app.domain.PillPosePolicy
import com.projectego.app.domain.PillReportSchedule
import com.projectego.app.domain.PillRowUpdate
import com.projectego.app.domain.PillStatusRingPolicy
import com.projectego.app.domain.PillTheme
import com.projectego.app.domain.PillTimerInk
import com.projectego.app.domain.RingSpec
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
import com.projectego.app.ui.PillDropView
import com.projectego.app.ui.PillEyesView
import com.projectego.app.ui.PillMoonView
import com.projectego.app.ui.PillShellDrawable
import com.projectego.app.ui.PillStatusRingView
import com.projectego.app.ui.PillWaveformView
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
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
    private var header: FrameLayout? = null
    private var contentRow: LinearLayout? = null
    private var numberView: TextView? = null
    // The redesign: one ring where there were three lights, and one slot that
    // holds either the ripple or the character's eyes - never both, and never
    // nothing that would resize the pill when one replaces the other.
    private var statusRing: PillStatusRingView? = null
    /** The waveform's slot; it leaves the layout whenever the slot is empty. */
    private var waveSlot: FrameLayout? = null
    private var eyesView: PillEyesView? = null
    /** The compact reminder's own little face, inside the reserved slot. */
    private var reminderSlotDrop: PillDropView? = null
    private var waveform: PillWaveformView? = null
    private var trafficState = TrafficLightState(TrafficLight.NONE)
    private var pulseAnimator: ValueAnimator? = null

    // The ten-minute visit. The due runnable is the only scheduler; the frame
    // runnable only exists while a visit is playing, so an idle pill has no
    // per-frame work at all.
    private var characterDue: Runnable? = null
    private var characterFrame: Runnable? = null
    private var cycleStartedAt = 0L
    private var waveAnimator: ValueAnimator? = null
    private var wavePhase = 0f

    // Whether anything is actually playing audio right now. The ripple is a
    // media indicator, not decoration: with nothing playing the slot stays empty
    // and no animation runs at all.
    private var musicPlaying = false

    /** The panel's resting bloom: the visit's light, held low while it is open. */
    private var auraAnimator: ValueAnimator? = null

    /**
     * Created lazily: the pill only asks for it on a tick, and a focus session
     * that is not running costs one preferences read.
     */
    private val focusManager by lazy { FocusSessionManager(this, TimeBankRepository(this)) }
    /** The low-battery warning's short window inside the number, and its latch. */
    private var batteryWarnUntil = 0L
    private var batteryWarnLevel = -1
    private var batteryWarned = false
    private val audioManager: AudioManager? by lazy {
        getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    /**
     * Two seconds is a compromise: fast enough that a TikTok clip brings the
     * ripple up while it is still playing, slow enough that the query costs
     * nothing measurable. It runs only while the pill is on screen.
     */
    private val audioProbe = object : Runnable {
        override fun run() {
            val manager = audioManager ?: return
            setMusicActive(runCatching { manager.isMusicActive }.getOrDefault(false))
            ticker.postDelayed(this, AUDIO_PROBE_MILLIS)
        }
    }
    private var panel: LinearLayout? = null
    private var panelPager: PagerScrollView? = null
    /** The three pages the chevrons navigate; one page in view at a time. */
    private var panelPages: List<LinearLayout> = emptyList()
    private var panelPrev: TextView? = null
    private var panelNext: TextView? = null
    private var panelDrinkLine: TextView? = null
    private var panelPromiseLine: TextView? = null
    /** Which of the three pages is in view; the chevrons follow it. */
    private var panelPageIndex = 0
    private var panelCharacter: ImageView? = null
    private var panelTotal: TextView? = null
    private var panelUnit: TextView? = null
    private var panelRemaining: TextView? = null
    /** The one line that explains the day, in the sideways strip. */
    private var panelLine: TextView? = null
    private var panelAppsLabel: TextView? = null
    private var panelRows: LinearLayout? = null
    private var panelMore: TextView? = null
    private var panelAction: TextView? = null
    private var progressTrack: FrameLayout? = null
    private var progressFill: View? = null
    private var lastProgress: Float? = null

    /**
     * A reminder is the one other thing this window may show. It keeps its own
     * panel because the usage panel's fields describe a day, and a reminder has
     * no day: it has one sentence and two answers.
     */
    private var reminderKind: ReminderKind? = null
    private var reminderPanel: LinearLayout? = null
    private var reminderTitle: TextView? = null
    private var reminderKicker: TextView? = null
    private var reminderBody: TextView? = null
    private var reminderPrimary: TextView? = null
    private var reminderSecondary: TextView? = null
    /** When the reminder rose, so an answer given elsewhere can end it. */
    private var reminderRaisedAt = 0L
    /** True when this reminder is the app's rehearsal, not the schedule's. */
    private var reminderIsPreview = false
    /**
     * The reading a reminder interrupted, if any. A focus countdown is the
     * person's actual task; answering "drank it" must not leave them without
     * the clock they were watching.
     */
    private var restoreAfterReminder: String? = null

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
    private var rootBackground: PillShellDrawable? = null
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
    /**
     * The panel's first attention, paid while the pill idles.
     *
     * The first tap used to pay for everything the panel had never done - the
     * app labels, the text layout, the character's sprite, the first measure -
     * and that measured 77 ms against 4 ms on every later tap, which is a
     * hitch exactly once and always on the open people see first. Doing it
     * here, after the arrival has finished, moves that cost to a moment
     * nobody is looking at; the panel stays hidden and the next tap finds a
     * panel that has already been measured.
     */
    private val warm = Runnable {
        // The ripple starts here, not at attach: the arrival and the first
        // measure deserve a still pill, and one animator that begins with the
        // warm-up has no timer of its own to clean up.
        startWave()
        val panel = panel ?: return@Runnable
        if (!isRunning || expanded || pill == null || reminderKind != null) return@Runnable
        trace("pill.warm") {
            renderPanel(lastReport, usageTracker.lockStateManager.isLocked())
            measurePanel(panel, panelWidthPx())
        }
    }
    /**
     * A reminder does not wait for a tap. The delay leaves the arrival its own
     * 240ms and a beat of stillness, so the open reads as the next sentence of
     * the same entrance rather than two animations fighting.
     */
    private val expandReminder = Runnable {
        if (isRunning && pill != null && !expanded && reminderKind != null) expand()
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) dismiss()
        }
    }
    /**
     * A note's own buttons answer the reminder while the island is still up.
     * The prefs change is the fact, but this is the signal that the surface
     * showing the question should leave now rather than on the next 20s tick.
     */
    private val reminderAnswered = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (reminderKind != null) finishReminder()
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
        ContextCompat.registerReceiver(
            this,
            reminderAnswered,
            IntentFilter(ACTION_REMINDER_ANSWERED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        application.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    private fun screenInteractive(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false

    private fun dismiss() {
        isRunning = false
        watching = null
        isReminderShowing = false
        reminderKind = null
        reminderIsPreview = false
        restoreAfterReminder = null
        // Only the pill's own clocks are stopped. Sweeping the whole queue here used
        // to drop a report completion that was already on its way, and the schedule
        // would keep the slot busy for the rest of the service's life - so a pill
        // restarted after a screen-off never read the day again.
        ticker.removeCallbacks(tick)
        ticker.removeCallbacks(arrive)
        ticker.removeCallbacks(warm)
        ticker.removeCallbacks(expandReminder)
        removeWindow()
        stopSelf()
    }

    private fun removeWindow() {
        stopMorph()
        morphEngine = null
        arrival.release()
        stopTrafficPulse()
        auraAnimator?.cancel()
        auraAnimator = null
        stopCharacterCycle()
        panel?.animate()?.cancel()
        reminderPanel?.animate()?.cancel()
        contentRow?.animate()?.cancel()
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
        val reminder = ReminderKind.of(intent?.getStringExtra(EXTRA_REMINDER))
        val preview = intent?.getBooleanExtra(EXTRA_REMINDER_PREVIEW, false) == true
        val asked = intent?.getStringExtra(EXTRA_PACKAGE)
        if ((asked == null && reminder == null) || !PillSettings.isEnabled(this) ||
            !Settings.canDrawOverlays(this) || !screenInteractive()
        ) {
            dismiss()
            return START_NOT_STICKY
        }
        // An explicit request outranks a wave-away: the person asking for the
        // island again while it is hidden must not be ignored for thirty seconds.
        EgoAccessibilityService.clearPillHidden()
        // A reminder has no app to measure. Moving the window from one kind of
        // content to the other rebuilds it, because the compact header, the
        // hidden slots and the open panel all belong to the previous kind.
        val previousReminder = reminderKind
        if (previousReminder != reminder && pill != null) {
            ticker.removeCallbacks(expandReminder)
            removeWindow()
            expanded = false
            currentFrame = PillMorphChoreography.rest(false)
        }
        // The number belongs to the app named beside it: moving to another app
        // starts that app's reading at zero, because a just-opened Instagram
        // showing TikTok's twenty minutes would be a lie. What was wrong before
        // was the *spurious* reset - the shade, a dialog, a stop event - not a
        // real switch.
        discardPreviousDayReport()
        // A reminder may arrive over a focus countdown or a reading; remember
        // what it covered, but only if this is a fresh interruption rather than
        // a repeated reminder.
        if (reminder != null && previousReminder == null && watchedPackage != null) {
            restoreAfterReminder = watchedPackage
        }
        if (reminder == null) restoreAfterReminder = null
        reminderKind = reminder
        isReminderShowing = reminder != null
        reminderIsPreview = reminder != null && preview
        reminderRaisedAt = System.currentTimeMillis()
        if (reminder != null) {
            watchedPackage = null
            watching = null
        } else {
            val previousPackage = watchedPackage
            watchedPackage = asked
            if (previousPackage == null || previousPackage != asked) {
                startSession()
            }
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
            // Deferred past the arrival, so the warm-up can never stutter the
            // entrance it is meant to protect. A reminder has nothing to warm:
            // its panel has no report to read.
            if (reminder == null) {
                ticker.postDelayed(warm, WARM_DELAY_MILLIS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        watching = null
        isReminderShowing = false
        reminderKind = null
        reminderIsPreview = false
        restoreAfterReminder = null
        ticker.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOff) }
        runCatching { unregisterReceiver(reminderAnswered) }
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        reportSchedule.close()
        reportExecutor.shutdownNow()
        removeWindow()
        headerText.clear()
        expanded = false
        root = null
        header = null
        numberView = null
        statusRing = null
        waveSlot = null
        eyesView = null
        reminderSlotDrop = null
        waveform = null
        trafficState = TrafficLightState(TrafficLight.NONE)
        panel = null
        panelPager = null
        panelPages = emptyList()
        panelPrev = null
        panelNext = null
        panelDrinkLine = null
        panelPromiseLine = null
        panelPageIndex = 0
        panelCharacter = null
        panelTotal = null
        panelUnit = null
        panelRemaining = null
        panelLine = null
        panelAppsLabel = null
        panelRows = null
        panelMore = null
        panelAction = null
        progressTrack = null
        progressFill = null
        lastProgress = null
        reminderPanel = null
        reminderTitle = null
        reminderKicker = null
        reminderBody = null
        reminderPrimary = null
        reminderSecondary = null
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
        // The reading row is settled before the window measures it: the slot
        // and its balance enter together, so the first frame is already the
        // state every later frame inherits.
        syncSlot()

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

        if (reminderKind == null) {
            // The ten-minute visit starts with the pill and dies with it. Both are
            // decorations on the same window, so one lifecycle owns both.
            startCharacterCycle()

            // Read the day while the pill is still arriving, so the numbers are
            // usually there before anyone taps - and the first tap never waits.
            requestReport()
        } else {
            // A reminder is not a reading: there is no app and no day. The
            // compact island still has a face - the character's eyes take the
            // slot and the ring wears the chosen light - so it is not a bare
            // word. The panel opens by itself a beat after the arrival.
            waveform?.alpha = 0f
            reminderSlotDrop?.visibility = View.VISIBLE
            statusRing?.apply {
                visibility = View.VISIBLE
                spec = RingSpec(
                    color = PillSettings.accent(this@InfoPillService).color,
                    glow = 0.55f,
                    pulsing = false,
                    ringed = false
                )
                progress = 1f
            }
            numberView?.text = getString(reminderCompactLabel())
            applyTimerInkPadding()
            updateHeaderDescription()
            ticker.removeCallbacks(expandReminder)
            ticker.postDelayed(expandReminder, REMINDER_EXPAND_DELAY_MILLIS)
        }

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
        // The window's y is measured from below the status bar, so a negative
        // tuck is what rides the island up into that inset. Four dp lifts it
        // noticeably while staying clear of the status-bar icons, which sit in
        // the middle of the bar rather than at its bottom edge.
        val tuck = ((TUCK_DP - LIFT_DP) * density).toInt()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return tuck

        val display = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return tuck
        val cutout = runCatching { display.cutout }.getOrNull() ?: return tuck
        val bounds = cutout.boundingRects.firstOrNull() ?: return tuck

        val width = resources.displayMetrics.widthPixels
        val centered = abs(bounds.exactCenterX() - width / 2f) < width / 8f
        return if (centered) tuck else maxOf(tuck, cutout.safeInsetTop)
    }

    /**
     * The window's one swipe gesture, owned here rather than by the header.
     *
     * The header used to be the only listener, which is why the expanded panel
     * could not be waved away: a finger that starts on the panel's rows or on a
     * reminder's buttons never touched the one view that was listening. This
     * intercepts at the window, so the same upward swipe works over any of it,
     * while a tap still falls through to whatever was touched - nothing is
     * intercepted until the finger has travelled the threshold, and the moment
     * it does, the child receives a cancel instead of a click.
     */
    private inner class SwipeAwayRoot(context: Context) : LinearLayout(context) {

        private var downX = 0f
        private var downY = 0f
        private var swiping = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> track(event)
                MotionEvent.ACTION_MOVE -> if (swiping || crossed(event)) {
                    swiping = true
                    return true
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Two paths share one answer. When a child (the header, a button)
            // owns the gesture, the intercept above catches the swipe and this
            // receives the tail. When the finger is on plain panel ground, no
            // child owns anything, so the window itself keeps the gesture - and
            // with it the same swipe.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> track(event)
                MotionEvent.ACTION_MOVE -> if (crossed(event)) swiping = true
                MotionEvent.ACTION_UP -> if (swiping) {
                    swiping = false
                    hidePillForAWhile()
                }
                MotionEvent.ACTION_CANCEL -> swiping = false
            }
            return true
        }

        private fun track(event: MotionEvent) {
            downX = event.rawX
            downY = event.rawY
            swiping = false
        }

        private fun crossed(event: MotionEvent): Boolean {
            val dx = event.rawX - downX
            val dy = event.rawY - downY
            // Vertical on purpose: a sideways drift over the panel's rows is a
            // failed tap, not a dismissal.
            return dy <= -px(SWIPE_HIDE_DP) && abs(dx) < abs(dy)
        }
    }

    /**
     * The panel's three pages, on one horizontal deck, the way NUSA's notch
     * carries its modules: a swipe slides between them, the chevrons either
     * side step one page, and the dots jump straight to a page. A release
     * snaps to whichever page is more than halfway into view. The wave-away
     * above only claims vertical gestures, so the two directions never fight
     * over one finger.
     */
    private inner class PagerScrollView(context: Context) : HorizontalScrollView(context) {

        /** The width one page occupies; pages are pinned to the window's width. */
        var pageWidth = 0
        var onPageChanged: ((Int) -> Unit)? = null
        private var lastPage = 0

        // Release handling: the finger's speed decides whether a short flick
        // already earns the next page, and the snap itself runs on a damped
        // spring instead of the platform's weightless 250ms smoothScrollTo.
        private var snapAnimator: ValueAnimator? = null
        private var downX = 0f
        private var downScrollX = 0
        private var lastX = 0f
        private var lastT = 0L
        private var velocity = 0f

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        override fun onScrollChanged(horizontal: Int, vertical: Int, oldHorizontal: Int, oldVertical: Int) {
            super.onScrollChanged(horizontal, vertical, oldHorizontal, oldVertical)
            val page = currentPage(horizontal)
            if (page != lastPage) {
                lastPage = page
                onPageChanged?.invoke(page)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val running = snapAnimator
                    snapAnimator = null
                    running?.cancel()
                    downX = event.rawX
                    downScrollX = scrollX
                    lastX = event.rawX
                    lastT = event.eventTime
                    velocity = 0f
                }

                MotionEvent.ACTION_MOVE -> {
                    val dt = event.eventTime - lastT
                    if (dt > 0) velocity = (event.rawX - lastX) / dt
                    lastX = event.rawX
                    lastT = event.eventTime
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val nearest = currentPage(scrollX)
                    val dragged = abs(scrollX - downScrollX)
                    // A long drag already chose its page by distance. Only a
                    // short flick lets the finger's speed earn the neighbour:
                    // a full-page drag followed by a fast release must not
                    // skip a second page just because the synthetic velocity
                    // of the last few frames is high.
                    val scrollVelocity = -velocity
                    val page = if (dragged < pageWidth * 0.45f && abs(scrollVelocity) > 0.35f) {
                        (nearest + if (scrollVelocity > 0f) 1 else -1).coerceIn(0, 2)
                    } else {
                        nearest
                    }
                    animateToPage(page)
                }
            }
            return super.onTouchEvent(event)
        }

        override fun computeScroll() {
            // While our spring owns the scroll, the platform's own scroller
            // must stay out of it or the two would trade frames.
            if (snapAnimator == null) super.computeScroll()
        }

        /** Steps to [page] with the same weighted release as a finger swipe. */
        fun moveToPage(page: Int) {
            animateToPage(page.coerceIn(0, 2))
        }

        private fun animateToPage(page: Int) {
            if (pageWidth <= 0) return
            val target = page.coerceIn(0, 2) * pageWidth
            val from = scrollX
            val running = snapAnimator
            snapAnimator = null
            running?.cancel()
            lastPage = page
            onPageChanged?.invoke(page)
            if (from == target) {
                snapAnimator = null
                return
            }
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = PAGE_SNAP_MILLIS
                interpolator = WeightedSnap()
                addUpdateListener { value ->
                    val fraction = value.animatedValue as Float
                    scrollTo(from + ((target - from) * fraction).roundToInt(), 0)
                }
                addListener(object : AnimatorListenerAdapter() {
                    private fun finish() {
                        if (snapAnimator === this@apply) {
                            snapAnimator = null
                            scrollTo(target, 0)
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) = finish()
                    override fun onAnimationCancel(animation: Animator) = finish()
                })
            }
            snapAnimator = anim
            anim.start()
        }

        private fun currentPage(horizontal: Int): Int =
            if (pageWidth > 0) ((horizontal + pageWidth / 2) / pageWidth).coerceIn(0, 2) else 0
    }

    /**
     * A critically-damped-feeling arrival for the page deck: it leaves with
     * speed, overshoots by a few percent and settles. The old smooth scroll
     * started slow and stopped dead, which is why the deck had no weight.
     */
    private class WeightedSnap : android.view.animation.Interpolator {
        // Slow to leave, quick through the middle, then a small settle past
        // the page and back. The old curve reached its target in the first
        // fifth of the clock, which is why the deck felt weightless.
        private val curve = android.view.animation.PathInterpolator(0.42f, 0f, 0.18f, 1.04f)
        override fun getInterpolation(t: Float): Float = curve.getInterpolation(t)
    }

    private fun build(): LinearLayout {
        // Pure black, not the app's ink: the island is its own surface, and on
        // an OLED the camera hole and the pill become one uninterrupted dark.
        // The expanded panel and the compact reading share it, so the state
        // change never flips the colour under the header. The drawable is held
        // separately: its corner is the one part of the shape that moves.
        val surface = PillShellDrawable().apply {
            cornerRadius = px(COMPACT_RADIUS_DP).toFloat()
            auraColor = PillSettings.accent(this@InfoPillService).color
        }
        rootBackground = surface
        currentRadiusPx = px(COMPACT_RADIUS_DP)
        val root = SwipeAwayRoot(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            background = surface
        }
        this.root = root

        val number = text("0", TIMER_TEXT_SP, R.font.space_grotesk_bold, R.color.alive_amber_action)
            .apply { setTextColor(PillTheme.TIMER_COLOR) }
        numberView = number
        // The timer is its own measured width, not a reserved two-digit box: the
        // pill hugs [slot][timer][ring] and the whole row is what sits on the
        // window's centre line, so nothing balances the slot from the far side.
        // The cost is that a digit change widens the pill and moves the ring by
        // half a digit - the symmetric trade.
        number.gravity = Gravity.CENTER
        number.maxLines = 1
        applyTimerInkPadding()

        // The ripple is the pill's ordinary sign of life; the eyes are a guest
        // that takes the whole notch when it arrives. The slot is in the row
        // only while it holds something, and the row as a whole is centred, so
        // an empty slot simply leaves - no balancing spacer behind it.
        val wave = PillWaveformView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                px(CHARACTER_SLOT_DP), px(CHARACTER_SLOT_DP), Gravity.CENTER
            )
        }
        waveform = wave
        // A reminder's compact pill is not just a word: one water drop lives
        // in the slot the ripple usually owns, so the island still has a mark
        // when it is only saying "minum".
        val slotDrop = PillDropView(this).apply {
            layoutParams = FrameLayout.LayoutParams(px(14), px(16), Gravity.CENTER)
            color = PillSettings.accent(this@InfoPillService).color
            withEyes = false
            glow = 0.45f
            visibility = View.GONE
        }
        reminderSlotDrop = slotDrop
        val characterSlot = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                px(CHARACTER_SLOT_DP), px(CHARACTER_SLOT_DP)
            ).apply { marginEnd = px(CHARACTER_GAP_DP) }
            addView(wave)
            addView(slotDrop)
            // Empty by default: syncSlot() settles it before the window is added.
            visibility = View.GONE
        }
        waveSlot = characterSlot

        // One ring, not three lights. It stays decorative: the header remains the
        // single touch target, and the ring carries the same status the lights did.
        val ring = PillStatusRingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(RING_SLOT_DP), px(RING_SLOT_DP))
        }
        statusRing = ring
        val rightGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(RING_GAP_DP), 0, 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(ring)
        }
        val leftGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(characterSlot)
            addView(number)
        }

        val header = FrameLayout(this).apply {
            setPadding(px(HEADER_PADDING_DP), px(4), px(HEADER_PADDING_DP), px(4))
            // iPhone proportions, on the owner's instruction: the island should
            // hug its content, not hang below it. The whole header stays the
            // touch target; it is narrower than 48dp by his decision.
            minimumHeight = px(COMPACT_HEIGHT_DP)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.pill_panel_expand)
            // A swipe up anywhere on the window is "not now" - the window root
            // owns that gesture (see SwipeAwayRoot), so a tap here is always
            // exactly a tap.
            setOnClickListener { toggleExpanded() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
            addView(leftGroup)
            addView(rightGroup)
        }
        contentRow = row
        header.addView(row)

        // The character's own layer: centred on the notch and larger than the
        // ripple's slot. The reading row stays measured behind it while it fades
        // in, which is what stops the window from resizing every five minutes.
        val eyes = PillEyesView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                px(EYES_OVERLAY_WIDTH_DP), px(EYES_OVERLAY_HEIGHT_DP), Gravity.CENTER
            )
            haloColor = PillSettings.accent(this@InfoPillService).color
        }
        eyesView = eyes
        header.addView(eyes)

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

        val reminderCard = buildReminderPanel()
        reminderPanel = reminderCard
        root.addView(
            reminderCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    /**
     * A reminder is two lines and two answers, in the same shell, the same
     * insets and the same ink as the usage panel. White is the sentence; amber
     * is the one action. "Later" is deliberately not amber - it is an answer,
     * not the offer.
     */
    /**
     * A reminder's own card: the character's eyes in a lit tile, a kicker, a
     * title and one line of sense - exactly one row, the same height as the
     * day's page. The two answers do not live here; they sit in the header
     * band above, so nothing in this panel can make the shell grow downward.
     */
    private fun buildReminderPanel(): LinearLayout {
        val accent = PillSettings.accent(this@InfoPillService).color
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // The drop and its words are one centred group, held a touch
            // above the middle so the card reads even under its button band.
            gravity = Gravity.CENTER
            setPadding(px(PANEL_PADDING_DP), px(4), px(PANEL_PADDING_DP), px(16))
            minimumHeight = px(122)
            visibility = View.GONE
        }

        val tile = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(44), px(44)).apply {
                marginEnd = px(10)
            }
            background = rounded(ColorUtils.setAlphaComponent(accent, 46), px(14))
        }
        // The character in its water mood: the same drop as the compact mark,
        // now with the eyes that make it ALIVE's own.
        val tileDrop = PillDropView(this).apply {
            layoutParams = FrameLayout.LayoutParams(px(30), px(36), Gravity.CENTER)
            color = accent
            withEyes = true
            glow = 0.75f
        }
        tile.addView(tileDrop)
        panel.addView(tile)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val kicker = text("", 9.5f, R.font.manrope_medium, R.color.alive_paper).apply {
            setTextColor(accent)
            letterSpacing = 0.12f
        }
        reminderKicker = kicker
        column.addView(kicker)

        val title = text("", 17.5f, R.font.space_grotesk_bold, R.color.alive_paper).apply {
            setTextColor(PillTheme.TIMER_COLOR)
            maxLines = 1
            maxWidth = px(130)
            ellipsize = TextUtils.TruncateAt.END
        }
        reminderTitle = title
        column.addView(title)

        val body = smallText("", 11.5f, 0.72f).apply {
            maxLines = 1
            maxWidth = px(130)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, px(2), 0, 0)
        }
        reminderBody = body
        column.addView(body)
        panel.addView(column)

        // The two answers sit beside the content now, stacked at its right:
        // the card is one centred group with no separate band to hunt for.
        val answers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = px(10) }
        }
        val later = capsule("", primary = false).apply {
            setOnClickListener { answerReminder(primary = false) }
        }
        reminderSecondary = later
        answers.addView(later)

        val primary = capsule("", primary = true).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(6) }
            setOnClickListener { answerReminder(primary = true) }
        }
        reminderPrimary = primary
        answers.addView(primary)
        panel.addView(answers)
        return panel
    }

    private fun renderReminder(kind: ReminderKind) {
        reminderKicker?.text = getString(R.string.pill_reminder_kicker)
        reminderTitle?.text = getString(reminderTitleRes(kind))
        reminderBody?.text = getString(reminderBodyRes(kind))
        reminderPrimary?.text = getString(reminderPrimaryRes(kind))
        reminderSecondary?.text = getString(reminderSecondaryRes(kind))
        updateHeaderDescription()
    }

    private fun answerReminder(primary: Boolean) {
        when (val kind = reminderKind) {
            ReminderKind.DRINK -> if (primary) {
                DrinkReminderWatch.markDrank(this)
            } else {
                DrinkReminderWatch.snooze(this)
            }

            ReminderKind.PROMISE -> if (primary) {
                PromiseWatch.clear(this)
                openPromise()
                // The app is the destination; restoring a pill over it would
                // only be torn down by the activity callback a frame later.
                dismiss()
                return
            } else {
                PromiseWatch.clear(this)
            }

            null -> Unit
        }
        finishReminder()
    }

    /**
     * Leaves the reminder and returns whatever it interrupted. The clock starts
     * a fresh visit because the window is a new window; a focus countdown reads
     * the session, so its minutes are unchanged.
     */
    private fun finishReminder() {
        val restore = restoreAfterReminder
        dismiss()
        if (restore != null && PillSettings.isEnabled(this) && Settings.canDrawOverlays(this)) {
            // The old window has to finish leaving first; starting the same
            // foreground service in the same turn is a request the platform may
            // fold into the one that is still stopping.
            val context = applicationContext
            Handler(Looper.getMainLooper()).postDelayed(
                { start(context, restore) },
                REMINDER_RESTORE_DELAY_MILLIS
            )
        }
    }

    private fun openPromise() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(PromiseWatch.EXTRA_OPEN_PROMISE, true)
                }
            )
        }
    }

    /**
     * The reminder may be answered somewhere else - the note's own buttons, or
     * the promise sheet in ALIVE. Reading the fact rather than owning it is what
     * keeps the island from continuing to ask a question already answered.
     */
    private fun refreshReminder(kind: ReminderKind) {
        // A rehearsal was asked for on the spot; nothing in the schedule -
        // an old snooze, a glass logged before it - can have answered it.
        if (reminderIsPreview) return
        val now = System.currentTimeMillis()
        when (kind) {
            ReminderKind.DRINK -> {
                val answered = ReminderSettings.lastDrinkAt(this) >= reminderRaisedAt
                // A snooze only ends *this* reminder when it was set after it
                // rose. Reading it against `now` made an old snooze from an
                // earlier reminder kill a fresh preview in its first frame.
                val snoozed = ReminderSettings.snoozeUntil(this) >= reminderRaisedAt
                if (answered || snoozed) finishReminder()
            }

            ReminderKind.PROMISE -> {
                val writtenTonight = NightContractRepository(this).stored()?.nightKey ==
                    com.projectego.app.domain.NightContract.nightKeyOf(now)
                if (writtenTonight) finishReminder()
            }
        }
    }

    private fun activePanel(): LinearLayout? =
        if (reminderKind != null) reminderPanel else panel

    /**
     * The expanded state: the character, the day's real figures, and the way
     * back into the app. Black continues the pill; the character keeps its
     * paper tile because its ink features would disappear on the dark ground.
     */
    /**
     * The expanded state: one figure, one line under it, one way back.
     *
     * No divider, no full-width bar, no second ring: the header's ring already
     * carries the day's proportion, and the panel's job is the number that is
     * left with the app that spent it.
     */
    /**
     * The expanded state, the way NUSA's notch carries its modules: one page
     * in view at a time, and two chevrons either side to step between pages.
     * A swipe over the deck moves it too, and a release snaps to the nearest
     * page. The deck keeps the panel's old height - one row, the same floor -
     * so the expanded island did not grow downward with its pages.
     *
     * Three pages live on the deck: the day's reading, the water reminder's
     * status, and tomorrow's promise. The settings behind them stay in the
     * app; each page carries one action that opens where it belongs.
     */
    private fun buildPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val deck = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val prev = chevron("\u2039", getString(R.string.pill_page_previous)) {
            panelPager?.moveToPage(panelPageIndex - 1)
        }
        panelPrev = prev
        deck.addView(prev)

        val pager = PagerScrollView(this)
        panelPager = pager
        pager.onPageChanged = { page ->
            panelPageIndex = page
            updatePagerChrome()
        }
        val pages = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val timePage = buildTimePage()
        val drinkPage = buildDrinkPage()
        val promisePage = buildPromisePage()
        panelPages = listOf(timePage, drinkPage, promisePage)
        pages.addView(timePage)
        pages.addView(drinkPage)
        pages.addView(promisePage)
        pager.addView(
            pages,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        deck.addView(
            pager,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val next = chevron("\u203a", getString(R.string.pill_page_next)) {
            panelPager?.moveToPage(panelPageIndex + 1)
        }
        panelNext = next
        deck.addView(next)
        panel.addView(deck)

        panelPageIndex = 0
        updatePagerChrome()
        return panel
    }

    /** One chevron: 40dp of the deck, a full touch target, dimmed at its end. */
    private fun chevron(glyph: String, description: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = glyph
            textSize = 24f
            typeface = ResourcesCompat.getFont(this@InfoPillService, R.font.space_grotesk_bold)
            setTextColor(PillTheme.TIMER_COLOR)
            includeFontPadding = false
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minWidth = px(CHEVRON_COLUMN_DP)
            minimumWidth = px(CHEVRON_COLUMN_DP)
            minHeight = px(44)
            minimumHeight = px(44)
            contentDescription = description
            setOnClickListener { onClick() }
        }

    /** The day's page: the reading the pill has always unfolded. */
    private fun buildTimePage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            // The reading is a centred group, held a touch above the middle:
            // the header band above it already carries weight, and the same
            // floor on every page keeps the deck one height.
            setPadding(px(PANEL_PADDING_DP), px(4), px(PANEL_PADDING_DP), px(16))
            minimumHeight = px(84)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        // White is the reading's colour everywhere: the compact timer is white,
        // so the panel's figure is too. Amber is left to the one action.
        val total = text("", 26f, R.font.space_grotesk_bold, R.color.alive_paper)
            .apply { setTextColor(PillTheme.TIMER_COLOR) }
        panelTotal = total
        val unit = text(
            getString(R.string.pill_panel_left_suffix),
            11.5f,
            R.font.manrope_medium,
            R.color.alive_paper
        ).apply {
            setPadding(px(7), 0, 0, px(4))
            alpha = 0.72f
        }
        panelUnit = unit
        hero.addView(total)
        hero.addView(unit)
        column.addView(hero)

        val line = smallText("", 11.5f, 0.7f).apply {
            maxLines = 1
            maxWidth = px(120)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, px(2), 0, 0)
        }
        panelLine = line
        column.addView(line)
        row.addView(column)

        // The day page's own answer wears the same amber capsule as the two
        // reminder pages: a pill, not a floating link.
        val action = capsule(getString(R.string.pill_panel_open), primary = true).apply {
            textSize = 11.5f
            setPadding(px(10), 0, px(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = px(12)
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener { openDashboard() }
        }
        panelAction = action
        row.addView(action)
        page.addView(row)
        return page
    }

    /**
     * The water page, dressed like the reminder card: a lit drop tile, the
     * reminder kicker, the title and a short cadence, with its answer in an
     * amber capsule. One row, so the deck keeps the day page's height.
     */
    private fun buildDrinkPage(): LinearLayout {
        val accent = PillSettings.accent(this@InfoPillService).color
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(PANEL_PADDING_DP), px(4), px(PANEL_PADDING_DP), px(16))
            minimumHeight = px(84)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        }
        row.addView(
            pageTile(
                PillDropView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(px(18), px(24), Gravity.CENTER)
                    color = accent
                    glow = 0.5f
                }
            )
        )
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        column.addView(pageKicker(accent))
        column.addView(pageTitle(getString(R.string.reminders_water_heading)))
        val line = pageLine()
        panelDrinkLine = line
        column.addView(line)
        row.addView(column)
        row.addView(
            capsule(getString(R.string.pill_page_drink_action), primary = true).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = px(8)
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener { openReminders() }
            }
        )
        page.addView(row)
        return page
    }

    /** The promise page, same dress with the moon mark. */
    private fun buildPromisePage(): LinearLayout {
        val accent = PillSettings.accent(this@InfoPillService).color
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(PANEL_PADDING_DP), px(4), px(PANEL_PADDING_DP), px(16))
            minimumHeight = px(84)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        }
        row.addView(
            pageTile(
                PillMoonView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(px(20), px(20), Gravity.CENTER)
                    color = accent
                    glow = 0.5f
                }
            )
        )
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        column.addView(pageKicker(accent))
        column.addView(pageTitle(getString(R.string.pill_page_promise_title)))
        val line = pageLine()
        panelPromiseLine = line
        column.addView(line)
        row.addView(column)
        row.addView(
            capsule(getString(R.string.pill_page_promise_action_short), primary = true).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = px(8)
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener { openPromise() }
            }
        )
        page.addView(row)
        return page
    }

    /** The small lit square both reminder pages wear, on the chosen palette. */
    private fun pageTile(glyph: View): FrameLayout {
        val accent = PillSettings.accent(this@InfoPillService).color
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(44), px(44)).apply { marginEnd = px(8) }
            background = rounded(ColorUtils.setAlphaComponent(accent, 46), px(13))
            addView(glyph)
        }
    }

    private fun pageKicker(accent: Int): TextView =
        text("", 9.5f, R.font.manrope_medium, R.color.alive_paper).apply {
            text = getString(R.string.pill_reminder_kicker)
            setTextColor(accent)
            letterSpacing = 0.12f
            maxLines = 1
        }

    private fun pageTitle(value: String): TextView =
        text(value, 19f, R.font.space_grotesk_bold, R.color.alive_paper).apply {
            setTextColor(PillTheme.TIMER_COLOR)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun pageLine(): TextView =
        smallText("", 12f, 0.72f).apply {
            maxLines = 1
            maxWidth = px(110)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, px(2), 0, 0)
        }

    /**
     * A real button rather than a word: an amber capsule with ink on it, or a
     * quiet glass capsule. The same object is used in the reminder's header
     * band and at the foot of the two reminder pages.
     */
    private fun capsule(label: String, primary: Boolean): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            typeface = ResourcesCompat.getFont(this@InfoPillService, R.font.manrope_medium)
            setTextColor(
                if (primary) colour(R.color.alive_ink) else PillTheme.TIMER_COLOR
            )
            includeFontPadding = false
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minHeight = px(30)
            minimumHeight = px(30)
            setPadding(px(12), 0, px(12), 0)
            background = rounded(
                if (primary) colour(R.color.alive_amber_action) else 0x1FFFFFFF,
                px(15)
            )
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
        val number = numberView ?: return
        if (number.text.toString() == text) return

        // A morph owns the window; a reading that arrives mid-morph is written
        // without ceremony, because the window is already moving.
        if (!animationsAllowed() || sizeAnimator != null) {
            number.text = text
            applyTimerInkPadding()
            updateHeaderDescription()
            return
        }

        // The digital roll: the old reading leaves downward, the new one arrives
        // from above. The width is swapped while the ink is invisible, so the
        // window's own resize is never seen as a jump.
        number.animate().cancel()
        number.animate()
            .alpha(0f)
            .translationY(px(DIGIT_SLIDE_DP).toFloat())
            .setDuration(DIGIT_OUT_MILLIS)
            .withEndAction {
                number.text = text
                applyTimerInkPadding()
                number.translationY = -px(DIGIT_SLIDE_DP).toFloat()
                number.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DIGIT_IN_MILLIS)
                    .start()
                updateHeaderDescription()
                }
            .start()
    }

    /**
     * Lands an in-flight digit roll on its answer before a morph takes over.
     *
     * The roll is a private animation inside the reading; left running under
     * the shell's morph, it would slide the digits while everything else is
     * meant to be still. [PillHeaderText.pending] carries the value the roll
     * was about to show, because [writeHeaderText] consumed it when the roll
     * began.
     */
    private fun settleDigitRoll() {
        // A reminder's compact label is not a reading: it has no digit roll to
        // land, and the last usage reading must not replace the word.
        if (reminderKind != null) return
        val number = numberView ?: return
        number.animate().cancel()
        number.alpha = 1f
        number.translationY = 0f
        val text = headerText.pending() ?: return
        if (number.text.toString() == text) return
        number.text = text
        applyTimerInkPadding()
        updateHeaderDescription()
    }

    private fun smallText(value: String, sizeSp: Float, alpha: Float): TextView =
        text(value, sizeSp, R.font.manrope_medium, R.color.alive_paper).apply {
            this.alpha = alpha
        }

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /**
     * Eases the shell's bloom to [target].
     *
     * The panel holds a low one while it is open - the same light the character
     * visits bring, so the two states share one vocabulary. A visit owns the
     * same value at full strength, and a visit is always stopped before the
     * panel opens, so they never fight over it.
     */
    private fun animateAuraTo(target: Float, millis: Long) {
        val surface = rootBackground ?: return
        auraAnimator?.cancel()
        if (!animationsAllowed()) {
            surface.aura = target
            return
        }
        auraAnimator = ValueAnimator.ofFloat(surface.aura, target).apply {
            duration = millis
            addUpdateListener { surface.aura = it.animatedValue as Float }
            start()
        }
    }

    /**
     * The number's colour, in one place so the state and the tick can never
     * disagree. A focus countdown is never crimson - focus time is not
     * entertainment - and a battery warning always is.
     */
    private fun applyTimerInk() {
        val number = numberView ?: return
        val warning = SystemClock.elapsedRealtime() < batteryWarnUntil
        val limitReached = trafficState.active == TrafficLight.RED && !trafficState.pulse
        number.setTextColor(
            if (PillTimerInk.isCrimson(focusMinutesRemaining() != null, limitReached, warning))
                ContextCompat.getColor(this, R.color.alive_red_short_mid)
            else PillTheme.TIMER_COLOR
        )
    }

    /** Minutes left in the running focus session, or null when none is running. */
    private fun focusMinutesRemaining(): Long? {
        val focus = runCatching { focusManager.getFocusSession() }.getOrNull() ?: return null
        if (!focus.isActive) return null
        val left = focus.endTimestamp - System.currentTimeMillis()
        return (left / 60_000L).coerceAtLeast(0L)
    }

    /**
     * One warning per unplugged drop through fifteen percent.
     *
     * The level is read from BatteryManager and the charging state from the
     * sticky battery broadcast - both without a permission. The warning lives
     * for [BATTERY_WARN_MILLIS] inside the pill's own number, so it cannot add a
     * second surface or change the window's size.
     */
    private fun maybeWarnBattery() {
        val level = runCatching {
            (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)
        val charging = runCatching {
            val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)
        if (charging) {
            batteryWarned = false
            return
        }
        if (!BatteryWarningPolicy.shouldWarn(level, charging, batteryWarned)) return
        batteryWarned = true
        batteryWarnLevel = level
        batteryWarnUntil = SystemClock.elapsedRealtime() + BATTERY_WARN_MILLIS
    }

    /**
     * A swipe up is the person saying "not now".
     *
     * The island leaves with the mirror of its arrival - up, a little smaller,
     * gone in 200 ms and with no overshoot, because leaving must not look like
     * it is asking to be looked at. The guard service brings it back after its
     * window if the same app is still in front.
     */
    private fun hidePillForAWhile() {
        val view = pill ?: return
        if (!animationsAllowed()) {
            EgoAccessibilityService.hidePillFor(this)
            dismiss()
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(-px(HIDE_LIFT_DP).toFloat())
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(HIDE_MILLIS)
            .withEndAction {
                EgoAccessibilityService.hidePillFor(this@InfoPillService)
                dismiss()
            }
            .start()
    }

    // ---------- the ten-minute visit ----------

    /**
     * Arms the visit clock for the pill that was just raised.
     *
     * One runnable is the whole schedule: it fires, plays a visit when the pill
     * is compact and motion is allowed, and re-arms itself. Nothing else in the
     * service knows about the interval, so there is no second timer to disagree
     * with this one and no way to stack two visits.
     */
    private fun startCharacterCycle() {
        stopCharacterCycle()
        val due = Runnable { fireCharacterVisit() }
        characterDue = due
        ticker.postDelayed(due, PillCharacterCycle.INTERVAL_MILLIS)
        startAudioWatch()
    }

    private fun fireCharacterVisit() {
        // An open panel owns the interaction; the next interval gets its turn.
        // Reduced motion skips the visit entirely: the eyes are decoration, and
        // decoration never overrides that setting.
        if (!expanded && pill != null && animationsAllowed()) {
            playCharacterVisit()
        }
        characterDue?.let { ticker.postDelayed(it, PillCharacterCycle.INTERVAL_MILLIS) }
    }

    private fun playCharacterVisit() {
        val eyes = eyesView ?: return
        stopCharacterFrame()
        cycleStartedAt = SystemClock.elapsedRealtime()
        // The character reads the clock: alert in the morning, sleepy late.
        val mood = PillCharacterCycle.moodFor(
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )
        val frame = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.elapsedRealtime() - cycleStartedAt
                val next = PillCharacterCycle.eyesAt(elapsed, mood)
                eyes.expression = next.expression
                eyes.reveal = next.alpha
                eyes.heightScale = next.heightScale
                eyes.gaze = next.gaze
                eyes.sleepPhase = next.sleepPhase
                // The shell lights up behind the eyes, exactly like the
                // reference island: black at the top, blue under the character.
                rootBackground?.aura = next.alpha
                // And the notch becomes the eyes: the reading and the ring fade
                // out with the same alpha, then come back when the visit ends.
                contentRow?.alpha = 1f - next.alpha
                // The slot holds one guest at a time: the ripple yields while the
                // character is on screen and returns only if audio is still playing.
                syncSlot()
                if (PillCharacterCycle.isFinished(elapsed)) {
                    stopCharacterFrame()
                } else {
                    ticker.postDelayed(this, FRAME_MILLIS)
                }
            }
        }
        characterFrame = frame
        ticker.post(frame)
    }

    private fun stopCharacterFrame() {
        characterFrame?.let { ticker.removeCallbacks(it) }
        characterFrame = null
        eyesView?.reveal = 0f
        rootBackground?.aura = 0f
        contentRow?.alpha = 1f
        syncSlot()
    }

    /** Cancels both the schedule and any visit mid-flight; safe to call twice. */
    private fun stopCharacterCycle() {
        characterDue?.let { ticker.removeCallbacks(it) }
        characterDue = null
        stopCharacterFrame()
        stopWave()
        stopAudioWatch()
    }

    /**
     * The ripple runs on one animator rather than a posted frame loop, so it
     * stops when cancelled and costs nothing while the pill is down. Its phase
     * only moves while the slot is actually showing it.
     */
    private fun startWave() {
        if (waveAnimator != null || !animationsAllowed() || !musicPlaying) return
        val wave = waveform ?: return
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = WAVE_PERIOD_MILLIS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (wave.alpha <= 0f) return@addUpdateListener
                wavePhase = (it.animatedValue as Float) % 1f
                wave.phase = wavePhase
            }
            start()
        }
    }

    private fun stopWave() {
        waveAnimator?.cancel()
        waveAnimator = null
    }

    // ---------- music, not decoration ----------

    /**
     * The ripple means "audio is playing", so its source is the platform's own
     * playback state rather than a timer. [AudioManager.isMusicActive] needs no
     * permission and no microphone: it reports whether any app is rendering to
     * the media stream, which is exactly what a TikTok clip or a song does.
     */
    private fun startAudioWatch() {
        ticker.removeCallbacks(audioProbe)
        ticker.post(audioProbe)
    }

    private fun stopAudioWatch() {
        ticker.removeCallbacks(audioProbe)
        musicPlaying = false
        syncSlot()
        stopWave()
    }

    private fun setMusicActive(active: Boolean) {
        val changed = musicPlaying != active
        musicPlaying = active
        if (changed) {
            if (active) startWave() else stopWave()
        }
        // Always re-apply: the slot's presence is part of the layout, not just
        // the animation, so it must be settled even on the first probe.
        syncSlot()
    }

    /**
     * Settles the one reserved slot in a single place: the ripple is in the row
     * only while audio is playing, the reminder's drop only while there is a
     * reminder, and neither is there when the slot has no guest. The slot is the
     * row's own left edge and the whole row is centred, so an empty slot leaves
     * without a balancing spacer and without anything else moving.
     */
    private fun syncSlot() {
        val eyesOnScreen = (eyesView?.reveal ?: 0f) > 0f
        // One guest at a time: the ripple only while audio is playing, the
        // reminder's drop while there is a reminder to show. The slot leaving
        // or returning only changes the row's width - the row itself stays on
        // the centre line, so no translation, no glide, and nothing for a morph
        // to shift.
        val reserved = musicPlaying || reminderKind != null
        val slotVisibility = if (reserved) View.VISIBLE else View.GONE
        if (waveSlot?.visibility != slotVisibility) {
            waveSlot?.visibility = slotVisibility
        }
        // The ripple is the compact pill's sign of life; the open panel has its
        // own pages, so the guest hides while the reservation stays behind.
        waveform?.alpha = if (!eyesOnScreen && musicPlaying && !expanded) 1f else 0f
    }

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
        // A digit roll lives inside the reading; it lands before the morph
        // starts, so the timer cannot move under a shell that is supposed to
        // be the only thing changing.
        settleDigitRoll()
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
        val panel = activePanel() ?: return
        if (expanded) return
        // A visit must not follow the pill into the panel: the reading row is
        // faded out for the character, and the panel's own header is that same
        // row. Tapping during a visit ends it here rather than leaving the open
        // panel without its timer and ring for the rest of the thirty seconds.
        stopCharacterFrame()
        ticker.removeCallbacks(warm)
        ticker.removeCallbacks(expandReminder)
        discardPreviousDayReport()
        expanded = true
        // The reservation stays; only the ripple guest is sent home.
        syncSlot()
        val reminder = reminderKind
        // The header keeps the compact inset through the open. Its only child
        // is the reading row, which is centred in it: widening the inset here
        // would make the row narrower than its content for the first frames
        // while the window is still compact, and LinearLayout lays an overflow
        // from the left - the measured 17 px jump of the timer and ring at the
        // tap. The panel below keeps its own 22 dp inset; the header does not
        // need it because nothing in it is left-aligned.
        //
        // A reminder's answers ride in this band. They are 36dp tall; the
        // band keeps its 38dp height only if its own air is removed for the
        // reminder, and the panel below then gets the height it measured.
        if (reminder != null) {
            // A reminder has no timer or ring to keep in this band. It keeps
            // no band at all: the content owns the whole card and sits
            // dead-centre in it, instead of below an empty strip.
            header.setPadding(0, 0, 0, 0)
            header.minimumHeight = 0
        }
        // The slot is deliberately not touched here. Whatever the compact pill
        // reserved is what the open panel reserves, so the timer and the ring
        // keep the same centre line through the whole morph instead of being
        // nudged by half a slot when the panel takes over.
        if (reminder == null) reminderPanel?.visibility = View.GONE
        // The island lights up as it opens: the same bloom the character visits
        // bring, held quiet so it is a surface, not a lamp.
        animateAuraTo(PANEL_AURA, AURA_OPEN_MILLIS)
        if (reminder == null) {
            // A reading is requested, never waited for: the first frame of the
            // morph must not sit behind a UsageStats query. Until it lands the
            // panel shows the last good report, or the honest "no numbers yet"
            // state.
            requestReport()
            // The opening sets the pose directly and lets the entrance carry it;
            // only changes while open crossfade. This is the first-use-heavy part:
            // labels, the sprite and the row views are all built here, on the tap.
            trace("pill.expand.content") {
                renderTraffic()
                renderedFace = null
                renderPanel(lastReport, usageTracker.lockStateManager.isLocked())
            }
        } else {
            renderReminder(reminder)
            this.panel?.visibility = View.GONE
            // The compact label was the knock; the panel's title is the door.
            // Keeping both would say the same sentence twice on one screen.
            contentRow?.visibility = View.GONE
            eyesView?.visibility = View.GONE
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

        // Freeze the window at the size it has now, *before* the panel below is
        // measured at its full resting size: measuring a wrap_content window
        // against the whole screen is how the pill became a bar once.
        if (windowParams != null) {
            windowParams.width = fromW
            windowParams.height = fromH
            window?.let { manager ->
                runCatching { manager.updateViewLayout(root, windowParams) }
            }
        }

        val toW = panelWidthPx()
        // The content is laid out once at the width it will rest at and clipped
        // by the growing shell. Left as MATCH_PARENT it would re-measure and
        // re-wrap its labels on every morph frame, which is exactly the drift
        // between the shell and its content this morph exists to prevent.
        holdPanelWidth(panel, toW)
        pinPageWidths(toW)
        // The target is the header plus the panel it carries, measured the same
        // way [resizePanelToContent] measures a settled one - not the current
        // window height plus the panel, which would count a half-open shell's
        // leftover twice and hand back a panel with a strip of empty ink under it.
        //
        // The header is measured fresh, not read from its last layout: a
        // reminder swaps in a 36dp answer row and removes the band's air in
        // this same call, and the compact header's stale height made the
        // window 18px shorter than the panel it had to carry - the card's
        // last line was clipped by exactly that missing strip.
        header.measure(
            View.MeasureSpec.makeMeasureSpec(toW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val headerHeight = header.measuredHeight
        val toH = headerHeight + trace("pill.expand.measure") { measurePanel(panel, toW) }

        animateWindow(fromW, fromH, toW, toH, expanding = true)
    }

    private fun collapse() {
        val root = root ?: return
        val header = header ?: return
        val panel = activePanel() ?: return
        if (!expanded) return
        expanded = false
        // Back to the compact pill: the ripple may return to its reserved slot,
        // which never left the row, so nothing under the reading moves.
        syncSlot()
        header.setPadding(px(HEADER_PADDING_DP), px(4), px(HEADER_PADDING_DP), px(4))
        if (reminderKind != null) {
            header.minimumHeight = px(COMPACT_HEIGHT_DP)
            eyesView?.visibility = View.VISIBLE
            contentRow?.apply {
                visibility = View.VISIBLE
                alpha = 0f
            }
        }
        // Same rule on the way back: the row's reservation does not change, so
        // the reading is already exactly where the compact pill will keep it.
        animateAuraTo(0f, AURA_CLOSE_MILLIS)
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
            // The reminder's compact label comes back only once the panel is
            // gone; bringing it back at the start would show it through the
            // closing title.
            if (reminderKind != null) contentRow?.alpha = 1f
            // Only now does the panel give its width back: the compact row ends at
            // the timer + lights, and the reading is a group centred in it.
            releasePanelWidth()
            val back = root.layoutParams as? WindowManager.LayoutParams ?: return@animateWindow
            // Back to wrapping the reading: the timer changes width as its
            // digits change, and nothing else is in the row.
            back.width = WindowManager.LayoutParams.WRAP_CONTENT
            back.height = WindowManager.LayoutParams.WRAP_CONTENT
            back.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            back.x = 0
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
            params.width = toW and 1.inv()
            params.height = toH
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
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
            params.width = toW and 1.inv()
            params.height = toH
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
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
                // Even widths only: the window is centred by the system, and
                // an odd width puts its centre on a half pixel that flips every
                // other frame. That one-pixel shimmer is what read as the
                // timer and the ring trembling while the shell moved.
                params.width = (fromW + (toW - fromW) * f).toInt() and 1.inv()
                params.height = (fromH + (toH - fromH) * f).toInt()
                // x and width must travel in the same transaction: the system
                // applies a new width before it recentres a CENTER window, and
                // that one-frame gap moved the whole pill sideways.
                params.gravity = Gravity.TOP or Gravity.LEFT
                params.x = (resources.displayMetrics.widthPixels - params.width) / 2
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
     * The pixel size the morph last wrote, or the laid-out size once the window
     * is back to wrapping its content, or the measured size before a layout pass.
     */
    private fun morphSize(paramsSize: Int?, layoutSize: Int, measureSize: Int): Int =
        paramsSize?.takeIf { it > 0 } ?: layoutSize.takeIf { it > 0 } ?: measureSize

    /** Sets every animated part of the content to the frame the clock says. */

    private fun applyFrame(frame: PillMorphChoreography.Frame) {
        currentFrame = frame
        panel?.alpha = frame.panelAlpha
        reminderPanel?.alpha = frame.panelAlpha
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

    /**
     * Gives the panel's width back to the window. Called when the island closes:
     * while it is open the panel stays pinned at the window's own width, which is
     * what keeps a refresh from re-wrapping the rows.
     */
    private fun releasePanelWidth() {
        val panel = activePanel() ?: return
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
        // A report that started before the window became a reminder must not
        // paint a day over it. The read is still consumed; only its answer is
        // no longer wanted.
        if (reminderKind != null) return
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
            // The panel answers "how much is left", so the figure is the
            // remainder, not the total the compact notches already imply.
            text = if (hasTotal) (model.remainingMinutes ?: 0L).toString() else "—"
            // The same signal the compact number uses when the day is spent -
            // the large figure, not the sentence, carries the state.
            setTextColor(
                if (model.mood == PillMood.LIMIT) colour(R.color.alive_red_short_mid)
                else PillTheme.TIMER_COLOR
            )
        }
        // When the day is spent the unit would read "0 min left": the sentence
        // below says it better.
        panelUnit?.visibility =
            if (hasTotal && model.mood != PillMood.LIMIT) View.VISIBLE else View.GONE
        panelLine?.apply {
            // Only the states that need a word get one; an ordinary day is
            // explained by the apps that spent it.
            val sentence = when {
                !hasTotal -> getString(R.string.pill_panel_unavailable)
                model.mood == PillMood.RECOVERY -> getString(R.string.time_bank_claim_recovery)
                model.mood == PillMood.LIMIT -> getString(R.string.pill_panel_spent)
                else -> null
            }
            text = sentence ?: model.rows.take(1).joinToString("   ·   ") { row ->
                AppLabels.labelFor(this@InfoPillService, row.packageName) + " " +
                    minutesText(row.minutes)
            }
            setTextColor(colour(R.color.alive_paper))
            alpha = 0.8f
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
        val panel = activePanel() ?: return
        if (panel.visibility != View.VISIBLE) return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        // The width is recomputed, not reused: a rotation or a split-screen
        // resize changes the panel's whole budget, and the y offset follows the
        // cutout on the new orientation.
        val width = panelWidthPx()
        val y = topOffsetPx()
        holdPanelWidth(panel, width)
        pinPageWidths(width)
        val headerHeight = header.height.takeIf { it > 0 } ?: header.measuredHeight
        val height = headerHeight + measurePanel(panel, width)
        if (params.width == width && params.height == height && params.y == y) return
        params.width = width and 1.inv()
        params.height = height
        params.y = y
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = (resources.displayMetrics.widthPixels - params.width) / 2
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

    /** The water page's answer: the reminders' own page, opened in the app. */
    private fun openReminders() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_OPEN_REMINDERS, true)
                }
            )
        }
        dismiss()
    }

    /** Pins each page to the deck's width, so a slide lands page-on-page. */
    private fun pinPageWidths(width: Int) {
        val pageWidth = width - 2 * px(CHEVRON_COLUMN_DP)
        panelPager?.pageWidth = pageWidth
        panelPages.forEach { page ->
            val params = page.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
            if (params.width != pageWidth) {
                params.width = pageWidth
                page.layoutParams = params
            }
        }
    }

    /**
     * The two reminder pages, written from the same stores the app's page
     * reads. They are status, not controls: the one action each page carries
     * opens the app, because the settings themselves live in the app.
     */
    private fun renderInfoPages() {
        val now = System.currentTimeMillis()
        panelDrinkLine?.text = if (ReminderSettings.waterEnabled(this)) {
            getString(
                R.string.pill_page_water_cadence,
                ReminderSettings.intervalMinutes(this),
                ReminderSettings.startHour(this),
                ReminderSettings.endHour(this)
            )
        } else {
            getString(R.string.pill_page_water_off_short)
        }
        val stored = NightContractRepository(this).stored()
        val writtenTonight = stored?.nightKey == NightContract.nightKeyOf(now)
        panelPromiseLine?.text = getString(
            if (writtenTonight) R.string.pill_page_promise_written_short
            else R.string.pill_page_promise_empty_short
        )
    }

    /** A chevron dims at its own end of the deck. */
    private fun updatePagerChrome() {
        val page = panelPageIndex.coerceIn(0, 2)
        panelPrev?.apply {
            alpha = if (page > 0) 1f else 0.28f
            isClickable = page > 0
        }
        panelNext?.apply {
            alpha = if (page < 2) 1f else 0.28f
            isClickable = page < 2
        }
    }

    // ---------- the words ----------

    private fun refresh() {
        if (pill == null) return
        // A reminder has no app and no day to measure; its panel is already
        // rendered. It still gets a heartbeat here so an answer given in the
        // shade (or a promise written elsewhere) takes the stale island down.
        val reminder = reminderKind
        if (reminder != null) {
            refreshReminder(reminder)
            return
        }
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
        // The number is the session's own clock - unless a focus session owns
        // the hour, in which case the countdown to its end is the clock that
        // matters. The lights carry the day's state either way.
        maybeWarnBattery()
        val warning = SystemClock.elapsedRealtime() < batteryWarnUntil
        val focusLeft = focusMinutesRemaining()
        headerText.offer(
            when {
                warning -> "$batteryWarnLevel%"
                focusLeft != null -> Durations.of(this, focusLeft)
                else -> Durations.of(this, sessionMinutes)
            }
        )
        applyTimerInk()
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
        // One ring carries what three lights did: the colour is status, the
        // pulse is motion, and reduced motion swaps the pulse for a static ring.
        val ring = PillStatusRingPolicy.of(state, motion)
        statusRing?.spec = ring
        statusRing?.progress = ringProgress(lastReport)
        // The ink follows the state, not the tick: a report that lands between
        // ticks used to leave the number in the previous state's colour for up
        // to twenty seconds.
        applyTimerInk()
        if (ring.pulsing) {
            startTrafficPulse()
        } else {
            stopTrafficPulse()
        }
        updateHeaderDescription()
        renderInfoPages()
    }

    /**
     * How much of the day's allowance is already spent, for the ring's arc.
     *
     * A report that cannot be read answers 1 - a full ring - because an empty
     * arc would claim a day nobody measured, which is the same lie the guard
     * refuses to tell with the numbers.
     */
    private fun ringProgress(report: UsageReport?): Float {
        val limit = report?.limitMinutes ?: return 1f
        if (limit <= 0L) return 1f
        return (report.totalEntertainmentMinutes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }

    private fun startTrafficPulse() {
        if (pulseAnimator != null) return
        val ring = statusRing ?: return
        pulseAnimator = ValueAnimator.ofFloat(1f, TRAFFIC_PULSE_MIN_GLOW).apply {
            duration = TRAFFIC_PULSE_HALF_MILLIS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { value ->
                statusRing?.glowFraction = value.animatedValue as Float
            }
            start()
        }
    }

    private fun stopTrafficPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        // A cancelled pulse can leave the halo mid-fade; the ring never moved.
        statusRing?.glowFraction = 1f
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
        val action = getString(
            if (expanded) R.string.pill_panel_collapse else R.string.pill_panel_expand
        )
        val reminder = reminderKind
        if (reminder != null) {
            header?.contentDescription = getString(reminderTitleRes(reminder)) + ". " + action
            return
        }
        val number = numberView?.text?.toString().orEmpty()
        header?.contentDescription = resources.getString(
            R.string.pill_panel_description,
            number,
            trafficDescription(trafficState),
            action
        )
    }

    private fun reminderCompactLabel(): Int = when (reminderKind) {
        ReminderKind.DRINK -> R.string.pill_drink_compact
        ReminderKind.PROMISE -> R.string.pill_promise_compact
        null -> R.string.pill_traffic_unknown
    }

    private fun reminderTitleRes(kind: ReminderKind): Int = when (kind) {
        ReminderKind.DRINK -> R.string.pill_drink_title
        ReminderKind.PROMISE -> R.string.pill_page_promise_title
    }

    private fun reminderBodyRes(kind: ReminderKind): Int = when (kind) {
        ReminderKind.DRINK -> R.string.pill_drink_body
        ReminderKind.PROMISE -> R.string.pill_promise_body_short
    }

    private fun reminderPrimaryRes(kind: ReminderKind): Int = when (kind) {
        ReminderKind.DRINK -> R.string.pill_drink_done
        ReminderKind.PROMISE -> R.string.pill_promise_action
    }

    private fun reminderSecondaryRes(kind: ReminderKind): Int = when (kind) {
        ReminderKind.DRINK -> R.string.pill_drink_later
        ReminderKind.PROMISE -> R.string.pill_promise_later
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
        const val EXTRA_REMINDER = "com.projectego.app.extra.PILL_REMINDER"
        const val EXTRA_REMINDER_PREVIEW = "com.projectego.app.extra.PILL_REMINDER_PREVIEW"
        const val ACTION_REMINDER_ANSWERED = "com.projectego.app.action.REMINDER_ANSWERED"

        /** Opens the reminders' page in the app rather than Home. */
        const val EXTRA_OPEN_REMINDERS = "com.projectego.app.extra.OPEN_REMINDERS"

        /** For the tile's own state: the pill is up, so its next tap takes it down. */
        @Volatile
        var isRunning = false
            private set

        /**
         * A reminder owns the window right now. The accessibility service reads
         * this so its ordinary "the person left the entertainment app" cleanup
         * does not tear down a reminder that has nothing to do with that app.
         */
        @Volatile
        var isReminderShowing = false
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

        /** After the arrival's 240 ms, with room for a slow first frame. */
        private const val WARM_DELAY_MILLIS = 1_200L

        /**
         * The morph's own clock: a slightly longer open than the old 230ms, so
         * the staggered content has room to settle, and a shorter close, because
         * leaving should never feel like it is asking permission. The open is the
         * one with a settle at the end - the close lands and stops.
         */
        private const val EXPAND_CAP_MILLIS = 740L
        private const val COLLAPSE_MILLIS = 260L

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
        private const val COMPACT_RADIUS_DP = 19
        /**
         * The expanded corner is proportional to the panel's height, not a fixed
         * soft box: 34dp on a 113dp shell read as a card, not as an island.
         */
        private const val EXPANDED_RADIUS_DP = 46

        /**
         * The island's height. iPhone's island is 37pt; at 38dp this hugs its
         * content the same way. Below the 48dp touch guideline on purpose - the
         * owner asked for this proportion, and the header is still a wide target.
         */
        private const val COMPACT_HEIGHT_DP = 38

        /**
         * The compact row's rhythm: [12dp][18dp slot][6dp][timer][10dp][16dp ring][12dp].
         * The slot is reserved in both moods - ripple and eyes take turns inside
         * it - because a slot that appears only sometimes would resize the pill
         * every ten minutes, and the window is the one thing the morph owns.
         */
        private const val HEADER_PADDING_DP = 12

        /** The expanded shell's own inset: wider than the compact pill's air. */
        private const val PANEL_PADDING_DP = 22
        private const val CHARACTER_SLOT_DP = 18
        private const val CHARACTER_GAP_DP = 6
        private const val RING_SLOT_DP = 16
        private const val RING_GAP_DP = 10

        /**
         * The character's own layer, centred on the notch. Wider than the
         * ripple's slot because when it appears it *is* the notch: the reading
         * and the ring fade out behind it, exactly like the reference island.
         */
        private const val EYES_OVERLAY_WIDTH_DP = 48
        private const val EYES_OVERLAY_HEIGHT_DP = 20

        /** The timer's new voice: the already bundled grotesk, not the pixel font. */
        private const val TIMER_TEXT_SP = 17f

        /** About one frame at 60 Hz; the visit's frames are posted at this pace. */
        private const val FRAME_MILLIS = 16L

        /** One full ripple, slow enough to read as breathing rather than as data. */
        private const val WAVE_PERIOD_MILLIS = 2_400L

        /** How often the pill asks whether anything is playing. */
        private const val AUDIO_PROBE_MILLIS = 2_000L

        /** How far up a swipe has to travel before it counts as a wave-away. */
        private const val SWIPE_HIDE_DP = 22

        /** The departure: faster than the arrival, and it never overshoots. */
        private const val HIDE_MILLIS = 200L
        private const val HIDE_LIFT_DP = 14

        /** How long the low-battery warning holds the number. */
        private const val BATTERY_WARN_MILLIS = 4_000L

        /** After the arrival's 240ms, with a beat held still before the open. */
        private const val REMINDER_EXPAND_DELAY_MILLIS = 700L

        /** Long enough for a stopped pill window to leave before returning. */
        private const val REMINDER_RESTORE_DELAY_MILLIS = 120L

        /** The panel's resting bloom, and the time it takes to settle there. */
        private const val PANEL_AURA = 0.30f
        private const val AURA_OPEN_MILLIS = 260L
        private const val AURA_CLOSE_MILLIS = 210L

        /** One chevron's column on the deck; the pages get the rest of it. */
        private const val CHEVRON_COLUMN_DP = 40

        /** The deck's release: a weighted snap, not the platform's short scroll. */
        private const val PAGE_SNAP_MILLIS = 520L

        /** The digital roll: out, swap, in. Short enough to read as one motion. */
        private const val DIGIT_OUT_MILLIS = 90L
        private const val DIGIT_IN_MILLIS = 110L
        private const val DIGIT_SLIDE_DP = 3

        /**
         * One full pulse is 1.2s: 600ms down, 600ms back. The halo dims, the
         * core stays at full colour.
         */
        private const val TRAFFIC_PULSE_HALF_MILLIS = 600L
        private const val TRAFFIC_PULSE_MIN_GLOW = 0.35f

        /** Every sprite is drawn on 16 cells, exactly like the app's sprites. */
        private const val SPRITE_CELL = 16
        private const val SPRITE_HALF = 8

        /** The island widens sideways: this is a strip's budget, not a page's. */
        private const val PANEL_MAX_WIDTH_DP = 340
        private const val PANEL_MIN_WIDTH_DP = 300

        /** How old the day's report may be before the pill reads it again. */
        private const val REPORT_REFRESH_MILLIS = 60_000L

        /** How far back the foreground check reads. See [PillForegroundProbe]. */
        const val FOREGROUND_WINDOW_MILLIS = 30 * 60_000L

        /** How far down from the very top edge the pill hangs on a centred hole. */
        private const val TUCK_DP = 2

        /** How far the island rides up into the status-bar inset. */
        private const val LIFT_DP = 4

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

        /**
         * Raises the island for something that did not come from an app switch.
         *
         * The same foreground-service caveat applies: an alarm receiver is a
         * background caller, and a platform may refuse the start. The reminder's
         * notification has already been posted by then, so the fallback is the
         * shade rather than silence.
         */
        fun startForReminder(context: Context, kind: ReminderKind, preview: Boolean = false) {
            runCatching {
                context.startForegroundService(
                    Intent(context, InfoPillService::class.java)
                        .putExtra(EXTRA_REMINDER, kind.name)
                        .putExtra(EXTRA_REMINDER_PREVIEW, preview)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, InfoPillService::class.java)) }
        }
    }
}