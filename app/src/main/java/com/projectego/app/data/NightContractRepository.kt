package com.projectego.app.data

import android.content.Context
import com.projectego.app.domain.NightContract

/**
 * Where last night's promise is kept, and what it decides.
 *
 * A contract is one number, one optional quest and the night it was written on.
 * It is read by the limit every time the day is measured, so it has to be plain
 * storage: no clock of its own, no side effects, and the rules it obeys live in
 * [NightContract] where they can be tested without a device.
 */
class NightContractRepository(context: Context) {

    data class Contract(
        val allowanceMinutes: Long,
        val questId: String?,
        val nightKey: String
    )

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** What was written, whether or not it governs today. */
    fun stored(): Contract? {
        val nightKey = prefs.getString(KEY_NIGHT, null) ?: return null
        if (!prefs.contains(KEY_ALLOWANCE)) return null
        return Contract(
            allowanceMinutes = prefs.getLong(KEY_ALLOWANCE, 0L),
            questId = prefs.getString(KEY_QUEST, null),
            nightKey = nightKey
        )
    }

    /** The contract that governs [todayKey], or null while it is still for tomorrow. */
    fun inForceOn(todayKey: String): Contract? =
        stored()?.takeIf { NightContract.isInForce(it.nightKey, todayKey) }

    fun save(allowanceMinutes: Long, questId: String?, nowMillis: Long) {
        prefs.edit()
            .putLong(KEY_ALLOWANCE, allowanceMinutes)
            .putString(KEY_QUEST, questId)
            .putString(KEY_NIGHT, NightContract.nightKeyOf(nowMillis))
            .apply()
    }

    private companion object {
        const val PREFS_FILE = "project_ego_night_contract"
        const val KEY_ALLOWANCE = "key_allowance_minutes"
        const val KEY_QUEST = "key_quest_id"
        const val KEY_NIGHT = "key_night"
    }
}
