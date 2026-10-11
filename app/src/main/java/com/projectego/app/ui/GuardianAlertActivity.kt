package com.projectego.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.projectego.app.R
import com.projectego.app.data.LockStateManager

/**
 * Guardian, in the middle of the screen, over whatever app is open.
 *
 * [GuardianWatchService] knocks when an entertainment app comes to the front
 * while the guard is off; this is the knock. It says what is no longer true
 * (nothing is being closed), what it costs (a day over the limit gets marked),
 * and gives the one action that fixes it - and it takes back nothing: back and
 * "Not now" both close it, HOME works, and the copy never calls itself a lock.
 *
 * It reads no extras and writes nothing. The state comes from the same two
 * sources the Home card reads, which is what keeps the popup from ever saying
 * something the app would not - and if the guard came back between the knock and
 * the window opening, it closes itself rather than announcing a solved problem.
 */
class GuardianAlertActivity : AppCompatActivity() {

    private val dismissHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val today = LockStateManager.dayKeyOf(System.currentTimeMillis())
        val state = Guardian.stateOf(
            protectionOff = !PermissionChecks.isAccessibilityServiceEnabled(this),
            gapOpen = LockStateManager(this).isGuardGapOpenOn(today),
            repairedToday = false
        )

        if (state == GuardianState.ACTIVE || state == GuardianState.RESTORED) {
            finish()
            return
        }

        // The one dark surface in the app. Every other card ALIVE draws is paper,
        // so the night the Guardian arrives is unmistakable before a word of it is
        // read - and the window dims further than the critical note does, because
        // this one is a scene rather than a note.
        window.attributes = window.attributes.apply { dimAmount = 0.62f }

        val ui = AliveComponents(this)
        val card = Guardian.plate(ui)
        val paper = ui.palette.paper

        card.addView(
            ui.mark(
                Guardian.spriteFor(state),
                // Twice the size a face sprite gets. The Guardian is 48 cells now
                // and this plate is the one screen where it is the subject rather
                // than a mood beside a sentence.
                ui.dimens.dp(176),
                getString(Guardian.descriptionFor(state))
            )
        )
        card.addView(ui.vgap(ui.dimens.lineGap))

        card.addView(Guardian.inCrimson(ui.sectionHeader(getString(R.string.guardian_name)), ui))
        card.addView(ui.vgap(ui.dimens.lineGap))

        card.addView(Guardian.inPaper(ui.headline(getString(Guardian.headlineFor(state))), ui))
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(Guardian.inPaper(ui.body(getString(Guardian.bodyFor(state))), ui))

        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(Guardian.inPaper(ui.note(getString(Guardian.noteFor(state))), ui))

        // The one action worth taking, in the amber ALIVE keeps for the thing you
        // are being asked to do - and a way out that costs nothing, because a
        // reminder you cannot dismiss is a lock, and this is not one.
        card.addView(ui.vgap(ui.dimens.groupGap))
        card.addView(
            ui.primary(getString(Guardian.actionFor(state) ?: R.string.guardian_action_restore))
                .apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        finish()
                    }
                }
        )
        card.addView(ui.vgap(ui.dimens.lineGap))
        card.addView(
            // A paper chip with an ink edge, not the app's flat "quiet" button:
            // that one is transparent until it is pressed, and a transparent
            // button on an ink plate is a button nobody can find. The way out has
            // to be the clearest thing here.
            ui.secondary(getString(R.string.guardian_later)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { finish() }
            }
        )

        setContentView(card)

        // A reminder left on top of a phone nobody is holding is litter: it
        // closes itself, and the watch will knock again the next time an
        // entertainment app comes forward.
        dismissHandler.postDelayed({ finish() }, AUTO_DISMISS_MILLIS)
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val AUTO_DISMISS_MILLIS = 30_000L
    }
}
