package com.projectego.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.PillSettings
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.domain.AppClassifier
import com.projectego.app.domain.CriticalAlertPolicy
import com.projectego.app.domain.FocusAppPolicy
import com.projectego.app.domain.PillWatchPolicy
import com.projectego.app.domain.QuestVerificationCoordinator
import com.projectego.app.domain.UsageReport
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.AppCategory
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
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Ignore self
        if (packageName == applicationContext.packageName) {
            return
        }

        lastForegroundPackage = packageName

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
                return
            }
            // App is allowed during Focus (Essential or Curated Productivity)
            return
        }

        // Check 2: NORMAL SCREEN TIME ENFORCEMENT (Phase 0/1)
        val category = AppClassifier.categorize(this, packageName)
        if (category != AppCategory.ENTERTAINMENT) {
            return
        }

        // Recalculate usage report and enforce limit if exceeded
        val usageReport = usageTracker.calculateTodayEntertainmentUsage()

        // Wrapped so that the note can never stop the enforcement below from
        // running: whatever happens here, the lock still happens.
        runCatching { maybeShowCriticalAlert(usageReport) }

        if (usageReport.isLimitExceeded) {
            enforceNormalLock(packageName)
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

    private fun enforceFocusLock(blockedPackage: String, activityName: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterceptionTime < 500) {
            return
        }
        lastInterceptionTime = now

        questVerificationCoordinator.onViolationConfirmed()

        performGlobalAction(GLOBAL_ACTION_HOME)

        val lockIntent = Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("EXTRA_BLOCKED_PACKAGE", blockedPackage)
            putExtra("EXTRA_LOCK_REASON", "FOCUS_SESSION")
            putExtra("EXTRA_FOCUS_ACTIVITY", activityName)
        }
        startActivity(lockIntent)
    }

    private fun enforceNormalLock(blockedPackage: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterceptionTime < 500) {
            return
        }
        lastInterceptionTime = now

        performGlobalAction(GLOBAL_ACTION_HOME)

        val lockIntent = Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("EXTRA_BLOCKED_PACKAGE", blockedPackage)
            putExtra("EXTRA_LOCK_REASON", "ENTERTAINMENT_EXCEEDED")
        }
        startActivity(lockIntent)
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
        AccessibilityWatch.verifySoon(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        // Required callback
    }
}
