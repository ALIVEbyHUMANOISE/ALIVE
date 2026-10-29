package com.projectego.app.domain

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.LockStateManager
import com.projectego.app.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppOpsManager

/**
 * The dashboard's fallback report is drawn, never acted on.
 *
 * `calculateTodayEntertainmentUsage()` answers with an empty day when the
 * source cannot be read. That empty day used to flow through the same latch
 * logic as a real report, so opening a display surface with Usage Access
 * revoked could clear a lock the guard had just set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageDisplayReadTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        (shadowOf(appOps) as ShadowAppOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ERRORED
        )
    }

    @Test
    fun `an unreadable day is drawn without moving the lock`() {
        val today = LockStateManager.dayKeyOf(System.currentTimeMillis())
        context.getSharedPreferences("project_ego_state", Context.MODE_PRIVATE).edit()
            .putString("key_last_tracked_date", today)
            .putBoolean("key_is_locked", true)
            .putString("key_day_type", DayType.NORMAL_DAY.name)
            .putBoolean("key_quest_rule_announced", true)
            .commit()
        val lockState = LockStateManager(context)
        assertTrue(lockState.isLocked())

        val report = UsageTracker(context).calculateTodayEntertainmentUsage()

        assertEquals("the stand-in is still an empty day", 0L, report.totalEntertainmentMinutes)
        assertTrue("a failed read must not unlock", lockState.isLocked())
    }
}
