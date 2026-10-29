package com.projectego.app.domain

import com.projectego.app.data.LockStateManager
import com.projectego.app.data.TimeBankLedger
import com.projectego.app.data.TimeBankRepository

/**
 * Executes an [EntertainmentClaimPolicy] decision.
 *
 * The ordering is the design: the bank is debited *before* the day's claimed
 * minutes are recorded. A crash between the two costs the person minutes they
 * chose to spend; the other order would hand out allowance nobody paid for.
 *
 * The lock comes off only when a fresh usage read says the raised limit covers
 * what was actually used - a claim that still leaves the day over its limit
 * must not unlock it. That unlocking lives here, not in `UsageTracker`, on
 * purpose: the tracker's latch is what keeps a flaky usage read from opening a
 * lock the day already hit, so only a deliberate act may clear it.
 */
class EntertainmentClaimCoordinator(
    private val lockStateManager: LockStateManager,
    private val timeBankRepository: TimeBankRepository,
    private val readUsage: () -> UsageRead
) {

    /**
     * One claim, capped by policy.
     *
     * @return the decision that was executed -
     *         [EntertainmentClaimPolicy.Decision.Claim] only when the minutes
     *         were actually debited and credited.
     */
    fun claim(): EntertainmentClaimPolicy.Decision {
        // Decision, debit and credit are one unit on the ledger: two claims can
        // never both read the old cap or the old balance, so the same minutes
        // cannot be sold twice and the day's cap cannot be overshot. The slow
        // part - asking the platform what was used - happens after this block,
        // never under the lock.
        val decision = TimeBankLedger.run {
            // The claim card can sit on screen across midnight. Judge the day
            // the claim is actually made on before pricing it: yesterday's
            // recovery-day refusal must not be skipped, and yesterday's
            // allowance must not be bought for a day that has none.
            lockStateManager.checkAndPerformDailyReset()

            val priced = EntertainmentClaimPolicy.decide(
                dayType = lockStateManager.getDayType(),
                claimedToday = lockStateManager.claimedMinutesToday(),
                availableMinutes = timeBankRepository.getTimeBank().availableMinutes
            )
            if (priced !is EntertainmentClaimPolicy.Decision.Claim) return@run priced

            if (!timeBankRepository.spendMinutes(priced.minutes)) {
                // The balance moved between the decision and the debit. Nothing
                // was granted, so the honest report is that the bank had
                // nothing.
                return@run EntertainmentClaimPolicy.Decision.BankEmpty
            }

            lockStateManager.addClaimedMinutesToday(priced.minutes)
            priced
        }
        if (decision !is EntertainmentClaimPolicy.Decision.Claim) return decision

        // The claim is the authority for the minutes it just added, so only the
        // verification read may decide the lock. When that read cannot answer,
        // the lock stays exactly as it was: "earned" is not "verified under the
        // limit". Recovery is not lost - the next successful report clears the
        // latch as soon as the raised limit covers the day.
        val read = readUsage()
        if (UsageSourcePolicy.claimClearsLock(read)) {
            lockStateManager.setLocked(false)
        }

        return decision
    }
}
