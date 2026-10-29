package com.projectego.app.domain

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.FocusWindow
import com.projectego.app.data.FocusWindowCodec
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.TimeBankLedger
import com.projectego.app.data.UsageHistoryRepository
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.TimeStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAppOpsManager
import org.robolectric.shadows.ShadowUsageStatsManager
import java.time.LocalDate
import java.util.Calendar
import java.util.SimpleTimeZone
import java.util.TimeZone

/** Real reports and stores; only the platform query controls the interleaving. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [UsageSnapshotTest.InterleavedUsageStats::class])
class UsageSnapshotTest {
    private lateinit var context: Context
    private lateinit var usage: InterleavedUsageStats
    private lateinit var originalTimeZone: TimeZone
    private lateinit var today: String
    private lateinit var yesterday: String
    private var dayStart = 0L

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        // App code uses the real wall clock. Place it at local noon so every
        // event is safely in the past, then shift the zone to test day changes.
        val noonOffset = (12 * 3_600_000L - System.currentTimeMillis() % DAY_MILLIS).toInt()
        TimeZone.setDefault(SimpleTimeZone(noonOffset, "SnapshotNoon"))
        today = LockStateManager.dayKeyOf(System.currentTimeMillis())
        yesterday = LocalDate.parse(today).minusDays(1).toString()
        dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        context = ApplicationProvider.getApplicationContext()
        PREFS.forEach { prefs(it).edit().clear().putString("snapshot_sentinel", "keep").commit() }
        prefs(STATE).edit()
            .putString("key_last_tracked_date", today)
            .putString("key_day_type", DayType.NORMAL_DAY.name)
            .putBoolean("key_is_locked", false)
            .putBoolean("key_quest_rule_announced", true)
            .putInt("key_current_streak", 4)
            .putInt("key_last_warning_tier", 3)
            .commit()
        prefs("project_ego_time_bank").edit()
            .putLong("key_earned_minutes", 90L)
            .putLong("key_spent_minutes", 10L)
            .commit()
        UsageHistoryRepository(context).recordDay("2026-01-01", 60_000L, 120L, emptyMap(), 60_000L)
        grantUsageAccess(true)
        usage = Shadow.extract(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
        usage.duringQuery = null
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `tracking day reads the persisted key without initializing or falling back to today`() {
        val manager = LockStateManager(context)
        val initialized = allPrefs()
        assertTrue(manager.isTrackingDay(today))
        assertFalse(manager.isTrackingDay(yesterday))
        assertEquals(initialized, allPrefs())
        prefs(STATE).edit().remove("key_last_tracked_date").commit()
        val before = allPrefs()

        assertFalse(manager.isTrackingDay(today))
        assertFalse(manager.isTrackingDay(""))
        assertEquals(before, allPrefs())
    }

    @Test
    fun `normal snapshot keeps all stores unchanged and matches authoritative charge and allowance`() {
        addSpan(YOUTUBE, 60, 90)
        addSpan(WHATSAPP, 100, 110)
        prefs("project_ego_night_contract").edit()
            .putString("key_night", yesterday)
            .putLong("key_allowance_minutes", 60L)
            .commit()
        prefs(STATE).edit().putString("key_claimed_day", today)
            .putLong("key_claimed_minutes", 10L).commit()
        val before = allPrefs()

        val report = success(snapshot())

        assertEquals(30L, report.totalEntertainmentMinutes)
        assertEquals(1_800_000L, report.totalEntertainmentMillis)
        assertEquals(10L, report.unblockedMinutes)
        assertEquals(40L, report.totalScreenMinutes)
        assertEquals(70L, report.limitMinutes)
        assertEquals(40L, report.remainingMinutes)
        assertEquals(4, report.currentStreak)
        assertEquals(90L, report.timeBank.earnedMinutes)
        assertEquals(10L, report.timeBank.spentMinutes)
        assertEquals(listOf(YOUTUBE to 30L), report.entertainmentAppDetails)
        assertEquals(listOf(WHATSAPP to 10L), report.unblockedAppDetails)
        assertEquals(before, allPrefs())
        assertEquals(report, success(UsageTracker(context).readTodayEntertainmentUsage()))
    }

    @Test
    fun `locked snapshot cannot clear the latch with a low usage report`() {
        prefs(STATE).edit().putBoolean("key_is_locked", true).commit()
        addSpan(YOUTUBE, 60, 61)
        val before = allPrefs()

        val report = success(snapshot())

        assertEquals(1L, report.totalEntertainmentMinutes)
        assertTrue(report.isLimitExceeded)
        assertEquals(TimeStatus.LOCKED, report.timeStatus)
        assertTrue(LockStateManager(context).isLocked())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `zero day snapshot reports zero allowance without writing even an absent latch`() {
        prefs(STATE).edit().putString("key_day_type", DayType.ZERO_ENTERTAINMENT_DAY.name)
            .remove("key_is_locked").commit()
        val before = allPrefs()

        val report = success(snapshot())

        assertEquals(0L, report.limitMinutes)
        assertEquals(0L, report.remainingMinutes)
        assertTrue(report.isLimitExceeded)
        assertEquals(TimeStatus.ZERO_ENTERTAINMENT_DAY, report.timeStatus)
        assertEquals(5, report.criticalZoneTier)
        assertFalse(LockStateManager(context).isLocked())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `over limit snapshot reports the limit without latching or recording history`() {
        addSpan(YOUTUBE, 60, 190)
        val before = allPrefs()

        val report = success(snapshot())

        assertEquals(130L, report.totalEntertainmentMinutes)
        assertTrue(report.isLimitExceeded)
        assertEquals(5, report.criticalZoneTier)
        assertFalse(LockStateManager(context).isLocked())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `focus exemption matches authoritative math without expiring a session or awarding growth`() {
        addSpan(YOUTUBE, 60, 120)
        addSpan(WHATSAPP, 130, 140)
        // Persisted legacy approval remains exempt after its session ends.
        val window = FocusWindow(atMinute(70), atMinute(90), FocusActivityType.STUDY, setOf(YOUTUBE))
        prefs("project_ego_focus_session").edit()
            .putString("key_windows_day", today)
            .putString("key_windows", FocusWindowCodec.encode(listOf(window)))
            .putBoolean("key_is_active", true)
            .putString("key_activity_type", FocusActivityType.STUDY.name)
            .putLong("key_start_timestamp", window.start)
            .putLong("key_end_timestamp", window.end)
            .commit()
        val before = allPrefs()

        val report = success(snapshot())

        assertEquals(40L, report.totalEntertainmentMinutes)
        assertEquals(2_400_000L, report.totalEntertainmentMillis)
        assertEquals(30L, report.unblockedMinutes)
        assertEquals(70L, report.totalScreenMinutes)
        assertEquals(listOf(YOUTUBE to 40L), report.entertainmentAppDetails)
        assertEquals(listOf(YOUTUBE to 20L, WHATSAPP to 10L), report.unblockedAppDetails)
        assertEquals(before, allPrefs())
        assertEquals(report, success(UsageTracker(context).readTodayEntertainmentUsage()))
    }

    @Test
    fun `missing tracked day is pending without initializing or querying`() {
        prefs(STATE).edit().remove("key_last_tracked_date").commit()
        usage.duringQuery = { throw AssertionError("pending day must not query") }
        val before = allPrefs()

        assertEquals(UsageRead.ProcessingFailed, snapshot())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `pending midnight does not borrow yesterday allowance or reset streak and history`() {
        prefs(STATE).edit().putString("key_last_tracked_date", yesterday).commit()
        usage.duringQuery = { throw AssertionError("pending rollover must not query") }
        val before = allPrefs()

        assertEquals(UsageRead.ProcessingFailed, snapshot())
        assertEquals(before, allPrefs())
        assertEquals(4, LockStateManager(context).getCurrentStreak())
    }

    @Test
    fun `permission failure leaves every store untouched`() {
        prefs(STATE).edit().putBoolean("key_is_locked", true).commit()
        grantUsageAccess(false)
        usage.duringQuery = { throw AssertionError("missing permission must not query") }
        val before = allPrefs()

        assertEquals(UsageRead.PermissionUnavailable, snapshot())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `permission failure on a pending day cannot perform rollover`() {
        prefs(STATE).edit().putString("key_last_tracked_date", yesterday).commit()
        grantUsageAccess(false)
        val before = allPrefs()

        assertFalse(snapshot().isAvailable)
        assertEquals(before, allPrefs())
    }

    @Test
    fun `permission revoked inside the query is not an empty day or a store write`() {
        usage.duringQuery = { throw SecurityException("grant revoked") }
        val before = allPrefs()

        assertEquals(UsageRead.PermissionUnavailable, snapshot())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `platform query failure leaves reset latch and history untouched`() {
        usage.duringQuery = { throw IllegalStateException("platform unavailable") }
        val before = allPrefs()

        assertEquals(UsageRead.QueryFailed, snapshot())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `a stale low read cannot unlock a latch set while the query runs`() {
        addSpan(YOUTUBE, 60, 61)
        var afterEnforcement: Map<String, Map<String, *>>? = null
        usage.duringQuery = {
            assertFalse("query I/O must not hold the ledger", Thread.holdsLock(TimeBankLedger))
            LockStateManager(context).setLocked(true)
            afterEnforcement = allPrefs()
        }

        val report = success(snapshot())

        assertNotNull(afterEnforcement)
        assertEquals(1L, report.totalEntertainmentMinutes)
        assertEquals(TimeStatus.LOCKED, report.timeStatus)
        assertTrue(LockStateManager(context).isLocked())
        assertEquals(afterEnforcement, allPrefs())
    }

    @Test
    fun `tracked day changed during query is refused without resetting it back`() {
        usage.duringQuery = {
            prefs(STATE).edit().putString("key_last_tracked_date", yesterday).commit()
        }
        val expected = allPrefs().toMutableMap().apply {
            put(STATE, getValue(STATE).toMutableMap().apply { put("key_last_tracked_date", yesterday) })
        }

        assertEquals(UsageRead.ProcessingFailed, snapshot())
        assertEquals(expected, allPrefs())
    }

    @Test
    fun `local day changing during query is pending even if tracked day did not change`() {
        usage.duringQuery = { moveToNextLocalDay() }
        val before = allPrefs()

        assertEquals(UsageRead.ProcessingFailed, snapshot())
        assertEquals(before, allPrefs())
    }

    @Test
    fun `yesterday query is refused even when enforcement already rolled the state forward`() {
        var afterEnforcement: Map<String, Map<String, *>>? = null
        usage.duringQuery = {
            moveToNextLocalDay()
            LockStateManager(context).checkAndPerformDailyReset()
            afterEnforcement = allPrefs()
        }

        assertEquals(UsageRead.ProcessingFailed, snapshot())
        assertNotNull(afterEnforcement)
        assertEquals(afterEnforcement, allPrefs())
    }

    @Test
    fun `authoritative read still judges a failed yesterday resets streak and latches zero day`() {
        prefs(STATE).edit().putString("key_last_tracked_date", yesterday).commit()

        val report = success(UsageTracker(context).readTodayEntertainmentUsage())

        assertEquals(today, prefs(STATE).getString("key_last_tracked_date", null))
        assertEquals(DayType.ZERO_ENTERTAINMENT_DAY, report.dayType)
        assertEquals(0, report.currentStreak)
        assertTrue(LockStateManager(context).isLocked())
        assertNotNull(UsageHistoryRepository(context).day(today))
    }

    @Test
    fun `authoritative read still latches an aligned over limit day`() {
        addSpan(YOUTUBE, 60, 190)

        val report = success(UsageTracker(context).readTodayEntertainmentUsage())

        assertEquals(130L, report.totalEntertainmentMinutes)
        assertTrue(LockStateManager(context).isLocked())
        assertEquals(130L, UsageHistoryRepository(context).day(today)?.totalMinutes)
    }

    @Test
    fun `authoritative read still clears an aligned low usage latch`() {
        prefs(STATE).edit().putBoolean("key_is_locked", true).commit()
        addSpan(YOUTUBE, 60, 61)

        val report = success(UsageTracker(context).readTodayEntertainmentUsage())

        assertEquals(1L, report.totalEntertainmentMinutes)
        assertFalse(report.isLimitExceeded)
        assertFalse(LockStateManager(context).isLocked())
        assertEquals(1L, UsageHistoryRepository(context).day(today)?.totalMinutes)
    }

    private fun snapshot(): UsageRead = UsageTracker(context).readTodayEntertainmentSnapshot()

    private fun success(read: UsageRead): UsageReport {
        assertTrue("expected a real report, got $read", read is UsageRead.Success)
        return (read as UsageRead.Success).report
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun allPrefs(): Map<String, Map<String, *>> = PREFS.associateWith { prefs(it).all.toMap() }

    private fun grantUsageAccess(granted: Boolean) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        (shadowOf(appOps) as ShadowAppOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            if (granted) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED
        )
    }

    private fun addSpan(packageName: String, startMinute: Int, endMinute: Int) {
        usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setPackage(packageName).setTimeStamp(atMinute(startMinute))
            .setEventType(UsageEvents.Event.ACTIVITY_RESUMED).build())
        usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setPackage(packageName).setTimeStamp(atMinute(endMinute))
            .setEventType(UsageEvents.Event.ACTIVITY_PAUSED).build())
    }

    private fun atMinute(minute: Int) = dayStart + minute * 60_000L

    private fun moveToNextLocalDay() {
        // Move noon past midnight without replacing any production clock or getter.
        TimeZone.setDefault(SimpleTimeZone(TimeZone.getDefault().rawOffset + 13 * 3_600_000, "SnapshotTomorrow"))
        assertEquals(LocalDate.parse(today).plusDays(1).toString(), LockStateManager.dayKeyOf(System.currentTimeMillis()))
    }

    @Implements(UsageStatsManager::class)
    class InterleavedUsageStats : ShadowUsageStatsManager() {
        var duringQuery: (() -> Unit)? = null

        @Implementation
        public override fun queryEvents(beginTime: Long, endTime: Long): UsageEvents {
            val events = super.queryEvents(beginTime, endTime)
            duringQuery?.invoke()
            return events
        }
    }

    private companion object {
        const val DAY_MILLIS = 24 * 3_600_000L
        const val YOUTUBE = "com.google.android.youtube"
        const val WHATSAPP = "com.whatsapp"
        const val STATE = "project_ego_state"
        val PREFS = listOf(
            STATE, "project_ego_time_bank", "project_ego_focus_session",
            "project_ego_usage_history", "project_ego_character_growth", "project_ego_night_contract"
        )
    }
}
