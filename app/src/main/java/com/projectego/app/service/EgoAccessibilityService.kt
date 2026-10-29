package com.projectego.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.projectego.app.R
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.domain.AppClassifier
import com.projectego.app.domain.CriticalAlertPolicy
import com.projectego.app.domain.FocusAppPolicy
import com.projectego.app.domain.ForegroundEnforcementPolicy
import com.projectego.app.domain.ForegroundEventPolicy
import com.projectego.app.domain.PillWatchPolicy
import com.projectego.app.domain.QuestVerificationCoordinator
import com.projectego.app.domain.UsageRead
import com.projectego.app.domain.UsageReport
import com.projectego.app.domain.UsageSourcePolicy
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.AppCategory
import com.projectego.app.model.DayType
import com.projectego.app.ui.CriticalAlertActivity
import com.projectego.app.ui.LockActivity

/**
 * Guards against a launcher that emits duplicate window-state changes for the
 * same package. Separate from the lock's own 500 ms throttle by design - see
 * [maybeShowCriticalAlert].
 */
private const val CRITICAL_ALERT_DEBOUNCE_MILLIS = 1500L

/**
 * How long a confirmed switch to another app waits before the pill is taken
 * down. A Home bounce or a quick Recents check lands here and back; inside
 * this window the same reading simply continues, with no blink and no new
 * 0-minute session. Longer than this is a real leave.
 */
private const val PILL_STOP_GRACE_MILLIS = 2_000L

class EgoAccessibilityService : AccessibilityService() {

    private lateinit var usageTracker: UsageTracker
    private lateinit var lockStateManager: LockStateManager
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var questVerificationCoordinator: QuestVerificationCoordinator

    private var lastInterceptionTime = 0L
    private var lastForegroundPackage: String? = null

    private val graceHandler = Handler(Looper.getMainLooper())
    private var pillStopScheduled = false
    private val stopPillAfterGrace = Runnable {
        pillStopScheduled = false
        InfoPillService.stop(this@EgoAccessibilityService)
    }

    // The package the person is actually inside, and the tick that re-asks the
    // enforcement question while they stay. A window-state event was the only
    // trigger, so an app that was under the limit at entry and then crossed it
    // was never asked again - the person simply kept watching.
    private var trackedForeground: String? = null
    private var lastPeriodicReportAt = 0L

    /** One classification per package per process: the tick asks every 5 seconds. */
    private val categoryCache = HashMap<String, AppCategory>()

    private val enforcementTick = object : Runnable {
        override fun run() {
            val packageName = trackedForeground
            val tick = runCatching {
                ForegroundEnforcementPolicy.decide(
                    state = foregroundState(packageName),
                    nowMillis = SystemClock.elapsedRealtime(),
                    lastReportAtMillis = lastPeriodicReportAt
                )
            }.getOrElse {
                // A failed state read must not end the guard; try again next tick.
                ForegroundEnforcementPolicy.Tick(ForegroundEnforcementPolicy.Action.IDLE, true)
            }

            val launched = runCatching {
                when (tick.action) {
                    ForegroundEnforcementPolicy.Action.IDLE -> false
                    ForegroundEnforcementPolicy.Action.LOCK_NOW ->
                        packageName != null && enforceNormalLock(packageName, usageReport = null)
                    ForegroundEnforcementPolicy.Action.FOCUS_LOCK ->
                        packageName != null && enforceFocusLock(packageName, currentFocusName())
                    ForegroundEnforcementPolicy.Action.EVALUATE_REPORT ->
                        packageName != null && evaluateReport(packageName)
                }
            }.getOrDefault(false)

            if (launched || !tick.keepTicking) {
                stopEnforcementTick()
            } else {
                graceHandler.postDelayed(this, ForegroundEnforcementPolicy.TICK_MILLIS)
            }
        }
    }

    // The critical note keeps its own throttle. Sharing lastInterceptionTime
    // would let a note suppress a real enforcement pass for 500ms, which on the
    // Focus path skips onViolationConfirmed() and leaves a quest that should
    // have failed still in progress.
    private var criticalAlertShownForCrossing = false
    private var lastCriticalAlertAt = 0L

    override fun onCreate() {
        super.onCreate()
        usageTracker = UsageTracker(this)
        lockStateManager = LockStateManager(this)
        val timeBankRepository = TimeBankRepository(this)
        focusSessionManager = FocusSessionManager(this, timeBankRepository)
        val questRepository = QuestRepository(this, timeBankRepository)
        questVerificationCoordinator = QuestVerificationCoordinator(questRepository, focusSessionManager)
    }

