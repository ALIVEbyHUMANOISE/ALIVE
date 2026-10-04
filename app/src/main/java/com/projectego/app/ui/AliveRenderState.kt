package com.projectego.app.ui

import com.projectego.app.model.FocusSession
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestAttempt

/** Which full-screen panel is sitting on top of the tabs. */
enum class AlivePanel {
    NONE,

    /** A quest the user is reading about, not yet committed to. */
    QUEST_DETAIL,

    /** The quest currently in flight, with its verification state. */
    CURRENT_QUEST,

    /** A focus session already running. */
    FOCUS_SESSION
}

/**
 * What the UI currently believes, held in memory only.
 *
 * Two jobs:
 *  - Panels and Profile render from these cached reads instead of calling the
 *    engine again. `calculateTodayEntertainmentUsage()` and `getFocusSession()`
 *    are not passive - they can roll the day, set the lock flag, and clear an
 *    expired session - so the number of times they run per render has to stay
 *    exactly where it was. Caching is what lets new surfaces exist without
 *    adding a single new call.
 *  - The focus setup form is rebuilt whenever the dashboard re-renders, so a
 *    typed-but-unsubmitted goal has to live here or it vanishes mid-edit.
 *
 * None of this is ever written to disk, and none of it is fed back to the
 * engine. If the process dies, the panel simply opens on Home again.
 */
class AliveRenderState {

    var signals: AliveSignals? = null

    var questCatalog: List<Quest> = emptyList()

    var attempt: QuestAttempt? = null

    var focusSession: FocusSession? = null

    /** Time Bank totals, kept apart because earned and available are not the same number. */
    var earnedMinutes: Long = 0L
    var spentMinutes: Long = 0L

    var dailyLimitMinutes: Long = 0L
    var usedMinutesToday: Long = 0L

    var panel: AlivePanel = AlivePanel.NONE

    /** The quest the detail panel is showing. */
    var panelQuestId: String? = null

    /** Draft goal text, so a re-render does not erase what was typed. */
    var focusGoalDraft: String = ""

    /** Index into FocusActivityType.values() and into the legal duration list. */
    var selectedActivityIndex: Int = 0
    var selectedDurationIndex: Int = 1

    val isPanelOpen: Boolean
        get() = panel != AlivePanel.NONE

    fun closePanel() {
        panel = AlivePanel.NONE
        panelQuestId = null
    }

    fun quest(id: String?): Quest? = questCatalog.find { it.id == id }
}
