package com.freedoom.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.freedoom.app.data.DayRange

/** Lee de UsageStatsManager el tiempo por app y los desbloqueos de un día. */
class UsageReader(private val context: Context) {

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun read(day: DayRange, now: Long = System.currentTimeMillis()): DayUsage {
        val until = minOf(now, day.end)
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = ArrayList<RawUsageEvent>()
        val query = usm.queryEvents(day.start, until)
        val event = UsageEvents.Event()
        while (query.hasNextEvent()) {
            query.getNextEvent(event)
            when (event.eventType) {
                UsageCalculator.ACTIVITY_RESUMED,
                UsageCalculator.ACTIVITY_PAUSED,
                UsageCalculator.SCREEN_NON_INTERACTIVE,
                UsageCalculator.KEYGUARD_HIDDEN,
                UsageCalculator.DEVICE_SHUTDOWN,
                -> events += RawUsageEvent(event.timeStamp, event.eventType, event.packageName, event.className)
            }
        }
        return UsageCalculator.compute(
            events = events,
            dayStart = day.start,
            until = until,
            excludedPackages = excludedPackages(),
            unlocksSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        )
    }

    /** Lanzadores y la interfaz del sistema: no son "apps" en las que se va el tiempo. */
    private fun excludedPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        return launchers.toSet() + "com.android.systemui"
    }
}
