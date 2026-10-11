package com.projectego.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.projectego.app.R

/**
 * Guardian: the name and the face of the protection state ALIVE already tracks.
 *
 * It is not a second state machine. The three facts that decide it are the three
 * the dashboard already computes for the guard gap - is the accessibility
 * service on, is an unguarded stretch marked on today, and has a repair already
 * been paid - so Guardian can never disagree with the enforcement it stands for.
 * Nothing here reads or writes the engine; it takes values the UI was already
 * given, which is what makes the whole vocabulary testable on plain JVM.
 *
 * Two rules it inherits and does not soften:
 *  - Guardian is never the only signal. Every state has its own words, and the
 *    state is always stated in text next to the character.
 *  - Guardian never claims a power it does not have. Android lets the user
 *    switch the service off, and the copy says what that costs instead of
 *    pretending it cannot happen.
 */
enum class GuardianState {
    /** Protection on, nothing marked: standing watch. */
    ACTIVE,

    /** Protection off, nothing owed yet: it has noticed, and nothing is marked. */
    DEGRADED,

    /** Protection off and a day over the limit went unguarded: a repair is owed. */
    RECOVERY_REQUIRED,

    /** Protection is back and the repair was paid: the watch is whole again. */
    RESTORED
}

object Guardian {

    /**
     * The state, from the three facts, with the one precedence that matters: a
     * mark on the day outranks the fact that the service is off, because being
     * unguarded on a day that owed enforcement is a real cost and being off on a
     * day that owed nothing is not.
     */
    fun stateOf(protectionOff: Boolean, gapOpen: Boolean, repairedToday: Boolean): GuardianState = when {
        protectionOff && gapOpen -> GuardianState.RECOVERY_REQUIRED
        protectionOff -> GuardianState.DEGRADED
        repairedToday -> GuardianState.RESTORED
        else -> GuardianState.ACTIVE
    }

    @DrawableRes
    fun spriteFor(state: GuardianState): Int = when (state) {
        GuardianState.ACTIVE -> R.drawable.ic_guardian_idle
        GuardianState.DEGRADED -> R.drawable.ic_guardian_alert
        GuardianState.RECOVERY_REQUIRED -> R.drawable.ic_guardian_recovery
        GuardianState.RESTORED -> R.drawable.ic_guardian_restored
    }

    @StringRes
    fun headlineFor(state: GuardianState): Int = when (state) {
        GuardianState.ACTIVE -> R.string.guardian_active_heading
        GuardianState.DEGRADED -> R.string.guardian_degraded_heading
        GuardianState.RECOVERY_REQUIRED -> R.string.guardian_recovery_heading
        GuardianState.RESTORED -> R.string.guardian_restored_heading
    }

    @StringRes
    fun bodyFor(state: GuardianState): Int = when (state) {
        GuardianState.ACTIVE -> R.string.guardian_active_body
        GuardianState.DEGRADED -> R.string.guardian_degraded_body
        GuardianState.RECOVERY_REQUIRED -> R.string.guardian_recovery_body
        GuardianState.RESTORED -> R.string.guardian_restored_body
    }

    /** The honest footnote, always the same fact: ALIVE cannot tell who did it. */
    @StringRes
    fun noteFor(state: GuardianState): Int = when (state) {
        GuardianState.ACTIVE, GuardianState.RESTORED -> R.string.guardian_note_whole
        GuardianState.DEGRADED, GuardianState.RECOVERY_REQUIRED -> R.string.guardian_note_off
    }

    /** The one action, or null when there is nothing to press. */
    @StringRes
    fun actionFor(state: GuardianState): Int? = when (state) {
        GuardianState.DEGRADED, GuardianState.RECOVERY_REQUIRED -> R.string.guardian_action_restore
        GuardianState.ACTIVE, GuardianState.RESTORED -> null
    }

    /** Spoken description, so the character never carries the state on its own. */
    @StringRes
    fun descriptionFor(state: GuardianState): Int = when (state) {
        GuardianState.ACTIVE -> R.string.cd_guardian_idle
        GuardianState.DEGRADED -> R.string.cd_guardian_alert
        GuardianState.RECOVERY_REQUIRED -> R.string.cd_guardian_recovery
        GuardianState.RESTORED -> R.string.cd_guardian_restored
    }

    /**
     * The plate a broken watch stands on.
     *
     * Ink face, deep crimson rim and shadow - because the app's own ink-on-ink
     * edge vanishes on the one surface that is itself ink. This is the single
     * place in ALIVE where a raised card is not paper, and it is the same plate
     * the site hangs the poster on, so the app and the legend are visibly one
     * object.
     */
    fun plate(ui: AliveComponents, weight: SurfaceWeight = SurfaceWeight.MAJOR): LinearLayout =
        ui.rimmedBlock(ui.palette.ink, ui.palette.shortDark, weight)

    /**
     * Paper type on that plate.
     *
     * Every ink token - heading, body, even the "muted" tone - is invisible on
     * ink, and the app has no light secondary, so on this one surface every word
     * is written in the same paper and separated by size instead of by weight of
     * grey. Legibility first: nothing here is allowed to be decorative.
     */
    fun <T : TextView> inPaper(text: T, ui: AliveComponents): T =
        text.apply { setTextColor(ui.palette.paper) }

    /** The eyebrow: crimson's pale end, which is the one tint that holds on ink. */
    fun <T : TextView> inCrimson(text: T, ui: AliveComponents): T =
        text.apply { setTextColor(ui.palette.shortPale) }

    /** A rule for a surface that is not paper: an ink rule would draw nothing here. */
    fun rule(context: Context, ui: AliveComponents): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ui.dimens.borderBold
        )
        setBackgroundColor(ui.palette.shortDark)
    }

    /**
     * The character as a bitmap, for the notification's large icon.
     *
     * A launcher and the shade cannot load a font or resolve an app vector in
     * another process, and a notification is drawn by SystemUI - so the sprite
     * arrives as pixels, at four screen pixels per sprite cell, which keeps every
     * edge on the grid the way the vectors do.
     */
    fun bitmap(context: Context, state: GuardianState, sizePx: Int = SPRITE_PX * 4): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val drawable = AppCompatResources.getDrawable(context, spriteFor(state))
        if (drawable != null) {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
        }
        return bitmap
    }

    /** The sprite grid, so an icon can be sized as a whole number of cells. */
    const val SPRITE_PX = 16
}
