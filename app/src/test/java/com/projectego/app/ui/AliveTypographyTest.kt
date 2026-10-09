package com.projectego.app.ui

import android.content.Context
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The typography is ALIVE's own faces, full stop.
 *
 * One regression is all it takes for a style to fall back to the platform
 * face, and a single Roboto line among the Silkscreen, Space Grotesk and
 * Manrope ones is exactly the "some of it is still normal" this design keeps
 * catching. This test locks every ALIVE text style to the faces the design
 * owns - the same way [AlivePaletteTest] locks the colours - and to a killed
 * font padding, which is what keeps mixed rows from sitting on different
 * baselines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveTypographyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val faces = setOf(
        R.font.silkscreen_regular,
        R.font.silkscreen_bold,
        R.font.space_grotesk_semibold,
        R.font.space_grotesk_bold,
        R.font.manrope_regular,
        R.font.manrope_medium,
        R.font.manrope_bold,
        R.font.press_start_2p
    )

    private val textStyles = listOf(
        R.style.TextAppearance_ALIVE_Display to "Display",
        R.style.TextAppearance_ALIVE_Headline to "Headline",
        R.style.TextAppearance_ALIVE_Lead to "Lead",
        R.style.TextAppearance_ALIVE_Body to "Body",
        R.style.TextAppearance_ALIVE_Note to "Note",
        R.style.TextAppearance_ALIVE_SectionHeader to "SectionHeader",
        R.style.TextAppearance_ALIVE_Meta to "Meta",
        R.style.TextAppearance_ALIVE_Error to "Error",
        R.style.TextAppearance_ALIVE_Reason to "Reason",
        R.style.TextAppearance_ALIVE_Label to "Label",
        R.style.TextAppearance_ALIVE_Timer to "Timer",
        R.style.TextAppearance_ALIVE_Number to "Number",
        R.style.TextAppearance_ALIVE_Annotation to "Annotation",
        R.style.TextAppearance_ALIVE_Option to "Option",
        R.style.TextAppearance_ALIVE_Input to "Input",
        R.style.TextAppearance_ALIVE_Button to "Button",
        R.style.TextAppearance_ALIVE_NavLabel to "NavLabel"
    )

    private val widgetStyles = listOf(
        R.style.Widget_ALIVE_Button_Primary to "Widget.ALIVE.Button.Primary"
    )

    @Test
    fun `every text style is one of the faces the design owns`() {
        (textStyles + widgetStyles).forEach { (style, name) ->
            val font = resolveFontFamily(style)
            assertTrue(
                "$name resolves to a face outside the design ($font); it must be " +
                    "Silkscreen, Space Grotesk, Manrope, or Press Start 2P for badges",
                font in faces
            )
        }
    }

    /**
     * The reading voice is Manrope, and each role sits on the weight it was
     * given: prose at 400, anything tapped or glanced at at 500, the one line
     * that says something went wrong at 700.
     *
     * Membership in [faces] alone would not catch a body style quietly moving
     * to medium, which is how a screen ends up with no weight contrast at all.
     */
    @Test
    fun `the reading styles are manrope at the weight their role asks for`() {
        listOf(
            Triple("Body", R.style.TextAppearance_ALIVE_Body, R.font.manrope_regular),
            Triple("Note", R.style.TextAppearance_ALIVE_Note, R.font.manrope_regular),
            Triple("Option", R.style.TextAppearance_ALIVE_Option, R.font.manrope_regular),
            Triple("Input", R.style.TextAppearance_ALIVE_Input, R.font.manrope_regular),
            Triple("Meta", R.style.TextAppearance_ALIVE_Meta, R.font.manrope_medium),
            Triple("Button", R.style.TextAppearance_ALIVE_Button, R.font.manrope_medium),
            Triple("NavLabel", R.style.TextAppearance_ALIVE_NavLabel, R.font.manrope_medium),
            Triple(
                "Widget.ALIVE.Button.Primary",
                R.style.Widget_ALIVE_Button_Primary,
                R.font.manrope_medium
            ),
            Triple("Error", R.style.TextAppearance_ALIVE_Error, R.font.manrope_bold),
            Triple("Reason", R.style.TextAppearance_ALIVE_Reason, R.font.manrope_medium)
        ).forEach { (name, style, face) ->
            assertEquals(
                "$name must resolve to the Manrope weight its role is given; those " +
                    "weights are what tell prose, offers and failures apart",
                face,
                resolveFontFamily(style)
            )
        }
    }

    @Test
    fun `no text style keeps the platform font padding`() {
        textStyles.forEach { (style, name) ->
            val padding = context.obtainStyledAttributes(
                style,
                intArrayOf(android.R.attr.includeFontPadding)
            ).use { it.getBoolean(0, true) }
            assertFalse(
                "$name still carries the platform font padding, which is what " +
                    "throws mixed rows off their baselines",
                padding
            )
        }
    }

    private fun resolveFontFamily(style: Int): Int {
        val value = TypedValue()
        context.obtainStyledAttributes(
            style,
            intArrayOf(android.R.attr.fontFamily)
        ).use {
            it.getValue(0, value)
        }
        return value.resourceId
    }
}
