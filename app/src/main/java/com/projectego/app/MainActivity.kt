package com.projectego.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestStatus
import com.projectego.app.model.TimeStatus
import com.projectego.app.service.EgoAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private lateinit var tabContent: FrameLayout
    private lateinit var bottomNav: BottomNavigationView

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

        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }
        val homeScroll = ScrollView(this)
        homeScroll.addView(statusContainer)

        questContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }
        val questScroll = ScrollView(this)
        questScroll.addView(questContainer)

        profileContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        tabContent = findViewById(R.id.tabContent)
        tabContent.addView(homeScroll)
        tabContent.addView(questScroll)
        tabContent.addView(profileContainer)

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showTab(0); renderDashboard(); true }
                R.id.nav_quest -> { showTab(1); renderQuestSection(); true }
                R.id.nav_profile -> { showTab(2); renderProfile(); true }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_home
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        questVerificationCoordinator.evaluateAttemptOnResume(this)
        renderDashboard()
        renderQuestSection()
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

    private fun renderDashboard() {
        statusContainer.removeAllViews()

        // Calculate usage report early for state-based rendering
        val report = usageTracker.calculateTodayEntertainmentUsage()
        val hasUsage = hasUsageAccess()
        val hasAccessibility = isAccessibilityServiceEnabled()

        // ==========================================
        // SECTION: HEADER WITH CHARACTER VISUALIZATION (BUILD 02)
        // ==========================================
        renderHeader(report.timeStatus)

        // Permission Checks
        if (!hasUsage || !hasAccessibility) {
            val warningTitle = TextView(this).apply {
                text = "PERHATIAN: Izin Diperlukan"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.YELLOW)
            }
            statusContainer.addView(warningTitle)

            if (!hasUsage) {
                val usageBtn = Button(this).apply {
                    text = "AKTIFKAN USAGE ACCESS (Wajib)"
                    setOnClickListener {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
                statusContainer.addView(usageBtn)
            }

            if (!hasAccessibility) {
                val accessBtn = Button(this).apply {
                    text = "AKTIFKAN ACCESSIBILITY SERVICE (Wajib)"
                    setOnClickListener {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
                statusContainer.addView(accessBtn)
            }

            val divider = TextView(this).apply {
                text = "\n----------------------------------------\n"
                setTextColor(Color.DKGRAY)
            }
            statusContainer.addView(divider)
        }

        val timeBank = report.timeBank

        // ==========================================
        // SECTION: YOUR TIME (BUILD 02 - Editorial Treatment)
        // ==========================================
        renderYourTimeSection(report)

        // ==========================================
        // SECTION: CURRENT QUEST (Not Full Quest Board)
        // ==========================================
        renderCurrentQuest()

        // ==========================================
        // SECTION: TIME BANK SUMMARY
        // ==========================================
        renderTimeBankSummary(timeBank.availableMinutes)

        // ==========================================
        // SECTION: FOCUS WITH YOUR ACTIVITY (PHASE 2.2)
        // ==========================================
        renderFocusSection(timeBank.availableMinutes)

        // ==========================================
        // SECTION: TIME BANK SPENDING FOUNDATION
        // ==========================================
        renderTimeBankSection(timeBank.availableMinutes)

        // ==========================================
        // SECTION: HIDDEN DEVELOPER TOOLS (DEBUG builds only)
        // ==========================================
        if (BuildConfig.DEBUG && showDevPanel) {
            renderDevPanel()
        }
    }

    /**
     * Renders the ALIVE header section with character visualization (BUILD 02)
     */
    private fun renderHeader(timeStatus: TimeStatus) {
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 32)
        }

        val characterIcon = ImageView(this).apply {
            setImageResource(
                if (timeStatus == TimeStatus.ACTIVE) R.drawable.ic_alive_character
                else R.drawable.ic_alive_character_aware
            )
            contentDescription = "ALIVE Character State"
        }
        headerLayout.addView(characterIcon)

        val titleView = TextView(this).apply {
            text = "ALIVE"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 8, 0, 4)
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
        }
        headerLayout.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "Focus With Your Activity & Screen Time Discipline"
            textSize = 14f
            setTextColor(0xFF5A5A5A.toInt())
        }
        headerLayout.addView(subtitleView)

        statusContainer.addView(headerLayout)
    }

    /**
     * Renders the YOUR TIME section with ALIVE editorial styling (BUILD 02)
     */
    private fun renderYourTimeSection(report: UsageReport) {
        val yourTimeHeader = TextView(this).apply {
            text = "YOUR TIME"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 24, 0, 8)
        }
        statusContainer.addView(yourTimeHeader)

        val timeIndicator = ImageView(this).apply {
            setImageResource(R.drawable.ic_time_indicator)
            contentDescription = "Time Status Indicator"
        }
        statusContainer.addView(timeIndicator)

        val remainingText = TextView(this).apply {
            val formattedTime = if (report.remainingMinutes <= 1) "${report.remainingMinutes} minute"
                               else "${report.remainingMinutes} minutes"
            text = "$formattedTime left"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(
                if (report.timeStatus == TimeStatus.ACTIVE) 0xFF2E7D32.toInt()
                else when (report.timeStatus) {
                    TimeStatus.CRITICAL -> 0xFFFFA726.toInt()
                    TimeStatus.LOCKED -> 0xFF7A0000.toInt()
                    TimeStatus.ZERO_ENTERTAINMENT_DAY -> 0xFF7A0000.toInt()
                    else -> 0xFF1D1D1B.toInt()
                }
            )
            setPadding(0, 12, 0, 4)
        }
        statusContainer.addView(remainingText)

        val limitText = TextView(this).apply {
            text = "Normal \u2022 ${report.limitMinutes} min daily limit"
            textSize = 13f
            setTextColor(0xFF5A5A5A.toInt())
            setPadding(0, 0, 0, 24)
        }
        statusContainer.addView(limitText)

        val separatorLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 0, 0, 32) }
            setBackgroundColor(0xFFF0EAD8.toInt())
        }
        statusContainer.addView(separatorLine)
    }

    /**
     * Renders the current quest in editorial style (not full quest board) (BUILD 02)
     */
    private fun renderCurrentQuest() {
        val currentAttempt = questRepository.getCurrentAttempt()

        if (currentAttempt == null || currentAttempt.state == QuestAttemptState.AVAILABLE) {
            return
        }

        val allQuests = questRepository.getAllQuests()
        val activeQuest = allQuests.find { it.id == currentAttempt.questId }

        if (activeQuest != null) {
            val currentQuestHeader = TextView(this).apply {
                text = "Current Quest:"
                textSize = 16f
                setTypeface(Typeface.SANS_SERIF)
                setTextColor(0xFF5A5A5A.toInt())
                setPadding(0, 16, 0, 4)
            }
            statusContainer.addView(currentQuestHeader)

            val questCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 0, 24, 16)
            }

            val questTitle = TextView(this).apply {
                text = activeQuest.title
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF1D1D1B.toInt())
            }
            questCard.addView(questTitle)

            val questDesc = TextView(this).apply {
                text = activeQuest.description
                textSize = 13f
                setTextColor(0xFF5A5A5A.toInt())
                setPadding(0, 4, 0, 8)
            }
            questCard.addView(questDesc)

            val questMeta = TextView(this).apply {
                text = "📚 ${activeQuest.category.name} • ${activeQuest.difficulty.name} • +${activeQuest.rewardMinutes} min reward"
                textSize = 12f
                setTextColor(0xFF315C45.toInt())
                setPadding(0, 0, 0, 4)
            }
            questCard.addView(questMeta)

            statusContainer.addView(questCard)

            val questSeparator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, 0, 0, 24) }
                setBackgroundColor(0xFFF0EAD8.toInt())
            }
            statusContainer.addView(questSeparator)
        }
    }

    /**
     * Renders Time Bank summary in minimalist ALIVE style (BUILD 02)
     */
    private fun renderTimeBankSummary(availableBankMinutes: Long) {
        if (availableBankMinutes <= 0) return

        val bankSummaryHeader = TextView(this).apply {
            text = "Time Bank"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 16, 0, 4)
        }
        statusContainer.addView(bankSummaryHeader)

        val bankSummaryText = TextView(this).apply {
            text = "You've reclaimed $availableBankMinutes minutes through completed quests.\nUse these minutes wisely when you need extra focus time."
            textSize = 13f
            setTextColor(0xFF5A5A5A.toInt())
            setPadding(0, 4, 0, 16)
        }
        statusContainer.addView(bankSummaryText)

        val balanceText = TextView(this).apply {
            text = "Balance: $availableBankMinutes min"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF315C45.toInt())
        }
        statusContainer.addView(balanceText)

        val bankSeparator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 0, 0, 24) }
            setBackgroundColor(0xFFF0EAD8.toInt())
        }
        statusContainer.addView(bankSeparator)
    }



    private fun renderFocusSection(availableBankMinutes: Long) {
        val focusHeader = TextView(this).apply {
            text = "\n⚡ FOCUS WITH YOUR ACTIVITY"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 20, 0, 10)
        }
        statusContainer.addView(focusHeader)

        val currentSession = focusSessionManager.getFocusSession()

        if (currentSession != null && currentSession.isActive) {
            // Active Focus Session Dashboard
            val activeFocusCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 28, 28, 28)
                setBackgroundColor(0xFF1B2B34.toInt())
            }

            val badge = TextView(this).apply {
                text = "● FOCUS SESSION ACTIVE"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF00E676.toInt())
            }
            activeFocusCard.addView(badge)

            val actTitle = TextView(this).apply {
                text = "Aktivitas: ${currentSession.activityType.displayName.uppercase()}"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 4)
            }
            activeFocusCard.addView(actTitle)

            val goalText = TextView(this).apply {
                text = "Goal: \"${currentSession.goal}\""
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 8)
            }
            activeFocusCard.addView(goalText)

            val remainingMinutes = currentSession.getRemainingMinutes()
            val timerText = TextView(this).apply {
                text = "Sisa Waktu: $remainingMinutes Menit (Total durasi: ${currentSession.durationMinutes}m)"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFFFD54F.toInt())
                setPadding(0, 0, 0, 12)
            }
            activeFocusCard.addView(timerText)

            val allowedList = TextView(this).apply {
                val approvedPkgs = FocusAppPolicy.getApprovedPackages(currentSession.activityType)
                text = "Aplikasi Produktivitas Diizinkan (${approvedPkgs.size}):\n" +
                        "Google Docs, Word, Drive, Keep, Notes, Calculator, AI tools, dll.\n" +
                        "(Aplikasi hiburan & browser tetap DIBLOKIR)"
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, 16)
            }
            activeFocusCard.addView(allowedList)

            // Extension via Time Bank (Only while active)
            val extendBtn = Button(this).apply {
                text = "EXTEND FOCUS +15 MIN (Pakai 15m Time Bank)"
                isEnabled = availableBankMinutes >= 15
                setOnClickListener {
                    val success = focusSessionManager.extendFocusWithTimeBank(15)
                    if (success) {
                        Toast.makeText(this@MainActivity, "Fokus diperpanjang +15 menit!", Toast.LENGTH_SHORT).show()
                        renderDashboard()
                    } else {
                        Toast.makeText(this@MainActivity, "Saldo Time Bank tidak mencukupi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            activeFocusCard.addView(extendBtn)

            statusContainer.addView(activeFocusCard)
        } else {
            // Setup Focus Form
            val setupCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(0xFF232830.toInt())
            }

            val desc = TextView(this).apply {
                text = "Komit pada aktivitas produktif. Aplikasi hiburan & browser tetap terkunci; hanya aplikasi produktivitas yang disetujui dapat dibuka."
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, 12)
            }
            setupCard.addView(desc)

            val actLabel = TextView(this).apply {
                text = "1. Pilih Aktivitas:"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            setupCard.addView(actLabel)

            val actGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
            val activities = FocusActivityType.values()
            activities.forEachIndexed { index, type ->
                val rb = RadioButton(this).apply {
                    id = index + 100
                    text = "${type.displayName} (Aplikasi: ${FocusAppPolicy.getApprovedPackages(type).size} terdaftar)"
                    setTextColor(Color.WHITE)
                    if (index == 0) isChecked = true
                }
                actGroup.addView(rb)
            }
            setupCard.addView(actGroup)

            val goalLabel = TextView(this).apply {
                text = "\n2. Masukkan Goal:"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            setupCard.addView(goalLabel)

            val goalInput = EditText(this).apply {
                hint = "Contoh: Latihan Soal Fisika / Menulis Bab 1"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(0xFF333333.toInt())
                setPadding(24, 16, 24, 16)
            }
            setupCard.addView(goalInput)

            val durLabel = TextView(this).apply {
                text = "\n3. Pilih Durasi:"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            setupCard.addView(durLabel)

            val durGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
            val durations = listOf(30L, 60L, 90L, 120L)
            durations.forEachIndexed { index, dur ->
                val rb = RadioButton(this).apply {
                    id = index + 200
                    text = "${dur}m"
                    setTextColor(Color.WHITE)
                    if (index == 1) isChecked = true // default 60m
                }
                durGroup.addView(rb)
            }
            setupCard.addView(durGroup)

            val startFocusBtn = Button(this).apply {
                text = "START FOCUS"
                setBackgroundColor(0xFF2E7D32.toInt())
                setTextColor(Color.WHITE)
                setPadding(0, 24, 0, 24)
                setOnClickListener {
                    val checkedActId = actGroup.checkedRadioButtonId
                    val selectedType = activities.getOrNull(checkedActId - 100) ?: FocusActivityType.STUDY

                    val goal = goalInput.text.toString().trim()
                    if (goal.isBlank()) {
                        Toast.makeText(this@MainActivity, "Harap masukkan Goal aktivitas!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val checkedDurId = durGroup.checkedRadioButtonId
                    val selectedDur = durations.getOrNull(checkedDurId - 200) ?: 60L

                    val started = focusSessionManager.startFocusSession(selectedType, goal, selectedDur)
                    if (started) {
                        Toast.makeText(this@MainActivity, "Focus Dimulai: $selectedDur Menit!", Toast.LENGTH_LONG).show()
                        renderDashboard()
                    } else {
                        Toast.makeText(this@MainActivity, "Gagal memulai Focus!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val spacer = View(this).apply { minimumHeight = 16 }
            setupCard.addView(spacer)
            setupCard.addView(startFocusBtn)

            statusContainer.addView(setupCard)
        }
    }

    private fun renderQuestSection() {
        val questHeader = TextView(this).apply {
            text = "\n📋 QUEST BOARD"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 16, 0, 12)
        }
        questContainer.addView(questHeader)

        val allQuests = questRepository.getAllQuests()

        // Phase 2.3.3: the Quest Board's active state is derived from the
        // active QuestAttempt, not the legacy KEY_ACTIVE_QUEST_ID state.
        // The legacy key may be stale (it is no longer written by the AMBIL
        // flow), so it must not decide which quest is shown as active nor
        // which quest is skipped in the list below.
        val currentAttempt = questRepository.getCurrentAttempt()
        val hasActiveAttempt = currentAttempt != null && when (currentAttempt.state) {
            QuestAttemptState.COMMITTED,
            QuestAttemptState.IN_PROGRESS,
            QuestAttemptState.PENDING_VERIFICATION,
            QuestAttemptState.VERIFIED -> true
            else -> false
        }
        val activeQuestId = if (hasActiveAttempt) currentAttempt?.questId else null
        val activeQuest = activeQuestId?.let { id -> allQuests.find { it.id == id } }

        if (activeQuest != null) {
            val activeCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(0xFF1E2A38.toInt())
            }

            val activeBadge = TextView(this).apply {
                text = "⚡ ACTIVE QUEST"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF4FC3F7.toInt())
            }
            activeCard.addView(activeBadge)

            val questTitle = TextView(this).apply {
                text = activeQuest.title
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 4)
            }
            activeCard.addView(questTitle)

            val questDesc = TextView(this).apply {
                text = activeQuest.description
                textSize = 14f
                setTextColor(Color.LTGRAY)
            }
            activeCard.addView(questDesc)

            val questDetails = TextView(this).apply {
                text = "Kategori: ${activeQuest.category} | Durasi: ${activeQuest.estimatedDurationMinutes}m | Hadiah: +${activeQuest.rewardMinutes} Menit"
                textSize = 13f
                setTextColor(Color.YELLOW)
                setPadding(0, 8, 0, 16)
            }
            activeCard.addView(questDetails)

            questContainer.addView(activeCard)
        } else {
            val noActiveText = TextView(this).apply {
                text = "Pilih satu quest di bawah untuk memulai real-world task:"
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, 0, 0, 12)
            }
            questContainer.addView(noActiveText)
        }

        for (quest in allQuests) {
            if (quest.id == activeQuestId) continue

            val questItem = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 16, 20, 16)
                setBackgroundColor(if (quest.status == QuestStatus.COMPLETED) 0xFF222222.toInt() else 0xFF2A2A2A.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }

            val questInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this).apply {
                text = quest.title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (quest.status == QuestStatus.COMPLETED) Color.DKGRAY else Color.WHITE)
            }
            questInfo.addView(title)

            val meta = TextView(this).apply {
                text = "${quest.category} • ${quest.estimatedDurationMinutes}m • +${quest.rewardMinutes} min"
                textSize = 12f
                setTextColor(if (quest.status == QuestStatus.COMPLETED) Color.DKGRAY else Color.GRAY)
            }
            questInfo.addView(meta)
            questItem.addView(questInfo)

            if (quest.status == QuestStatus.COMPLETED) {
                val doneLabel = TextView(this).apply {
                    text = "✓ SELESAI"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.GRAY)
                    setPadding(16, 8, 16, 8)
                }
                questItem.addView(doneLabel)
            } else {
                val startBtn = Button(this).apply {
                    text = "AMBIL"
                    isEnabled = !hasActiveAttempt
                    setOnClickListener {
                        val success = questStartCoordinator.startDigitalQuest(quest.id)
                        if (success) {
                            renderDashboard()
                        } else {
                            Toast.makeText(this@MainActivity, "Quest tidak dapat dimulai saat ini.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                questItem.addView(startBtn)
            }
            questContainer.addView(questItem)
        }
    }

    private fun renderTimeBankSection(availableBankMinutes: Long) {
        val bankSectionHeader = TextView(this).apply {
            text = "\n🪙 TIME BANK SPENDING FOUNDATION"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 16, 0, 8)
        }
        statusContainer.addView(bankSectionHeader)

        val bankDesc = TextView(this).apply {
            text = "Tersedia: $availableBankMinutes menit. Pengeluaran waktu tidak boleh melebihi saldo yang tersedia."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 8)
        }
        statusContainer.addView(bankDesc)

        val spendRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val spend10Btn = Button(this).apply {
            text = "SPEND 10 MIN"
            isEnabled = availableBankMinutes >= 10
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                if (timeBankRepository.spendMinutes(10)) {
                    Toast.makeText(this@MainActivity, "10 menit berhasil dikeluarkan dari Time Bank", Toast.LENGTH_SHORT).show()
                    renderDashboard()
                }
            }
        }
        spendRow.addView(spend10Btn)

        val spend15Btn = Button(this).apply {
            text = "SPEND 15 MIN"
            isEnabled = availableBankMinutes >= 15
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener {
                if (timeBankRepository.spendMinutes(15)) {
                    Toast.makeText(this@MainActivity, "15 menit berhasil dikeluarkan dari Time Bank", Toast.LENGTH_SHORT).show()
                    renderDashboard()
                }
            }
        }
        spendRow.addView(spend15Btn)
        statusContainer.addView(spendRow)
    }

    private fun renderDevPanel() {
        val devPanelTitle = TextView(this).apply {
            text = "\n[DEVELOPER DEBUG PANEL - HIDDEN]\n"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.MAGENTA)
        }
        statusContainer.addView(devPanelTitle)

        val devTestToggleBtn = Button(this).apply {
            text = if (lockStateManager.isDevTestModeEnabled()) {
                "DEV TEST MODE: AKTIF (Limit 1 min)"
            } else {
                "DEV TEST MODE: NONAKTIF (Limit 120 min)"
            }
            setOnClickListener {
                val enabled = lockStateManager.isDevTestModeEnabled()
                lockStateManager.setDevTestModeEnabled(!enabled)
                usageTracker.calculateTodayEntertainmentUsage()
                renderDashboard()
            }
        }
        statusContainer.addView(devTestToggleBtn)

        val testFocusSessionBtn = Button(this).apply {
            text = "DEV: START 15-SECOND TEST FOCUS (STUDY)"
            setOnClickListener {
                focusSessionManager.startTestFocusSession(FocusActivityType.STUDY, "Test 15s Goal", 15)
                Toast.makeText(this@MainActivity, "Started 15-second Study Focus", Toast.LENGTH_SHORT).show()
                renderDashboard()
            }
        }
        statusContainer.addView(testFocusSessionBtn)

        val simulateSuccessRolloverBtn = Button(this).apply {
            text = "SIMULASI: ROLLOVER HARI SUKSES (+1 Streak)"
            setOnClickListener {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, -1)
                val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                lockStateManager.simulateRollover(yesterday, simulateLock = false)
                renderDashboard()
            }
        }
        statusContainer.addView(simulateSuccessRolloverBtn)

        val simulateFailRolloverBtn = Button(this).apply {
            text = "SIMULASI: ROLLOVER HARI GAGAL (0 Streak + Zero-Day)"
            setOnClickListener {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, -1)
                val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                lockStateManager.simulateRollover(yesterday, simulateLock = true)
                renderDashboard()
            }
        }
        statusContainer.addView(simulateFailRolloverBtn)

        // Phase 2.3.2: the old direct-reward completeActiveQuest() API was
        // removed from QuestRepository because it was a second reward path
        // that bypassed verification. This button is intentionally inert
        // until a later Phase 2.3 step wires up the real commit -> start ->
        // requestVerification -> verifyQuest -> claimReward pipeline in the
        // UI - it is not re-implemented here to avoid adding UI flow logic
        // in a step scoped to QuestRepository only.
        val devCompleteActiveQuestBtn = Button(this).apply {
            text = "DEV: Quest reward now requires the verification pipeline (coming in a later Phase 2.3 step)"
            isEnabled = false
            setTextColor(Color.GRAY)
        }
        statusContainer.addView(devCompleteActiveQuestBtn)
    }

    private fun showTab(index: Int) {
        tabContent.getChildAt(0).visibility = if (index == 0) View.VISIBLE else View.GONE
        tabContent.getChildAt(1).visibility = if (index == 1) View.VISIBLE else View.GONE
        tabContent.getChildAt(2).visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun renderProfile() {
        profileContainer.removeAllViews()

        val title = TextView(this).apply {
            text = "PROFILE"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1D1D1B.toInt())
        }
        profileContainer.addView(title)

        val body = TextView(this).apply {
            text = "Profil ALIVE akan diisi pada tahap berikutnya."
            textSize = 14f
            setTextColor(0xFF1D1D1B.toInt())
            setPadding(0, 12, 0, 0)
        }
        profileContainer.addView(body)
    }
}

