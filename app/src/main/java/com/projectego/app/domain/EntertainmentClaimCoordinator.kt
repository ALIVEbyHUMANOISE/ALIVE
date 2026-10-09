package com.projectego.app.domain

import com.projectego.app.data.LockStateManager
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
    private val usageTracker: UsageTracker
) {

    /**
     * One claim, capped by policy.
     *
     * @return the decision that was executed -
     *         [EntertainmentClaimPolicy.Decision.Claim] only when the minutes
     *         were actually debited and credited.
     */
    fun claim(): EntertainmentClaimPolicy.Decision {
        val decision = EntertainmentClaimPolicy.decide(
            dayType = lockStateManager.getDayType(),
            claimedToday = lockStateManager.claimedMinutesToday(),
            availableMinutes = timeBankRepository.getTimeBank().availableMinutes
        )
        if (decision !is EntertainmentClaimPolicy.Decision.Claim) return decision

        if (!timeBankRepository.spendMinutes(decision.minutes)) {
            // The balance moved between the decision and the debit. Nothing was
            // granted, so the honest report is that the bank had nothing.
            return EntertainmentClaimPolicy.Decision.BankEmpty
        }

        lockStateManager.addClaimedMinutesToday(decision.minutes)

        val report = usageTracker.calculateTodayEntertainmentUsage()
        if (report.totalEntertainmentMinutes < report.limitMinutes) {
            lockStateManager.setLocked(false)
        }

        return decision
    }
}
