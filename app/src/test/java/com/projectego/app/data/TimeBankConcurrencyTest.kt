package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ledger boundary, exercised by real threads over separate repository
 * instances - the shape every caller has in production (activity, service,
 * alarm receiver, pill worker).
 *
 * These are invariant checks, not a reproduction of a live failure: today every
 * balance writer happens to run on the main looper, so the pre-fix code could
 * only lose or double an update if that changed. The barrier makes both threads
 * arrive together; with the shared monitor the outcome is fixed regardless of
 * how the scheduler interleaves them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimeBankConcurrencyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    @Test
    fun `two repository instances cannot spend the same minutes`() {
        TimeBankRepository(context).addEarnedMinutes(100L)
        val barrier = CyclicBarrier(2)
        val outcomes = Collections.synchronizedList(mutableListOf<Boolean>())
        val threads = (0 until 2).map {
            Thread {
                val repository = TimeBankRepository(context)
                barrier.await(2, TimeUnit.SECONDS)
                outcomes += repository.spendMinutes(100L)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        assertFalse("a writer hung on the ledger", threads.any { it.isAlive })
        assertEquals("exactly one writer may take the balance", 1, outcomes.count { it })
        val bank = TimeBankRepository(context).getTimeBank()
        assertEquals("the debit is not doubled", 100L, bank.spentMinutes)
        assertEquals(0L, bank.availableMinutes)
    }

    @Test
    fun `two repository instances earning at once lose nothing`() {
        val barrier = CyclicBarrier(2)
        val threads = (0 until 2).map {
            Thread {
                val repository = TimeBankRepository(context)
                barrier.await(2, TimeUnit.SECONDS)
                repository.addEarnedMinutes(10L)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        assertFalse(threads.any { it.isAlive })
        assertEquals(
            "both rewards must survive one another",
            20L,
            TimeBankRepository(context).getTimeBank().earnedMinutes
        )
    }

    private fun prefs() =
        context.getSharedPreferences("project_ego_time_bank", Context.MODE_PRIVATE)
}
