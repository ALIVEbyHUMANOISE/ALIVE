package com.projectego.app.domain

import android.content.Context
import com.projectego.app.model.AppCategory
import com.projectego.app.model.FocusActivityType

object FocusAppPolicy {

    // Curated catalog of productivity apps per activity type
    // Strict requirement: NO normal browsers (Chrome, Firefox, Edge, Opera, Samsung Internet)
    private val STUDY_PACKAGES = setOf(
        "com.google.android.apps.docs.editors.docs",       // Google Docs
        "com.google.android.apps.docs.editors.sheets",     // Google Sheets
        "com.google.android.apps.docs.editors.slides",     // Google Slides
        "com.microsoft.office.word",                       // Microsoft Word
        "com.microsoft.office.excel",                      // Microsoft Excel
        "com.microsoft.office.powerpoint",                 // Microsoft PowerPoint
        "com.microsoft.office.officehubrow",               // Microsoft 365 (Office)
        "com.google.android.apps.docs",                    // Google Drive
        "com.google.android.keep",                         // Google Keep / Notes
        "com.samsung.android.app.notes",                   // Samsung Notes
        "com.google.android.calculator",                   // Google Calculator
        "com.sec.android.app.popupcalculator",             // Samsung Calculator
        "com.openai.chatgpt",                              // ChatGPT (AI study tool)
        "com.anthropic.claude",                            // Claude
        "com.quizlet.quizletandroid",                      // Quizlet
        "com.notion.id"                                    // Notion
    )

    private val WORK_PACKAGES = setOf(
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.officehubrow",
        "com.google.android.apps.docs",
        "com.google.android.keep",
        "com.samsung.android.app.notes",
        "com.google.android.calculator",
        "com.sec.android.app.popupcalculator",
        "com.notion.id",
        "com.slack",                                       // Slack
        "com.microsoft.teams",                             // Microsoft Teams
        "com.google.android.gm",                           // Gmail (Work email)
        "com.google.android.calendar",                     // Google Calendar
        "com.samsung.android.calendar",                    // Samsung Calendar
        "com.openai.chatgpt",
        "com.anthropic.claude"
    )

    private val CODING_PACKAGES = setOf(
        "com.github.android",                              // GitHub
        "io.spck",                                         // Spck Editor
        "com.foxdebug.acode",                              // Acode
        "com.rhmsoft.edit",                                // QuickEdit
        "com.termux",                                      // Termux (Terminal)
        "com.google.android.apps.nbu.files",               // Google Files
        "com.sec.android.app.myfiles",                     // Samsung My Files
        "com.notion.id",
        "com.google.android.keep",
        "com.openai.chatgpt",
        "com.anthropic.claude"
    )

    private val CREATIVE_PACKAGES = setOf(
        "com.google.android.keep",
        "com.samsung.android.app.notes",
        "com.adsk.sketchbook",                             // Sketchbook
        "com.canva.editor",                                // Canva
        "com.lemon.lvoverseas",                            // CapCut
        "com.nexstreaming.app.kinemasterfree",             // KineMaster
        "com.camerasideas.instashot",                      // InShot
        "com.notion.id",
        "com.openai.chatgpt",
        "com.anthropic.claude"
    )

    private val WRITING_PACKAGES = setOf(
        "com.google.android.apps.docs.editors.docs",
        "com.microsoft.office.word",
        "com.google.android.keep",
        "com.samsung.android.app.notes",
        "com.notion.id",
        "com.openai.chatgpt",
        "com.anthropic.claude"
    )

    fun getApprovedPackages(activityType: FocusActivityType): Set<String> {
        return when (activityType) {
            FocusActivityType.STUDY -> STUDY_PACKAGES
            FocusActivityType.WORK -> WORK_PACKAGES
            FocusActivityType.CODING -> CODING_PACKAGES
            FocusActivityType.CREATIVE -> CREATIVE_PACKAGES
            FocusActivityType.WRITING -> WRITING_PACKAGES
        }
    }

    /**
     * What a session allows: the person's own list when they named one, the
     * catalog's otherwise.
     *
     * One function rather than the decision spread across call sites, because
     * the list has to answer the same way in three places at once - while the
     * session runs, when the day is charged, and in the archive after the
     * session has ended - and a session whose apps were allowed while it ran but
     * charged afterwards is a session that lied about itself.
     */
    fun approvedFor(activityType: FocusActivityType, override: Set<String>?): Set<String> =
        override ?: getApprovedPackages(activityType)

    /**
     * The focus gate as a pure rule, so the rule can be tested as a rule.
     *
     * Order is the whole policy:
     * 1. The activity's curated list wins first. A curated app is allowed even
     *    when AppClassifier's screen-time defaults paint it as entertainment,
     *    because naming the third-party apps a session is FOR is the list's
     *    entire job. (The old order ran the entertainment veto first, which
     *    silently blocked every non-system curated app.)
     * 2. ESSENTIAL, SYSTEM_IGNORED, TRACKED and WORK always stay open. TRACKED
     *    is messaging and WORK is the tools a session is for - a block could
     *    cost the person a message or a file they cannot see, which is as true
     *    while focusing as at any other time (see AppCategory.TRACKED and
     *    AppCategory.WORK).
     * 3. Everything else - entertainment, browsers, anything the list does
     *    not name - stays closed.
     */
    fun decide(approved: Set<String>, category: AppCategory, packageName: String): Boolean =
        approved.contains(packageName) ||
            category == AppCategory.ESSENTIAL ||
            category == AppCategory.SYSTEM_IGNORED ||
            category == AppCategory.TRACKED ||
            category == AppCategory.WORK

    /**
     * Whether [packageName] is allowed during an active Focus Session for
     * [activityType]. See [decide] for the rule and its order.
     */
    fun isPackageAllowedDuringFocus(
        context: Context,
        packageName: String,
        activityType: FocusActivityType,
        override: Set<String>? = null
    ): Boolean = decide(
        approvedFor(activityType, override),
        AppClassifier.categorize(context, packageName),
        packageName
    )
}
