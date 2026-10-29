package com.projectego.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.model.DayType
import com.projectego.app.model.TimeBank
import com.projectego.app.model.TimeStatus
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The claim's transaction order, as an executable contract.
 *
 * The bank is debited before the day is credited (the crash-safe direction),
 * the verification read - and only it - decides the lock, and a claim started
 * after midnight is judged on the new day before any minute is spent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntertainmentClaimCoordinatorTest {

    private lateinit var context: Context
    private lateinit var lockState: LockStateManager
    private lateinit var timeBank: TimeBankRepository

    private val yesterday get() = LocalDate.now().minusDays(1).toString()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        statePrefs().edit().clear().commit()
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("project_ego_character_growth", Context.MODE_PRIVATE)
            .edit().clear().commit()
        lockState = LockStateManager(context)
        timeBank = TimeBankRepository(context)
        // First-run initialisation is part of the store's own life, not of a
        // claim; get it out of the way so each test starts on an ordinary day.
        lockState.checkAndPerformDailyReset()
    }

    @Test
    fun `a claim is debited and credited exactly once`() {
        timeBank.addEarnedMinutes(50L)
        var reads = 0
        val coordinator = coordinator {
            reads++
            UsageRead.Success(report(used = 10L, limit = 140L))
        }

        assertEquals(EntertainmentClaimPolicy.Decision.Claim(20L), coordinator.claim())
        assertEquals(30L, timeBank.getTimeBank().availableMinutes)
        assertEquals(20L, lockState.claimedMinutesToday())
        assertEquals(140L, lockState.getDailyLimitMinutes())

        // The second press of the same button finds the day's cap gone.
        assertEquals(EntertainmentClaimPolicy.Decision.DailyCapReached, coordinator.claim())
        assertEquals(30L, timeBank.getTimeBank().availableMinutes)
        assertEquals(20L, lockState.claimedMinutesToday())
        assertEquals("only the executed claim read usage", 1, reads)
    }

    @Test
    fun `a failed verification leaves the lock exactly where it was`() {
        timeBank.addEarnedMinutes(50L)
        lockState.setLocked(true)

        val decision = coordinator { UsageRead.QueryFailed }.claim()

        assertEquals(EntertainmentClaimPolicy.Decision.Claim(20L), decision)
        assertEquals("the claim itself is still granted", 20L, lockState.claimedMinutesToday())
        assertTrue("earned is not verified under the limit", lockState.isLocked())
    }

    @Test
    fun `a verified under-limit claim clears the lock`() {
        timeBank.addEarnedMinutes(50L)
        lockState.setLocked(true)

        coordinator { UsageRead.Success(report(used = 100L, limit = 140L)) }.claim()

        assertFalse(lockState.isLocked())
    }

    @Test
    fun `a verified at-or-over-limit claim keeps the lock`() {
        timeBank.addEarnedMinutes(50L)
        lockState.setLocked(true)

        coordinator { UsageRead.Success(report(used = 140L, limit = 140L)) }.claim()

        assertTrue(lockState.isLocked())
    }

    @Test
    fun `a claim started after midnight judges the new day first`() {
        timeBank.addEarnedMinutes(50L)
        statePrefs().edit()
            .putString("key_last_tracked_date", yesterday)
            .putString("key_day_type", DayType.NORMAL_DAY.name)
            .putBoolean("key_quest_rule_announced", true)
            .putInt("key_current_streak", 4)
            .putBoolean("key_is_locked", false)
            .commit()
        var reads = 0

        val decision = coordinator {
            reads++
            UsageRead.Success(report(used = 0L, limit = 0L))
        }.claim()

        // Yesterday earned nothing, so today is a recovery day: no allowance to
        // buy, no verification read, no minute spent from the bank.
        assertEquals(EntertainmentClaimPolicy.Decision.RecoveryDay, decision)
        assertEquals("no debit on a refused day", 50L, timeBank.getTimeBank().availableMinutes)
        assertEquals(0L, lockState.claimedMinutesToday())
        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, lockState.getDayType())
        assertTrue(lockState.isLocked())
        assertEquals("a refusal reads nothing", 0, reads)
    }

    @Test
    fun `two claims racing cannot both take the day's cap`() {
        timeBank.addEarnedMinutes(50L)
        val barrier = CyclicBarrier(2)
        val decisions = Collections.synchronizedList(
            mutableListOf<EntertainmentClaimPolicy.Decision>()
        )
        // Separate managers per thread on purpose: the boundary has to be the
        // ledger, not one object's monitor.
        val threads = (0 until 2).map {
            Thread {
                val coordinator = EntertainmentClaimCoordinator(
                    LockStateManager(context),
                    TimeBankRepository(context)
                ) { UsageRead.QueryFailed }
                barrier.await(2, TimeUnit.SECONDS)
                decisions += coordinator.claim()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        assertFalse("a claim hung on the ledger", threads.any { it.isAlive })
        assertEquals(2, decisions.size)
        assertEquals(
            "only one claim may pass the cap",
            1,
            decisions.count { it is EntertainmentClaimPolicy.Decision.Claim }
        )
        assertEquals(
            1,
            decisions.count { it == EntertainmentClaimPolicy.Decision.DailyCapReached }
        )
        assertEquals(20L, LockStateManager(context).claimedMinutesToday())
        assertEquals(30L, TimeBankRepository(context).getTimeBank().availableMinutes)
    }

    private fun coordinator(read: () -> UsageRead) =
        EntertainmentClaimCoordinator(lockState, timeBank, read)

    private fun statePrefs() =
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE)

    private fun report(used: Long, limit: Long) = UsageReport(
        totalEntertainmentMinutes = used,
        totalEntertainmentMillis = used * 60_000L,
        unblockedMinutes = 0L,
        totalScreenMinutes = used,
        limitMinutes = limit,
        isLimitExceeded = used >= limit,
        remainingMinutes = (limit - used).coerceAtLeast(0L),
        criticalZoneTier = if (used >= limit) 5 else 0,
        timeStatus = if (used >= limit) TimeStatus.LOCKED else TimeStatus.ACTIVE,
        dayType = DayType.NORMAL_DAY,
        currentStreak = 0,
        timeBank = TimeBank(),
        entertainmentAppDetails = emptyList(),
        unblockedAppDetails = emptyList()
    )
}
