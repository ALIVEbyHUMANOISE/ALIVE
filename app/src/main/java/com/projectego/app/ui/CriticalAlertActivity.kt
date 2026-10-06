package com.projectego.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * The critical-time note, shown over whatever app the user is in.
 *
 * Two things make this safe to launch from the accessibility service. It reads
 * no engine state - the service already computed the figures and hands them
 * over as extras, so opening this card cannot re-run a day reset or touch the
 * lock flag. And it takes nothing: back and tap-outside both just finish it,
 * landing the user back in the app they were using.
 *
 * The extra keys are literals on both sides, matching the existing
 * `LockActivity` contract and its documented reason for staying that way.
 */
class CriticalAlertActivity : AppCompatActivity() {

    private val dismissHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val remaining = intent.getLongExtra("EXTRA_CRITICAL_REMAINING_MINUTES", 0L)
        val limit = intent.getLongExtra("EXTRA_CRITICAL_LIMIT_MINUTES", 0L)

        // Zero belongs to the lock, not to a note. If the extras ever arrive
        // empty there is nothing honest to say, so say nothing.
        if (remaining <= 0L) {
            finish()
            return
        }

        setContentView(
            CriticalTimeCard.build(this, AliveComponents(this), remaining, limit) { finish() }
        )

        // A note that would have been forgotten must not sit over someone's
        // dialler. This only ever removes the card; it never acts on it.
        dismissHandler.postDelayed({ finish() }, AUTO_DISMISS_MILLIS)
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val AUTO_DISMISS_MILLIS = 20_000L
    }
}
