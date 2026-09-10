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
     * Determines whether [packageName] is allowed during an active Focus Session for [activityType].
     * Rules:
     * 1. ESSENTIAL apps: Always allowed.
     * 2. ENTERTAINMENT apps: NEVER allowed.
     * 3. Focus-approved productivity apps: Allowed only if in [activityType]'s approved list.
     * 4. All other apps (unknown, browsers, non-approved): Blocked.
     */
    fun isPackageAllowedDuringFocus(
        context: Context,
        packageName: String,
        activityType: FocusActivityType
    ): Boolean {
        // Rule A: Essential apps always allowed
        val baseCategory = AppClassifier.categorize(context, packageName)
        if (baseCategory == AppCategory.ESSENTIAL || baseCategory == AppCategory.SYSTEM_IGNORED) {
            return true
        }

        // Rule B: Entertainment apps NEVER allowed during Focus
        if (baseCategory == AppCategory.ENTERTAINMENT) {
            return false
        }

        // Rule C: Approved productivity apps for the current activity type
        val approved = getApprovedPackages(activityType)
        return approved.contains(packageName)
    }
}
