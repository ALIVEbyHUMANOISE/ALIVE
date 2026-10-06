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
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.data.UsageHistoryRepository
import com.projectego.app.domain.CriticalAlertPolicy
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
import com.projectego.app.model.UsageDay
import com.projectego.app.service.EgoAccessibilityService
import com.projectego.app.ui.AliveComponents
import com.projectego.app.ui.AlivePanel
import com.projectego.app.ui.AliveRenderState
import com.projectego.app.ui.AliveSignals
import com.projectego.app.ui.AliveStage
import com.projectego.app.ui.AliveStates
import com.projectego.app.ui.AliveSeries
import com.projectego.app.ui.AppLabels
import com.projectego.app.ui.BrutalistBarChart
import com.projectego.app.ui.BrutalistDonut
import com.projectego.app.ui.ChartRange
import com.projectego.app.ui.DayBar
import com.projectego.app.ui.DayBarKind
import com.projectego.app.ui.HomeAction
import com.projectego.app.ui.PixelMarks
import com.projectego.app.ui.StatusTone
import com.projectego.app.ui.SurfaceWeight
import com.projectego.app.ui.UsageChartStates
import com.projectego.app.ui.VerificationReasonClass
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
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

    /** The chart's own store. Side-effect free, so panels may read it directly. */
    private val usageHistory by lazy { UsageHistoryRepository(this) }

    private var focusSetupAnchor: View? = null
    private var goalInput: EditText? = null

    /**
     * Which of Home's cards owns the screen's one MAJOR frame this render.
     *
     * A budget, not a decoration: with twelve cards all asking for the heaviest
     * edge, nothing on the screen was louder than anything else. The order is
     * what is broken first, then what is in flight, then the day itself.
     */
    private enum class HomeMajor { GUARD, PERMISSIONS, ATTEMPT, TODAY }

    private var homeMajor = HomeMajor.TODAY

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

    /**
     * A page is a stack with `card_gap` between its blocks, so a card's hard
     * shadow always lands on clean canvas instead of on the next card's edge.
     */
    private fun pageColumn(): LinearLayout = ui.stack(ui.dimens.cardGap).apply {
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

    /**
     * A panel scrolls with the same gutter as the tabs.
     *
     * A plain column, not a `stack`: panel content is one continuous flow that is
     * authored with its own rhythm (a journal page, a session read-out), whereas
     * the tabs are genuinely stacks of cards. Automatic gaps here would stack on
     * top of the deliberate ones.
     */
    private fun panel(): Pair<ScrollView, LinearLayout> {
        val column = ui.column().apply {
            setPadding(
                ui.dimens.pageHorizontal,
                ui.dimens.pageTop,
                ui.dimens.pageHorizontal,
                ui.dimens.pageBottom
            )
        }
        val scroll = ScrollView(this).apply {
            addView(column)
            isFillViewport = true
        }
        return scroll to column
    }

    private fun panelHeader(kicker: CharSequence): LinearLayout = ui.row().apply {
        addView(
            ui.backButton(getString(R.string.action_back)).apply {
                setOnClickListener { closeOverlay() }
            }
        )
        addView(
            ui.sectionHeader(kicker).apply {
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
        state.entertainmentAppDetails = report.entertainmentAppDetails
        evaluateGuardState(report)

        // Read once per render and handed down: these reach AppOps and Settings,
        // and calling them again inside each block would poll the system twice
        // for the same frame.
        val usageMissing = !hasUsageAccess()
        val guardMissing = !isAccessibilityServiceEnabled()

        // Home gets exactly one heavy frame, and what earns it is what is broken
        // or in flight - not whatever happens to be rendered first.
        homeMajor = when {
            state.guardGapOpen -> HomeMajor.GUARD
            usageMissing || guardMissing -> HomeMajor.PERMISSIONS
            AliveStates.isAttemptActive(attempt?.state) -> HomeMajor.ATTEMPT
            else -> HomeMajor.TODAY
        }

        renderHeader(signals)

        if (state.guardGapOpen || state.guardGapRepairedToday) {
            renderGuardCard()
        }

        if (usageMissing || guardMissing) {
            renderPermissionsNotice(usageMissing, guardMissing)
        }

        renderTodayCard(report, signals)
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
            ui.animatedPose(
                AliveStates.poseFor(stage),
                ui.dimens.characterLarge,
                characterDescription(stage)
            )
        )
        line.addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.l, 0, 0, 0)
                addView(
                    ui.row().apply {
                        gravity = Gravity.BOTTOM
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
                        // A block cursor, so the wordmark reads as something that
                        // is running rather than a printed title.
                        addView(ui.caret(ui.dimens.dp(20)))
                    }
                )
                addView(ui.vgap(ui.dimens.lineGap))
                addView(ui.annotation(getString(AliveStates.annotationFor(signals))))
            }
        )
        header.addView(line)

        header.addView(ui.vgap(ui.dimens.groupGap))
        header.addView(ui.note(getString(R.string.alive_tagline)))
        header.addView(ui.vgap(ui.dimens.lineGap))
        header.addView(ui.accents(seed = 1))
        // No rule here: the TODAY card below is the break, and a second one
        // above it would read as a frame around empty paper.

        statusContainer.addView(header)
    }

    /**
     * Notices that the guard is off, and notices when it comes back.
     *
     * Only a day that owed enforcement gets marked: when nothing was over the
     * limit, a service that was off was hiding nothing, and charging for it
     * would punish the phone's battery manager rather than the person. Detection
     * lives here because this is the only moment ALIVE is on screen to look.
     */
    private fun evaluateGuardState(report: UsageReport) {
        val today = LockStateManager.dayKeyOf(System.currentTimeMillis())
        val owedEnforcement = report.isLimitExceeded || report.dayType == DayType.ZERO_ENTERTAINMENT_DAY

        if (!isAccessibilityServiceEnabled()) {
            if (owedEnforcement) lockStateManager.markGuardGap(today, report.timeBank.earnedMinutes)
        } else {
            lockStateManager.repairGuardGapIfEarned(report.timeBank.earnedMinutes)
        }

        state.guardGapOpen = lockStateManager.isGuardGapOpenOn(today)
        state.guardGapRepairedToday = !state.guardGapOpen && lockStateManager.unguardedDay() == today
    }

    /**
     * The one card that speaks before the numbers do.
     *
     * It states what is no longer true (nothing is being closed), what it costs
     * (today is marked, and an unrepaired mark is not a kept day), and how to
     * clear it - without pretending the mark can be erased. It also says out loud
     * that ALIVE cannot tell who turned the service off, because a phone that
     * kills services to save battery is a real possibility and a false accusation
     * here would cost more trust than the mark ever recovers.
     */
    private fun renderGuardCard() {
        val repaired = !state.guardGapOpen
        val card = ui.bentoCard(
            getString(if (repaired) R.string.guard_back_heading else R.string.guard_off_heading),
            weight = if (homeMajor == HomeMajor.GUARD) SurfaceWeight.MAJOR else SurfaceWeight.MINOR
        )

        card.addView(
            ui.mark(
                R.drawable.ic_alive_character_aware,
                ui.dimens.character,
                getString(R.string.cd_guard_mark)
            )
        )
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            ui.note(
                getString(if (repaired) R.string.guard_back_body else R.string.guard_off_body)
            )
        )
        card.addView(ui.vgap(ui.dimens.pairGap))
        card.addView(
            ui.note(
                getString(if (repaired) R.string.guard_repair_done else R.string.guard_repair_needed)
            )
        )
        card.addView(ui.vgap(ui.dimens.pairGap))
        card.addView(ui.note(getString(R.string.guard_who_turned_it_off)))

        if (!repaired) {
            card.addView(ui.vgap(ui.dimens.groupGap))
            card.addView(
                ui.secondary(getString(R.string.guard_enable_action)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            )
        }

        statusContainer.addView(card)
    }

    private fun characterDescription(stage: AliveStage): String =
        getString(R.string.cd_character_pose, getString(AliveStates.stageLabel(stage)))

    /**
     * What ALIVE cannot see, and how to let it.
     *
     * A titled card rather than a bare block, because the heading was a sentence
     * in a 10sp pixel face - the one place on the screen where the words that
     * mattered most were the hardest to read.
     */
    private fun renderPermissionsNotice(usageMissing: Boolean, guardMissing: Boolean) {
        val card = ui.bentoCard(
            getString(R.string.permissions_heading),
            weight = if (homeMajor == HomeMajor.PERMISSIONS) SurfaceWeight.MAJOR else SurfaceWeight.MINOR
        )
        card.addView(ui.note(getString(R.string.permissions_body)))
        card.addView(ui.vgap(ui.dimens.groupGap))

        if (usageMissing) {
            card.addView(
                ui.secondary(getString(R.string.permissions_usage_action)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
            )
            card.addView(ui.vgap(ui.dimens.lineGap))
        }
        if (guardMissing) {
            // Named out loud, because this is the permission people are most
            // wary of and the one they can walk away from - at a price.
            card.addView(ui.note(getString(R.string.permissions_accessibility_why)))
            card.addView(ui.vgap(ui.dimens.lineGap))
            card.addView(
                ui.secondary(getString(R.string.permissions_accessibility_action)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            )
        }

        statusContainer.addView(card)
    }

    /**
     * The day, in one heavy card: the four numbers that explain what ALIVE
     * tracks, then the figure that matters right now.
     *
     * The number always comes from remainingMinutes and the critical-zone tier
     * only picks the wording, because dev-test mode collapses the tier scale -
     * copy inferred from a tier would then lie. A recovery day never prints its
     * zero limit as if it were an allowance; the grid still shows the zero,
     * because hiding it would hide the reason the day is closed.
     */
    private fun renderTodayCard(report: UsageReport, signals: AliveSignals) {
        val card = ui.bentoCard(
            getString(R.string.today_card_title),
            weight = if (homeMajor == HomeMajor.TODAY) SurfaceWeight.MAJOR else SurfaceWeight.MINOR,
            tone = AliveStates.timeTone(report.timeStatus)
        )

        card.addView(statGrid(signals))
        card.addView(ui.vgap(ui.dimens.pairGap))
        card.addView(ui.note(getString(R.string.stat_unit_note)))
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.rule())
        card.addView(ui.vgap(ui.dimens.groupGap))

        val headlineRes = AliveStates.timeHeadline(
            report.timeStatus,
            report.criticalZoneTier,
            report.remainingMinutes
        )
        val printsANumber = headlineRes == R.string.time_minutes_left ||
            headlineRes == R.string.time_minutes_left_critical

        if (printsANumber) {
            // Say what the big number is before showing it: a bare 75 means
            // nothing to someone who opened the app for the first time.
            card.addView(ui.note(getString(R.string.time_hero_line)))
            card.addView(ui.vgap(ui.dimens.lineGap))

            val timeLine = ui.row().apply { gravity = Gravity.BOTTOM }
            timeLine.addView(ui.timer(getString(R.string.stat_minutes, report.remainingMinutes)))
            timeLine.addView(
                ui.tag(getString(R.string.stat_min_left)).apply {
                    setPadding(ui.dimens.m, 0, 0, ui.dimens.l)
                }
            )
            timeLine.addView(
                ui.weighted(
                    ui.note(getString(R.string.time_of_allowance, report.limitMinutes))
                ).apply { gravity = Gravity.BOTTOM or Gravity.END }
            )
            card.addView(timeLine)
            card.addView(ui.vgap(ui.dimens.lineGap))
            // The same fact the number states, in a second channel: red appears
            // exactly where the critical note would, because it is the same
            // threshold - not a new one invented for the picture.
            card.addView(
                ui.timeBar(
                    report.remainingMinutes,
                    report.limitMinutes,
                    fill = if (report.remainingMinutes in 1..CriticalAlertPolicy.REMAINING_WINDOW_MINUTES) {
                        ui.palette.shortMid
                    } else {
                        ui.palette.forest
                    }
                )
            )
        } else {
            // Closed, or a recovery day: a statement, not a number, and the
            // figure walking toward something else.
            card.addView(ui.headline(getString(headlineRes)))
            card.addView(ui.vgap(ui.dimens.lineGap))
            card.addView(
                ui.mark(
                    R.drawable.ic_alive_character_aware,
                    ui.dimens.character,
                    getString(R.string.lock_intent)
                )
            )
        }

        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            ui.note(
                getString(AliveStates.timeSupportLine(report.timeStatus, report.criticalZoneTier))
            )
        )

        // The chart opens from here because this is where the eye is already on
        // the number the chart then explains.
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(
            ui.secondary(getString(R.string.chart_open)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { openUsageChartPanel() }
            }
        )

        statusContainer.addView(card)
    }

    /**
     * The four facts a first run has no other way to learn: how many clean days
     * in a row, what today's allowance is, how much of it went, and how much
     * reclaimed time is banked. Ruled like a table so the numbers read as a
     * ledger, not as decoration.
     */
    private fun statGrid(signals: AliveSignals): LinearLayout = ui.column().apply {
        addView(
            ui.row().apply {
                addView(ui.weighted(ui.statTile(getString(R.string.stat_streak), getString(R.string.stat_minutes, signals.currentStreak))))
                addView(ui.ruleVertical())
                addView(ui.weighted(ui.statTile(getString(R.string.stat_allowance), getString(R.string.stat_minutes, state.dailyLimitMinutes))))
            }
        )
        addView(ui.rule())
        addView(
            ui.row().apply {
                addView(ui.weighted(ui.statTile(getString(R.string.stat_used), getString(R.string.stat_minutes, state.usedMinutesToday))))
                addView(ui.ruleVertical())
                addView(ui.weighted(ui.statTile(getString(R.string.stat_bank), getString(R.string.stat_minutes, signals.reclaimedAvailableMinutes))))
            }
        )
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

        statusContainer.addView(
            buildQuestStatusBlock(
                attempt,
                quest,
                withHeader = true,
                weight = if (homeMajor == HomeMajor.ATTEMPT) SurfaceWeight.MAJOR else SurfaceWeight.MINOR
            )
        )
    }

    private fun buildQuestStatusBlock(
        attempt: QuestAttempt,
        quest: Quest,
        withHeader: Boolean,
        weight: SurfaceWeight = SurfaceWeight.MINOR
    ): LinearLayout {
        val block = ui.block(weight, AliveStates.attemptTone(attempt.state))

        if (withHeader) {
            block.addView(ui.sectionHeader(getString(R.string.quest_current)))
            block.addView(ui.vgap(ui.dimens.lineGap))
        }

        block.addView(
            ui.pose(
                PixelMarks.quest(quest.category),
                ui.dimens.character,
                getString(PixelMarks.description(quest.category))
            )
        )
        block.addView(ui.vgap(ui.dimens.lineGap))

        block.addView(ui.lead(quest.title))
        block.addView(ui.vgap(ui.dimens.pairGap))
        block.addView(ui.note(quest.description))
        block.addView(ui.vgap(ui.dimens.groupGap))

        val available = state.signals?.reclaimedAvailableMinutes ?: 0L
        val headline = when (attempt.state) {
            QuestAttemptState.REWARDED ->
                getString(R.string.attempt_rewarded, attempt.rewardMinutes ?: quest.rewardMinutes)
            else -> getString(AliveStates.attemptHeadline(attempt.state))
        }
        block.addView(ui.lead(headline))

        val support = when (attempt.state) {
            // VERIFIED is not REWARDED: the minutes have counted, not landed.
            QuestAttemptState.VERIFIED -> getString(AliveStates.verifiedSupport())
            QuestAttemptState.REWARDED -> getString(R.string.attempt_rewarded_total, available)
            else -> getString(AliveStates.attemptSupport(attempt.state))
        }
        block.addView(ui.vgap(ui.dimens.lineGap))
        block.addView(ui.note(support))

        if (attempt.state == QuestAttemptState.FAILED) {
            // A raw engine reason is never shown; only its honest family is.
            val reason = AliveStates.classifyFailure(attempt.verificationResult?.reason)
            if (reason != VerificationReasonClass.UNKNOWN) {
                block.addView(ui.vgap(ui.dimens.lineGap))
                block.addView(ui.note(getString(AliveStates.failureSupport(reason))))
            }
        }

        if (AliveStates.offersAction(attempt.state)) {
            block.addView(ui.vgap(ui.dimens.groupGap))
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
    /**
     * The Time Bank, as a card with its own sprite and its own ledger.
     *
     * It used to be a label and one quiet line, which is why nobody knew the
     * feature existed: the thing that pays for Focus extensions looked like a
     * footnote. The clock says "time is kept here", the ledger says how much
     * came in and went out, and the figure says what is spendable now.
     */
    private fun renderTimeBank(signals: AliveSignals) {
        val available = signals.reclaimedAvailableMinutes
        val card = ui.bentoCard(getString(R.string.time_bank_heading))

        val head = ui.row()
        head.addView(
            ui.pose(
                R.drawable.ic_time_indicator,
                ui.dimens.character,
                getString(R.string.cd_time_bank)
            )
        )
        head.addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.l, 0, 0, 0)
                if (available <= 0L) {
                    addView(ui.note(getString(R.string.time_bank_empty)))
                } else {
                    val bankLine = ui.row().apply { gravity = Gravity.BOTTOM }
                    bankLine.addView(ui.timer(getString(R.string.stat_minutes, available)))
                    bankLine.addView(
                        ui.tag(getString(R.string.stat_reclaimed)).apply {
                            setPadding(ui.dimens.m, 0, 0, ui.dimens.m)
                        }
                    )
                    addView(bankLine)
                }
            }
        )
        card.addView(head)

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.rule())
        card.addView(
            ui.row().apply {
                addView(ui.weighted(ui.statTile(getString(R.string.stat_bank_earned), getString(R.string.stat_minutes, state.earnedMinutes))))
                addView(ui.ruleVertical())
                addView(ui.weighted(ui.statTile(getString(R.string.stat_bank_spent), getString(R.string.stat_minutes, state.spentMinutes))))
            }
        )

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.note(getString(R.string.time_bank_explain)))

        if (available > 0L) {
            renderTimeBankUse(card, available)
        }

        statusContainer.addView(card)
    }

    private fun renderTimeBankUse(block: LinearLayout, available: Long) {
        block.addView(ui.vgap(ui.dimens.groupGap))
        block.addView(ui.note(getString(R.string.time_bank_use_intro)))
        block.addView(ui.vgap(ui.dimens.lineGap))

        val row = ui.row()
        row.addView(
            ui.secondary(getString(R.string.time_bank_use_10)).apply {
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
        row.addView(ui.hgap(ui.dimens.m))
        row.addView(
            ui.secondary(getString(R.string.time_bank_use_15)).apply {
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

        // A card like the rest of Home's blocks: the form was floating on bare
        // canvas, which made the one thing a new user is meant to try look like
        // leftover text.
        val card = ui.bentoCard(getString(R.string.focus_heading))

        if (session != null && session.isActive) {
            card.addView(
                ui.pose(
                    PixelMarks.focus(session.activityType),
                    ui.dimens.character,
                    getString(R.string.cd_focus_mark, activityLabel(session.activityType))
                )
            )
            card.addView(ui.vgap(ui.dimens.lineGap))
            card.addView(ui.lead(getString(R.string.focus_active_heading, activityLabel(session.activityType))))
            card.addView(ui.vgap(ui.dimens.lineGap))
            card.addView(ui.note(focusRemainingLine(session)))
            card.addView(ui.vgap(ui.dimens.groupGap))
            card.addView(ui.secondary(getString(R.string.action_return_session)).apply {
                setOnClickListener { openFocusSessionPanel() }
            })
        } else {
            card.addView(ui.note(getString(R.string.focus_intro)))
            card.addView(ui.vgap(ui.dimens.groupGap))
            renderFocusSetup(card)
        }

        statusContainer.addView(card)
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

        // The mark above the list is the answer to "what am I about to pick":
        // it changes the moment another activity is chosen.
        val markType = activities.getOrNull(state.selectedActivityIndex) ?: FocusActivityType.STUDY
        val mark = ui.pose(
            PixelMarks.focus(markType),
            ui.dimens.character,
            getString(R.string.cd_focus_mark, activityLabel(markType))
        )
        anchor.addView(mark)
        anchor.addView(ui.vgap(ui.dimens.groupGap))

        anchor.addView(ui.sectionHeader(getString(R.string.focus_activity_heading)))
        anchor.addView(ui.vgap(ui.dimens.lineGap))

        val actGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        activities.forEachIndexed { index, type ->
            actGroup.addView(
                ui.option(
                    getString(
                        R.string.focus_activity_option,
                        activityLabel(type),
                        FocusAppPolicy.getApprovedPackages(type).size
                    )
                ).apply {
                    id = index + 100
                    isChecked = index == state.selectedActivityIndex
                }
            )
        }
        actGroup.setOnCheckedChangeListener { _, checkedId ->
            state.selectedActivityIndex = (checkedId - 100).coerceAtLeast(0)
            val type = activities.getOrNull(state.selectedActivityIndex)
            if (type != null) {
                mark.setImageResource(PixelMarks.focus(type))
                mark.contentDescription = getString(R.string.cd_focus_mark, activityLabel(type))
            }
        }
        anchor.addView(actGroup)

        anchor.addView(ui.vgap(ui.dimens.groupGap))
        anchor.addView(ui.sectionHeader(getString(R.string.focus_goal_heading)))
        anchor.addView(ui.vgap(ui.dimens.lineGap))

        val goalField = ui.input(getString(R.string.focus_goal_hint)).apply {
            setText(state.focusGoalDraft)
            addTextChangedListener(afterTextChanged = { text ->
                state.focusGoalDraft = text?.toString()?.trim().orEmpty()
            })
        }
        goalInput = goalField
        anchor.addView(goalField)

        anchor.addView(ui.vgap(ui.dimens.groupGap))
        anchor.addView(ui.sectionHeader(getString(R.string.focus_duration_heading)))
        anchor.addView(ui.vgap(ui.dimens.lineGap))

        val durGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        durations.forEachIndexed { index, duration ->
            durGroup.addView(
                // A bare number, with the unit in the heading: four "60 min"
                // radios will not fit a 320dp screen, and these four values are
                // the only ones the engine accepts.
                ui.option(getString(R.string.focus_duration_value, duration)).apply {
                    id = index + 200
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = RadioGroup.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    isChecked = index == state.selectedDurationIndex
                }
            )
        }
        durGroup.setOnCheckedChangeListener { _, checkedId ->
            state.selectedDurationIndex = (checkedId - 200).coerceAtLeast(0)
        }
        anchor.addView(durGroup)

        anchor.addView(ui.vgap(ui.dimens.groupGap))
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
        anchor.addView(ui.vgap(ui.dimens.lineGap))
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
        header.addView(ui.vgap(ui.dimens.lineGap))
        header.addView(ui.annotation(getString(R.string.quest_subhead)))
        header.addView(ui.vgap(ui.dimens.lineGap))
        header.addView(ui.accents(seed = 2))
        questContainer.addView(header)

        // On this screen the attempt is the news, so it takes the one heavy
        // frame; with nothing in flight the board takes it instead.
        val attemptIsLive = currentAttempt != null && activeQuest != null
        if (currentAttempt != null && activeQuest != null) {
            questContainer.addView(
                buildQuestStatusBlock(
                    currentAttempt,
                    activeQuest,
                    withHeader = true,
                    weight = SurfaceWeight.MAJOR
                )
            )
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
            // One ruled card for the whole board: separate floating rows read as
            // leftovers, and the hairlines inside give each quest its own line.
            val listCard = ui.bentoCard(
                getString(R.string.quest_yours),
                weight = if (attemptIsLive) SurfaceWeight.MINOR else SurfaceWeight.MAJOR
            )
            others.forEachIndexed { index, quest ->
                if (index > 0) listCard.addView(ui.rowRule())
                listCard.addView(buildQuestRow(quest))
            }
            questContainer.addView(listCard)
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

        // The mark goes first: on a board of similar rows it is what lets the
        // eye find "the walking one" without reading four titles.
        row.addView(
            ui.pose(
                PixelMarks.quest(quest.category),
                ui.dimens.touchMin,
                getString(PixelMarks.description(quest.category))
            )
        )
        row.addView(ui.hgap(ui.dimens.m))

        val info = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(ui.lead(quest.title))
        info.addView(ui.vgap(ui.dimens.pairGap))
        info.addView(ui.note(quest.description))
        info.addView(ui.vgap(ui.dimens.lineGap))

        val meta = ui.row()
        meta.addView(ui.tag(getString(R.string.unit_minutes, quest.estimatedDurationMinutes)))
        meta.addView(ui.hgap(ui.dimens.m))
        // Gold means one thing in ALIVE: time you earned. Dark gold, because it
        // has to stay readable as text at 10sp.
        meta.addView(
            ui.tag(getString(R.string.unit_minutes_gain, quest.rewardMinutes)).apply {
                setTextColor(ui.palette.rewardDark)
            }
        )
        info.addView(meta)

        if (!verifiable) {
            info.addView(ui.vgap(ui.dimens.lineGap))
            info.addView(ui.note(getString(R.string.quest_not_verifiable)))
        }
        row.addView(info)

        row.addView(
            if (completed) ui.note(getString(R.string.quest_reclaimed_label))
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

        column.addView(
            ui.pose(
                PixelMarks.quest(quest.category),
                ui.dimens.characterLarge,
                getString(PixelMarks.description(quest.category))
            )
        )
        column.addView(ui.vgap(ui.dimens.groupGap))

        column.addView(ui.headline(quest.title))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.lead(getString(R.string.quest_detail_duration, quest.estimatedDurationMinutes)))
        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.body(quest.description))

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_how_title)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(
            ui.note(
                getString(if (verifiable) R.string.quest_detail_how_body else R.string.quest_detail_how_body_unsupported)
            )
        )

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_reward_title)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.note(getString(R.string.quest_detail_reward_body, quest.rewardMinutes)))

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(
            ui.note(
                getString(AliveStates.categoryLabel(quest.category)) + "  ·  " +
                    getString(AliveStates.difficultyLabel(quest.difficulty))
            )
        )

        column.addView(ui.vgap(ui.dimens.groupGap))

        when {
            !verifiable -> column.addView(ui.note(getString(R.string.quest_not_verifiable)))

            AliveStates.isAttemptActive(state.attempt?.state) -> {
                val blocked = ui.primary(getString(R.string.action_start_quest))
                blocked.isEnabled = false
                column.addView(blocked)
                column.addView(ui.vgap(ui.dimens.lineGap))
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

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_how_title)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.note(getString(R.string.focus_monitors)))

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
        column.addView(
            ui.pose(
                PixelMarks.focus(session.activityType),
                ui.dimens.characterLarge,
                getString(R.string.cd_focus_mark, activityLabel(session.activityType))
            )
        )
        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.headline(getString(R.string.focus_active_heading, activityLabel(session.activityType))))
        column.addView(ui.vgap(ui.dimens.groupGap))

        column.addView(ui.timer(focusRemainingLine(session)))
        column.addView(ui.vgap(ui.dimens.pairGap))
        column.addView(ui.note(getString(R.string.focus_of_total, session.durationMinutes)))

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.note(getString(R.string.focus_goal_quoted, session.goal)))

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.sectionHeader(getString(R.string.focus_allowed_heading)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(
            ui.note(
                getString(
                    R.string.focus_allowed_body,
                    FocusAppPolicy.getApprovedPackages(session.activityType).size
                )
            )
        )
        column.addView(ui.vgap(ui.dimens.lineGap))
        // ALIVE watches which app is foregrounded. That is the whole claim.
        column.addView(ui.note(getString(R.string.focus_monitors)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.annotation(getString(R.string.focus_next)))

        val bank = state.signals?.reclaimedAvailableMinutes ?: 0L
        column.addView(ui.vgap(ui.dimens.groupGap))
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
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.note(getString(R.string.focus_extend_support)))
    }

    // ------------------------------------------------------------------
    // USAGE CHART - reads the history store, never the engine
    // ------------------------------------------------------------------

    /**
     * Today, the last seven days, the month so far.
     *
     * The only figure that comes from the engine is today's, and it comes from
     * [state] rather than a fresh read. Everything older comes from
     * [usageHistory], a store with no side effects at all: it cannot roll the
     * day, set the lock, or clear a session.
     */
    private fun openUsageChartPanel() {
        state.panel = AlivePanel.USAGE_CHART

        val (scroll, column) = panel()
        column.addView(panelHeader(getString(R.string.chart_heading)))
        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.annotation(getString(R.string.chart_subhead)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.accents(seed = 3))

        val ranges = ChartRange.values()
        val switch = RadioGroup(this)
        ranges.forEachIndexed { index, range ->
            switch.addView(
                // Stacked rather than side by side: "Last 7 days" and "This
                // month" cannot share a 320dp row with their radio circles.
                ui.option(getString(UsageChartStates.rangeLabel(range))).apply {
                    id = CHART_RANGE_ID_BASE + index
                    isChecked = index == state.chartRangeIndex
                }
            )
        }

        val body = ui.column()
        switch.setOnCheckedChangeListener { _, checkedId ->
            state.chartRangeIndex = (checkedId - CHART_RANGE_ID_BASE).coerceIn(0, ranges.size - 1)
            renderChartBody(body)
        }

        column.addView(switch)
        column.addView(body)
        renderChartBody(body)

        openOverlay(scroll)
    }

    /** Redrawn in place when the range changes, so the panel keeps its scroll. */
    private fun renderChartBody(body: LinearLayout) {
        body.removeAllViews()

        val range = ChartRange.values().getOrNull(state.chartRangeIndex) ?: ChartRange.TODAY
        val today = LocalDate.now()
        val todayKey = today.toString()
        val bars = chartBars(UsageChartStates.dateKeys(range, today), todayKey)

        body.addView(ui.vgap(ui.dimens.cardGap))
        body.addView(buildDayCard(range, todayKey, bars))

        // Per-app time is only ever stated for today. It is stored for other
        // days, but a month of package names reads as surveillance, which is
        // not what ALIVE claims to do.
        if (range == ChartRange.TODAY) {
            buildAppCard()?.let { card ->
                body.addView(ui.vgap(ui.dimens.cardGap))
                body.addView(card)
            }
        }
    }

    /** The days card: bars, what the marks mean, and how much of the range is real. */
    private fun buildDayCard(range: ChartRange, todayKey: String, bars: List<DayBar>): LinearLayout {
        val card = ui.bentoCard(
            getString(R.string.chart_days_heading),
            weight = SurfaceWeight.MAJOR
        )
        val detail = ui.note(getString(R.string.chart_tap_hint))

        val chart = BrutalistBarChart(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dimens.dp(if (range == ChartRange.THIS_MONTH) CHART_HEIGHT_MONTH else CHART_HEIGHT_DAY)
            )
            contentDescription = getString(R.string.cd_usage_chart)
            setData(
                bars.map { bar ->
                    BrutalistBarChart.Column(
                        label = chartLabel(bar, todayKey, range),
                        minutes = bar.minutes,
                        fraction = bar.fraction,
                        kind = bar.kind
                    )
                }
            )
            onColumnTapped = { index ->
                detail.text = if (index < 0) {
                    getString(R.string.chart_tap_hint)
                } else {
                    dayDetail(bars[index])
                }
            }
        }
        card.addView(chart)
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(detail)

        val (totalMinutes, recordedCount) = UsageChartStates.totals(bars)
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.note(getString(R.string.chart_total_minutes, totalMinutes)))
        if (bars.size > 1) {
            card.addView(ui.vgap(ui.dimens.pairGap))
            card.addView(ui.note(getString(R.string.chart_days_recorded, recordedCount, bars.size)))
        }
        if (recordedCount < bars.size) {
            card.addView(ui.vgap(ui.dimens.lineGap))
            card.addView(ui.annotation(getString(UsageChartStates.coverageCopy(recordedCount, bars.size))))
        }

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.note(getString(R.string.chart_axis_note)))
        card.addView(ui.vgap(ui.dimens.pairGap))
        // The two marks that are not self-evident, named rather than left to
        // guesswork: a gap in the record and a day with nothing to spend.
        card.addView(ui.note(getString(R.string.chart_shape_note)))
        if (range == ChartRange.THIS_MONTH) {
            card.addView(ui.vgap(ui.dimens.pairGap))
            // A month has no room for 31 readable labels, so the row is
            // explained as a whole instead of named day by day.
            card.addView(ui.note(getString(R.string.chart_month_axis)))
        }
        return card
    }

    /**
     * Today's time by app: a ring, and the legend that names every slice.
     *
     * Null when the device reported no entertainment apps, because a card that
     * says "today, by app" with nothing in it is a card that should not exist.
     *
     * The number in the middle of the ring is the day's total, not the sum of
     * the slices that fit: it used to be measured after the cut-off, so a fifth
     * app made the ring disagree with the `Used` tile on the card above it.
     * Whatever is left over becomes one hatched slice that says so out loud.
     */
    private fun buildAppCard(): LinearLayout? {
        val recorded = state.entertainmentAppDetails.filter { it.second > 0L }
        if (recorded.isEmpty()) return null

        val total = recorded.sumOf { it.second }
        val shown = recorded.take(AliveSeries.MAX_SLICES)
        val foldedCount = recorded.size - shown.size
        val foldedMinutes = total - shown.sumOf { it.second }

        val series = AliveSeries.donut.map { ContextCompat.getColor(this, it) }
        val aggregateColor = ContextCompat.getColor(this, AliveSeries.aggregate)
        val card = ui.bentoCard(getString(R.string.chart_apps_heading))

        val slices = shown.mapIndexed { index, entry ->
            BrutalistDonut.Slice(
                label = AppLabels.labelFor(this, entry.first),
                minutes = entry.second,
                color = series[index],
                hatched = false
            )
        } + if (foldedMinutes > 0L) {
            listOf(
                BrutalistDonut.Slice(
                    label = getString(R.string.chart_apps_folded, foldedCount),
                    minutes = foldedMinutes,
                    color = aggregateColor,
                    hatched = true
                )
            )
        } else {
            emptyList()
        }

        val donut = BrutalistDonut(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dimens.dp(CHART_HEIGHT_DAY)
            )
            totalCaption = getString(R.string.chart_total_label)
            contentDescription = getString(R.string.cd_app_donut)
            setData(slices, total)
        }
        card.addView(donut)

        card.addView(ui.vgap(ui.dimens.groupGap))
        slices.forEachIndexed { index, slice ->
            val row = ui.row().apply {
                setPadding(0, ui.dimens.lineGap, 0, ui.dimens.lineGap)
                // Tapping a name selects its slice, which is the same thing the
                // ring does when tapped: the two never disagree.
                setOnClickListener { donut.select(index) }
            }
            row.addView(ui.swatch(slice.color))
            row.addView(
                ui.weighted(ui.body(slice.label)).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    minimumWidth = 0
                }
            )
            row.addView(ui.hgap(ui.dimens.lineGap))
            row.addView(ui.tag(getString(R.string.unit_minutes, slice.minutes)))
            card.addView(row)
        }

        card.addView(ui.vgap(ui.dimens.lineGap))
        // A fallback label is still printed as a label, so this says where the
        // names come from instead of pretending every one of them resolved.
        card.addView(ui.annotation(getString(R.string.chart_labels_note)))
        return card
    }

    /**
     * One bar per requested day. Today is the figure Home is showing, not the
     * last snapshot the engine wrote; a day ALIVE never recorded stays MISSING
     * rather than being drawn as a zero.
     */
    private fun chartBars(dateKeys: List<String>, todayKey: String): List<DayBar> {
        val recorded = usageHistory.daysFor(dateKeys).toMutableMap()
        if (dateKeys.contains(todayKey)) {
            recorded[todayKey] = UsageDay(
                date = todayKey,
                totalMinutes = state.usedMinutesToday,
                limitMinutes = state.dailyLimitMinutes,
                perAppMinutes = state.entertainmentAppDetails.toMap()
            )
        }
        return UsageChartStates.bars(dateKeys, recorded)
    }

    /** Under a bar: which day it was, and what that day's number means. */
    private fun dayDetail(bar: DayBar): String {
        val date = LocalDate.parse(bar.date)
        val name = getString(
            R.string.chart_day_name,
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            date.dayOfMonth
        )
        return when (bar.kind) {
            DayBarKind.MISSING -> getString(R.string.chart_day_missing_detail, name)
            DayBarKind.RECOVERY -> getString(R.string.chart_day_recovery_detail, name)
            else -> getString(R.string.chart_day_detail, name, bar.minutes, bar.limitMinutes)
        }
    }

    /**
     * What sits under each bar. Today is named, the week says the weekday, the
     * month says the day of the month - and only on the first and every fifth,
     * because 31 labels do not fit and a truncated one is worse than none.
     */
    private fun chartLabel(bar: DayBar, todayKey: String, range: ChartRange): String = when {
        bar.date == todayKey -> getString(R.string.chart_label_today)
        range == ChartRange.LAST_7_DAYS ->
            LocalDate.parse(bar.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        range == ChartRange.THIS_MONTH -> {
            val dayOfMonth = LocalDate.parse(bar.date).dayOfMonth
            if (dayOfMonth == 1 || dayOfMonth % 5 == 0) dayOfMonth.toString() else ""
        }
        else -> ""
    }

    // ------------------------------------------------------------------
    // PROFILE - read only, from what the dashboard already read
    // ------------------------------------------------------------------

    private fun renderProfile() {
        profileContainer.removeAllViews()

        // Masthead on bare canvas, like Home and Quest: the identity lines are
        // the one place Profile is allowed to stay quiet.
        profileContainer.addView(ui.display(getString(R.string.profile_heading)))
        profileContainer.addView(ui.vgap(ui.dimens.lineGap))
        profileContainer.addView(ui.annotation(getString(R.string.profile_identity)))
        profileContainer.addView(ui.vgap(ui.dimens.lineGap))
        profileContainer.addView(ui.note(getString(R.string.profile_identity_support)))
        profileContainer.addView(ui.vgap(ui.dimens.lineGap))
        profileContainer.addView(ui.accents(seed = 4))

        // Everything below comes from state.*; nothing re-polls the engine.
        val signals = state.signals
        if (signals != null) {
            profileContainer.addView(ui.vgap(ui.dimens.groupGap))
            profileContainer.addView(buildStateCard(signals))
        }

        profileContainer.addView(ui.vgap(ui.dimens.groupGap))
        profileContainer.addView(buildBankCard())

        renderProfilePermissions()
        renderProfileHistory()

        profileContainer.addView(ui.vgap(ui.dimens.groupGap))
        // PLAIN on purpose: one sentence about how the app decides does not need
        // the same frame as the numbers it is explaining.
        val how = ui.bentoCard(
            getString(R.string.profile_how_title),
            weight = SurfaceWeight.PLAIN
        )
        how.addView(ui.note(getString(R.string.profile_how_body)))
        profileContainer.addView(how)
    }

    /** Where the day stands: the pose, the sentence, and the two day facts. */
    private fun buildStateCard(signals: AliveSignals): LinearLayout {
        val card = ui.bentoCard(getString(R.string.profile_state_heading))
        val stage = AliveStates.stageOf(signals)

        val line = ui.row()
        line.addView(ui.animatedPose(AliveStates.poseFor(stage), ui.dimens.character, characterDescription(stage)))
        line.addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.l, 0, 0, 0)
                addView(ui.body(getString(AliveStates.annotationFor(signals))))
                addView(ui.vgap(ui.dimens.lineGap))
                addView(ui.note(streakLine(signals.currentStreak)))
                if (state.guardGapOpen || state.guardGapRepairedToday) {
                    addView(ui.vgap(ui.dimens.lineGap))
                    addView(ui.note(getString(R.string.guard_profile_line)))
                }
            }
        )
        card.addView(line)

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.rule())
        card.addView(
            ui.row().apply {
                addView(ui.weighted(ui.statTile(getString(R.string.stat_allowance), getString(R.string.stat_minutes, state.dailyLimitMinutes))))
                addView(ui.ruleVertical())
                addView(ui.weighted(ui.statTile(getString(R.string.stat_used), getString(R.string.stat_minutes, state.usedMinutesToday))))
            }
        )
        return card
    }

    /** The bank, as a ledger rather than three stacked sentences. */
    private fun buildBankCard(): LinearLayout {
        val card = ui.bentoCard(getString(R.string.time_bank_heading))
        card.addView(
            ui.row().apply {
                addView(ui.weighted(ui.statTile(getString(R.string.stat_bank_earned), getString(R.string.stat_minutes, state.earnedMinutes))))
                addView(ui.ruleVertical())
                addView(ui.weighted(ui.statTile(getString(R.string.stat_bank_spent), getString(R.string.stat_minutes, state.spentMinutes))))
            }
        )
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(
            ui.body(
                getString(R.string.time_bank_available_total, state.signals?.reclaimedAvailableMinutes ?: 0L)
            )
        )
        return card
    }

    /** A streak is a fact about days, not a badge. */
    private fun streakLine(streak: Int): String = when {
        streak <= 0 -> getString(R.string.profile_no_days)
        streak == 1 -> getString(R.string.profile_one_day_in_a_row)
        else -> getString(R.string.profile_days_in_a_row, streak)
    }

    private fun renderProfilePermissions() {
        val card = ui.bentoCard(getString(R.string.profile_permissions_heading))
        card.addView(
            permissionLine(
                getString(R.string.permissions_usage_action),
                hasUsageAccess(),
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )
        )
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            permissionLine(
                getString(R.string.permissions_accessibility_action),
                isAccessibilityServiceEnabled(),
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )
        profileContainer.addView(ui.vgap(ui.dimens.groupGap))
        profileContainer.addView(card)
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

    /** What already landed, each row carrying the mark of the quest it was. */
    private fun renderProfileHistory() {
        val completed = state.questCatalog.filter { it.status == QuestStatus.COMPLETED }

        val card = ui.bentoCard(getString(R.string.profile_history_heading))
        if (completed.isEmpty()) {
            card.addView(ui.note(getString(R.string.profile_history_empty)))
        } else {
            completed.forEachIndexed { index, quest ->
                if (index > 0) card.addView(ui.rowRule())
                val row = ui.row().apply { setPadding(0, ui.dimens.s, 0, ui.dimens.s) }
                row.addView(
                    ui.pose(
                        PixelMarks.quest(quest.category),
                        ui.dimens.dp(24),
                        getString(PixelMarks.description(quest.category))
                    )
                )
                row.addView(ui.hgap(ui.dimens.m))
                row.addView(
                    ui.weighted(
                        ui.note(
                            getString(R.string.profile_history_row, quest.title, quest.rewardMinutes)
                        )
                    )
                )
                card.addView(row)
            }
        }
        profileContainer.addView(ui.vgap(ui.dimens.groupGap))
        profileContainer.addView(card)
    }

    // ------------------------------------------------------------------
    // DEVELOPER PANEL - debug builds only, still the only way to reach the
    // locked and recovery-day states by hand
    // ------------------------------------------------------------------

    private fun renderDevPanel() {
        val block = ui.column()
        block.addView(ui.tag("[dev]"))
        block.addView(ui.vgap(ui.dimens.lineGap))

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

/** Kept clear of the focus form's 100/200 offsets, which are also view ids. */
private const val CHART_RANGE_ID_BASE = 300

/** Chart heights in dp. A month is shorter: 31 columns need width, not height. */
private const val CHART_HEIGHT_DAY = 200
private const val CHART_HEIGHT_MONTH = 150
