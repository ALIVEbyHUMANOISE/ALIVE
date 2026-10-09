package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget, held to what a launcher will actually accept.
 *
 * A home-screen layout is not inflated by ALIVE - the launcher builds it in its
 * own process, with its own theme, and only out of the classes RemoteViews
 * names. The first version put a bare `android.view.View` in the app rows and
 * the launcher answered "couldn't add widget": the card never appeared at all,
 * because one chip it refused to build sinks the whole layout. These assertions
 * keep every view inside that list, and keep the chips the colour of the slice
 * each row stands for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveWidgetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every view in the widget is one a launcher will inflate`() {
        val root = LayoutInflater.from(context).inflate(R.layout.widget_alive_stats, null)

        val allowed = setOf(
            FrameLayout::class.java,
            LinearLayout::class.java,
            TextView::class.java,
            ImageView::class.java,
            ProgressBar::class.java,
        )

        val built = mutableListOf<View>()
        fun walk(view: View) {
            built.add(view)
            if (view is ViewGroup) {
                (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
            }
        }
        walk(root)

        assertEquals(
            "A class a launcher will not build leaves the home screen with no card at all",
            emptyList<String>(),
            built.filterNot { allowed.contains(it.javaClass) }.map { it.javaClass.name },
        )
    }

    @Test
    fun `the figure and the meter are pictures ALIVE paints, not text the launcher sets`() {
        val root = LayoutInflater.from(context).inflate(R.layout.widget_alive_stats, null)

        // A launcher cannot load a font that ships with this app, so anything
        // wearing Silkscreen has to arrive as a picture. A TextView in either of
        // these two slots is the system face on the home screen again.
        assertTrue(root.findViewById<View>(R.id.widget_figure) is ImageView)
        assertTrue(root.findViewById<View>(R.id.widget_meter) is ImageView)
    }

    @Test
    fun `the row chips are the ring's own first three slices`() {
        val chips = listOf(
            R.drawable.widget_chip_first,
            R.drawable.widget_chip_second,
            R.drawable.widget_chip_third,
        )

        chips.forEachIndexed { index, chip ->
            assertEquals(
                ContextCompat.getColor(context, AliveSeries.donut[index]),
                solidColorOf(chip),
            )
        }
    }

    private fun solidColorOf(chip: Int): Int =
        (AppCompatResources.getDrawable(context, chip) as GradientDrawable).color?.defaultColor ?: 0
}
