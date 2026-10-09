package com.projectego.app.domain

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.core.content.ContextCompat
import com.projectego.app.model.AppCategory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The real device as a [PhysicalQuestEvidenceProvider]: the step counter
 * sensor and the usage-stats event log.
 */
class AndroidPhysicalQuestEvidenceProvider(private val context: Context) : PhysicalQuestEvidenceProvider {

    override fun readStepCount(): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null

        // One-shot read on a dedicated looper thread: the step counter is an
        // on-change sensor whose cached last value is re-delivered on
        // registration, so the first event IS the current count. Never
        // register on the main looper - the wait would deadlock it.
        val thread = HandlerThread("alive-step-read")
        thread.start()
        try {
            val latch = CountDownLatch(1)
            var count: Int? = null
            val handler = Handler(thread.looper)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    count = event.values[0].toInt()
                    latch.countDown()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
            try {
                latch.await(2, TimeUnit.SECONDS)
            } finally {
                sensorManager.unregisterListener(listener)
            }
            return count
        } finally {
            thread.quitSafely()
        }
    }

    override fun entertainmentPackagesBetween(startMillis: Long, endMillis: Long): List<String>? {
        if (!hasUsageAccess()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usageStatsManager.queryEvents(startMillis, endMillis)
        } catch (_: Exception) {
            return null
        }

        val selfPackage = context.packageName
        val seen = mutableSetOf<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED &&
                event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                continue
            }
            val packageName = event.packageName ?: continue
            if (packageName == selfPackage) continue
            if (AppClassifier.categorize(context, packageName) != AppCategory.ENTERTAINMENT) continue
            seen.add(packageName)
        }
        return seen.toList()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
