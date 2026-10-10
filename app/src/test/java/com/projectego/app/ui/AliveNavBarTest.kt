package com.projectego.app.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The circle and the hole cut around it, placed by a width the bar may not have
 * yet.
 *
 * Opening ALIVE from the home-screen widget chooses a key from `onResume`, before
 * the first measure pass exists, and every centre on an unmeasured bar is zero.
 * The circle has to end up on the key it was asked for once the bar does have a
 * width - and a trip started in the meantime must not drag it back afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveNavBarTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val barWidth = 1080

    private fun measureAndPlace(bar: AliveNavBar) {
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(barWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        bar.layout(0, 0, barWidth, bar.measuredHeight)
    }

    /** The one circle is the bar's last child; its centre is what must sit on a key. */
    private fun circleCentre(bar: AliveNavBar): Float = bar.getChildAt(bar.childCount - 1).let {
        it.translationX + it.width / 2f
    }

    private fun centreOfKey(index: Int): Float = barWidth * (index + 0.5f) / KEYS

    @Test
    fun `a key chosen before the bar has a width still lands the circle on that key`() {
        val bar = AliveNavBar(context)
        bar.select(AliveNavBar.Index.HOME, animate = false)
        bar.select(AliveNavBar.Index.STATS)

        measureAndPlace(bar)
        // Whatever frames the trip between those two keys still wants to run.
        ShadowLooper.idleMainLooper(600)

        assertEquals(centreOfKey(AliveNavBar.Index.STATS), circleCentre(bar), 1f)
    }

    @Test
    fun `a bar that changes width mid-trip seats the circle on the chosen key`() {
        val bar = AliveNavBar(context)
        bar.select(AliveNavBar.Index.HOME, animate = false)
        measureAndPlace(bar)

        bar.select(AliveNavBar.Index.PROFILE)
        // Rotation: the trip is still running when the bar is measured again.
        bar.layout(0, 0, barWidth / 2, bar.measuredHeight)
        ShadowLooper.idleMainLooper(600)

        assertEquals(
            (barWidth / 2) * (AliveNavBar.Index.PROFILE + 0.5f) / KEYS,
            circleCentre(bar),
            1f
        )
    }

    private companion object {
        const val KEYS = 4
    }
}
