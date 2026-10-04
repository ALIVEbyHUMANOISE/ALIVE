package com.projectego.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.domain.FocusAppPolicy
import com.projectego.app.domain.QuestStartCoordinator
import com.projectego.app.domain.QuestVerificationCoordinator
import com.projectego.app.domain.UsageReport
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.FocusSession
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.TimeStatus
import com.projectego.app.service.EgoAccessibilityService
import com.projectego.app.ui.AliveComponents
import com.projectego.app.ui.AlivePanel
import com.projectego.app.ui.AliveRenderState
import com.projectego.app.ui.AliveSignals
import com.projectego.app.ui.AliveStage
import com.projectego.app.ui.AliveStates
import com.projectego.app.ui.HomeAction
import com.projectego.app.ui.StatusTone
import com.projectego.app.ui.VerificationReasonClass
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ALIVE's only screen.
 *
 * Home, Quest and Profile are three children of one FrameLayout; a panel is
 * appended as a fourth. The engine objects below are built in [onCreate] and
 * are the only way this app's UI reaches the engine - nothing in `ui/` is given
 * a repository or a coordinator - so every state change stays traceable to a
 * line in this file.
 *
 * [onResume] verifies before it renders, and the two reads that can mutate
 * state (today's usage, the current focus session) happen once per dashboard
 * render and are cached in [state] for the panels and Profile to reuse. New
 * surfaces read the cache; they never poll again.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var usageTracker: UsageTracker
    private lateinit var lockStateManager: LockStateManager
    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var questRepository: QuestRepository
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var questStartCoordinator: QuestStartCoordinator
    private lateinit var questVerificationCoordinator: QuestVerificationCoordinator

    private lateinit var statusContainer: LinearLayout
    private lateinit var questContainer: LinearLayout
    private lateinit var profileContainer: LinearLayout
    private lateinit var overlayContainer: FrameLayout
    private lateinit var tabContent: FrameLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var homeScroll: ScrollView

    private val ui by lazy { AliveComponents(this) }
    private val state = AliveRenderState()

    private var focusSetupAnchor: View? = null
    private var goalInput: EditText? = null

    // Hidden developer trigger state
    private var devTapCount = 0
    private var lastDevTapTime = 0L
    private var showDevPanel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageTracker = UsageTracker(this)
        lockStateManager = LockStateManager(this)
        timeBankRepository = TimeBankRepository(this)
        questRepository = QuestRepository(this, timeBankRepository)
        focusSessionManager = FocusSessionManager(this, timeBankRepository)
        questStartCoordinator = QuestStartCoordinator(questRepository, focusSessionManager)
        questVerificationCoordinator = QuestVerificationCoordinator(questRepository, focusSessionManager)

        setContentView(R.layout.activity_main)

        statusContainer = pageColumn()
        homeScroll = ScrollView(this).apply { addView(statusContainer) }

        questContainer = pageColumn()
        val questScroll = ScrollView(this).apply { addView(questContainer) }

        // Profile needs a scroll wrapper or everything below its first line is
        // unreachable. It still occupies tab index 2.
        profileContainer = pageColumn()
        val profileScroll = ScrollView(this).apply { addView(profileContainer) }

        tabContent = findViewById(R.id.tabContent)
        tabContent.addView(homeScroll)
        tabContent.addView(questScroll)
        tabContent.addView(profileScroll)

        // Appended last and never inserted before index 2: showTab() addresses
        // the three tabs by position.
        overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(ui.palette.paper)
            visibility = View.GONE
        }
        tabContent.addView(overlayContainer)

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            closeOverlay()
            when (item.itemId) {
                R.id.nav_home -> { showTab(0); renderDashboard(); true }
                R.id.nav_quest -> { showTab(1); renderQuestSection(); true }
                R.id.nav_profile -> { showTab(2); renderProfile(); true }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_home
        showTab(0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (state.isPanelOpen) {
                    closeOverlay()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        questVerificationCoordinator.evaluateAttemptOnResume(this)
        renderDashboard()
        renderQuestSection()
        // Profile draws from the cache those two renders just refreshed.
        if (bottomNav.selectedItemId == R.id.nav_profile) renderProfile()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "${packageName}/${EgoAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // SHELL
    // ------------------------------------------------------------------

    private fun pageColumn(): LinearLayout = ui.column().apply {
        setPadding(
            ui.dimens.pageHorizontal,
            ui.dimens.pageTop,
            ui.dimens.pageHorizontal,
            ui.dimens.pageBottom
        )
    }

    private fun openOverlay(content: View) {
        overlayContainer.removeAllViews()
        overlayContainer.addView(content)
        overlayContainer.visibility = View.VISIBLE
    }

    private fun closeOverlay() {
        if (!state.isPanelOpen && overlayContainer.visibility == View.GONE) return
        state.closePanel()
        overlayContainer.removeAllViews()
        overlayContainer.visibility = View.GONE
    }

    /** A panel scrolls with the same gutter as the tabs. */
    private fun panel(): Pair<ScrollView, LinearLayout> {
        val column = pageColumn()
        val scroll = ScrollView(this).apply {
            addView(column)
            isFillViewport = true
        }
        return scroll to column
    }

    private fun panelHeader(kicker: CharSequence): LinearLayout = ui.row().apply {
        addView(ui.quiet(getString(R.string.action_back)).apply {
            setOnClickListener { closeOverlay() }
        })
        addView(
            ui.label(kicker).apply {
                setPadding(ui.dimens.m, 0, 0, 0)
            }
        )
    }

    // ------------------------------------------------------------------
    // HOME
    // ------------------------------------------------------------------

    private fun renderDashboard() {
        statusContainer.removeAllViews()

        // Four reads - the same four this dashboard always performed, hoisted so
        // one render cannot quietly poll twice. Everything below uses these.
        val report = usageTracker.calculateTodayEntertainmentUsage()
        val quests = questRepository.getAllQuests()
        val attempt = questRepository.getCurrentAttempt()
        val focusSession = focusSessionManager.getFocusSession()

        val signals = AliveSignals(
            timeStatus = report.timeStatus,
            dayType = report.dayType,
            criticalZoneTier = report.criticalZoneTier,
            remainingMinutes = report.remainingMinutes,
            currentStreak = report.currentStreak,
            reclaimedAvailableMinutes = report.timeBank.availableMinutes,
            attemptState = attempt?.state,
            focusActive = focusSession != null && focusSession.isActive
        )

        state.signals = signals
        state.questCatalog = quests
        state.attempt = attempt
        state.focusSession = focusSession
        state.earnedMinutes = report.timeBank.earnedMinutes
        state.spentMinutes = report.timeBank.spentMinutes
        state.dailyLimitMinutes = report.limitMinutes
        state.usedMinutesToday = report.totalEntertainmentMinutes

        renderHeader(signals)

        if (!hasUsageAccess() || !isAccessibilityServiceEnabled()) {
            renderPermissionsNotice()
        }

        renderYourTime(report)
        renderCurrentAction(signals)
        renderCurrentQuestBlock(attempt, quests)
        renderTimeBank(signals)
        renderFocusSection(focusSession)

        if (BuildConfig.DEBUG && showDevPanel) {
            renderDevPanel()
        }
    }

    /**
     * The character, the wordmark, one handwritten line.
     *
     * The stage is a display derivation, never an engine fact: it shows as a
     * pose and at most one annotation, and is never printed as a name, a level,
     * or a bar.
     */
    private fun renderHeader(signals: AliveSignals) {
        val stage = AliveStates.stageOf(signals)
        val header = ui.column()

        val line = ui.row()
        line.addView(
            ui.pose(
                AliveStates.poseFor(stage),
                ui.dimens.character,
                characterDescription(stage)
            )
        )
        line.addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.l, 0, 0, 0)
                addView(ui.display(getString(R.string.alive_wordmark)).apply {
                    // Seven quick taps stays the only way into the dev panel.
                    setOnClickListener {
                        if (BuildConfig.DEBUG) {
                            val now = System.currentTimeMillis()
                            if (now - lastDevTapTime < 500) {
                                devTapCount++
                                if (devTapCount >= 7) {
                                    showDevPanel = !showDevPanel
                                    devTapCount = 0
                                    renderDashboard()
                                }
                            } else {
                                devTapCount = 1
                            }
                            lastDevTapTime = now
                        }
                    }
                })
                addView(ui.vgap(ui.dimens.s))
                addView(ui.annotation(getString(AliveStates.annotationFor(signals))))
            }
        )
        header.addView(line)

        header.addView(ui.vgap(ui.dimens.l))
        header.addView(ui.bodySecondary(getString(R.string.alive_tagline)))
        header.addView(ui.separator())

        statusContainer.addView(header)
    }

    private fun characterDescription(stage: AliveStage): String =
        getString(R.string.cd_character_pose, getString(AliveStates.stageLabel(stage)))

    private fun renderPermissionsNotice() {
        val block = ui.block(StatusTone.HELD)
        block.addView(ui.label(getString(R.string.permissions_heading)))
        block.addView(ui.vgap(ui.dimens.m))
        block.addView(ui.bodySecondary(getString(R.string.permissions_body)))
        block.addView(ui.vgap(ui.dimens.l))

        if (!hasUsageAccess()) {
            block.addView(ui.secondary(getString(R.string.permissions_usage_action)).apply {
                setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            })
            block.addView(ui.vgap(ui.dimens.s))
        }
        if (!isAccessibilityServiceEnabled()) {
            block.addView(ui.secondary(getString(R.string.permissions_accessibility_action)).apply {
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
        }

        statusContainer.addView(block)
        statusContainer.addView(ui.separator())
    }

    /**
     * How much of today's own allowance is left.
     *
     * The number always comes from remainingMinutes and the critical-zone tier
     * only picks the wording, because dev-test mode collapses the tier scale -
     * copy inferred from a tier would then lie. A recovery day never prints its
     * zero limit as if it were an allowance.
     */
    private fun renderYourTime(report: UsageReport) {
        val block = ui.column()
        block.addView(ui.label(getString(R.string.your_time_heading)))
        block.addView(ui.vgap(ui.dimens.m))

        val headlineRes = AliveStates.timeHeadline(
            report.timeStatus,
            report.criticalZoneTier,
            report.remainingMinutes
        )
        val printsANumber = headlineRes == R.string.time_minutes_left ||
            headlineRes == R.string.time_minutes_left_critical
        val timeText = if (printsANumber) ui.timer(getString(headlineRes, report.remainingMinutes))
        else ui.statement(getString(headlineRes))

        if (printsANumber) {
            val timeLine = ui.row()
            timeLine.addView(
                ui.mark(
                    R.drawable.ic_time_indicator,
                    ui.dimens.xl,
                    getString(R.string.cd_time_state, timeText.text)
                )
            )
            timeLine.addView(
                timeText.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.m, 0, 0, 0)
                }
            )
            block.addView(timeLine)
        } else {
            block.addView(timeText)
        }

        block.addView(ui.vgap(ui.dimens.s))
        block.addView(
            ui.bodySecondary(
                getString(AliveStates.timeSupportLine(report.timeStatus, report.criticalZoneTier))
            )
        )

        if (report.dayType == DayType.NORMAL_DAY && report.limitMinutes > 0) {
            block.addView(ui.vgap(ui.dimens.m))
            block.addView(
                ui.quietText(
                    getString(
                        R.string.time_allowance_and_used,
                        report.limitMinutes,
                        report.totalEntertainmentMinutes
                    )
                )
            )
        }

        if (report.timeStatus == TimeStatus.LOCKED ||
            report.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY
        ) {
            block.addView(ui.vgap(ui.dimens.l))
            block.addView(
                ui.mark(
                    R.drawable.ic_alive_character_aware,
                    ui.dimens.character,
                    getString(R.string.lock_intent)
                )
            )
        }

        block.addView(ui.separator())
        statusContainer.addView(block)
    }

    /**
     * The single thing Home invites.
     *
     * Pending verification offers nothing on purpose: prompting a press would
     * imply the outcome depends on one.
     */
    private fun renderCurrentAction(signals: AliveSignals) {
        val action = AliveStates.homeAction(signals)
        val labelRes = AliveStates.homeActionLabel(action)
        if (labelRes == 0) return

        val button = ui.primary(getString(labelRes)).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            when (action) {
                HomeAction.START_FOCUS -> setOnClickListener { revealFocusSetup() }
                HomeAction.CONTINUE_QUEST -> setOnClickListener { openCurrentQuestPanel() }
                HomeAction.RETURN_TO_SESSION -> setOnClickListener { openFocusSessionPanel() }
                HomeAction.CONTINUE -> setOnClickListener {
                    closeOverlay()
                    bottomNav.selectedItemId = R.id.nav_quest
                }
                HomeAction.NONE -> Unit
            }
        }

        statusContainer.addView(button)
        statusContainer.addView(ui.separator())
    }

    private fun revealFocusSetup() {
        val anchor = focusSetupAnchor ?: return
        homeScroll.post { homeScroll.smoothScrollTo(0, anchor.top) }
        goalInput?.requestFocus()
    }

    // ------------------------------------------------------------------
    // CURRENT QUEST - one block, reused by Home, the Quest tab and its panel
    // ------------------------------------------------------------------

    private fun renderCurrentQuestBlock(attempt: QuestAttempt?, quests: List<Quest>) {
        if (attempt == null || attempt.state == QuestAttemptState.AVAILABLE) return
        val quest = quests.find { it.id == attempt.questId } ?: return

        statusContainer.addView(buildQuestStatusBlock(attempt, quest, withHeader = true))
        statusContainer.addView(ui.separator())
    }

    private fun buildQuestStatusBlock(
        attempt: QuestAttempt,
        quest: Quest,
        withHeader: Boolean
    ): LinearLayout {
        val block = ui.block(AliveStates.attemptTone(attempt.state))

        if (withHeader) {
            block.addView(ui.label(getString(R.string.quest_current)))
            block.addView(ui.vgap(ui.dimens.m))
        }

        block.addView(ui.questRowTitle(quest.title))
        block.addView(ui.vgap(ui.dimens.xs))
        block.addView(ui.bodySecondary(quest.description))
        block.addView(ui.vgap(ui.dimens.l))

        val available = state.signals?.reclaimedAvailableMinutes ?: 0L
        val headline = when (attempt.state) {
            QuestAttemptState.REWARDED ->
                getString(R.string.attempt_rewarded, attempt.rewardMinutes ?: quest.rewardMinutes)
            else -> getString(AliveStates.attemptHeadline(attempt.state))
        }
        block.addView(ui.statement(headline))

        val support = when (attempt.state) {
            // VERIFIED is not REWARDED: the minutes have counted, not landed.
            QuestAttemptState.VERIFIED -> getString(AliveStates.verifiedSupport())
            QuestAttemptState.REWARDED -> getString(R.string.attempt_rewarded_total, available)
            else -> getString(AliveStates.attemptSupport(attempt.state))
        }
        block.addView(ui.vgap(ui.dimens.s))
        block.addView(ui.bodySecondary(support))

        if (attempt.state == QuestAttemptState.FAILED) {
            // A raw engine reason is never shown; only its honest family is.
            val reason = AliveStates.classifyFailure(attempt.verificationResult?.reason)
            if (reason != VerificationReasonClass.UNKNOWN) {
                block.addView(ui.vgap(ui.dimens.s))
                block.addView(ui.bodySecondary(getString(AliveStates.failureSupport(reason))))
            }
        }

        if (AliveStates.offersAction(attempt.state)) {
            block.addView(ui.vgap(ui.dimens.l))
            block.addView(ui.secondary(getString(R.string.action_continue)).apply {
                setOnClickListener { openCurrentQuestPanel() }
            })
        }

        return block
    }

    // ------------------------------------------------------------------
    // TIME BANK
    // ------------------------------------------------------------------

    /** Reclaimed time, stated as time - never as a currency or a score. */
    private fun renderTimeBank(signals: AliveSignals) {
        val available = signals.reclaimedAvailableMinutes
        val block = ui.column()
        block.addView(ui.label(getString(R.string.time_bank_heading)))
        block.addView(ui.vgap(ui.dimens.m))

        if (available <= 0L) {
            block.addView(ui.bodySecondary(getString(R.string.time_bank_empty)))
        } else {
            block.addView(ui.timer(getString(R.string.time_bank_minutes, available)))
            block.addView(ui.vgap(ui.dimens.s))
            block.addView(ui.bodySecondary(getString(R.string.time_body)))
            renderTimeBankUse(block, available)
        }

        block.addView(ui.separator())
        statusContainer.addView(block)
    }

    private fun renderTimeBankUse(block: LinearLayout, available: Long) {
        block.addView(ui.vgap(ui.dimens.xl))
        block.addView(ui.bodySecondary(getString(R.string.time_bank_use_intro)))
        block.addView(ui.vgap(ui.dimens.m))

        val row = ui.row()
        row.addView(
            ui.quiet(getString(R.string.time_bank_use_10)).apply {
                isEnabled = available >= 10
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    if (timeBankRepository.spendMinutes(10)) {
                        toast(R.string.time_bank_spent_toast, 10)
                        renderDashboard()
                    } else {
                        toast(R.string.time_bank_not_enough)
                    }
                }
            }
        )
        row.addView(
            ui.quiet(getString(R.string.time_bank_use_15)).apply {
                isEnabled = available >= 15
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    if (timeBankRepository.spendMinutes(15)) {
                        toast(R.string.time_bank_spent_toast, 15)
                        renderDashboard()
                    } else {
                        toast(R.string.time_bank_not_enough)
                    }
                }
            }
        )
        block.addView(row)
    }

    // ------------------------------------------------------------------
    // FOCUS - a session, not a destination
    // ------------------------------------------------------------------

    private fun renderFocusSection(session: FocusSession?) {
        focusSetupAnchor = null
        goalInput = null

        val block = ui.column()
        block.addView(ui.label(getString(R.string.focus_heading)))
        block.addView(ui.vgap(ui.dimens.m))

        if (session != null && session.isActive) {
            block.addView(ui.statement(getString(R.string.focus_active_heading, activityLabel(session.activityType))))
            block.addView(ui.vgap(ui.dimens.s))
            block.addView(ui.bodySecondary(focusRemainingLine(session)))
            block.addView(ui.vgap(ui.dimens.l))
            block.addView(ui.secondary(getString(R.string.action_return_session)).apply {
                setOnClickListener { openFocusSessionPanel() }
            })
        } else {
            block.addView(ui.bodySecondary(getString(R.string.focus_intro)))
            block.addView(ui.vgap(ui.dimens.xl))
            renderFocusSetup(block)
        }

        block.addView(ui.separator())
        statusContainer.addView(block)
    }

    /**
     * The setup form.
     *
     * The synthetic ids and the -100 / -200 decoding are what bind a selection to
     * a FocusActivityType and to one of the four durations the engine accepts.
     * Reordering either list, or changing an offset, starts a plausible but wrong
     * session instead of failing, so both lists and both offsets move together.
     */
    private fun renderFocusSetup(block: LinearLayout) {
        val anchor = ui.column()
        focusSetupAnchor = anchor

        val activities = FocusActivityType.values()
        val durations = listOf(30L, 60L, 90L, 120L)

        anchor.addView(ui.label(getString(R.string.focus_activity_heading)))
        anchor.addView(ui.vgap(ui.dimens.s))

        val actGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        activities.forEachIndexed { index, type ->
            actGroup.addView(
                RadioButton(this).apply {
                    id = index + 100
                    text = getString(
                        R.string.focus_activity_option,
                        activityLabel(type),
                        FocusAppPolicy.getApprovedPackages(type).size
                    )
                    setTextColor(ui.palette.inkSecondary)
                    minimumHeight = ui.dimens.touchMin
                    isChecked = index == state.selectedActivityIndex
                }
            )
        }
        actGroup.setOnCheckedChangeListener { _, checkedId ->
            state.selectedActivityIndex = (checkedId - 100).coerceAtLeast(0)
        }
        anchor.addView(actGroup)

        anchor.addView(ui.vgap(ui.dimens.xl))
        anchor.addView(ui.label(getString(R.string.focus_goal_heading)))
        anchor.addView(ui.vgap(ui.dimens.s))

        val goalField = EditText(this).apply {
            hint = getString(R.string.focus_goal_hint)
            setText(state.focusGoalDraft)
            setTextColor(ui.palette.ink)
            setHintTextColor(ui.palette.inkDisabled)
            setBackgroundColor(ui.palette.paper)
            setPadding(ui.dimens.m, ui.dimens.m, ui.dimens.m, ui.dimens.m)
            addTextChangedListener(afterTextChanged = { text ->
                state.focusGoalDraft = text?.toString()?.trim().orEmpty()
            })
        }
        goalInput = goalField
        anchor.addView(goalField)

        anchor.addView(ui.vgap(ui.dimens.xl))
        anchor.addView(ui.label(getString(R.string.focus_duration_heading)))
        anchor.addView(ui.vgap(ui.dimens.s))

        val durGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        durations.forEachIndexed { index, duration ->
            durGroup.addView(
                RadioButton(this).apply {
                    id = index + 200
                    // A bare number, with the unit in the heading: four "60 min"
                    // radios will not fit a 320dp screen, and these four values
                    // are the only ones the engine accepts.
                    text = getString(R.string.focus_duration_value, duration)
                    setTextColor(ui.palette.inkSecondary)
                    gravity = Gravity.CENTER_HORIZONTAL
                    minimumHeight = ui.dimens.touchMin
                    layoutParams = RadioGroup.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    isChecked = index == state.selectedDurationIndex
                }
            )
        }
        durGroup.setOnCheckedChangeListener { _, checkedId ->
            state.selectedDurationIndex = (checkedId - 200).coerceAtLeast(0)
        }
        anchor.addView(durGroup)

        anchor.addView(ui.vgap(ui.dimens.l))
        anchor.addView(
            ui.primary(getString(R.string.focus_start)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    val selectedType = activities.getOrNull(actGroup.checkedRadioButtonId - 100)
                        ?: FocusActivityType.STUDY

                    val goal = goalField.text.toString().trim()
                    if (goal.isBlank()) {
                        toast(R.string.focus_goal_required)
                        return@setOnClickListener
                    }

                    val selectedDuration = durations.getOrNull(durGroup.checkedRadioButtonId - 200) ?: 60L

                    val started = focusSessionManager.startFocusSession(selectedType, goal, selectedDuration)
                    if (started) {
                        state.focusGoalDraft = ""
                        toast(R.string.focus_started_toast, selectedDuration)
                        renderDashboard()
                    } else {
                        toast(R.string.focus_start_failed)
                    }
                }
            }
        )
        anchor.addView(ui.vgap(ui.dimens.m))
        anchor.addView(ui.annotation(getString(R.string.focus_setup_hint)))

        block.addView(anchor)
    }

    private fun focusRemainingLine(session: FocusSession): String {
        val remaining = session.getRemainingMinutes()
        return when {
            remaining <= 0L -> getString(R.string.focus_remaining_none)
            remaining == 1L -> getString(R.string.focus_remaining_minute)
            else -> getString(R.string.focus_remaining_minutes, remaining)
        }
    }

    private fun activityLabel(type: FocusActivityType): String =
        getString(AliveStates.focusActivityLabel(type))

    // ------------------------------------------------------------------
    // QUEST TAB
    // ------------------------------------------------------------------

    private fun renderQuestSection() {
        // Clear first: onResume and every tab switch re-render this board, so
        // appending would stack another copy under the last.
        questContainer.removeAllViews()

        val allQuests = questRepository.getAllQuests()

        // The board's active state comes from the live QuestAttempt, never from
        // the legacy KEY_ACTIVE_QUEST_ID, which the start flow no longer writes.
        val currentAttempt = questRepository.getCurrentAttempt()
        val hasActiveAttempt = AliveStates.isAttemptActive(currentAttempt?.state)
        val activeQuestId = if (hasActiveAttempt) currentAttempt?.questId else null
        val activeQuest = activeQuestId?.let { id -> allQuests.find { it.id == id } }

        val header = ui.column()
        header.addView(ui.display(getString(R.string.quest_heading)))
        header.addView(ui.vgap(ui.dimens.s))
        header.addView(ui.annotation(getString(R.string.quest_subhead)))
        header.addView(ui.separator())
        questContainer.addView(header)

        if (currentAttempt != null && activeQuest != null) {
            questContainer.addView(buildQuestStatusBlock(currentAttempt, activeQuest, withHeader = true))
            questContainer.addView(ui.separator())
        }

        val others = allQuests.filter { it.id != activeQuestId }
        if (others.isEmpty()) {
            questContainer.addView(
                ui.emptyState(
                    getString(R.string.empty_heading),
                    getString(R.string.empty_body),
                    getString(R.string.empty_body_second)
                )
            )
        } else {
            questContainer.addView(ui.label(getString(R.string.quest_yours)))
            questContainer.addView(ui.vgap(ui.dimens.l))
            others.forEachIndexed { index, quest ->
                questContainer.addView(buildQuestRow(quest))
                if (index < others.lastIndex) questContainer.addView(ui.hairline())
            }
        }
    }

    /**
     * A row is name, what you actually do, duration, reclaimed time. Difficulty
     * is at most one faded word; there are no levels, rarities, streak rewards,
     * or checkmarks anywhere on this board.
     */
    private fun buildQuestRow(quest: Quest): LinearLayout {
        val completed = quest.status == QuestStatus.COMPLETED
        val verifiable = AliveStates.isSelfVerifiable(quest.category)

        val row = ui.row().apply {
            setPadding(0, ui.dimens.m, 0, ui.dimens.m)
            setBackgroundResource(android.R.color.transparent)
        }

        val info = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(ui.questRowTitle(quest.title))
        info.addView(ui.vgap(ui.dimens.xs))
        info.addView(ui.bodySecondary(quest.description))
        info.addView(ui.vgap(ui.dimens.m))

        val meta = ui.row()
        meta.addView(ui.label(getString(R.string.quest_duration, quest.estimatedDurationMinutes)))
        meta.addView(ui.label(getString(R.string.quest_reclaimed, quest.rewardMinutes)).apply {
            setPadding(ui.dimens.l, 0, 0, 0)
        })
        info.addView(meta)

        if (!verifiable) {
            info.addView(ui.vgap(ui.dimens.s))
            info.addView(ui.quietText(getString(R.string.quest_not_verifiable)))
        }
        row.addView(info)

        row.addView(
            if (completed) ui.quietText(getString(R.string.quest_reclaimed_label))
            else ui.quiet(getString(R.string.quest_detail_open)).apply {
                // A row never starts a quest in one tap: it opens the page that
                // explains it first, so a tap here cannot fail silently.
                setOnClickListener { openQuestDetailPanel(quest.id) }
            }
        )

        return row
    }

    private fun openQuestDetailPanel(questId: String?) {
        val quest = state.quest(questId) ?: return

        state.panel = AlivePanel.QUEST_DETAIL
        state.panelQuestId = quest.id

        val (scroll, column) = panel()
        column.addView(panelHeader(getString(AliveStates.categoryLabel(quest.category))))
        buildQuestDetailInto(column, quest)
        openOverlay(scroll)
    }

    /**
     * The journal page for one quest: what you do, how long, how ALIVE decides,
     * what it gives back. A quest ALIVE cannot verify says so instead of
     * offering a button that would fail.
     */
    private fun buildQuestDetailInto(column: LinearLayout, quest: Quest) {
        val verifiable = AliveStates.isSelfVerifiable(quest.category)

        column.addView(ui.pose(R.drawable.ic_alive_journal_figure, ui.dimens.characterLarge, quest.title))
        column.addView(ui.vgap(ui.dimens.xl))

        column.addView(ui.headline(quest.title))
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(ui.statement(getString(R.string.quest_detail_duration, quest.estimatedDurationMinutes)))
        column.addView(ui.vgap(ui.dimens.l))
        column.addView(ui.body(quest.description))

        column.addView(ui.vgap(ui.dimens.xxl))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_how_title)))
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(
            ui.bodySecondary(
                getString(if (verifiable) R.string.quest_detail_how_body else R.string.quest_detail_how_body_unsupported)
            )
        )

        column.addView(ui.vgap(ui.dimens.xl))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_reward_title)))
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(ui.bodySecondary(getString(R.string.quest_detail_reward_body, quest.rewardMinutes)))

        column.addView(ui.vgap(ui.dimens.l))
        column.addView(
            ui.quietText(
                getString(AliveStates.categoryLabel(quest.category)) + "  ·  " +
                    getString(AliveStates.difficultyLabel(quest.difficulty))
            )
        )

        column.addView(ui.vgap(ui.dimens.xxl))

        when {
            !verifiable -> column.addView(ui.bodySecondary(getString(R.string.quest_not_verifiable)))

            AliveStates.isAttemptActive(state.attempt?.state) -> {
                val blocked = ui.primary(getString(R.string.action_start_quest))
                blocked.isEnabled = false
                column.addView(blocked)
                column.addView(ui.vgap(ui.dimens.s))
                column.addView(ui.annotation(getString(R.string.quest_cannot_start)))
            }

            else -> column.addView(
                ui.primary(getString(R.string.action_start_quest)).apply {
                    setOnClickListener {
                        // One call owns the whole commit -> start -> link chain.
                        if (questStartCoordinator.startDigitalQuest(quest.id)) {
                            closeOverlay()
                            renderDashboard()
                            renderQuestSection()
                        } else {
                            toast(R.string.quest_start_failed)
                        }
                    }
                }
            )
        }
    }

    private fun openCurrentQuestPanel() {
        val attempt = state.attempt
        if (attempt == null || attempt.state == QuestAttemptState.AVAILABLE) return
        val quest = state.quest(attempt.questId) ?: return

        state.panel = AlivePanel.CURRENT_QUEST
        state.panelQuestId = quest.id

        val (scroll, column) = panel()
        column.addView(panelHeader(getString(AliveStates.categoryLabel(quest.category))))
        column.addView(buildQuestStatusBlock(attempt, quest, withHeader = false))

        column.addView(ui.vgap(ui.dimens.xxl))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_how_title)))
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(ui.bodySecondary(getString(R.string.focus_monitors)))

        openOverlay(scroll)
    }

    private fun openFocusSessionPanel() {
        val session = state.focusSession
        if (session == null || !session.isActive) {
            bottomNav.selectedItemId = R.id.nav_home
            revealFocusSetup()
            return
        }

        state.panel = AlivePanel.FOCUS_SESSION
        val (scroll, column) = panel()
        column.addView(panelHeader(activityLabel(session.activityType)))
        buildFocusSessionInto(column, session)
        openOverlay(scroll)
    }

    /**
     * A running session, stated calmly: what it is, how much is left, what stays
     * open, what happens when time runs out. No celebration, no counter to beat.
     */
    private fun buildFocusSessionInto(column: LinearLayout, session: FocusSession) {
        column.addView(ui.headline(getString(R.string.focus_active_heading, activityLabel(session.activityType))))
        column.addView(ui.vgap(ui.dimens.xl))

        column.addView(ui.timer(focusRemainingLine(session)))
        column.addView(ui.vgap(ui.dimens.xs))
        column.addView(ui.bodySecondary(getString(R.string.focus_of_total, session.durationMinutes)))

        column.addView(ui.vgap(ui.dimens.xl))
        column.addView(ui.bodySecondary(getString(R.string.focus_goal_quoted, session.goal)))

        column.addView(ui.vgap(ui.dimens.xxl))
        column.addView(ui.sectionHeader(getString(R.string.focus_allowed_heading)))
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(
            ui.bodySecondary(
                getString(
                    R.string.focus_allowed_body,
                    FocusAppPolicy.getApprovedPackages(session.activityType).size
                )
            )
        )
        column.addView(ui.vgap(ui.dimens.m))
        // ALIVE watches which app is foregrounded. That is the whole claim.
        column.addView(ui.quietText(getString(R.string.focus_monitors)))
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(ui.annotation(getString(R.string.focus_next)))

        val bank = state.signals?.reclaimedAvailableMinutes ?: 0L
        column.addView(ui.vgap(ui.dimens.xxl))
        column.addView(
            ui.secondary(getString(R.string.focus_extend)).apply {
                isEnabled = bank >= 15
                setOnClickListener {
                    if (focusSessionManager.extendFocusWithTimeBank(15)) {
                        toast(R.string.focus_extended_toast)
                        renderDashboard()
                        renderQuestSection()
                        openFocusSessionPanel()
                    } else {
                        toast(R.string.time_bank_not_enough)
                    }
                }
            }
        )
        column.addView(ui.vgap(ui.dimens.s))
        column.addView(ui.quietText(getString(R.string.focus_extend_support)))
    }

    // ------------------------------------------------------------------
    // PROFILE - read only, from what the dashboard already read
    // ------------------------------------------------------------------

    private fun renderProfile() {
        profileContainer.removeAllViews()

        profileContainer.addView(ui.display(getString(R.string.profile_heading)))
        profileContainer.addView(ui.vgap(ui.dimens.s))
        profileContainer.addView(ui.annotation(getString(R.string.profile_identity)))
        profileContainer.addView(ui.vgap(ui.dimens.s))
        profileContainer.addView(ui.bodySecondary(getString(R.string.profile_identity_support)))
        profileContainer.addView(ui.separator())

        // Everything here comes from state.*; nothing re-polls the engine.
        val signals = state.signals
        if (signals != null) {
            val stage = AliveStates.stageOf(signals)
            val block = ui.column()
            block.addView(ui.label(getString(R.string.profile_state_heading)))
            block.addView(ui.vgap(ui.dimens.m))

            val line = ui.row()
            line.addView(ui.pose(AliveStates.poseFor(stage), ui.dimens.character, characterDescription(stage)))
            line.addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.l, 0, 0, 0)
                    addView(ui.body(getString(AliveStates.annotationFor(signals))))
                    addView(ui.vgap(ui.dimens.s))
                    addView(ui.quietText(streakLine(signals.currentStreak)))
                    if (signals.dayType == DayType.NORMAL_DAY && state.dailyLimitMinutes > 0) {
                        addView(ui.vgap(ui.dimens.s))
                        addView(
                            ui.quietText(
                                getString(
                                    R.string.time_allowance_and_used,
                                    state.dailyLimitMinutes,
                                    state.usedMinutesToday
                                )
                            )
                        )
                    }
                }
            )
            block.addView(line)
            block.addView(ui.separator())
            profileContainer.addView(block)
        }

        val bankBlock = ui.column()
        bankBlock.addView(ui.label(getString(R.string.time_bank_heading)))
        bankBlock.addView(ui.vgap(ui.dimens.m))
        bankBlock.addView(ui.bodySecondary(getString(R.string.time_bank_earned_total, state.earnedMinutes)))
        bankBlock.addView(ui.vgap(ui.dimens.xs))
        bankBlock.addView(ui.bodySecondary(getString(R.string.time_bank_spent_total, state.spentMinutes)))
        bankBlock.addView(ui.vgap(ui.dimens.xs))
        bankBlock.addView(
            ui.body(
                getString(R.string.time_bank_available_total, state.signals?.reclaimedAvailableMinutes ?: 0L)
            )
        )
        bankBlock.addView(ui.separator())
        profileContainer.addView(bankBlock)

        renderProfilePermissions()
        renderProfileHistory()

        val how = ui.block(StatusTone.HELD)
        how.addView(ui.label(getString(R.string.profile_how_title)))
        how.addView(ui.vgap(ui.dimens.m))
        how.addView(ui.bodySecondary(getString(R.string.profile_how_body)))
        profileContainer.addView(how)
    }

    /** A streak is a fact about days, not a badge. */
    private fun streakLine(streak: Int): String = when {
        streak <= 0 -> getString(R.string.profile_no_days)
        streak == 1 -> getString(R.string.profile_one_day_in_a_row)
        else -> getString(R.string.profile_days_in_a_row, streak)
    }

    private fun renderProfilePermissions() {
        val block = ui.column()
        block.addView(ui.label(getString(R.string.profile_permissions_heading)))
        block.addView(ui.vgap(ui.dimens.m))
        block.addView(
            permissionLine(
                getString(R.string.permissions_usage_action),
                hasUsageAccess(),
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )
        )
        block.addView(ui.vgap(ui.dimens.s))
        block.addView(
            permissionLine(
                getString(R.string.permissions_accessibility_action),
                isAccessibilityServiceEnabled(),
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )
        block.addView(ui.separator())
        profileContainer.addView(block)
    }

    private fun permissionLine(label: String, granted: Boolean, settingsAction: String): View {
        val button = ui.secondary(
            "$label · ${getString(if (granted) R.string.permissions_granted else R.string.permissions_not_granted)}"
        )
        button.isEnabled = !granted
        if (!granted) {
            button.setOnClickListener { startActivity(Intent(settingsAction)) }
        }
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return button
    }

    private fun renderProfileHistory() {
        val completed = state.questCatalog.filter { it.status == QuestStatus.COMPLETED }

        val block = ui.column()
        block.addView(ui.label(getString(R.string.profile_history_heading)))
        block.addView(ui.vgap(ui.dimens.m))

        if (completed.isEmpty()) {
            block.addView(ui.bodySecondary(getString(R.string.profile_history_empty)))
        } else {
            completed.forEach { quest ->
                block.addView(
                    ui.bodySecondary(getString(R.string.profile_history_row, quest.title, quest.rewardMinutes))
                )
                block.addView(ui.vgap(ui.dimens.xs))
            }
        }
        block.addView(ui.separator())
        profileContainer.addView(block)
    }

    // ------------------------------------------------------------------
    // DEVELOPER PANEL - debug builds only, still the only way to reach the
    // locked and recovery-day states by hand
    // ------------------------------------------------------------------

    private fun renderDevPanel() {
        val block = ui.column()
        block.addView(ui.label("[dev]"))
        block.addView(ui.vgap(ui.dimens.m))

        block.addView(
            ui.quiet(
                if (lockStateManager.isDevTestModeEnabled()) {
                    "[dev] test mode ON - 1 min allowance"
                } else {
                    "[dev] test mode OFF - 120 min allowance"
                }
            ).apply {
                setOnClickListener {
                    val enabled = lockStateManager.isDevTestModeEnabled()
                    lockStateManager.setDevTestModeEnabled(!enabled)
                    usageTracker.calculateTodayEntertainmentUsage()
                    renderDashboard()
                }
            }
        )

        block.addView(
            ui.quiet("[dev] start 15-second test focus").apply {
                setOnClickListener {
                    focusSessionManager.startTestFocusSession(FocusActivityType.STUDY, "Test 15s Goal", 15)
                    Toast.makeText(this@MainActivity, "[dev] 15-second study focus started", Toast.LENGTH_SHORT).show()
                    renderDashboard()
                }
            }
        )

        block.addView(
            ui.quiet("[dev] simulate rollover - day kept").apply {
                setOnClickListener {
                    lockStateManager.simulateRollover(yesterday(), simulateLock = false)
                    renderDashboard()
                }
            }
        )

        block.addView(
            ui.quiet("[dev] simulate rollover - day exceeded").apply {
                setOnClickListener {
                    lockStateManager.simulateRollover(yesterday(), simulateLock = true)
                    renderDashboard()
                }
            }
        )

        // The old direct-reward button stays inert: a reward has to come out of
        // the verification pipeline, and re-implementing it here would be a
        // second reward path.
        val inert = ui.quiet("[dev] reward requires the verification pipeline")
        inert.isEnabled = false
        block.addView(inert)

        statusContainer.addView(block)
    }

    private fun yesterday(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    // ------------------------------------------------------------------
    // SHARED
    // ------------------------------------------------------------------

    private fun showTab(index: Int) {
        tabContent.getChildAt(0).visibility = if (index == 0) View.VISIBLE else View.GONE
        tabContent.getChildAt(1).visibility = if (index == 1) View.VISIBLE else View.GONE
        tabContent.getChildAt(2).visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun toast(resId: Int, vararg args: Any) {
        val text = if (args.isEmpty()) getString(resId) else getString(resId, *args)
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
