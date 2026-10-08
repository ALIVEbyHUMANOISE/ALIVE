package com.projectego.app.domain

/**
 * The check a quest ALIVE cannot watch has to pass.
 *
 * A walk, a cleaned room, a chapter of a paper book - no app is open during
 * those, and ALIVE does not pretend otherwise. The one fact the device can
 * establish by itself is that the time the person committed to has actually
 * gone by on the clock. Everything beyond that is their report, and the copy
 * says so out loud rather than dressing a self-report up as a check.
 *
 * Pure: timestamps in, decision out. It keeps no clock of its own, so the rule
 * is testable and cannot drift between the screen and the verdict - both ask
 * this object the same question.
 */
object PhysicalQuestVerifier {

    sealed class Result {
        /** Nothing was declared yet: there is nothing to decide, and no verdict is owed. */
        object Waiting : Result()

        /** Declared, and the committed time had genuinely gone by. */
        data class Passed(val reason: String) : Result()

        /** Declared before the committed time had gone by. */
        data class Failed(val reason: String) : Result()
    }

    /**
     * Minutes a person still has to wait before saying they are done, or 0 when
     * they may say it now.
     *
     * An attempt that never started has no clock to wait on; it reports 0 and the
     * verdict then comes from [verify], which fails it for the missing start.
     */
    fun remainingMinutes(startedAt: Long?, now: Long, committedMinutes: Long): Long {
        if (startedAt == null || committedMinutes <= 0L) return 0L
        val elapsed = (now - startedAt) / MILLIS_PER_MINUTE
        return (committedMinutes - elapsed).coerceAtLeast(0L)
    }

    /**
     * The verdict on a declaration.
     *
     * Measured from the attempt's own start to [declaredAt] - not from when the
     * app next happens to be opened, which is why the end timestamp is taken at
     * the moment of declaring rather than at evaluation.
     */
    fun verify(startedAt: Long?, declaredAt: Long, committedMinutes: Long): Result {
        if (startedAt == null) {
            return Result.Failed("No start was recorded, so the committed time cannot be measured")
        }
        val elapsed = (declaredAt - startedAt) / MILLIS_PER_MINUTE
        return if (elapsed < committedMinutes) {
            Result.Failed("Declared done after ${elapsed} of ${committedMinutes} committed minutes")
        } else {
            Result.Passed("Self-reported after the committed $committedMinutes minutes")
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
