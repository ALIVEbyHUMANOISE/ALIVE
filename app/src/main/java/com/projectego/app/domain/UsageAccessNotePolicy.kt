package com.projectego.app.domain

/**
 * When the usage-access note appears and disappears.
 *
 * Posted on the transition only. A guard that keeps finding the grant missing
 * ticks every five seconds; without this rule that would be a notification every
 * five seconds, which is how a warning becomes noise people swipe away. Pure so
 * the transition can be tested without a NotificationManager.
 */
object UsageAccessNotePolicy {

    enum class Action { SHOW, CLEAR, NONE }

    fun action(shown: Boolean, granted: Boolean): Action = when {
        !granted && !shown -> Action.SHOW
        granted && shown -> Action.CLEAR
        else -> Action.NONE
    }
}
