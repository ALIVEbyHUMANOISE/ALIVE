package com.projectego.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.QuestEvidenceRepository
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.data.UsageHistoryRepository
import com.projectego.app.data.WelcomeGate
import com.projectego.app.domain.AndroidPhysicalQuestEvidenceProvider
import com.projectego.app.domain.CriticalAlertPolicy
import com.projectego.app.domain.EntertainmentClaimCoordinator
import com.projectego.app.domain.EntertainmentClaimPolicy
import com.projectego.app.domain.FocusAppPolicy
import com.projectego.app.domain.PhysicalQuestPolicy
import com.projectego.app.domain.PhysicalQuestVerifier
import com.projectego.app.domain.QuestStartCoordinator
import com.projectego.app.domain.QuestVerificationCoordinator
import com.projectego.app.domain.UsageReport
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.FocusSession
import com.projectego.app.model.PhysicalQuestRequirement
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.TimeStatus
import com.projectego.app.model.UsageDay
import com.projectego.app.model.VerificationMethod
import com.projectego.app.ui.AliveComponents
import com.projectego.app.ui.AliveFace
import com.projectego.app.ui.AlivePanel
import com.projectego.app.ui.AliveRenderState
import com.projectego.app.ui.AliveSignals
import com.projectego.app.ui.AliveStates
import com.projectego.app.ui.AliveSeries
import com.projectego.app.ui.AppLabels
import com.projectego.app.ui.BrutalistBarChart
import com.projectego.app.ui.BrutalistDonut
import com.projectego.app.ui.ChartRange
import com.projectego.app.ui.DayBar
import com.projectego.app.ui.DayBarKind
import com.projectego.app.ui.HomeAction
import com.projectego.app.ui.PermissionChecks
import com.projectego.app.ui.PixelMarks
import com.projectego.app.ui.QuestHues
import com.projectego.app.ui.RewardBurst
import com.projectego.app.ui.StatusTone
import com.projectego.app.ui.SurfaceWeight
import com.projectego.app.ui.UsageChartStates
import com.projectego.app.ui.VerificationReasonClass
import com.projectego.app.ui.WelcomeActivity
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

    private companion object {
        // The only runtime permission this app ever asks for; nothing else
        // uses onRequestPermissionsResult, so the code cannot collide.
        const val REQUEST_CODE_ACTIVITY_RECOGNITION = 1001
    }

    private lateinit var usageTracker: UsageTracker
    private lateinit var lockStateManager: LockStateManager
    private lateinit var timeBankRepository: TimeBankRepository
    private lateinit var questRepository: QuestRepository
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var questStartCoordinator: QuestStartCoordinator
    private lateinit var questVerificationCoordinator: QuestVerificationCoordinator
    private lateinit var claimCoordinator: EntertainmentClaimCoordinator

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
    private var timeBankAnchor: View? = null
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

    // True between launching the welcome and the first resume that follows it.
    // The app is built hidden the whole time; the return trip flips it visible.
    private var welcomeStarted = false

    // The physical quest whose start is waiting on the ACTIVITY_RECOGNITION
    // dialog. Only one quest can start at a time, so one slot is enough.
    private var pendingPhysicalQuestId: String? = null
    private var activityRecognitionDenied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageTracker = UsageTracker(this)
        lockStateManager = LockStateManager(this)
        timeBankRepository = TimeBankRepository(this)
        questRepository = QuestRepository(this, timeBankRepository)
        focusSessionManager = FocusSessionManager(this, timeBankRepository)
        val questEvidenceRepository = QuestEvidenceRepository(this)
        val physicalEvidenceProvider = AndroidPhysicalQuestEvidenceProvider(this)
        questStartCoordinator = QuestStartCoordinator(
            questRepository,
            focusSessionManager,
            physicalEvidenceProvider = physicalEvidenceProvider,
            questEvidenceRepository = questEvidenceRepository
        )
        questVerificationCoordinator = QuestVerificationCoordinator(
            questRepository,
            focusSessionManager,
            physicalEvidenceProvider = physicalEvidenceProvider,
            questEvidenceRepository = questEvidenceRepository
        )
        claimCoordinator = EntertainmentClaimCoordinator(lockStateManager, timeBankRepository, usageTracker)

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

        // The first cold start is the only moment that can open the welcome.
        // Deliberately here and not in onResume: onResume also fires when the
        // welcome itself closes, and relaunching from there would trap anyone
        // who backs out of its first screen. What waits behind the welcome is
        // built hidden - INVISIBLE, not unrendered - so the app never flashes
        // a half-configured Home between the splash and the welcome, and the
        // return trip costs one visibility flip.
        if (savedInstanceState == null && !WelcomeGate.isDone(this)) {
            welcomeStarted = true
            findViewById<View>(R.id.main).visibility = View.INVISIBLE
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // The first resume after launching the welcome is not the return trip -
        // the welcome is still about to cover this screen. Every other resume
        // is the app being visible for real, including the one right after the
        // welcome closes, however it closed.
        if (welcomeStarted) {
            welcomeStarted = false
        } else {
            findViewById<View>(R.id.main).visibility = View.VISIBLE
        }
        questVerificationCoordinator.evaluateAttemptOnResume(this)
        renderDashboard()
        renderQuestSection()
        // Profile draws from the cache those two renders just refreshed.
        if (bottomNav.selectedItemId == R.id.nav_profile) renderProfile()
    }

    private fun hasUsageAccess(): Boolean = PermissionChecks.hasUsageAccess(this)

    private fun isAccessibilityServiceEnabled(): Boolean =
        PermissionChecks.isAccessibilityServiceEnabled(this)

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
        // A preference read with no side effects, done once per render like the
        // guard state: it decides what the Today card says about tomorrow.
        state.questDoneToday = lockStateManager.isQuestCompletedOn(
            LockStateManager.dayKeyOf(System.currentTimeMillis())
        )
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
     * The face is a display derivation, never an engine fact: it shows as a
     * sprite and at most one annotation, and is never printed as a name, a
     * level, or a bar.
     */
    private fun renderHeader(signals: AliveSignals) {
        val face = AliveStates.faceFor(signals)
        val header = ui.column()

        header.addView(homeShortcuts())
        header.addView(ui.vgap(ui.dimens.groupGap))

        val line = ui.row()
        line.addView(
            ui.animatedPose(
                AliveStates.faceSpriteFor(face),
                ui.dimens.characterLarge,
                characterDescription(face)
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
                        addView(ui.caret(ui.dimens.caretHeight))
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
     * The three places on Home worth jumping to, as tiles at the top rather than
     * as words somewhere further down the scroll.
     *
     * The Time Bank especially: it is the thing that pays for an extension, and
     * while it lived only in a card six screens' worth of scrolling down, most
     * people never found out it existed.
     */
    private fun homeShortcuts(): View = ui.row().apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        addView(
            shortcutTile(R.drawable.ic_pixel_chart, R.string.cd_shortcut_chart, ui.palette.workPale) {
                openUsageChartPanel()
            }
        )
        addView(ui.hgap(ui.dimens.lineGap))
        addView(
            shortcutTile(R.drawable.ic_time_indicator, R.string.cd_shortcut_bank, ui.palette.rewardPale) {
                revealTimeBank()
            }
        )
        addView(ui.hgap(ui.dimens.lineGap))
        addView(
            shortcutTile(R.drawable.ic_pixel_target, R.string.cd_shortcut_focus, ui.palette.softForest) {
                revealFocusSetup()
            }
        )
    }

    private fun shortcutTile(
        @DrawableRes glyph: Int,
        @StringRes name: Int,
        @ColorInt hue: Int,
        onTap: () -> Unit
    ): View = ui.iconTile(hue, glyph, ui.dimens.touchMin).apply {
        contentDescription = getString(name)
        setOnClickListener { onTap() }
    }

    private fun revealTimeBank() {
        val anchor = timeBankAnchor ?: return
        homeScroll.post { homeScroll.smoothScrollTo(0, anchor.top) }
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
        // A tinted card, and the tint is the documented meaning of this pale: a
        // closed door is a pale surface with an ink edge, never a red alarm.
        val card = ui.tintedBlock(
            if (repaired) ui.palette.softForest else ui.palette.shortPale,
            weight = if (homeMajor == HomeMajor.GUARD) SurfaceWeight.MAJOR else SurfaceWeight.MINOR
        )

        // The figure used to sit above three grey paragraphs of the same size,
        // which is how the most important sentence on the screen ended up looking
        // like fine print. Now the figure and the headline share a line, and the
        // rest is separated by what it is for. Static on purpose: a gap asks
        // "who turned this off?", a repaired guard is just ALIVE at ease.
        val guardFace = if (repaired) AliveFace.NORMAL else AliveFace.CONFUSED
        val line = ui.row()
        line.addView(
            ui.iconTile(
                ui.palette.white,
                AliveStates.faceSpriteFor(guardFace),
                ui.dimens.character
            ).apply { contentDescription = getString(AliveStates.faceDescription(guardFace)) }
        )
        line.addView(ui.hgap(ui.dimens.m))
        line.addView(
            ui.weighted(
                ui.column().apply {
                    addView(
                        ui.headline(
                            getString(if (repaired) R.string.guard_back_heading else R.string.guard_off_heading)
                        )
                    )
                    addView(ui.vgap(ui.dimens.pairGap))
                    addView(ui.body(getString(if (repaired) R.string.guard_back_body else R.string.guard_off_body)))
                }
            )
        )
        card.addView(line)

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.rowRule())
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            ui.body(getString(if (repaired) R.string.guard_repair_done else R.string.guard_repair_needed))
        )
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(ui.annotation(getString(R.string.guard_who_turned_it_off)))

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

    private fun characterDescription(face: AliveFace): String =
        getString(AliveStates.faceDescription(face))

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
            // No big number: the day is locked (ALIVE is angry at the phone),
            // in recovery (ALIVE naps), or down to its last minute.
            val face = AliveStates.faceFor(signals)
            card.addView(ui.headline(getString(headlineRes)))
            card.addView(ui.vgap(ui.dimens.lineGap))
            card.addView(
                ui.mark(
                    AliveStates.faceSpriteFor(face),
                    ui.dimens.character,
                    getString(AliveStates.faceDescription(face))
                )
            )
        }

        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            ui.note(
                getString(AliveStates.timeSupportLine(report.timeStatus, report.criticalZoneTier))
            )
        )

        // The rule, said on the day it still applies to - not for the first time
        // on the morning it bites.
        card.addView(ui.vgap(ui.dimens.pairGap))
        card.addView(
            ui.note(
                getString(if (state.questDoneToday) R.string.today_kept else R.string.today_not_kept)
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
    private fun statGrid(signals: AliveSignals): LinearLayout = ui.stack(ui.dimens.pairGap).apply {
        addView(
            statRow(
                streakTile(signals.currentStreak),
                ui.statTile(
                    getString(R.string.stat_allowance),
                    getString(R.string.stat_minutes, state.dailyLimitMinutes),
                    ui.palette.workPale
                )
            )
        )
        addView(
            statRow(
                ui.statTile(
                    getString(R.string.stat_used),
                    getString(R.string.stat_minutes, state.usedMinutesToday),
                    ui.palette.softForest
                ),
                ui.statTile(
                    getString(R.string.stat_bank),
                    getString(R.string.stat_minutes, signals.reclaimedAvailableMinutes),
                    ui.palette.rewardPale
                )
            )
        )
    }

    /**
     * Two tiles side by side, half each, and the same height as each other.
     *
     * [AliveComponents.EqualRow] rather than MATCH_PARENT children: inside a panel
     * - a ScrollView that fills its own viewport - a match-parent height means the
     * whole screen, and the pair then pushes the description and the Start button
     * off it. A streak tile carrying pips is taller, and the row levels to that.
     */
    private fun statRow(left: View, right: View): LinearLayout = ui.equalRow().apply {
        listOf(left, right).forEachIndexed { index, tile ->
            if (index > 0) addView(ui.hgap(ui.dimens.pairGap))
            addView(
                tile.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
        }
    }

    /**
     * The run twice over: as a number, and as the shape of that number.
     *
     * Seven gold cells filled from the left, and nothing else - the pips cannot
     * say a bigger streak than seven, so the figure above them stays the one that
     * is true. A run is worth showing as a shape because it is the only thing in
     * this app that takes more than a day to build.
     */
    private fun streakTile(streak: Int): View = ui.statTile(
        getString(R.string.stat_streak),
        getString(R.string.stat_minutes, streak),
        ui.palette.rewardPale
    ).apply {
            if (streak > 0) {
                addView(ui.vgap(ui.dimens.lineGap))
                addView(ui.streakPips(streak))
            }
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

        // The face follows the attempt - one is always in flight in this block -
        // inside the tile that keeps the category hue.
        val face = state.signals?.let { AliveStates.faceFor(it) } ?: AliveFace.NORMAL
        block.addView(
            ui.iconTile(
                ContextCompat.getColor(this, QuestHues.quest(quest.category)),
                AliveStates.faceSpriteFor(face),
                ui.dimens.character
            ).apply { contentDescription = getString(AliveStates.faceDescription(face)) }
        )
        block.addView(ui.vgap(ui.dimens.lineGap))

        block.addView(ui.lead(quest.title))
        block.addView(ui.vgap(ui.dimens.pairGap))
        block.addView(ui.note(quest.description))
        block.addView(ui.vgap(ui.dimens.groupGap))

        val available = state.signals?.reclaimedAvailableMinutes ?: 0L
        if (attempt.state == QuestAttemptState.REWARDED) {
            // The payout reads as a chip, not as a sentence: the word is the fact,
            // the gold square is the amount, and the line below says where it went.
            val rewardLine = ui.row()
            rewardLine.addView(ui.lead(getString(R.string.quest_reclaimed_label)))
            rewardLine.addView(ui.hgap(ui.dimens.m))
            rewardLine.addView(ui.rewardChip(getString(R.string.unit_minutes_gain, attempt.rewardMinutes ?: quest.rewardMinutes)))
            block.addView(rewardLine)
            celebrateReward(block, attempt)
        } else {
            block.addView(ui.lead(getString(AliveStates.attemptHeadline(attempt.state))))
        }

        // A physical quest in flight is the one state whose support line has to be
        // recomputed rather than looked up: what is true about it changes with the
        // clock, and "nothing to press" would be a lie with a button below it.
        val physicalWait = attempt.method == VerificationMethod.PHYSICAL &&
            attempt.state == QuestAttemptState.IN_PROGRESS
        val gateLeft = if (physicalWait) {
            PhysicalQuestVerifier.remainingMinutes(
                attempt.activityStartTimestamp,
                System.currentTimeMillis(),
                attempt.committedDurationMinutes
            )
        } else 0L

        val support = when {
            physicalWait -> getString(
                if (gateLeft > 0L) R.string.quest_gate_left else R.string.quest_gate_open,
                gateLeft
            )
            // VERIFIED is not REWARDED: the minutes have counted, not landed.
            attempt.state == QuestAttemptState.VERIFIED -> getString(
                if (attempt.method == VerificationMethod.PHYSICAL) {
                    R.string.verified_reason_signals
                } else {
                    AliveStates.verifiedSupport()
                }
            )
            attempt.state == QuestAttemptState.REWARDED -> getString(R.string.attempt_rewarded_total, available)
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

        if (physicalWait) {
            block.addView(ui.vgap(ui.dimens.groupGap))
            block.addView(
                ui.primary(getString(R.string.quest_declare_done)).apply {
                    // Disabled is a statement about the clock, not a punishment, and
                    // the line above says exactly how much clock is left.
                    isEnabled = gateLeft == 0L
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        if (questVerificationCoordinator.declarePhysicalAttemptDone()) {
                            renderDashboard()
                            renderQuestSection()
                        } else {
                            toast(R.string.quest_declare_failed)
                        }
                    }
                }
            )
        }

        if (!physicalWait && AliveStates.offersAction(attempt.state)) {
            block.addView(ui.vgap(ui.dimens.groupGap))
            block.addView(ui.secondary(getString(R.string.action_continue)).apply {
                setOnClickListener { openCurrentQuestPanel() }
            })
        }

        return block
    }

    /**
     * One party per quest.
     *
     * The burst is thrown from the rewarded card itself and fires the first time
     * that attempt id is rendered, so re-renders, tab switches and returning to
     * the app stay silent - a reward that celebrates for the fourth time is not a
     * reward, it is a glitch. Which card wins is not decided here: Home, the Quest
     * tab and the attempt panel all build this block, and the first one to render
     * an unpaid-celebration attempt gets it. Nothing is owed a party after a
     * restart, because the id is kept in memory only.
     */
    private fun celebrateReward(block: LinearLayout, attempt: QuestAttempt) {
        if (state.celebratedAttemptId == attempt.attemptId) return
        state.celebratedAttemptId = attempt.attemptId

        val host = findViewById<ViewGroup>(android.R.id.content) ?: return
        block.post {
            if (block.width == 0) return@post
            val card = IntArray(2)
            val surface = IntArray(2)
            block.getLocationInWindow(card)
            host.getLocationInWindow(surface)
            RewardBurst(this).apply {
                paletteColors = intArrayOf(
                    ui.palette.rewardMid,
                    ui.palette.rewardDark,
                    ui.palette.forest,
                    ui.palette.ink
                )
                // Out of the sprite, not the middle of the screen: the reward has
                // to look like it came from the thing that earned it.
                show(
                    host,
                    (card[0] - surface[0] + block.width / 2f).coerceAtLeast(0f),
                    (card[1] - surface[1] + block.height / 3f).coerceAtLeast(0f)
                )
            }
        }
    }


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
            // Blue is the Focus hue - the shortcut that jumps here is gold, but
            // these minutes are earned by focusing, and spent either on a focus
            // extension or on a claim onto today's allowance.
            ui.iconTile(ui.palette.workPale, R.drawable.ic_time_indicator, ui.dimens.character).apply {
                contentDescription = getString(R.string.cd_time_bank)
            }
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
        card.addView(
            // In is the house green these minutes come back through, and out is
            // the critter's rose. The pair is the flow, not a status.
            statRow(
                ui.statTile(
                    getString(R.string.stat_bank_earned),
                    getString(R.string.stat_minutes, state.earnedMinutes),
                    ui.palette.softForest
                ),
                ui.statTile(
                    getString(R.string.stat_bank_spent),
                    getString(R.string.stat_minutes, state.spentMinutes),
                    ui.palette.pinkSoft
                )
            )
        )

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(
            ui.note(
                getString(
                    R.string.time_bank_explain,
                    EntertainmentClaimPolicy.DAILY_CAP_MINUTES
                )
            )
        )

        renderTimeBankUse(card)

        timeBankAnchor = card
        statusContainer.addView(card)
    }

    /**
     * Where the bank's minutes actually go.
     *
     * The claim is capped per day on purpose: a bank that could refill a
     * finished day without limit would make the limit decorative. What the
     * claim cannot do is buy back a recovery day, so those are told why rather
     * than shown a button that would refuse - the decision rendered here is
     * the same [EntertainmentClaimPolicy] one the tap would execute.
     */
    private fun renderTimeBankUse(block: LinearLayout) {
        block.addView(ui.vgap(ui.dimens.groupGap))
        block.addView(ui.rule())
        block.addView(ui.vgap(ui.dimens.lineGap))

        val claimedToday = lockStateManager.claimedMinutesToday()
        val decision = EntertainmentClaimPolicy.decide(
            dayType = lockStateManager.getDayType(),
            claimedToday = claimedToday,
            availableMinutes = state.signals?.reclaimedAvailableMinutes ?: 0L
        )

        if (claimedToday > 0L) {
            block.addView(
                ui.note(
                    getString(
                        R.string.time_bank_claim_status,
                        claimedToday,
                        EntertainmentClaimPolicy.DAILY_CAP_MINUTES
                    )
                )
            )
            block.addView(ui.vgap(ui.dimens.lineGap))
        }

        when (decision) {
            // The claim is the one thing on this card you are invited to press,
            // so it wears the offer colour rather than the paper one.
            is EntertainmentClaimPolicy.Decision.Claim -> block.addView(
                ui.primary(getString(R.string.time_bank_claim_action, decision.minutes)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { claimBankMinutes() }
                }
            )
            EntertainmentClaimPolicy.Decision.DailyCapReached ->
                block.addView(ui.note(getString(R.string.time_bank_claim_full)))
            EntertainmentClaimPolicy.Decision.RecoveryDay ->
                block.addView(ui.note(getString(R.string.time_bank_claim_recovery)))
            // The head of the card already says the bank is empty; a line here
            // would only say it twice.
            EntertainmentClaimPolicy.Decision.BankEmpty -> Unit
        }
    }

    private fun claimBankMinutes() {
        val outcome = claimCoordinator.claim()
        if (outcome is EntertainmentClaimPolicy.Decision.Claim) {
            toast(R.string.time_bank_claim_toast, outcome.minutes)
        }
        // A refusal needs no toast of its own: the render below re-reads the
        // day and the bank, so the card ends up showing why instead.
        renderDashboard()
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
                ui.iconTile(
                    ContextCompat.getColor(this, QuestHues.focus(session.activityType)),
                    PixelMarks.focus(session.activityType),
                    ui.dimens.character
                ).apply { contentDescription = getString(R.string.cd_focus_mark, activityLabel(session.activityType)) }
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

        // The tile above the list is the answer to "what am I about to pick":
        // its mark and its colour both change the moment another activity is
        // chosen. Sheet poses replace the mark where one exists; WORK keeps its
        // briefcase because the sheet has no work pose.
        val markType = activities.getOrNull(state.selectedActivityIndex) ?: FocusActivityType.STUDY
        val previewFace = AliveStates.focusPreviewSpriteFor(markType)
        val mark = ui.iconTile(
            ContextCompat.getColor(this, QuestHues.focus(markType)),
            previewFace?.let { AliveStates.faceSpriteFor(it) } ?: PixelMarks.focus(markType),
            ui.dimens.character
        ).apply {
            contentDescription = previewFace?.let { getString(AliveStates.faceDescription(it)) }
                ?: getString(R.string.cd_focus_mark, activityLabel(markType))
        }
        anchor.addView(mark)
        anchor.addView(ui.vgap(ui.dimens.groupGap))

        anchor.addView(ui.sectionHeader(getString(R.string.focus_activity_heading)))
        anchor.addView(ui.vgap(ui.dimens.lineGap))

        val actGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        activities.forEachIndexed { index, type ->
            actGroup.addView(
                ui.tileOption(
                    getString(
                        R.string.focus_activity_option,
                        activityLabel(type),
                        FocusAppPolicy.getApprovedPackages(type).size
                    ),
                    PixelMarks.focus(type),
                    ContextCompat.getColor(this@MainActivity, QuestHues.focus(type))
                ).apply {
                    id = index + 100
                    isChecked = index == state.selectedActivityIndex
                    layoutParams = RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.MATCH_PARENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        // Tiles need air between them; a ruled list did not,
                        // because the rule was the separation.
                        if (index > 0) topMargin = ui.dimens.pairGap
                    }
                }
            )
        }
        actGroup.setOnCheckedChangeListener { _, checkedId ->
            state.selectedActivityIndex = (checkedId - 100).coerceAtLeast(0)
            val type = activities.getOrNull(state.selectedActivityIndex)
            if (type != null) {
                mark.setImageResource(PixelMarks.focus(type))
                ui.tintTile(mark, ContextCompat.getColor(this@MainActivity, QuestHues.focus(type)))
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
                ui.tileOption(getString(R.string.focus_duration_value, duration), 0, ui.palette.softForest).apply {
                    id = index + 200
                    gravity = Gravity.CENTER
                    layoutParams = RadioGroup.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply {
                        if (index > 0) marginStart = ui.dimens.pairGap
                    }
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
        // Two display lines with no gap between them: the leading is already cut
        // in the type style, so the pair reads as one sentence that wrapped.
        header.addView(ui.display(getString(R.string.quest_heading)))
        header.addView(ui.display(getString(R.string.quest_heading_second)))
        header.addView(ui.vgap(ui.dimens.lineGap))
        header.addView(ui.annotation(getString(R.string.quest_subhead)))
        header.addView(ui.vgap(ui.dimens.lineGap))
        header.addView(ui.accents(seed = 2))
        questContainer.addView(header)

        // On this screen the attempt is the news, so it takes the one heavy frame.
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
            // The board is a grid of cards, not a list of rows: a quest is chosen
            // by what it looks like from a distance, and a list of nine sentences
            // cannot be scanned that way.
            questContainer.addView(ui.sectionHeader(getString(R.string.quest_yours)))
            questContainer.addView(questGrid(others))
        }
    }

    /** Two cards per row, with the same air between them as between the rows. */
    private fun questGrid(quests: List<Quest>): LinearLayout = ui.equalGrid().apply {
        quests.chunked(QUEST_COLUMNS).forEachIndexed { rowIndex, pair ->
            if (rowIndex > 0) addView(ui.vgap(ui.dimens.cardGap))
            val row = ui.row()
            pair.forEachIndexed { index, quest ->
                if (index > 0) row.addView(ui.hgap(ui.dimens.cardGap))
                row.addView(
                    questCard(quest).apply {
                        // WRAP_CONTENT on purpose: the grid pins every card to the
                        // tallest one itself, and a MATCH_PARENT height would stop
                        // the row from reporting that card's size honestly.
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                )
            }
            if (pair.size < QUEST_COLUMNS) {
                // The odd card stays half-width. Stretching it would make the last
                // quest look like a different kind of thing.
                row.addView(
                    View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, (QUEST_COLUMNS - pair.size).toFloat())
                    }
                )
            }
            addView(row)
        }
    }

    /**
     * One quest: its category's tab on the top edge, its mark in a tile of the
     * same colour, what it costs and what it pays, and its name.
     *
     * The description is not here - it is on the page a tap opens. A grid cell
     * that carries a paragraph is a grid of two columns that is really a list.
     */
    private fun questCard(quest: Quest): FrameLayout {
        val completed = quest.status == QuestStatus.COMPLETED
        val hue = ContextCompat.getColor(this, QuestHues.quest(quest.category))
        val wrapper = FrameLayout(this)

        // The tab is added first so the card draws over its bottom half: a tab is
        // meant to look tucked behind the card, and an outline on it would draw a
        // second box around something that is only a flag.
        wrapper.addView(ui.categoryTab(hue, widthPx = ui.dimens.xxxl * 2))
        wrapper.addView(
            ui.block(SurfaceWeight.MINOR).apply {
                // The row decides how tall this card is; the card agrees with it and
                // spends the difference above its name, so a neighbour with a longer
                // title does not end its ink box somewhere else.
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { topMargin = ui.dimens.s }

                addView(
                    ui.row().apply {
                        addView(ui.iconTile(hue, PixelMarks.quest(quest.category)))
                        addView(ui.hgap(ui.dimens.m))
                        addView(
                            ui.weighted(
                                ui.column().apply {
                                    addView(ui.meta(getString(R.string.unit_minutes, quest.estimatedDurationMinutes)))
                                    addView(ui.meta(getString(R.string.unit_minutes_gain, quest.rewardMinutes)))
                                }
                            )
                        )
                    }
                )
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    }
                )
                addView(ui.vgap(ui.dimens.groupGap))
                addView(ui.lead(quest.title).apply { minLines = QUEST_TITLE_LINES })
                addView(ui.vgap(ui.dimens.lineGap))
                addView(questCardFoot(quest, completed))
            }
        )

        // Never a start: a card opens the page that explains the quest first, so a
        // tap here cannot fail silently.
        wrapper.setOnClickListener { openQuestDetailPanel(quest.id) }
        return wrapper
    }

    /**
     * The card's last line, which every card has.
     *
     * A grid whose rows are uneven reads as broken rather than varied, and the
     * foot is where a card can say the one thing it knows about itself beyond its
     * name: that it already paid out, or how hard it is. Both are one faded word
     * or one gold square and a word - never a badge, a star, or a score.
     */
    private fun questCardFoot(quest: Quest, completed: Boolean): View = ui.row().apply {
        if (completed) {
            addView(ui.swatch(ui.palette.rewardMid))
            addView(
                ui.tag(getString(R.string.quest_reclaimed_label)).apply {
                    setTextColor(ui.palette.rewardDark)
                    gravity = Gravity.CENTER_VERTICAL
                }
            )
        } else {
            addView(
                ui.tag(getString(AliveStates.difficultyLabel(quest.difficulty))).apply {
                    setTextColor(ui.palette.inkMuted)
                }
            )
        }
    }

    /** One numbered step: the number in a tile, the sentence beside it. */
    private fun stepRow(number: Int, text: String): View = ui.row().apply {
        gravity = Gravity.TOP
        addView(ui.textTile(number.toString(), ui.palette.softForest))
        addView(ui.hgap(ui.dimens.m))
        addView(ui.weighted(ui.body(text)))
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
        val requirement = PhysicalQuestPolicy.requirementFor(quest.id, quest.category)
        val stepsMeasurable = stepSensorPresent() && activityRecognitionGranted()

        val hue = ContextCompat.getColor(this, QuestHues.quest(quest.category))

        // Name, mark, and what kind of doing it is - one line. The page used to
        // open with a paragraph, which is the last thing anyone reads about a quest
        // they are deciding whether to spend twenty minutes on. Categories with a
        // sheet pose get the character; the rest keep their pixel mark.
        val heroFace = AliveStates.questHeroSpriteFor(quest.category)
        val hero = ui.row()
        hero.addView(
            ui.iconTile(
                hue,
                heroFace?.let { AliveStates.faceSpriteFor(it) } ?: PixelMarks.quest(quest.category),
                ui.dimens.characterLarge
            ).apply {
                contentDescription = getString(
                    heroFace?.let { AliveStates.faceDescription(it) }
                        ?: PixelMarks.description(quest.category)
                )
            }
        )
        hero.addView(ui.hgap(ui.dimens.l))
        hero.addView(
            ui.weighted(
                ui.column().apply {
                    addView(ui.headline(quest.title))
                    addView(ui.vgap(ui.dimens.pairGap))
                    addView(
                        ui.meta(
                            getString(AliveStates.categoryLabel(quest.category)) + "  ·  " +
                                getString(AliveStates.difficultyLabel(quest.difficulty))
                        )
                    )
                }
            )
        )
        column.addView(hero)

        // The two numbers a quest is actually decided by, as tiles: what it takes
        // out of the day, and what it puts back.
        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(
            statRow(
                ui.statTile(
                    getString(R.string.quest_detail_duration_title),
                    getString(R.string.stat_minutes, quest.estimatedDurationMinutes),
                    ui.palette.workPale
                ),
                ui.statTile(
                    getString(R.string.quest_detail_reward_title),
                    getString(R.string.unit_minutes_gain, quest.rewardMinutes),
                    ui.palette.rewardPale
                )
            )
        )

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.body(quest.description))

        column.addView(ui.vgap(ui.dimens.groupGap))
        column.addView(ui.sectionHeader(getString(R.string.quest_detail_how_title)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        listOf(
            getString(R.string.quest_step_start),
            getString(if (verifiable) R.string.quest_step_watch_digital else AliveStates.physicalQuestWatchLine(requirement)),
            getString(R.string.quest_step_reward, quest.rewardMinutes)
        ).forEachIndexed { index, step ->
            if (index > 0) column.addView(ui.vgap(ui.dimens.pairGap))
            column.addView(stepRow(index + 1, step))
        }

        column.addView(ui.vgap(ui.dimens.groupGap))

        when {
            AliveStates.isAttemptActive(state.attempt?.state) -> {
                val blocked = ui.primary(getString(R.string.action_start_quest))
                blocked.isEnabled = false
                column.addView(blocked)
                column.addView(ui.vgap(ui.dimens.lineGap))
                column.addView(ui.annotation(getString(R.string.quest_cannot_start)))
            }

            else -> {
                column.addView(
                    ui.primary(getString(R.string.action_start_quest)).apply {
                        setOnClickListener {
                            // One call owns the whole chain, and which chain it is
                            // comes down to what ALIVE can watch. A quest it cannot
                            // see starts a clock and whatever evidence its policy
                            // asks for.
                            if (verifiable) {
                                val started = questStartCoordinator.startDigitalQuest(quest.id)
                                if (started) {
                                    closeOverlay()
                                    renderDashboard()
                                    renderQuestSection()
                                } else {
                                    toast(R.string.quest_start_failed)
                                }
                            } else {
                                startPhysicalQuestFlow(quest)
                            }
                        }
                    }
                )
                if (!verifiable) {
                    // Said under the button, not instead of it: the button works,
                    // and what it promises is bounded by the clock and the quest's
                    // own signals.
                    column.addView(ui.vgap(ui.dimens.lineGap))
                    column.addView(
                        ui.annotation(
                            getString(
                                AliveStates.physicalQuestHowLine(requirement, stepsMeasurable),
                                quest.estimatedDurationMinutes
                            )
                        )
                    )
                    if (requirement.needsSteps && activityRecognitionDenied) {
                        column.addView(ui.vgap(ui.dimens.pairGap))
                        column.addView(ui.annotation(getString(R.string.quest_how_steps_permission)))
                    }
                }
            }
        }
    }

    /**
     * The door a physical quest starts through. A steps-quest on a phone that
     * can measure steps asks for the physical-activity permission first; either
     * answer still starts the quest, because a denied permission only means
     * ALIVE checks what it can (see PhysicalQuestSignalsVerifier).
     */
    private fun startPhysicalQuestFlow(quest: Quest) {
        val requirement = PhysicalQuestPolicy.requirementFor(quest.id, quest.category)
        if (requirement.needsSteps &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            stepSensorPresent() &&
            !activityRecognitionGranted()
        ) {
            pendingPhysicalQuestId = quest.id
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                REQUEST_CODE_ACTIVITY_RECOGNITION
            )
            return
        }
        completePhysicalQuestStart(quest.id)
    }

    private fun completePhysicalQuestStart(questId: String) {
        val started = questStartCoordinator.startPhysicalQuest(questId)
        if (started) {
            closeOverlay()
            renderDashboard()
            renderQuestSection()
        } else {
            toast(R.string.quest_start_failed)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_ACTIVITY_RECOGNITION) return

        val questId = pendingPhysicalQuestId
        pendingPhysicalQuestId = null
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            activityRecognitionDenied = true
        }
        if (questId != null) completePhysicalQuestStart(questId)
    }

    private fun stepSensorPresent(): Boolean {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    private fun activityRecognitionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

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
            ui.iconTile(
                ContextCompat.getColor(this, QuestHues.focus(session.activityType)),
                PixelMarks.focus(session.activityType),
                ui.dimens.characterLarge
            ).apply { contentDescription = getString(R.string.cd_focus_mark, activityLabel(session.activityType)) }
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
                // month" cannot share a 320dp row at a readable size. Blue is the
                // colour of the work of looking, and the selected one wears it.
                ui.tileOption(getString(UsageChartStates.rangeLabel(range)), 0, ui.palette.workPale).apply {
                    id = CHART_RANGE_ID_BASE + index
                    isChecked = index == state.chartRangeIndex
                    layoutParams = RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.MATCH_PARENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = ui.dimens.pairGap
                    }
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
        val (_, recordedCount) = UsageChartStates.totals(bars)

        body.addView(ui.vgap(ui.dimens.groupGap))
        body.addView(coverageBanner(bars, recordedCount))

        // One card holds the chart and its key. Side by side looked right in the
        // reference and wrong on a phone: the legend's longest label, "No
        // allowance", hyphenated across three lines in a sixty-dp column, and a
        // chart whose key is broken is not worth reading.
        body.addView(ui.vgap(ui.dimens.cardGap))
        body.addView(buildDayCard(range, todayKey, bars))

        // Per-app time is only ever stated for today. It is stored for other
        // days, but a month of package names reads as surveillance, which is
        // not what ALIVE claims to do.
        if (range == ChartRange.TODAY) {
            body.addView(ui.vgap(ui.dimens.cardGap))
            // An empty day is named, not omitted: a heading that vanished with it
            // left people wondering whether the feature had broken.
            body.addView(buildAppCard() ?: emptyAppCard())
        }
    }

    /**
     * The one raised line on this screen: how much of the range ALIVE actually
     * saw, said before any number below it is read.
     *
     * It is a banner rather than a footnote because the answer changes how every
     * bar should be taken, and a footnote is how that gets skipped.
     */
    private fun coverageBanner(bars: List<DayBar>, recordedCount: Int): View {
        val banner = ui.tintedBlock(ui.palette.softForest)
        val line = ui.row()
        line.addView(ui.iconTile(ui.palette.white, R.drawable.ic_pixel_chart, ui.dimens.character))
        line.addView(ui.hgap(ui.dimens.m))
        line.addView(
            ui.weighted(
                ui.column().apply {
                    addView(ui.sectionHeader(getString(R.string.chart_coverage_eyebrow)))
                    addView(ui.vgap(ui.dimens.pairGap))
                    addView(ui.note(getString(UsageChartStates.coverageCopy(recordedCount, bars.size))))
                }
            )
        )
        banner.addView(line)
        return banner
    }

    /**
     * What each bar means, counted, and tappable.
     *
     * Choosing a kind dims every bar that is not one, which is the only way to
     * answer "how many of these days did ALIVE never see" without asking somebody
     * to count stubs by eye. The count is printed in the row and repeated in the
     * line under it, so the answer is a number and a word - not a colour.
     */
    private fun kindLegend(bars: List<DayBar>, chart: BrutalistBarChart): LinearLayout {
        val options = listOf(
            KindOption(null, R.string.chart_legend_all, ui.palette.white),
            KindOption(DayBarKind.SPENT, R.string.chart_kind_spent, ui.palette.softForest),
            KindOption(DayBarKind.FULL, R.string.chart_kind_full, ui.palette.shortMid),
            KindOption(DayBarKind.ZERO, R.string.chart_kind_zero, ui.palette.ink),
            KindOption(DayBarKind.RECOVERY, R.string.chart_kind_recovery, ui.palette.warmGray),
            KindOption(DayBarKind.MISSING, R.string.chart_kind_missing, ui.palette.paper)
        )
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val note = ui.note(getString(R.string.chart_legend_showing_all))

        options.forEachIndexed { index, option ->
            group.addView(
                ui.legendOption(
                    getString(R.string.chart_legend_row, getString(option.label), countOf(bars, option.kind)),
                    option.hue
                ).apply {
                    id = KIND_FILTER_ID_BASE + index
                    // "All days" is checked on open: the chart starts showing the
                    // whole range it was asked about, not a slice of it.
                    isChecked = index == 0
                }
            )
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val option = options.getOrNull(checkedId - KIND_FILTER_ID_BASE) ?: return@setOnCheckedChangeListener
            chart.highlightedKind = option.kind
            note.text = if (option.kind == null) getString(R.string.chart_legend_showing_all)
            else getString(R.string.chart_legend_showing, getString(option.label), countOf(bars, option.kind))
        }

        return ui.column().apply {
            addView(group)
            addView(ui.vgap(ui.dimens.lineGap))
            addView(note)
        }
    }

    /** Every day in the range when nothing is chosen, and the kind's own count when it is. */
    private fun countOf(bars: List<DayBar>, kind: DayBarKind?): Int =
        if (kind == null) bars.size else bars.count { it.kind == kind }

    private data class KindOption(
        val kind: DayBarKind?,
        @StringRes val label: Int,
        @ColorInt val hue: Int
    )

    /** The app card with nothing in it - which is still a thing worth saying. */
    private fun emptyAppCard(): LinearLayout {
        val card = ui.bentoCard(getString(R.string.chart_apps_heading), weight = SurfaceWeight.PLAIN)
        card.addView(ui.body(getString(R.string.chart_apps_none)))
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(ui.note(getString(R.string.chart_apps_none_support)))
        return card
    }

    /** The days card: bars, what the marks mean, and how much of the range is real. */
    private fun buildDayCard(range: ChartRange, todayKey: String, bars: List<DayBar>): LinearLayout {
        val card = ui.bentoCard(
            getString(R.string.chart_days_heading),
            weight = SurfaceWeight.MAJOR
        )
        val detail = ui.note(getString(R.string.chart_tap_hint))

        val chart = BrutalistBarChart(this).apply {
            // Wrap content, not a fixed height: the chart measures how many rows
            // its own columns need.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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

        // The key belongs under the picture, not in a card beside it: the question
        // "what is that hollow one?" is asked while looking at this chart.
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.rule())
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(kindLegend(bars, chart))

        val (totalMinutes, _) = UsageChartStates.totals(bars)
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.note(getString(R.string.chart_total_minutes, totalMinutes)))

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(ui.note(getString(R.string.chart_axis_note)))
        // The fills and shapes are named by the rows above, counted; a paragraph
        // here would be the same sentence twice.
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
     * Null when the device reported no entertainment apps - the caller then
     * prints [emptyAppCard], because the honest answer to "where did today go" is
     * "nowhere that ALIVE could see", not a silent absence.
     *
     * The number in the middle of the ring is the day's total, not the sum of
     * the slices that fit: it used to be measured after the cut-off, so a fifth
     * app made the ring disagree with the `Used` tile on the card above it.
     * Whatever is left over becomes one hatched slice that says so out loud.
     */
    private fun buildAppCard(): LinearLayout? {
        // Sorted before it is cut off. The card claims the four biggest, and
        // "whatever order the device happened to return" is not the four biggest.
        val recorded = state.entertainmentAppDetails
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
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
                ui.dimens.dp(DONUT_HEIGHT_DP)
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
            // The tile wears the light end of the slice's own colour, so the row
            // and its wedge on the ring are one thing at two weights. The folded
            // remainder keeps its warm gray fill - it is not one app and does not
            // pretend to be.
            val tileHue = if (slice.hatched) aggregateColor
            else ContextCompat.getColor(this, AliveSeries.tileColor(index))
            row.addView(ui.textTile((index + 1).toString(), tileHue))
            row.addView(ui.hgap(ui.dimens.m))
            row.addView(
                ui.weighted(
                    ui.column().apply {
                        addView(
                            ui.body(slice.label).apply {
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                                minimumWidth = 0
                            }
                        )
                        addView(ui.vgap(ui.dimens.pairGap))
                        // Measured against the day, not against first place: "half
                        // my evening" is the fact, and a bar scaled to the biggest
                        // app would make every other bar look nearly empty.
                        addView(ui.meter(slice.minutes.toFloat() / total.toFloat(), slice.color))
                    }
                )
            )
            row.addView(ui.hgap(ui.dimens.m))
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
        renderProfileLanguage()
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

    /** Where the day stands: the face, the sentence, and the two day facts. */
    private fun buildStateCard(signals: AliveSignals): LinearLayout {
        val card = ui.bentoCard(getString(R.string.profile_state_heading))
        val face = AliveStates.faceFor(signals)

        val line = ui.row()
        line.addView(ui.animatedPose(AliveStates.faceSpriteFor(face), ui.dimens.character, characterDescription(face)))
        line.addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.l, 0, 0, 0)
                addView(ui.body(getString(AliveStates.annotationFor(signals))))
                addView(ui.vgap(ui.dimens.lineGap))
                addView(ui.note(streakLine(signals.currentStreak)))
                if (signals.currentStreak > 0) {
                    addView(ui.vgap(ui.dimens.lineGap))
                    // The sentence is the fact; the pips are its shape, capped at
                    // seven because a run longer than that is read from the number.
                    addView(ui.streakPips(signals.currentStreak, sizePx = ui.dimens.m))
                }
                if (state.guardGapOpen || state.guardGapRepairedToday) {
                    addView(ui.vgap(ui.dimens.lineGap))
                    addView(ui.note(getString(R.string.guard_profile_line)))
                }
            }
        )
        card.addView(line)

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(
            statRow(
                ui.statTile(
                    getString(R.string.stat_allowance),
                    getString(R.string.stat_minutes, state.dailyLimitMinutes),
                    ui.palette.workPale
                ),
                ui.statTile(
                    getString(R.string.stat_used),
                    getString(R.string.stat_minutes, state.usedMinutesToday),
                    ui.palette.softForest
                )
            )
        )
        return card
    }

    /** The bank, as a ledger rather than three stacked sentences. */
    private fun buildBankCard(): LinearLayout {
        val card = ui.bentoCard(getString(R.string.time_bank_heading))
        card.addView(
            statRow(
                ui.statTile(
                    getString(R.string.stat_bank_earned),
                    getString(R.string.stat_minutes, state.earnedMinutes),
                    ui.palette.softForest
                ),
                ui.statTile(
                    getString(R.string.stat_bank_spent),
                    getString(R.string.stat_minutes, state.spentMinutes),
                    ui.palette.pinkSoft
                )
            )
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

    /**
     * The one setting ALIVE has.
     *
     * Both names are written in their own language - "Bahasa Indonesia" never
     * becomes "Indonesian" in the Indonesian list - because a language you cannot
     * recognise in your own writing is a language you cannot choose.
     */
    private fun renderProfileLanguage() {
        val card = ui.bentoCard(getString(R.string.profile_language_heading))
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val selected = if (currentTag.startsWith("in")) 1 else 0

        listOf(
            getString(R.string.language_english) to "en",
            getString(R.string.language_indonesian) to "in"
        ).forEachIndexed { index, (label, tag) ->
            group.addView(
                ui.tileOption(label, 0, ui.palette.workPale).apply {
                    id = LANGUAGE_ID_BASE + index
                    gravity = Gravity.CENTER
                    isChecked = index == selected
                    layoutParams = RadioGroup.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply {
                        if (index > 0) marginStart = ui.dimens.pairGap
                    }
                    setOnClickListener {
                        if (currentTag != tag) {
                            // AppCompat stores the choice and recreates the screen;
                            // ALIVE keeps no settings of its own to write first.
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                        }
                    }
                }
            )
        }
        card.addView(group)
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(ui.note(getString(R.string.profile_language_support)))

        profileContainer.addView(ui.vgap(ui.dimens.groupGap))
        profileContainer.addView(card)
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
                    // The same tile the quest wears on the board, at the small
                    // size, so a finished quest is recognisable in its own history.
                    ui.iconTile(
                        ContextCompat.getColor(this@MainActivity, QuestHues.quest(quest.category)),
                        PixelMarks.quest(quest.category),
                        ui.dimens.markSmallSize
                    ).apply { contentDescription = getString(PixelMarks.description(quest.category)) }
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
        // A hairline and no shadow - the same veto a closed door gets. This panel
        // is a way into the engine's switches and must never read as one of the
        // things ALIVE shows a person.
        val block = ui.block(SurfaceWeight.MINOR, StatusTone.QUIET)
        block.addView(ui.sectionHeader(getString(R.string.dev_eyebrow)))
        block.addView(ui.vgap(ui.dimens.lineGap))

        block.addView(
            ui.quiet(
                getString(
                    if (lockStateManager.isDevTestModeEnabled()) R.string.dev_test_mode_on
                    else R.string.dev_test_mode_off
                )
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
            ui.quiet(getString(R.string.dev_start_test_focus)).apply {
                setOnClickListener {
                    focusSessionManager.startTestFocusSession(FocusActivityType.STUDY, "Test 15s Goal", 15)
                    toast(R.string.dev_test_focus_started)
                    renderDashboard()
                }
            }
        )

        // The two outcomes the new rule can produce, set up by writing the day a
        // quest landed - which is the only evidence the rollover reads now.
        block.addView(
            ui.quiet(getString(R.string.dev_rollover_quest_landed)).apply {
                setOnClickListener {
                    LockStateManager.recordQuestCompleted(this@MainActivity, yesterday())
                    lockStateManager.simulateRollover(yesterday())
                    renderDashboard()
                }
            }
        )

        block.addView(
            ui.quiet(getString(R.string.dev_rollover_no_quest)).apply {
                setOnClickListener {
                    // Marking today is how yesterday becomes empty: the key holds
                    // the last day anything landed, and nothing clears it.
                    LockStateManager.recordQuestCompleted(
                        this@MainActivity,
                        LockStateManager.dayKeyOf(System.currentTimeMillis())
                    )
                    lockStateManager.simulateRollover(yesterday())
                    renderDashboard()
                }
            }
        )

        // The old direct-reward button stays inert: a reward has to come out of
        // the verification pipeline, and re-implementing it here would be a
        // second reward path.
        val inert = ui.quiet(getString(R.string.dev_reward_locked))
        inert.isEnabled = false
        block.addView(inert)

        // The welcome once per install is also once per review. Replaying it
        // here beats clearing app data on a phone that is full of real days.
        block.addView(
            ui.quiet(getString(R.string.dev_replay_welcome)).apply {
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
                }
            }
        )

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

/** The language row's two options. Same synthetic-id contract as the pickers. */
private const val LANGUAGE_ID_BASE = 500

/** The chart's legend rows: "All days" first, then one per kind. */
private const val KIND_FILTER_ID_BASE = 700

/** The ring is a circle, so it does need a height of its own. */
private const val DONUT_HEIGHT_DP = 200

/** Cards per row on the quest board. Three is a mosaic; one is the list it was. */
private const val QUEST_COLUMNS = 2

/**
 * Title lines every card reserves. Quest names are sentences, not words, so a
 * grid without this is a grid of two heights.
 */
private const val QUEST_TITLE_LINES = 2