    /**
     * Up while an entertainment app is in front, down shortly after one is not.
     *
     * Entertainment only, on purpose: a pill over a bank or a chat app would be
     * a reading nobody asked to see, in a place ALIVE has promised never to
     * interfere. "Shortly" is [PILL_STOP_GRACE_MILLIS]: a Home bounce or a quick
     * Recents check is not a new sitting, and tearing the session down for it
     * is what made the number reset by itself.
     */
    private fun syncPill(packageName: String) {
        // The shade and the volume panel are windows over the app, not apps the
        // person went to. Treating them as a switch used to take the pill down on
        // every pull of the shade - and the app's resume after closing it started
        // a fresh session, which is why the number looked like it reset itself.
        if (AppClassifier.isTransientWindow(packageName)) return

        // The setting decides whether the pill may go UP, never whether it may
        // come DOWN. It used to guard the whole method, which meant that once
        // the switch was off the one event that could take a live pill away -
        // the person leaving the app - was also the event that returned early,
        // so the pill sat on the home screen until the process died.
        val wanted =
            AppClassifier.categorize(this, packageName) == AppCategory.ENTERTAINMENT &&
                PillSettings.isEnabled(this)
        val watching = InfoPillService.watching

        // A window change is evidence of a window, not of a switch. When the
        // pill is already up, ask the activity log before moving or closing it:
        // a keyboard, Game Booster or system panel raises a window over the
        // app with no new activity, and acting on that restarted the session
        // at 0 while the person had not gone anywhere.
        fun confirmed(): Boolean = runCatching {
            val front = PillForegroundProbe.latest(
                this,
                InfoPillService.FOREGROUND_WINDOW_MILLIS
            )
            PillWatchPolicy.isConfirmedSwitch(packageName, front?.packageName)
        }.getOrDefault(false)

        when {
            // Raising has no evidence to check yet - the person asked for the
            // pill on an app the log already agrees is in front - and waiting
            // for the log here would make a fresh start lag behind its app.
            wanted && watching == null -> {
                cancelPillStop()
                InfoPillService.start(this, packageName)
            }
            // The app whose reading is up came back inside the grace. Nothing
            // is torn down, so the number never blinks or starts again.
            wanted && watching == packageName ->
                cancelPillStop()
            wanted -> {
                cancelPillStop()
                if (confirmed()) InfoPillService.start(this, packageName)
            }
            InfoPillService.isRunning || watching != null -> {
                // Unconfirmed means the pill stays until its own periodic probe
                // sees a real foreground change, instead of blinking to 0.
                if (confirmed()) schedulePillStop()
            }
        }
    }

    private fun schedulePillStop() {
        // Measured from the first confirmed leave, not the last window: moving
        // between non-entertainment apps must not extend the grace.
        if (pillStopScheduled) return
        pillStopScheduled = true
        graceHandler.postDelayed(stopPillAfterGrace, PILL_STOP_GRACE_MILLIS)
    }

    private fun cancelPillStop() {
        pillStopScheduled = false
        graceHandler.removeCallbacks(stopPillAfterGrace)
    }

    override fun onDestroy() {
        cancelPillStop()
        stopEnforcementTick()
        super.onDestroy()
    }

