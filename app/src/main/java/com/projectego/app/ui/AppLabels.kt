package com.projectego.app.ui

import android.content.Context

/**
 * Turning a package name into something a person can read.
 *
 * ALIVE declares no `<queries>` block and no `QUERY_ALL_PACKAGES`, so on
 * Android 11+ these lookups are subject to package-visibility filtering and may
 * legitimately fail - the fallback below is expected to be the common path, not
 * the rare one. That is why the chart never presents a fallback as an app's
 * name; it says so in `chart_labels_note` instead.
 */
object AppLabels {

    fun labelFor(context: Context, packageName: String): String {
        val resolved = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(context.packageManager).toString().trim()
        }.getOrNull()

        return if (!resolved.isNullOrBlank() && resolved != packageName) resolved
        else fallbackName(packageName)
    }

    /**
     * The last meaningful segment of a package name, humanised.
     *
     * Kept free of `Context` so it can be tested on plain JVM.
     */
    fun fallbackName(packageName: String): String {
        val segments = packageName.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) return packageName

        // Trailing segments like "app" or "android" carry no identity; step back
        // one when the last one is in that set. "com.facebook.lite" -> "Lite" is
        // worse than useless, so the rule keeps the first segment that means
        // something, and stops at two steps so odd packages cannot loop.
        for (offset in 0 until 3) {
            val index = segments.lastIndex - offset
            if (index < 0) break
            val candidate = segments[index]
            if (candidate.lowercase() !in UNMEANINGFUL_SEGMENTS) return humanise(candidate)
        }
        return humanise(segments.last())
    }

    private fun humanise(segment: String): String {
        val spaced = segment.replace('_', ' ').trim()
        return if (spaced.isEmpty()) spaced
        else spaced.replaceFirst(spaced[0], spaced[0].uppercaseChar())
    }

    private val UNMEANINGFUL_SEGMENTS = setOf(
        "app", "apps", "android", "google", "mobile", "lite", "ugc",
        "com", "org", "net", "io", "www", "client", "service"
    )
}
