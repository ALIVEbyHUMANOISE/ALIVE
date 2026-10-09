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
    FOCUS_SESSION,

    /** Today, the last seven days, the month so far. Read-only. */
    USAGE_CHART
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
 *
 * The usage chart is the one surface that also reads `UsageHistoryRepository`
 * directly. That store is allowed because it has no side effects at all - no
 * day reset, no lock flag, no session clearing - unlike the two engine reads
 * above, which is precisely the property this cache exists to protect.
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

    /**
     * Today's per-app entertainment minutes, exactly as the engine handed them
     * over. A new *field*, not a new *read*: it is filled from the report the
     * dashboard already holds, which is what keeps the chart off the engine.
     */
    var entertainmentAppDetails: List<Pair<String, Long>> = emptyList()

    /**
     * Time today in apps ALIVE never blocks, in minutes. Reported to the person
     * and charged to nobody: the allowance is entertainment time, and a chat is
     * not entertainment just because it happened on a screen.
     */
    var unblockedMinutes: Long = 0L

    /** Which stretch of days the chart is showing. */
    var chartRangeIndex: Int = 0

    /**
     * Today's counted usage, hour by hour - a fresh read taken when the chart is
     * opened, not a copy of the daily total. The daily number cannot be cut into
     * hours after the fact, and a chart of "when today went" is stale the moment
     * it is an hour old, so this one field is the single place the panel reads
     * the engine instead of the report.
     */
    var hourlyUsedMillis: LongArray = LongArray(24)

    /**
     * Today's guard state, read from `LockStateManager` during the render that
     * already happens. [guardGapOpen] is true while the accessibility service is
     * off and the repair has not landed yet; [guardGapRepairedToday] is the same
     * day after a reward closed it, which still shows - that stretch really
     * happened.
     */
    var guardGapOpen: Boolean = false
    var guardGapRepairedToday: Boolean = false

    /**
     * Whether a quest has actually paid out today - the one thing that keeps
     * tomorrow's allowance. Read once per render like the guard state, from a
     * preference that has no side effects at all.
     */
    var questDoneToday: Boolean = false

    /**
     * The last attempt ALIVE has already celebrated, so the burst is one event
     * per quest instead of one per render. Memory only, like everything here:
     * nothing is owed a party after a restart.
     */
    var celebratedAttemptId: String? = null

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
