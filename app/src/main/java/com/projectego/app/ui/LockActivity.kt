package com.projectego.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.domain.UsageTracker
import com.projectego.app.model.DayType

/**
 * ALIVE's lock screen.
 *
 * It says three things and nothing else: entertainment time is over, that is on
 * purpose, and there are other things to do. Deliberately not red, not shouting,
 * and never worded as an accusation or as catching anyone out.
 *
 * The back interception and the intent-extra contract with the accessibility
 * service are bypass-prevention, not styling. The extra keys stay literal on
 * both sides: the writer lives in `service/`, and a shared constant would only
 * create a second place for the two ends to drift apart.
 */
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

        val ui = AliveComponents(this)

        // This screen performs the same single read the previous revision did.
        val report = UsageTracker(this).calculateTodayEntertainmentUsage()

        val lockReason = intent.getStringExtra("EXTRA_LOCK_REASON") ?: "ENTERTAINMENT_EXCEEDED"
        val blockedPkg = intent.getStringExtra("EXTRA_BLOCKED_PACKAGE")
            ?: getString(R.string.lock_blocked_app_unknown)
        val focusActivityName = intent.getStringExtra("EXTRA_FOCUS_ACTIVITY") ?: getString(R.string.focus_heading)

        val isFocusSession = lockReason == "FOCUS_SESSION"
        val isRecoveryDay = report.dayType == DayType.ZERO_ENTERTAINMENT_DAY

        val page = ui.column().apply {
            setPadding(
                ui.dimens.xxxl,
                ui.dimens.xxxl,
                ui.dimens.xxxl,
                ui.dimens.xxl
            )
        }

        page.addView(
            ui.pose(
                R.drawable.ic_alive_character_aware,
                ui.dimens.characterLarge,
                getString(R.string.lock_intent)
            )
        )
        page.addView(ui.vgap(ui.dimens.xxl))

        page.addView(
            ui.headline(
                getString(
                    when {
                        isFocusSession -> R.string.lock_title_focus
                        isRecoveryDay -> R.string.lock_title_zero
                        else -> R.string.lock_title_over
                    }
                )
            )
        )
        page.addView(ui.vgap(ui.dimens.l))
        page.addView(ui.annotation(getString(R.string.lock_intent)))

        page.addView(ui.vgap(ui.dimens.l))
        page.addView(ui.accents(seed = 5, count = 4))
        page.addView(ui.vgap(ui.dimens.xl))

        if (isFocusSession) {
            page.addView(ui.body(getString(R.string.lock_focus_body, focusActivityName)))
        } else if (isRecoveryDay) {
            // A recovery day has no numbers to show: there was no allowance to
            // spend, so printing "0 min of 0 min" would be noise.
            page.addView(ui.body(getString(R.string.lock_zero_body)))
            page.addView(ui.vgap(ui.dimens.s))
            page.addView(ui.bodySecondary(getString(R.string.lock_zero_support)))
        } else {
            page.addView(ui.body(getString(R.string.lock_over_body)))
            page.addView(ui.vgap(ui.dimens.m))
            page.addView(ui.bodySecondary(getString(R.string.lock_allowance_line, report.limitMinutes)))
            page.addView(ui.vgap(ui.dimens.xs))
            page.addView(ui.bodySecondary(getString(R.string.lock_used_line, report.totalEntertainmentMinutes)))
        }

        page.addView(ui.vgap(ui.dimens.xl))
        page.addView(ui.hairline())
        page.addView(ui.vgap(ui.dimens.xl))

        page.addView(
            ui.quietText(
                if (isFocusSession) getString(R.string.lock_focus_blocked, blockedPkg)
                else getString(R.string.lock_blocked_app, blockedPkg)
            )
        )
        page.addView(ui.vgap(ui.dimens.s))
        page.addView(ui.quietText(getString(R.string.lock_days_row, report.currentStreak)))
        page.addView(ui.vgap(ui.dimens.s))
        page.addView(ui.quietText(getString(R.string.lock_essential)))

        if (!isFocusSession) {
            page.addView(ui.vgap(ui.dimens.xxl))
            page.addView(ui.label(getString(R.string.lock_do_instead)))
            page.addView(ui.vgap(ui.dimens.m))
            page.addView(
                ui.primary(getString(R.string.lock_action_focus)).apply {
                    layoutParams = matchWidth()
                    setOnClickListener {
                        val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("EXTRA_NAVIGATE_TO_FOCUS", true)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            )
        }

        page.addView(ui.vgap(ui.dimens.l))
        page.addView(
            ui.secondary(getString(R.string.lock_action_home)).apply {
                layoutParams = matchWidth()
                setOnClickListener {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }
            }
        )

        page.addView(ui.vgap(ui.dimens.s))
        page.addView(
            ui.quiet(getString(R.string.lock_action_dashboard)).apply {
                layoutParams = matchWidth()
                setOnClickListener {
                    val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    finish()
                }
            }
        )

        // A scroll container is required: on a short display the buttons
        // otherwise fall below the fold while back is still intercepted.
        setContentView(
            ScrollView(this).apply {
                addView(page)
                setBackgroundResource(R.drawable.bg_canvas)
                isFillViewport = true
            }
        )
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
