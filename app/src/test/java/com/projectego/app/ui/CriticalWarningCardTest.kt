package com.projectego.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The five seconds in front of the critical note.
 *
 * Two things here are load-bearing and both are the kind that rot silently: the
 * red only breathes when the device allows animation, and the window really does
 * hand over to the old card once the countdown is done. A warning that never let
 * go would be the worst bug in the app; a warning that ignored the system's
 * "remove animations" setting would be the second.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CriticalWarningCardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val ui = AliveComponents(context)

    private fun withAnimations(allowed: Boolean) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            if (allowed) 1f else 0f
        )
    }

    private fun texts(root: View): List<String> {
        val found = mutableListOf<String>()
        fun walk(view: View) {
            if (view is TextView) found += view.text.toString()
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(root)
        return found
    }

    private fun faceOf(view: View): GradientDrawable? =
        (view.background as? LayerDrawable)?.getDrawable(1) as? GradientDrawable

    @Test
    fun `the field stops breathing when the device asks for no animation`() {
        val mid = ui.palette.shortMid
        val deep = ui.palette.shortDark
        val still = listOf(0f, 0.35f, 0.5f, 1f).map { CriticalWarningCard.fieldColour(it, false, mid, deep) }

        assertEquals(listOf(mid, mid, mid, mid), still)

        // ...and with animation allowed it is a real breath between the two reds,
        // so the gate above is not passing by accident.
        val moving = CriticalWarningCard.fieldColour(0.5f, true, mid, deep)
        assertNotEquals(mid, moving)
        assertNotEquals(deep, moving)
    }

    @Test
    fun `the warning states the minutes, counts down from five, and cannot be backed out of`() {
        withAnimations(false)
        val activity = Robolectric.buildActivity(
            CriticalAlertActivity::class.java,
            alertIntent(remaining = 12L, limit = 60L)
        ).setup().get()

        val shown = texts(activity.window.decorView.findViewById(android.R.id.content))
        assertTrue(shown.contains(activity.getString(R.string.alert_warning_eyebrow)))
        assertTrue(shown.contains("12"))
        assertTrue(shown.contains(CriticalWarningCard.LOCK_SECONDS.toString()))

        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse("back must not close the warning", activity.isFinishing)

        // The countdown is what releases the window, so it is the thing to wait on.
        ShadowLooper.idleMainLooper(CriticalWarningCard.LOCK_SECONDS * 1_000L + 200L)

        val after = texts(activity.window.decorView.findViewById(android.R.id.content))
        assertTrue(after.contains(activity.getString(R.string.chart_alert_eyebrow)))
        assertFalse("the warning is replaced, not stacked", after.contains(activity.getString(R.string.alert_warning_eyebrow)))
        assertFalse("the card does not close itself the moment it appears", activity.isFinishing)

        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("the card takes back every exit, including back", activity.isFinishing)
    }

    @Test
    fun `the warning keeps its red while the device refuses animation`() {
        withAnimations(false)
        val warning = CriticalWarningCard.build(context, ui, remainingMinutes = 12L) {}

        assertEquals(ui.palette.shortMid, faceOf(warning)?.color?.defaultColor)
    }

    private fun alertIntent(remaining: Long, limit: Long): Intent =
        Intent(context, CriticalAlertActivity::class.java).apply {
            putExtra("EXTRA_CRITICAL_REMAINING_MINUTES", remaining)
            putExtra("EXTRA_CRITICAL_LIMIT_MINUTES", limit)
        }
}