    /**
     * The service just (re)connected: whatever was in front before the rebind is
     * no longer known, so tracking starts empty and the next window event starts
     * it again. A tick from the previous life is cancelled here.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        stopEnforcementTick()
        lastForegroundPackage = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        when (ForegroundEventPolicy.targetOf(packageName, applicationContext.packageName)) {
            ForegroundEventPolicy.Target.SELF -> {
                // Our own screen is in front: the person has left whatever they
                // were in, and a lock this guard just launched must not be
                // launched again by the tick that is still running behind it.
                stopEnforcementTick()
                return
            }

            ForegroundEventPolicy.Target.TRANSIENT -> {
                // A shade, a keyboard, the screenshot preview: a window over the
                // app, not a switch away from it. Keep tracking and keep the
                // tick - stopping here was how a pulled shade silently ended
                // enforcement while the person stayed in the entertainment app.
                return
            }

            ForegroundEventPolicy.Target.PACKAGE -> Unit
        }

        lastForegroundPackage = packageName
        trackedForeground = packageName

        // The pill follows whoever is in front, once the person has asked for it
        // with the tile. No second watch is needed: this callback already knows
        // which app is in front, and it is the one place that cannot be wrong
        // about it - so the pill and the enforcement can never disagree.
        syncPill(packageName)

        // Check 1: FOCUS MODE ENFORCEMENT
        val activeFocusSession = focusSessionManager.getFocusSession()
        if (activeFocusSession != null && activeFocusSession.isActive) {
            val allowed = FocusAppPolicy.isPackageAllowedDuringFocus(
                this,
                packageName,
                activeFocusSession.activityType,
                activeFocusSession.approvedOverride
            )

            if (!allowed) {
                enforceFocusLock(packageName, activeFocusSession.activityType.displayName)
                stopEnforcementTick()
                return
            }
            // Allowed for now - but the session can end while this app stays
            // open, so the tick keeps asking.
            startEnforcementTick()
            return
        }

        // Check 2: NORMAL SCREEN TIME ENFORCEMENT (Phase 0/1)
        val category = AppClassifier.categorize(this, packageName)
        if (category != AppCategory.ENTERTAINMENT) {
            stopEnforcementTick()
            return
        }

        // The day, as an answer that can also say "I could not read it".
        val read = usageTracker.readTodayEntertainmentUsage()
        noteUsageAccess(read)
        // Whatever the answer, the tick should not ask again before its interval.
        lastPeriodicReportAt = SystemClock.elapsedRealtime()

        // From here on the person is inside an enforceable app: keep asking, so
        // crossing the limit while they stay does not wait for the next window.
        startEnforcementTick()

        when (read) {
            is UsageRead.Success -> {
                // Wrapped so that the note can never stop the enforcement below
                // from running: whatever happens here, the lock still happens.
                runCatching { maybeShowCriticalAlert(read.report) }
                if (read.report.isLimitExceeded) {
                    enforceNormalLock(packageName, read.report)
                }
            }

            UsageRead.PermissionUnavailable,
            UsageRead.QueryFailed,
            UsageRead.ProcessingFailed -> {
                // An unreadable day is not a zero day. Only what is known without
                // it may act: a lock that is already persisted and a
                // zero-entertainment day. Everything else waits for a later read
                // instead of inheriting an allowance nobody measured.
                val known = foregroundState(packageName)
                val mustLock = runCatching {
                    UsageSourcePolicy.lockFromKnownStateOnly(
                        alreadyLocked = known.alreadyLocked,
                        zeroDay = known.zeroDay
                    )
                }.getOrDefault(false)
                if (mustLock) {
                    runCatching { enforceNormalLock(packageName, usageReport = null) }
                }
            }
        }
    }

    /**
     * Shows the "you are running low" note, at most once per crossing of the
     * threshold, using the report this method already computed.
     *
     * Reaching here already means: the event was a window change, the package
     * is not ALIVE itself, no Focus session is active (that branch returns),
     * and the app is an entertainment app. The note is deliberately the only
     * thing in this file that does not press HOME - it is information, not
     * enforcement.
     */
    private fun maybeShowCriticalAlert(usageReport: UsageReport) {
        // Re-arm as soon as there is comfortably more time left again, which in
        // practice means the day rolled over. Usage only grows within a day, so
        // the window can be entered once without storing any date.
        if (usageReport.remainingMinutes > CriticalAlertPolicy.REMAINING_WINDOW_MINUTES) {
            criticalAlertShownForCrossing = false
            return
        }
        if (criticalAlertShownForCrossing) return
        if (!CriticalAlertPolicy.shouldShow(usageReport, focusActive = false)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastCriticalAlertAt < CRITICAL_ALERT_DEBOUNCE_MILLIS) return
        lastCriticalAlertAt = now

        criticalAlertShownForCrossing = true

        val alertIntent = Intent(this, CriticalAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("EXTRA_CRITICAL_REMAINING_MINUTES", usageReport.remainingMinutes)
            putExtra("EXTRA_CRITICAL_LIMIT_MINUTES", usageReport.limitMinutes)
        }
        startActivity(alertIntent)
    }

    private fun enforceFocusLock(blockedPackage: String, activityName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterceptionTime < 500) {
            return false
        }
        lastInterceptionTime = now

        questVerificationCoordinator.onViolationConfirmed()

        // SEC-07 experiment: no GLOBAL_ACTION_HOME before the lock, matching the
        // normal-lock path that now holds on this device. The lock activity is
        // what must cover the blocked app.

