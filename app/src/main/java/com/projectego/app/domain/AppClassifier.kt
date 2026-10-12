package com.projectego.app.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import com.projectego.app.model.AppCategory

object AppClassifier {

    // Every package below must also appear in the <queries> block of
    // AndroidManifest.xml: without it, Android 11+ refuses to hand the package's
    // label over, and the chart falls back to a fragment of the package name.
    // A package added here and not there shows up under a made-up name.
    private val KNOWN_ENTERTAINMENT_PACKAGES = setOf(
        // Social Media & Video
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.instagram.android",
        "com.zhiliaoapp.musically",      // TikTok Global
        "com.ss.android.ugc.trill",      // TikTok Asia
        "com.twitter.android",           // X / Twitter
        "com.facebook.katana",          // Facebook
        "com.facebook.lite",
        "com.facebook.orca",            // Messenger
        "com.snapchat.android",         // Snapchat
        "com.reddit.frontpage",         // Reddit
        "tv.twitch.android.app",        // Twitch
        "com.netflix.mediaclient",      // Netflix
        "com.disney.disneyplus",        // Disney+
        "com.spotify.music",            // Spotify
        "com.pinterest",                // Pinterest
        "com.bilibili.app.in",          // Bilibili
        // Browsers (Often used as alternate entertainment escape routes)
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",  // Samsung Internet
        "com.microsoft.emmx",           // Edge
        "com.brave.browser",
        // Popular Games
        "com.mobile.legends",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.pubg.krmobile",
        "com.tencent.ig",
        "com.miHoYo.GenshinImpact",
        "com.roblox.client",
        // Games that ship without declaring a category. The declared-category
        // check in categorize() catches the ones that do say "game", but a
        // title that stays silent would otherwise fall to the unknown-app
        // default and be treated as free - and a game is the one kind of app
        // this default must never get wrong.
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.supercell.brawlstars",
        "com.supercell.hayday",
        "com.mojang.minecraftpe",
        "com.innersloth.spacemafia"
    )

    // Seen, never locked and never charged - see AppCategory.TRACKED. Same
    // manifest rule as the entertainment set above.
    private val TRACKED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    // Tools for making things - see AppCategory.WORK. Curated here and nowhere
    // else: no social app, no game, no browser may ever enter this list, because
    // the list is the cheat and the cheat is the one thing it must not have.
    private val WORK_PACKAGES = setOf(
        "com.microsoft.office.word",                    // Microsoft Word
        "com.microsoft.office.excel",                   // Microsoft Excel
        "com.microsoft.office.powerpoint",              // Microsoft PowerPoint
        "com.microsoft.office.officehubrow",            // Microsoft 365 (Office)
        "com.google.android.apps.docs.editors.docs",    // Google Docs
        "com.google.android.apps.docs.editors.sheets",  // Google Sheets
        "com.google.android.apps.docs.editors.slides",  // Google Slides
        "com.google.android.apps.docs",                 // Google Drive
        "com.google.android.keep",                      // Google Keep
        "com.samsung.android.app.notes",                // Samsung Notes
        "com.notion.id",                                // Notion
        "com.quizlet.quizletandroid",                   // Quizlet
        "com.slack",                                    // Slack
        "com.microsoft.teams",                          // Microsoft Teams
        "com.canva.editor",                             // Canva
        "com.adsk.sketchbook",                          // Sketchbook
        "com.lemon.lvoverseas",                         // CapCut
        "com.nexstreaming.app.kinemasterfree",          // KineMaster
        "com.camerasideas.instashot",                   // InShot
        "com.adobe.reader",                             // Adobe Acrobat Reader
        "cn.wps.moffice_eng",                           // WPS Office
        "com.openai.chatgpt",                           // ChatGPT
        "com.anthropic.claude"                          // Claude
    )

    // Money, and the apps a day gets built on. Blocking any of these can cost the
    // person something they cannot see - the same reason a message is never
    // charged - so they are reported in the free lens: visible in the chart,
    // never charged, never blocked.
    //
    // A missing id here is not a hole. An app that is not entertainment, is not
    // named in any curated list and declares no category is free by the fallback
    // at the bottom of categorize(), so this list only decides whether the app
    // gets a row in the free lens rather than a silent one. It grows by
    // observation, never by guesswork.
    private val MONEY_PACKAGES = setOf(
        "id.dana",                  // DANA
        "com.gojek.app",            // Gojek / GoPay
        "com.shopee.id",            // Shopee / ShopeePay
        "com.tokopedia.tkpd",       // Tokopedia
        "id.co.ovo",                // OVO
        "id.co.bri.brimo",          // BRImo (BRI)
        "com.bca.mybca",            // myBCA
        "id.bmri.livin",            // LIVIN' by Mandiri
        "src.com.bni",              // BNI Mobile Banking
        "com.bsm.bankbsm",          // BSI Mobile
        "com.btpn.dc",              // Jenius
        "com.jago.digitalBanking"   // Jago
    )

