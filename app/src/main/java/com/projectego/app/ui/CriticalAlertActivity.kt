package com.projectego.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * The critical-time note, shown over whatever app the user is in - in two phases.
 *
 * First [CriticalWarningCard]: five seconds of a red field that says the
 * allowance is nearly gone, with no way out of it - back does nothing and there
 * is nothing outside to tap. Then the card that was here before, which states the
 * minutes left, lists the bar, and takes back every exit: back, tap-outside and
 * its own button all just finish it, landing the user back in the app they were
 * using. That second phase is what the note has always been; only the five
 * seconds in front of it are new.
 *
 * Two things make this safe to launch from the accessibility service. It reads no
 * engine state - the service already computed the figures and hands them over as
 * extras, so opening this card cannot re-run a day reset or touch the lock flag.
 * And it takes nothing: it never presses HOME and never blocks the button, which
 * is why the warning says "seconds to go" instead of pretending to be a trap.
 *
 * The extra keys are literals on both sides, matching the existing
 * `LockActivity` contract and its documented reason for staying that way.
 */
class CriticalAlertActivity : AppCompatActivity() {

    private val dismissHandler = Handler(Looper.getMainLooper())
    private var phase = Phase.WARNING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val remaining = intent.getLongExtra("EXTRA_CRITICAL_REMAINING_MINUTES", 0L)
        val limit = intent.getLongExtra("EXTRA_CRITICAL_LIMIT_MINUTES", 0L)

        // Zero belongs to the lock, not to a note. If the extras ever arrive
        // empty there is nothing honest to say, so say nothing - and take the
        // dim with it. The theme greys the screen for the card's benefit, and a
        // card that never appears would otherwise leave a rectangle of grey over
        // someone's dialler for a frame.
        if (remaining <= 0L) {
            window.attributes = window.attributes.apply { dimAmount = 0f }
            finish()
            return
        }

        // Stands for the whole life of the window: silent while the warning runs,
        // an exit once the card is up.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (phase == Phase.CARD) finish()
                }
            }
        )

        setFinishOnTouchOutside(false)
        setContentView(
            CriticalWarningCard.build(this, AliveComponents(this), remaining) {
                showCard(remaining, limit)
            }
        )
    }

    private fun showCard(remaining: Long, limit: Long) {
        phase = Phase.CARD
        setFinishOnTouchOutside(true)
        setContentView(
            CriticalTimeCard.build(this, AliveComponents(this), remaining, limit) { finish() }
        )

        // A note that would have been forgotten must not sit over someone's
        // dialler. This only ever removes the card; it never acts on it. The
        // countdown starts here, so the five seconds of warning are not taken out
        // of the twenty the card is allowed to stay.
        dismissHandler.postDelayed({ finish() }, AUTO_DISMISS_MILLIS)
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private enum class Phase { WARNING, CARD }

    private companion object {
        const val AUTO_DISMISS_MILLIS = 20_000L
    }
}
