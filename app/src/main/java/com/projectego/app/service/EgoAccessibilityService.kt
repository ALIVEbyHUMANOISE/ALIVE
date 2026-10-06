package com.projectego.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.domain.AppClassifier
import com.projectego.app.domain.CriticalAlertPolicy
import com.projectego.app.domain.FocusAppPolicy
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

class EgoAccessibilityService : AccessibilityService() {

    private lateinit var usageTracker: UsageTracker
    private lateinit var lockStateManager: LockStateManager
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var questVerificationCoordinator: QuestVerificationCoordinator

    private var lastInterceptionTime = 0L
    private var lastForegroundPackage: String? = null

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

        // Check 1: FOCUS MODE ENFORCEMENT
        val activeFocusSession = focusSessionManager.getFocusSession()
        if (activeFocusSession != null && activeFocusSession.isActive) {
            val allowed = FocusAppPolicy.isPackageAllowedDuringFocus(
                this,
                packageName,
                activeFocusSession.activityType
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

    override fun onInterrupt() {
        // Required callback
    }
}
