package com.projectego.app.domain

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppOpsManager
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidPhysicalQuestEvidenceProviderTest {

    private lateinit var context: Context
    private lateinit var provider: AndroidPhysicalQuestEvidenceProvider
    private lateinit var sensorManager: SensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        provider = AndroidPhysicalQuestEvidenceProvider(context)
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun grantActivityRecognition() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private fun setUsageAccessMode(mode: Int) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val shadow = shadowOf(appOps) as ShadowAppOpsManager
        shadow.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            mode
        )
    }

    @Test
    fun `readStepCount returns null when the device has no step sensor`() {
        grantActivityRecognition()
        assertNull(provider.readStepCount())
    }

    @Test
    fun `readStepCount returns null without the physical activity permission`() {
        shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        assertNull(provider.readStepCount())
    }

    @Test
    fun `readStepCount reads the counter's latest value`() {
        grantActivityRecognition()
        val shadow = shadowOf(sensorManager) as ShadowSensorManager
        shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))

        // The provider blocks its own thread waiting for the sensor's first
        // event, so the read runs on a separate thread while the test thread
        // dispatches that event once the listener is registered.
        val result = AtomicReference<Int?>()
        val reader = Thread { result.set(provider.readStepCount()) }
        reader.start()

        val deadline = System.currentTimeMillis() + 5_000
        while (shadow.listeners.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("provider never registered its sensor listener", shadow.listeners.isNotEmpty())

        val event = ShadowSensorManager.createSensorEvent(1)
        event.values[0] = 42f
        shadow.sendSensorEventToListeners(event)

        reader.join(5_000)
        assertEquals(42, result.get())
    }

    @Test
    fun `entertainmentPackagesBetween keeps only entertainment foreground events`() {
        setUsageAccessMode(AppOpsManager.MODE_ALLOWED)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val shadow = shadowOf(usageStatsManager)
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        shadow.addEvent("com.instagram.android", 1_100_000L, UsageEvents.Event.MOVE_TO_FOREGROUND)
        shadow.addEvent("com.android.settings", 1_200_000L, UsageEvents.Event.MOVE_TO_FOREGROUND)
        shadow.addEvent("com.whatsapp", 1_300_000L, UsageEvents.Event.ACTIVITY_RESUMED)
        shadow.addEvent(context.packageName, 1_400_000L, UsageEvents.Event.ACTIVITY_RESUMED)
        shadow.addEvent("com.instagram.android", 1_500_000L, UsageEvents.Event.ACTIVITY_RESUMED)
        // Outside the window; must not leak in.
        shadow.addEvent("com.netflix.mediaclient", 999_000L, UsageEvents.Event.MOVE_TO_FOREGROUND)

        assertEquals(
            listOf("com.instagram.android"),
            provider.entertainmentPackagesBetween(windowStart, windowEnd)
        )
    }

    @Test
    fun `entertainmentPackagesBetween returns null without usage access`() {
        setUsageAccessMode(AppOpsManager.MODE_ERRORED)
        assertNull(provider.entertainmentPackagesBetween(0L, 10_000L))
    }
}
