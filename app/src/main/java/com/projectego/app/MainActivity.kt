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
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.projectego.app.data.FocusSessionManager
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.QuestRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.domain.FocusAppPolicy
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
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

    private lateinit var statusContainer: LinearLayout
    private lateinit var scrollView: ScrollView

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

        scrollView = ScrollView(this)
        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
            setBackgroundColor(0xFF181818.toInt())
        }
        scrollView.addView(statusContainer)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        renderDashboard()
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

        // Title with hidden developer tap sequence (tap 7 times rapidly to toggle dev panel)
        val titleView = TextView(this).apply {
            text = "PROJECT EGO"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setOnClickListener {
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
        statusContainer.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "Focus With Your Activity & Screen Time Discipline\n"
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }
        statusContainer.addView(subtitleView)

        // Permission Checks
        val hasUsage = hasUsageAccess()
        val hasAccessibility = isAccessibilityServiceEnabled()

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

        // Calculate usage report
        val report = usageTracker.calculateTodayEntertainmentUsage()

        // Top Metrics Layout: Streak + Time Bank Cards
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val streakCard = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF2E3440.toInt())
            setTextColor(Color.WHITE)
            text = "🔥 STREAK: ${report.currentStreak} HARI"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 12, 0)
            }
        }
        metricsRow.addView(streakCard)

        val timeBank = report.timeBank
        val timeBankCard = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF3B4252.toInt())
            setTextColor(Color.WHITE)
            text = "🪙 BANK: ${timeBank.availableMinutes} MIN"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(12, 0, 0, 0)
            }
        }
        metricsRow.addView(timeBankCard)
        statusContainer.addView(metricsRow)

        val spacer1 = View(this).apply { minimumHeight = 24 }
        statusContainer.addView(spacer1)

        // Time Status Banner
        val statusBanner = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 24, 24, 24)

            when (report.timeStatus) {
                TimeStatus.ZERO_ENTERTAINMENT_DAY -> {
                    text = "STATUS: ZERO-TIME RECOVERY DAY\n" +
                            "Kemarin melebihi batas 120 menit.\n" +
                            "Hari ini Anda memiliki 0 menit hiburan dasar."
                    setBackgroundColor(0xFF7A0000.toInt())
                    setTextColor(Color.WHITE)
                }
                TimeStatus.LOCKED -> {
                    text = "STATUS: TERKUNCI (HARD LOCK)\n" +
                            "Batas hiburan harian telah habis."
                    setBackgroundColor(0xFF990000.toInt())
                    setTextColor(Color.WHITE)
                }
                TimeStatus.CRITICAL -> {
                    val remainingAlert = when (report.criticalZoneTier) {
                        4 -> "TINGGAL 1 MENIT!"
                        3 -> "TINGGAL 5 MENIT!"
                        2 -> "TINGGAL 15 MENIT!"
                        1 -> "TINGGAL 30 MENIT!"
                        else -> "ZONA KRITIS"
                    }
                    text = "STATUS: CRITICAL ZONE ($remainingAlert)\n" +
                            "Sisa waktu: ${report.remainingMinutes} menit."
                    setBackgroundColor(0xFFB8860B.toInt())
                    setTextColor(Color.WHITE)
                }
                TimeStatus.ACTIVE -> {
                    text = "STATUS: ACTIVE (AMAN)\n" +
                            "Sisa waktu: ${report.remainingMinutes} menit."
                    setBackgroundColor(0xFF2E7D32.toInt())
                    setTextColor(Color.WHITE)
                }
            }
        }
        statusContainer.addView(statusBanner)

        // Usage Statistics
        val statsView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 16)
            text = buildString {
                append("• Waktu Terpakai: ${report.totalEntertainmentMinutes} menit\n")
                append("• Sisa Waktu Dasar: ${report.remainingMinutes} menit\n")
                append("• Batas Harian: ${report.limitMinutes} menit (Tetap/Non-editable)\n")
                append("• Tipe Hari: ${if (report.dayType == DayType.NORMAL_DAY) "NORMAL" else "RECOVERY (0 MENIT)"}\n\n")

                append("Rincian Pemakaian Hiburan Hari Ini:\n")
                if (report.entertainmentAppDetails.isEmpty()) {
                    append("- Belum ada pemakaian hiburan hari ini.\n")
                } else {
                    for ((pkg, min) in report.entertainmentAppDetails) {
                        append("• $pkg: $min menit\n")
                    }
                }
            }
        }
        statusContainer.addView(statsView)

        val refreshBtn = Button(this).apply {
            text = "REFRESH STATUS"
            setOnClickListener {
                renderDashboard()
            }
        }
        statusContainer.addView(refreshBtn)

        // ==========================================
        // SECTION: FOCUS WITH YOUR ACTIVITY (PHASE 2.2)
        // ==========================================
        renderFocusSection(timeBank.availableMinutes)

        // ==========================================
        // SECTION: QUEST BOARD
        // ==========================================
        renderQuestSection()

        // ==========================================
        // SECTION: TIME BANK SPENDING FOUNDATION
        // ==========================================
        renderTimeBankSection(timeBank.availableMinutes)

        // ==========================================
        // SECTION: HIDDEN DEVELOPER TOOLS
        // ==========================================
        if (showDevPanel) {
            renderDevPanel()
        }
    }

    private fun renderFocusSection(availableBankMinutes: Long) {
        val focusHeader = TextView(this).apply {
            text = "\n⚡ FOCUS WITH YOUR ACTIVITY"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
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
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 12)
        }
        statusContainer.addView(questHeader)

        val allQuests = questRepository.getAllQuests()
        val activeQuest = questRepository.getActiveQuest()

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

            val completeBtn = Button(this).apply {
                text = "DEV: COMPLETE QUEST (+${activeQuest.rewardMinutes} MIN)"
                setBackgroundColor(0xFF2E7D32.toInt())
                setTextColor(Color.WHITE)
                setOnClickListener {
                    val reward = questRepository.completeActiveQuest(activeQuest.id)
                    if (reward != null) {
                        Toast.makeText(
                            this@MainActivity,
                            "✓ QUEST COMPLETE! +$reward Menit masuk ke Time Bank",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Gagal menyelesaikan quest atau sudah selesai.", Toast.LENGTH_SHORT).show()
                    }
                    renderDashboard()
                }
            }
            activeCard.addView(completeBtn)
            statusContainer.addView(activeCard)
        } else {
            val noActiveText = TextView(this).apply {
                text = "Pilih satu quest di bawah untuk memulai real-world task:"
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, 0, 0, 12)
            }
            statusContainer.addView(noActiveText)
        }

        for (quest in allQuests) {
            if (quest.status == QuestStatus.ACTIVE) continue

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
                    isEnabled = (activeQuest == null)
                    setOnClickListener {
                        val success = questRepository.startQuest(quest.id)
                        if (success) {
                            renderDashboard()
                        } else {
                            Toast.makeText(this@MainActivity, "Hanya boleh 1 quest aktif sekaligus!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                questItem.addView(startBtn)
            }
            statusContainer.addView(questItem)
        }
    }

    private fun renderTimeBankSection(availableBankMinutes: Long) {
        val bankSectionHeader = TextView(this).apply {
            text = "\n🪙 TIME BANK SPENDING FOUNDATION"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
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
    }
}
