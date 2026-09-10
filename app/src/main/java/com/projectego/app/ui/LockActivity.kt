package com.projectego.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.projectego.app.MainActivity
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.DayType

class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intercept back button - prevent returning to blocked app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }
        })

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 128, 64, 64)
            setBackgroundColor(0xFF111111.toInt())
        }

        val lockReason = intent.getStringExtra("EXTRA_LOCK_REASON") ?: "ENTERTAINMENT_EXCEEDED"
        val blockedPkg = intent.getStringExtra("EXTRA_BLOCKED_PACKAGE") ?: "Aplikasi Terlarang"

        val titleView = TextView(this).apply {
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            if (lockReason == "FOCUS_SESSION") {
                text = "PROJECT EGO\nFOCUS SESSION ACTIVE"
                setTextColor(0xFFFF8800.toInt())
            } else {
                text = "PROJECT EGO\nHARD LOCK"
                setTextColor(0xFFFF3333.toInt())
            }
        }
        layout.addView(titleView)

        val tracker = UsageTracker(this)
        val report = tracker.calculateTodayEntertainmentUsage()
        val focusActivityName = intent.getStringExtra("EXTRA_FOCUS_ACTIVITY") ?: "Focus"

        val bodyView = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 32, 0, 48)
            text = buildString {
                if (lockReason == "FOCUS_SESSION") {
                    append("Sesi Focus ($focusActivityName) sedang aktif.\n\n")
                    append("Aplikasi yang dicegat:\n$blockedPkg\n\n")
                    append("Selama sesi Focus, hanya aplikasi produktivitas yang disetujui dan aplikasi esensial (Telepon/SMS) yang dapat digunakan.\n")
                    append("Aplikasi hiburan, browser, dan aplikasi di luar katalog tetap diblokir.")
                } else {
                    if (report.dayType == DayType.ZERO_ENTERTAINMENT_DAY) {
                        append("STATUS: ZERO-ENTERTAINMENT RECOVERY DAY\n\n")
                        append("Hari kemarin batas hiburan terlampaui (Gagal).\n")
                        append("Hari ini Anda memulai dengan 0 menit hiburan.\n\n")
                    } else {
                        append("Waktu hiburan harian Anda telah habis.\n\n")
                        append("Batas maksimal: ${report.limitMinutes} menit\n")
                        append("Total pemakaian: ${report.totalEntertainmentMinutes} menit\n\n")
                    }
                    append("Aplikasi yang dicegat:\n$blockedPkg\n\n")
                    append("Streak saat ini: ${report.currentStreak} hari\n\n")
                    append("Aplikasi hiburan dikunci hingga besok.\n")
                    append("Aplikasi esensial (Telepon, SMS, Navigasi) tetap aktif.")
                }
            }
        }
        layout.addView(bodyView)

        // Option: FOCUS WITH YOUR ACTIVITY (Allows productive activity even when entertainment is locked)
        if (lockReason != "FOCUS_SESSION") {
            val startFocusBtn = Button(this).apply {
                text = "⚡ FOCUS WITH YOUR ACTIVITY"
                textSize = 16f
                setBackgroundColor(0xFF2E7D32.toInt())
                setTextColor(Color.WHITE)
                setOnClickListener {
                    val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("EXTRA_NAVIGATE_TO_FOCUS", true)
                    }
                    startActivity(intent)
                    finish()
                }
            }
            layout.addView(startFocusBtn)
        }

        val homeButton = Button(this).apply {
            text = "KEMBALI KE HOME"
            textSize = 16f
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        }
        layout.addView(homeButton)

        val dashboardButton = Button(this).apply {
            text = "BUKA DASHBOARD EGO"
            textSize = 16f
            setOnClickListener {
                val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
                finish()
            }
        }
        layout.addView(dashboardButton)

        setContentView(layout)
    }
}
