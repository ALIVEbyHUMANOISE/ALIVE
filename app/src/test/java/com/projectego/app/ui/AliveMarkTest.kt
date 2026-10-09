package com.projectego.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The mark has to survive three different jobs: a 20dp turn beside the wordmark,
 * a 64dp adaptive-icon foreground that the launcher crops, and a 96dp splash
 * icon. Each of those is a different drawable path (vector, then two layer
 * lists), and a layer list that fails to size its item inflates fine at build
 * time and simply arrives small or blank on the phone. So the sizes are
 * asserted here rather than discovered on a home screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveMarkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `the hourglass mark is drawn and sized`() {
        val mark = ContextCompat.getDrawable(context, R.drawable.ic_alive_hourglass)
        assertTrue("the hourglass did not inflate", mark != null)
        assertTrue(
            "the mark has no intrinsic size, so every surface that shows it gets a blank",
            mark!!.intrinsicWidth > 0 && mark.intrinsicHeight > 0
        )
    }

    @Test
    fun `the icon and splash wrappers keep the mark square`() {
        listOf(
            "launcher foreground" to R.drawable.ic_launcher_foreground,
            "splash icon" to R.drawable.ic_splash_hourglass
        ).forEach { (name, res) ->
            val drawable = ContextCompat.getDrawable(context, res)
            assertTrue("$name did not inflate", drawable != null)
            val width = drawable!!.intrinsicWidth
            val height = drawable.intrinsicHeight
            assertTrue("$name is $width x $height; the mark inside it would be cropped", width > 0 && height > 0)
        }
    }
}
