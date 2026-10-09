package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * A step snapshot for one physical attempt.
 *
 * A null field means "measured and unavailable" - which is a recorded fact,
 * distinct from a snapshot that was never written at all.
 */
data class PhysicalStepSnapshot(
    val startSteps: Int?,
    val declareSteps: Int?
)

/**
 * Stores the physical evidence gathered around one quest attempt, keyed by
 * attemptId in its own preferences file.
 *
 * Deliberately separate from QuestRepository's serialized attempt blob: the
 * evidence is transient (it must die with the attempt) and the blob is a
 * field-counted format whose only job is the attempt's own identity.
 */
class QuestEvidenceRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("project_ego_quest_evidence", Context.MODE_PRIVATE)

    @Synchronized
    fun recordStepsAtStart(attemptId: String, steps: Int?) {
        val current = snapshotFor(attemptId)
        prefs.edit()
            .putString(
                attemptId,
                serialize(
                    PhysicalStepSnapshot(
                        startSteps = steps,
                        declareSteps = current?.declareSteps
                    )
                )
            )
            .apply()
    }

    @Synchronized
    fun recordStepsAtDeclare(attemptId: String, steps: Int?) {
        val current = snapshotFor(attemptId)
        prefs.edit()
            .putString(
                attemptId,
                serialize(
                    PhysicalStepSnapshot(
                        startSteps = current?.startSteps,
                        declareSteps = steps
                    )
                )
            )
            .apply()
    }

    /** The recorded snapshot, or null when nothing was ever recorded for this attempt. */
    @Synchronized
    fun snapshotFor(attemptId: String): PhysicalStepSnapshot? {
        val raw = prefs.getString(attemptId, null) ?: return null
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != FIELD_COUNT) return null
        return try {
            PhysicalStepSnapshot(
                startSteps = parseField(parts[0]),
                declareSteps = parseField(parts[1])
            )
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear(attemptId: String) {
        prefs.edit().remove(attemptId).apply()
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun serialize(snapshot: PhysicalStepSnapshot): String {
        val start = snapshot.startSteps?.toString() ?: UNAVAILABLE_MARKER
        val declare = snapshot.declareSteps?.toString() ?: UNAVAILABLE_MARKER
        return "$start$FIELD_SEPARATOR$declare"
    }

    private fun parseField(raw: String): Int? = raw.takeIf { it != UNAVAILABLE_MARKER }?.toInt()

    companion object {
        private const val FIELD_SEPARATOR = "\u001F"
        private const val FIELD_COUNT = 2
        private const val UNAVAILABLE_MARKER = "-"
    }
}
