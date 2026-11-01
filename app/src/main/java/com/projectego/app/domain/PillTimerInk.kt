package com.projectego.app.domain

/**
 * What colour the pill's number should be.
 *
 * Three inputs, in priority order: a battery warning always wins, a focus
 * countdown is never crimson - focus time is not entertainment and cannot be
 * "spent" - and an ordinary reading turns crimson only when the day's limit is
 * the thing on screen.
 */
object PillTimerInk {

    fun isCrimson(focusShown: Boolean, limitReached: Boolean, warning: Boolean): Boolean =
        warning || (!focusShown && limitReached)
}
