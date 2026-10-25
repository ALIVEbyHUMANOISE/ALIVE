package com.projectego.app.domain

/**
 * How many times one app was actually entered today.
 *
 * Counting raw resume events overcounts: one visit to an app with several
 * activities is several resumes, so the pill's "3× today" could describe a
 * single sitting. A visit begins when a resume names a different package than
 * the last resume did, and a screen-off ends whatever visit was running - the
 * same app opened again after the screen was off is a new visit.
 *
 * Pure, so the rule is testable without the platform's event stream.
 */
object ForegroundVisits {

    enum class Kind { RESUMED, SCREEN_OFF }

    data class Event(val kind: Kind, val packageName: String? = null)

    fun count(
        events: List<Event>,
        packageName: String,
        ignoredPackages: Set<String> = emptySet()
    ): Int {
        var last: String? = null
        var visits = 0

        events.forEach { event ->
            when (event.kind) {
                Kind.SCREEN_OFF -> last = null
                Kind.RESUMED -> {
                    val resumed = event.packageName ?: return@forEach
                    // The shade over the app is not a visit anywhere, and it must
                    // not split the visit that was already running.
                    if (resumed in ignoredPackages) return@forEach
                    if (resumed == last) return@forEach
                    if (resumed == packageName) visits += 1
                    last = resumed
                }
            }
        }
        return visits
    }
}