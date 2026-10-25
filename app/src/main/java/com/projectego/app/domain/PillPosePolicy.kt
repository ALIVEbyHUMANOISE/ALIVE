package com.projectego.app.domain

import com.projectego.app.model.DayType
import com.projectego.app.model.TimeStatus

/**
 * Which ALIVE pose the expanded pill should wear, from states the system can
 * actually prove.
 *
 * The policy knows package identity, not activity: TikTok in front does not
 * mean the person is watching a video, so VIDEO and SOCIAL share the same
 * "holding a phone" pose in the UI. Nothing here reads app content, and the
 * compact pill never grows a character.
 *
 * Priority: recovery first, then a reached limit, then the critical zone, then
 * the app's own context, then the neutral pose. Recovery is deliberately ahead
 * of limit because a recovery day's report is *constructed* as exceeded - the
 * day was closed on purpose, and wearing the limit face would call a rest a
 * failure. The rest of the order is enforcement-first: an app pose must never
 * outrank a state the guard is acting on.
 */
enum class PillPose {
    IDLE,
    VIDEO,
    SOCIAL,
    GAMING,
    BROWSING,
    APPROACHING,
    LIMIT,
    RECOVERY
}

object PillPosePolicy {

    private val VIDEO = setOf(
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "tv.twitch.android.app",
        "com.bilibili.app.in"
    )

    private val SOCIAL = setOf(
        "com.instagram.android",
        "com.twitter.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest"
    )

    private val BROWSING = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.brave.browser"
    )

    private val GAMING = setOf(
        "com.mobile.legends",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.pubg.krmobile",
        "com.tencent.ig",
        "com.miHoYo.GenshinImpact",
        "com.roblox.client",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.supercell.brawlstars",
        "com.supercell.hayday",
        "com.mojang.minecraftpe",
        "com.innersloth.spacemafia"
    )

    fun poseFor(report: UsageReport?, locked: Boolean, packageName: String?): PillPose {
        val recovery = report != null && (
            report.dayType == DayType.ZERO_ENTERTAINMENT_DAY ||
                report.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY
            )
        if (recovery) return PillPose.RECOVERY

        val limit = locked ||
            report?.isLimitExceeded == true ||
            report?.timeStatus == TimeStatus.LOCKED
        if (limit) return PillPose.LIMIT

        val approaching = report != null && (
            report.criticalZoneTier >= 1 || report.timeStatus == TimeStatus.CRITICAL
            )
        if (approaching) return PillPose.APPROACHING

        return contextFor(packageName)
    }

    /** Only the app's identity is known, so only a category-level pose is chosen. */
    fun contextFor(packageName: String?): PillPose = when {
        packageName == null -> PillPose.IDLE
        packageName in VIDEO -> PillPose.VIDEO
        packageName in SOCIAL -> PillPose.SOCIAL
        packageName in BROWSING -> PillPose.BROWSING
        packageName in GAMING -> PillPose.GAMING
        else -> PillPose.IDLE
    }

    /**
     * Every mapped package must also be entertainment to the classifier that
     * decides what the pill watches. Exposed so a future edit cannot give a
     * bank a gaming pose.
     */
    internal fun contextEntries(): List<Pair<String, PillPose>> = buildList {
        VIDEO.forEach { add(it to PillPose.VIDEO) }
        SOCIAL.forEach { add(it to PillPose.SOCIAL) }
        BROWSING.forEach { add(it to PillPose.BROWSING) }
        GAMING.forEach { add(it to PillPose.GAMING) }
    }
}