        val lockIntent = Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("EXTRA_BLOCKED_PACKAGE", blockedPackage)
            putExtra("EXTRA_LOCK_REASON", "FOCUS_SESSION")
            putExtra("EXTRA_FOCUS_ACTIVITY", activityName)
        }
        startActivity(lockIntent)
        return true
    }

    private fun enforceNormalLock(blockedPackage: String, usageReport: UsageReport?): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterceptionTime < 500) {
            return false
        }
        lastInterceptionTime = now

        // SEC-07 experiment: no GLOBAL_ACTION_HOME before the lock. On the test
        // device the home transition raced the lock start and the lock task was
        // trimmed before it ever showed. The lock activity is what must cover
        // the blocked app; this build tests whether pressing home first is what
        // prevents it from doing so.

        val lockIntent = Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("EXTRA_BLOCKED_PACKAGE", blockedPackage)
            putExtra("EXTRA_LOCK_REASON", "ENTERTAINMENT_EXCEEDED")
            // The numbers the decision was made from, when a report produced the
            // decision. A lock launched by the tick from the persisted lock or a
            // zero day has no report and sends none - the screen copes with that,
            // it does not invent a zero.
            if (usageReport != null) {
                putExtra("EXTRA_USED_MINUTES", usageReport.totalEntertainmentMinutes)
                putExtra("EXTRA_LIMIT_MINUTES", usageReport.limitMinutes)
            }
        }
        startActivity(lockIntent)
        return true
    }

    /**
     * One pending tick, always: repeated window events reset the wait instead of
     * stacking callbacks. The tick stops itself when the person leaves, when the
     * screen goes off, and after it has launched a lock.
     */
    private fun startEnforcementTick() {
        graceHandler.removeCallbacks(enforcementTick)
        graceHandler.postDelayed(enforcementTick, ForegroundEnforcementPolicy.TICK_MILLIS)
    }

    private fun stopEnforcementTick() {
        graceHandler.removeCallbacks(enforcementTick)
        trackedForeground = null
        lastPeriodicReportAt = 0L
    }

    /**
     * The cheap state the tick is allowed to decide on.
     *
     * Everything here is a preference read or a cached classification - no usage
     * query, no pill report. The decision it feeds treats the persisted lock and a
     * zero day as already decided, exactly as the report does.
     */
    private fun foregroundState(packageName: String?): ForegroundEnforcementPolicy.State {
        val interactive =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
        val focus = focusSessionManager.getFocusSession()
        val focusActive = focus != null && focus.isActive
        val allows = focus != null && focus.isActive && packageName != null &&
            FocusAppPolicy.isPackageAllowedDuringFocus(
                this,
                packageName,
                focus.activityType,
                focus.approvedOverride
            )
        return ForegroundEnforcementPolicy.State(
            packageName = packageName,
            screenInteractive = interactive,
            isTransient = AppClassifier.isTransientWindow(packageName),
            isEntertainment = packageName != null &&
                categoryOf(packageName) == AppCategory.ENTERTAINMENT,
            focusActive = focusActive,
            focusAllowsPackage = allows,
            focusActivityName = focus?.activityType?.displayName,
            alreadyLocked = lockStateManager.isLocked(),
            zeroDay = lockStateManager.getDayType() == DayType.ZERO_ENTERTAINMENT_DAY
        )
    }

    private fun categoryOf(packageName: String): AppCategory =
        categoryCache.getOrPut(packageName) { AppClassifier.categorize(this, packageName) }

    private fun currentFocusName(): String =
        focusSessionManager.getFocusSession()?.activityType?.displayName
            ?: getString(R.string.focus_heading)

    /**
     * The authoritative question, asked at the tick's cadence: the same call and
     * the same `isLimitExceeded` the event path uses. No cached report is read
     * here - the pill's numbers are display-only and never decide a lock.
     */
    /**
     * The guard can be on and blind at the same time. Only the missing grant is
     * worth a note: a query that failed for another reason is transient and must
     * not become an alert, and a success is what takes the note away.
     */
    private fun noteUsageAccess(read: UsageRead) {
        when (read) {
            is UsageRead.Success -> UsageAccessWatch.sync(this, granted = true)
            UsageRead.PermissionUnavailable -> UsageAccessWatch.sync(this, granted = false)
            UsageRead.QueryFailed, UsageRead.ProcessingFailed -> Unit
        }
    }

    private fun evaluateReport(packageName: String): Boolean {
        lastPeriodicReportAt = SystemClock.elapsedRealtime()
        val read = usageTracker.readTodayEntertainmentUsage()
        noteUsageAccess(read)
        val report = (read as? UsageRead.Success)?.report ?: return false
        if (!report.isLimitExceeded) return false
        return enforceNormalLock(packageName, report)
    }

    /**
     * The guard just went away - switched off by the user, or unbound by the
     * system; from inside here the two are the same event.
     *
     * Nothing can be enforced while this service is off, so the only thing left
     * to do is to say so and keep saying it. The decision is not made here: the
     * secure setting may not have settled this instant, and a nag that fires on a
     * service that was only re-bound would be a false alarm. [AccessibilityWatch]
     * looks again in a moment, and the repeating alarm takes it from there.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        stopEnforcementTick()
        AccessibilityWatch.verifySoon(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        stopEnforcementTick()
    }
}
