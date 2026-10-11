package com.projectego.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.projectego.app.service.EgoAccessibilityService

/**
 * The two switches ALIVE cannot flip itself.
 *
 * Read by the Home dashboard, the Profile card and the first-run welcome, so
 * those surfaces can never disagree about what is on. The logic is the logic
 * MainActivity used to hold privately; it moved here unchanged so the welcome
 * reads the same sources as the app it ushers in.
 */
object PermissionChecks {

    fun hasUsageAccess(context: Context): Boolean {
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

    /**
     * "Display over other apps".
     *
     * Only Guardian asks for it, and only for the moments the guard is off: it is
     * what lets the recovery popup appear over an entertainment app, and it is
     * also the grant that lets ALIVE start that popup from the background at all.
     * Nothing else in the app draws over anything.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName =
            "${context.packageName}/${EgoAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
