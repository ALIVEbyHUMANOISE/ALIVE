package com.projectego.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
import com.projectego.app.domain.PillMood
import com.projectego.app.domain.PillPanelPolicy
import com.projectego.app.domain.PillPose
import com.projectego.app.domain.PillPosePolicy
import com.projectego.app.domain.PillTrafficPolicy
import com.projectego.app.domain.PillWatchPolicy
import com.projectego.app.domain.TrafficLight
import com.projectego.app.domain.TrafficLightState
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

    // What the panel already shows, so a 20-second refresh does not rebuild
    // views that did not change (and cannot flicker while it is open).
    private var renderedFace: AliveFace? = null
    private var renderedRows: List<Pair<String, Long>> = emptyList()
    private var renderedCurrent: String? = null

    // Two clocks for two questions: elapsed time for the session (a manual clock
    // change must not move it), wall time for the day it belongs to.
    private var startedAtElapsed = 0L
    private var startedAtWall = 0L

    // The day's report is expensive; the pill needs it at a human pace, not at a
    // tick pace.
    private var lastReportAtElapsed = 0L
    private var lastReport: UsageReport? = null

    private val usageTracker by lazy { UsageTracker(this) }

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ticker.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Only a request raises the pill. A null intent means the system restarted
        // the service on its own, and a restart is not a request - answering one
        // by re-showing the pill is how a reading nobody asked for appeared on
        // the home screen.
        val asked = intent?.getStringExtra(EXTRA_PACKAGE)
        if (asked == null || !PillSettings.isEnabled(this) || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // The number belongs to the app named beside it: moving to another app
        // starts that app's reading at zero, because a just-opened Instagram
        // showing TikTok's twenty minutes would be a lie. What was wrong before
        // was the *spurious* reset - the shade, a dialog, a stop event - not a
        // real switch.
        val previousPackage = watchedPackage
        watchedPackage = asked
        if (previousPackage == null || previousPackage != asked) {
            startSession()
        }

        val foregroundStarted = runCatching {
            startForeground(NOTIFICATION_ID, ongoingNote())
        }.isSuccess
        if (!foregroundStarted) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        watching = watchedPackage

        if (pill != null) {
            // The reading is already on screen. A switch between entertainment
            // apps changes the label, not the arrival - so it is not animated in
            // again and the number never blinks.
            ticker.removeCallbacksAndMessages(null)
            ticker.post(tick)
        } else {
            // The press that summons it happens in the shade, and an overlay is
            // drawn below the shade - so arriving immediately would mean arriving
            // while nobody can see it. Half a second is enough for that, and short
            // enough that it still feels like the press caused it. Any earlier
            // arrival is cleared first, so a quick app switch cannot queue two.
            ticker.removeCallbacksAndMessages(null)
            ticker.postDelayed({
                if (!isRunning) return@postDelayed
                show()
                ticker.post(tick)
            }, ARRIVE_DELAY_MILLIS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        watching = null
        ticker.removeCallbacksAndMessages(null)
        sizeAnimator?.cancel()
        sizeAnimator = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        panel?.animate()?.cancel()
        panelCharacter?.animate()?.cancel()
        expanded = false
        // It leaves the way it arrived: shrinking back towards the top edge,
        // rather than vanishing between two frames.
        val leaving = pill
        pill = null
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
        renderedFace = null
        renderedRows = emptyList()
        renderedCurrent = null
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
            stopSelf()
            return
        }
        pill = view

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
        // change never flips the colour under the header.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            background = rounded(Color.BLACK, px(26))
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
            // 14dp on the timer side, 12dp on the lights side; the lights'
            // 12dp slots keep their cores 2dp inside, so both visible edges
            // are 14dp.
            setPadding(px(14), px(8), px(12), px(8))
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
            // 72dp against the day's 24sp figure: the character is a companion
            // to the number, not a poster above it.
            layoutParams = LinearLayout.LayoutParams(px(72), px(72))
        }
        val character = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(spritePx(56), spritePx(56), Gravity.CENTER)
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
        // An interrupted animation is not stacked: the next state starts from
        // wherever the window currently is.
        sizeAnimator?.cancel()
        sizeAnimator = null
        if (expanded) collapse() else expand()
    }

    private fun expand() {
        val root = root ?: return
        val header = header ?: return
        val panel = panel ?: return
        if (expanded) return
        expanded = true
        ensureReport()
        renderTraffic()
        // The opening sets the pose directly and lets the entrance carry it;
        // only changes while open crossfade.
        renderedFace = null
        renderPanel(lastReport, usageTracker.lockStateManager.isLocked())
        panel.visibility = View.VISIBLE

        val animate = animationsAllowed()
        if (animate) {
            panel.alpha = 0f
            panelCharacter?.apply {
                alpha = 0f
                scaleX = 0.72f
                scaleY = 0.72f
            }
        } else {
            panel.alpha = 1f
            panelCharacter?.apply {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
            }
        }

        val fromW = root.width.takeIf { it > 0 } ?: header.measuredWidth
        val fromH = root.height.takeIf { it > 0 } ?: header.measuredHeight

        // Freeze the window at the compact size *before* the spacer gains its
        // weight: a weighted child in a wrap_content window measures against
        // the whole screen, which is how the pill became a bar once.
        val windowParams = root.layoutParams as? WindowManager.LayoutParams
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
        val toH = fromH + measurePanel(panel, toW)

        if (animate) {
            panel.animate().alpha(1f).setDuration(220L).start()
            panelCharacter?.animate()
                ?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
                ?.setStartDelay(50L)
                ?.setDuration(240L)
                ?.start()
            // The character and the day's figure first, then the app rows, then
            // the way out. Modest stagger only: the panel is already unfolding,
            // so each extra delay is a small settle, not a performance.
            panelRows?.apply {
                alpha = 0f
                translationY = px(4).toFloat()
                animate().alpha(1f).translationY(0f)
                    .setStartDelay(60L)
                    .setDuration(200L)
                    .start()
            }
            panelAction?.apply {
                alpha = 0f
                translationY = px(6).toFloat()
                animate().alpha(1f).translationY(0f)
                    .setStartDelay(100L)
                    .setDuration(220L)
                    .start()
            }
        } else {
            panelRows?.apply {
                alpha = 1f
                translationY = 0f
                animate().cancel()
            }
            panelAction?.apply {
                alpha = 1f
                translationY = 0f
                animate().cancel()
            }
        }
        animateWindow(fromW, fromH, toW, toH)
    }

    private fun collapse() {
        val root = root ?: return
        val header = header ?: return
        val panel = panel ?: return
        if (!expanded) return
        expanded = false
        updateHeaderDescription()
        if (animationsAllowed()) {
            panel.animate().alpha(0f).setDuration(180L).start()
        } else {
            panel.alpha = 0f
        }

        val params = root.layoutParams as? WindowManager.LayoutParams
        val fromW = params?.width?.takeIf { it > 0 } ?: root.width
        val fromH = params?.height?.takeIf { it > 0 } ?: root.height
        header.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        animateWindow(fromW, fromH, header.measuredWidth, header.measuredHeight) {
            panel.visibility = View.GONE
            panel.alpha = 1f
            // The compact row ends at the timer + lights; the spacer only
            // exists to pin them to the expanded panel's right edge.
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
     */
    private fun animateWindow(fromW: Int, fromH: Int, toW: Int, toH: Int, onEnd: () -> Unit = {}) {
        val root = root ?: return
        val manager = window ?: return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (!animationsAllowed()) {
            sizeAnimator?.cancel()
            sizeAnimator = null
            params.width = toW
            params.height = toH
            runCatching { manager.updateViewLayout(root, params) }
            onEnd()
            return
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EXPAND_MILLIS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { value ->
                val f = value.animatedFraction
                params.width = (fromW + (toW - fromW) * f).toInt()
                params.height = (fromH + (toH - fromH) * f).toInt()
                runCatching { manager.updateViewLayout(root, params) }
            }
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // A cancelled run belongs to a state that is already gone.
                if (sizeAnimator !== anim) return
                sizeAnimator = null
                params.width = toW
                params.height = toH
                runCatching { manager.updateViewLayout(root, params) }
                onEnd()
            }
        })
        sizeAnimator = anim
        anim.start()
    }

    private fun animationsAllowed(): Boolean =
        runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)

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

    private fun ensureReport() {
        if (lastReport != null) return
        lastReportAtElapsed = SystemClock.elapsedRealtime()
        lastReport = runCatching { usageTracker.calculateTodayEntertainmentUsage() }.getOrNull()
    }

    private fun renderPanel(report: UsageReport?, locked: Boolean) {
        val model = PillPanelPolicy.from(report, locked)
        val face = faceFor(
            PillPosePolicy.poseFor(report, locked, watchedPackage)
        )
        // During the expansion the face is set directly and the panel's own
        // entrance carries it. Only a pose change while the panel is already
        // open crossfades.
        applyCharacter(face, crossfade = expanded && sizeAnimator == null)

        lastProgress = model.progress
        updateProgressBar(model.progress, model.mood)
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
        if (rowKeys != renderedRows || renderedCurrent != watchedPackage) {
            renderedRows = rowKeys
            renderedCurrent = watchedPackage
            panelRows?.apply {
                removeAllViews()
                model.rows.forEach { row ->
                    addView(appRow(row.packageName, row.minutes, row.packageName == watchedPackage))
                }
            }
        }
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
        fun place() {
            image.setImageResource(AliveStates.faceSpriteFor(face))
            image.contentDescription = getString(AliveStates.faceDescription(face))
        }
        if (!crossfade || !hadFace || !animationsAllowed()) {
            image.animate().cancel()
            image.alpha = 1f
            place()
            return
        }
        image.animate().cancel()
        image.animate()
            .alpha(0f)
            .setDuration(70L)
            .withEndAction {
                place()
                image.animate().alpha(1f).setDuration(110L).start()
            }
            .start()
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

    private fun appRow(packageName: String, minutes: Long, current: Boolean): View {
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
            smallText(AppLabels.labelFor(this, packageName), 12.5f, if (current) 1f else 0.8f).apply {
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
        row.addView(
            smallText(
                getString(R.string.stat_minutes, minutes) + " " +
                    getString(R.string.unit_minutes_suffix),
                12f,
                0.8f
            ).apply { setPadding(px(10), 0, 0, 0) }
        )
        return row
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
        val headerHeight = header.height.takeIf { it > 0 } ?: header.measuredHeight
        val height = headerHeight + measurePanel(panel, width)
        if (params.width == width && params.height == height && params.y == y) return
        params.width = width
        params.height = height
        params.y = y
        window?.let { manager -> runCatching { manager.updateViewLayout(root, params) } }
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
        // The pill is an overlay over activities; leaving it up while ALIVE
        // comes forward would draw the pill over its own app.
        stopSelf()
    }

    // ---------- the words ----------

    private fun refresh() {
        if (pill == null) return
        var app = watchedPackage ?: return

        // A pill that cannot draw or cannot read must not stay up. Both grants can
        // be revoked while the service runs, and neither sends a callback.
        if (!Settings.canDrawOverlays(this) || !PermissionChecks.hasUsageAccess(this)) {
            stopSelf()
            return
        }

        // Screen off means the app is gone: there is nothing to read over, and a
        // pill that waits through the night wakes up as a ghost over the launcher.
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false) {
            stopSelf()
            return
        }

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

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
                    stopSelf()
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
            lastReport = runCatching {
                usageTracker.calculateTodayEntertainmentUsage()
            }.getOrNull()
            lastReportAtElapsed = nowElapsed
        }

        val report = lastReport
        renderTraffic()
        // The number is the session's own clock; the lights carry the day's
        // state. The only time the number itself turns crimson is a reached
        // limit - a recovery day is green, not spent.
        numberView?.apply {
            text = Durations.of(this@InfoPillService, sessionMinutes)
            val limitReached = trafficState.active == TrafficLight.RED && !trafficState.pulse
            setTextColor(
                ContextCompat.getColor(
                    this@InfoPillService,
                    if (limitReached) R.color.alive_red_short_mid else R.color.alive_amber_action
                )
            )
        }
        applyTimerInkPadding()
        updateHeaderDescription()

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

        private const val CHANNEL_ID = "alive_pill"
        private const val NOTIFICATION_ID = 7401
        private const val TICK_MILLIS = 20_000L
        private const val ARRIVE_DELAY_MILLIS = 800L

        /** One expansion/collapse, shared by the window resize and the content. */
        private const val EXPAND_MILLIS = 230L

        /**
         * One light is a 14dp slot holding a 10dp core and a 2dp halo; the
         * slots touch, so the cores sit 14dp apart with 4dp of visible gap.
         * The group's leading pad is the gap from the timer.
         */
        private const val TRAFFIC_SLOT_DP = 14
        private const val TRAFFIC_GAP_DP = 10

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
        private const val PANEL_MAX_WIDTH_DP = 360
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