    // System components that must never be counted or blocked
    private val SYSTEM_IGNORED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.projectego.app",
        "com.humanoise.alive"
    )

    /**
     * The curated lists only, without a Context: null means "nothing here names
     * this package".
     *
     * Split out so the rule "which app is treated as what" is testable on plain
     * JVM, and so [categorize] can consult it before it starts asking the
     * platform anything.
     */
    fun curated(packageName: String?): AppCategory? {
        if (packageName.isNullOrBlank()) return AppCategory.SYSTEM_IGNORED
        if (SYSTEM_IGNORED_PACKAGES.contains(packageName)) return AppCategory.SYSTEM_IGNORED
        if (TRACKED_PACKAGES.contains(packageName)) return AppCategory.TRACKED
        if (MONEY_PACKAGES.contains(packageName)) return AppCategory.TRACKED
        if (WORK_PACKAGES.contains(packageName)) return AppCategory.WORK
        if (KNOWN_ENTERTAINMENT_PACKAGES.contains(packageName)) return AppCategory.ENTERTAINMENT
        return null
    }

    /**
     * Every package the curated lists name, paired with what its list decides.
     *
     * Exposed for the test that keeps the lists disjoint. They are drawn by hand,
     * and an id that appears twice is resolved silently by which list is
     * consulted first - which is how a bank ends up charged as entertainment
     * because somebody pasted it into the wrong set.
     */
    internal fun curatedEntries(): List<Pair<String, AppCategory>> = buildList {
        SYSTEM_IGNORED_PACKAGES.forEach { add(it to AppCategory.SYSTEM_IGNORED) }
        TRACKED_PACKAGES.forEach { add(it to AppCategory.TRACKED) }
        MONEY_PACKAGES.forEach { add(it to AppCategory.TRACKED) }
        WORK_PACKAGES.forEach { add(it to AppCategory.WORK) }
        KNOWN_ENTERTAINMENT_PACKAGES.forEach { add(it to AppCategory.ENTERTAINMENT) }
    }

    fun categorize(context: Context, packageName: String?): AppCategory {
        curated(packageName)?.let { return it }
        if (packageName.isNullOrBlank()) return AppCategory.SYSTEM_IGNORED

        // Essential: Default Phone Dialer
        val dialerIntent = Intent(Intent.ACTION_DIAL)
        val dialerResolveInfo = context.packageManager.resolveActivity(dialerIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (dialerResolveInfo?.activityInfo?.packageName == packageName) {
            return AppCategory.ESSENTIAL
        }

        // Essential: Default SMS App
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
        if (defaultSmsPackage == packageName) {
            return AppCategory.ESSENTIAL
        }

        // Essential: Default Home Launcher
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homeResolveInfo = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (homeResolveInfo?.activityInfo?.packageName == packageName) {
            return AppCategory.ESSENTIAL
        }

        // Essential: Android Settings (Needed for system control / accessibility maintenance)
        if (packageName == "com.android.settings" || packageName.startsWith("com.samsung.accessibility")) {
            return AppCategory.ESSENTIAL
        }

        // Entertainment check
        if (KNOWN_ENTERTAINMENT_PACKAGES.contains(packageName)) {
            return AppCategory.ENTERTAINMENT
        }

        // Check if categorized as Game in PackageManager
        try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    appInfo.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO
                ) {
                    return AppCategory.ENTERTAINMENT
                }
            }
        } catch (_: Exception) {
            // Package might not be installed or query failed
        }

        // Nothing named it and it declared nothing. It is left alone: free, and
        // not counted in any figure.
        //
        // This used to read "third-party and not a system app, so it is
        // entertainment" - which meant a bank, a wallet, a government app or a
        // ticket app was charged against the allowance and could be closed
        // mid-transfer at the exact moment the limit was reached. A game is now
        // caught by its declared category or by the curated list above, and a
        // wrong guess in the other direction costs only a row in the chart.
        return AppCategory.SYSTEM_IGNORED
    }
}
