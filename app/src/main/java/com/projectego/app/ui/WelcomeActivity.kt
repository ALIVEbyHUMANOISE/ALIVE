package com.projectego.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.projectego.app.R
import com.projectego.app.data.WelcomeGate

/**
 * The first-run welcome: three screens, shown once, before the app proper.
 *
 * Screen one is the only place ALIVE allows itself poetry - someone trying to
 * climb out of a relapse loop is being handed a door, not a product tour, and
 * the door deserves to say why it exists. Screens two and three obey the
 * standing clarity rule: the mechanic is stated plainly first (120 minutes,
 * what closes, what pays minutes back) and the voice stays out of the way. The
 * two switches on screen three are the same ones Home keeps asking about
 * afterwards; this is simply the first, calmer ask, and skipping it costs
 * nothing because the Home card is still there.
 *
 * It touches exactly three things: two permission checks and one flag. No
 * engine state - the welcome introduces ALIVE without reading a single number
 * ALIVE keeps, which is also why it can never disagree with Home about them.
 *
 * Back walks backwards through the steps; back on the first screen leaves the
 * welcome unfinished (the flag is only written on Start or Skip), so leaving a
 * screen unread does not count as having read it.
 */
class WelcomeActivity : AppCompatActivity() {

    private companion object {
        const val KEY_STEP = "welcome_step"
        const val LAST_STEP = 2
    }

    private val ui by lazy { AliveComponents(this) }
    private var step = 0
    private lateinit var content: AliveComponents.Stack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt(KEY_STEP) ?: 0

        content = ui.stack(ui.dimens.cardGap).apply {
            setPadding(
                ui.dimens.pageHorizontal,
                ui.dimens.pageTop,
                ui.dimens.pageHorizontal,
                ui.dimens.pageBottom
            )
        }
        setContentView(ScrollView(this).apply { addView(content) })

        // Forward is a button; back is back. Nobody is trapped: leaving from
        // the first screen just postpones this until the next cold start.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 0) {
                    step -= 1
                    renderStep()
                } else {
                    finish()
                }
            }
        })
    }

    /**
     * Rendered on every resume, not just the first: this screen sends people
     * to Settings, and the two status lines must be true by the time they
     * come back.
     */
    override fun onResume() {
        super.onResume()
        renderStep()
    }

    private fun renderStep() {
        content.removeAllViews()
        when (step) {
            0 -> buildPhilosophy()
            1 -> buildMechanics()
            else -> buildPermissions()
        }
    }

    /** Screen one: who this is for, in the only voice that gets to be a voice. */
    private fun buildPhilosophy() {
        content.addView(
            ui.row().apply {
                gravity = Gravity.BOTTOM
                addView(ui.display(getString(R.string.alive_wordmark)))
                addView(ui.caret(ui.dimens.caretHeight))
            }
        )
        content.addView(
            ui.animatedPose(
                AliveStates.faceSpriteFor(AliveFace.WAVING),
                ui.dimens.characterLarge,
                getString(AliveStates.faceDescription(AliveFace.WAVING))
            )
        )
        content.addView(
            ui.column().apply {
                addView(ui.lead(getString(R.string.welcome_lead)))
                addView(ui.vgap(ui.dimens.groupGap))
                addView(ui.body(getString(R.string.welcome_body)))
                addView(ui.vgap(ui.dimens.groupGap))
                addView(ui.annotation(getString(R.string.welcome_note)))
            }
        )
        content.addView(stepButton(R.string.welcome_next))
        content.addView(progressRow())
    }

    /** Screen two: the loop, stated as plainly as the clarity rule demands. */
    private fun buildMechanics() {
        content.addView(ui.sectionHeader(getString(R.string.welcome_loop_heading)))
        content.addView(
            ui.column().apply {
                val lines = listOf(
                    R.string.welcome_loop_1,
                    R.string.welcome_loop_2,
                    R.string.welcome_loop_3,
                    R.string.welcome_loop_4
                )
                lines.forEachIndexed { index, line ->
                    if (index > 0) addView(ui.vgap(ui.dimens.groupGap))
                    addView(
                        ui.row().apply {
                            addView(ui.tag("%02d".format(index + 1)))
                            addView(ui.hgap(ui.dimens.m))
                            addView(ui.weighted(ui.body(getString(line))))
                        }
                    )
                }
            }
        )
        content.addView(ui.annotation(getString(R.string.welcome_loop_note)))
        content.addView(stepButton(R.string.welcome_next))
        content.addView(progressRow())
    }

    /** Screen three: the same two switches Home will keep asking about. */
    private fun buildPermissions() {
        content.addView(ui.sectionHeader(getString(R.string.welcome_permissions_heading)))
        content.addView(
            permissionBlock(
                label = getString(R.string.welcome_usage_label),
                why = getString(R.string.welcome_usage_why),
                granted = PermissionChecks.hasUsageAccess(this),
                settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS,
                action = getString(R.string.permissions_usage_action)
            )
        )
        content.addView(
            permissionBlock(
                label = getString(R.string.welcome_accessibility_label),
                why = getString(R.string.permissions_accessibility_why),
                granted = PermissionChecks.isAccessibilityServiceEnabled(this),
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                action = getString(R.string.permissions_accessibility_action)
            )
        )
        content.addView(
            fullWidth(ui.primary(getString(R.string.welcome_start))).apply {
                setOnClickListener { complete() }
            }
        )
        content.addView(
            fullWidth(ui.quiet(getString(R.string.welcome_skip))).apply {
                setOnClickListener { complete() }
            }
        )
        content.addView(progressRow())
    }

    /**
     * One switch: its name with a status word, why the switch exists, and the
     * button that opens the right Settings page. A granted switch keeps its
     * button, disabled - the row still explains itself after the fact instead
     * of disappearing.
     */
    private fun permissionBlock(
        label: String,
        why: String,
        granted: Boolean,
        settingsAction: String,
        action: String
    ): View = ui.block(SurfaceWeight.MINOR).apply {
        addView(
            ui.row().apply {
                addView(ui.lead(label))
                addView(
                    ui.weighted(
                        ui.meta(
                            getString(
                                if (granted) R.string.permissions_granted
                                else R.string.permissions_not_granted
                            )
                        ).apply {
                            gravity = Gravity.END
                            setTextColor(
                                if (granted) ui.palette.forest else ui.palette.inkMuted
                            )
                        }
                    )
                )
            }
        )
        addView(ui.vgap(ui.dimens.groupGap))
        addView(ui.note(why))
        addView(ui.vgap(ui.dimens.groupGap))
        addView(
            fullWidth(ui.secondary(action)).apply {
                isEnabled = !granted
                if (!granted) setOnClickListener { startActivity(Intent(settingsAction)) }
            }
        )
    }

    private fun stepButton(resId: Int): View =
        fullWidth(ui.primary(getString(resId))).apply {
            setOnClickListener {
                step += 1
                renderStep()
            }
        }

    private fun <T : View> fullWidth(button: T): T = button.apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    /** Three squares, one per screen; the one you are on is ink. */
    private fun progressRow(): View = ui.row().apply {
        gravity = Gravity.CENTER
        for (index in 0..LAST_STEP) {
            if (index > 0) addView(ui.hgap(ui.dimens.s))
            addView(
                View(this@WelcomeActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ui.dimens.stepSize,
                        ui.dimens.stepSize
                    )
                    setBackgroundColor(
                        if (index == step) ui.palette.ink else ui.palette.inkHairline
                    )
                }
            )
        }
    }

    private fun complete() {
        WelcomeGate.markDone(this)
        finish()
    }
}
