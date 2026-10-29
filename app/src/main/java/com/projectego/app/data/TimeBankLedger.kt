package com.projectego.app.data

/**
 * The single in-process transaction boundary for Time Bank state.
 *
 * The stores are separate SharedPreferences files, so no editor can make a
 * debit and its claimed-minute credit one disk write. What this boundary does
 * guarantee is that no two threads or repository instances can interleave a
 * read-modify-write of the same balance, claimed-minute or rollover keys: every
 * writer takes this monitor, and a claim holds it across its whole
 * decide -> debit -> credit unit.
 *
 * It is deliberately a process-wide object rather than `@Synchronized` methods:
 * that annotation protects one object, while the app builds several
 * repositories over the same files (activity, accessibility service, alarm
 * receiver, pill worker). It is never held across a UsageStats query - the
 * claim releases it before asking the platform how much was used.
 */
internal object TimeBankLedger {

    fun <T> run(block: () -> T): T = synchronized(this) { block() }
}
