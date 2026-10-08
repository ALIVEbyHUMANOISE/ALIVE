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
        "com.roblox.client"
    )

    // Counted, never locked - see AppCategory.TRACKED. Same manifest rule as
    // the entertainment set above.
    private val TRACKED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    // System components that must never be counted or blocked
    private val SYSTEM_IGNORED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.projectego.app"
    )

    fun categorize(context: Context, packageName: String?): AppCategory {
        if (packageName.isNullOrBlank()) return AppCategory.SYSTEM_IGNORED
        if (SYSTEM_IGNORED_PACKAGES.contains(packageName)) return AppCategory.SYSTEM_IGNORED

        if (TRACKED_PACKAGES.contains(packageName)) return AppCategory.TRACKED

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

        // Default heuristic: If it is a third-party non-system app, treat as entertainment screen time
        // Unless it's explicitly identified as system app.
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (!isSystemApp && !isUpdatedSystemApp) {
                return AppCategory.ENTERTAINMENT
            }
        } catch (_: Exception) {
            // Ignore
        }

        return AppCategory.SYSTEM_IGNORED
    }
}
