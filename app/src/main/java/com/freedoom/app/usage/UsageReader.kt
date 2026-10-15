package com.freedoom.app.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.freedoom.app.data.DayRange

/** Lee de UsageStatsManager el tiempo por app y los desbloqueos de un día. */
class UsageReader(private val context: Context) {

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION") // las dos variantes están marcadas como obsoletas según la versión del SDK
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // Con MODE_DEFAULT el sistema mira el permiso (por ejemplo, concedido con "adb shell pm grant").
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun read(day: DayRange, now: Long = System.currentTimeMillis()): DayUsage {
        val until = minOf(now, day.end)
        return UsageCalculator.compute(
            // Se lee desde antes de medianoche para saber qué app estaba abierta al empezar el día.
            events = events(day.start - lookbackMs, until),
            dayStart = day.start,
            until = until,
            excludedPackages = excludedPackages(),
            unlocksSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        )
    }

    /** Milisegundos en primer plano de [packageName] entre [from] y [to]. 0 si falta el permiso. */
    fun foregroundMs(packageName: String, from: Long, to: Long): Long {
        if (to <= from || !hasPermission()) return 0L
        return try {
            UsageCalculator.foregroundMs(events(from - lookbackMs, to, packageName), packageName, from, to)
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo leer el uso", e)
            0L
        }
    }

    /** Último paso de [packageName] a primer plano entre [after] y [until], o null (también sin permiso). */
    fun lastResumeAfter(packageName: String, after: Long, until: Long = System.currentTimeMillis()): Long? {
        if (until <= after || !hasPermission()) return null
        return try {
            UsageCalculator.lastResumeAfter(events(after, until, packageName), packageName, after)
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo leer el uso", e)
            null
        }
    }

    /** Eventos útiles entre [from] y [to]. Con [onlyPackage], descarta las actividades de otras apps. */
    private fun events(from: Long, to: Long, onlyPackage: String? = null): List<RawUsageEvent> {
        val events = ArrayList<RawUsageEvent>()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return events
        val query = usm.queryEvents(from, to) ?: return events
        val event = UsageEvents.Event()
        while (query.hasNextEvent()) {
            query.getNextEvent(event)
            val type = event.eventType
            if (type !in UsageCalculator.EVENT_TYPES) continue
            val pkg = event.packageName ?: ""
            if (onlyPackage != null && pkg != onlyPackage && type in ACTIVITY_TYPES) continue
            events += RawUsageEvent(event.timeStamp, type, pkg, event.className)
        }
        return events
    }

    /** Lanzadores y la interfaz del sistema: no son "apps" en las que se va el tiempo. */
    private fun excludedPackages(): Set<String> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = pm
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            // Ajustes declara FallbackHome como HOME con prioridad -1000 (solo para el arranque): no es un lanzador.
            .filter { it.priority >= 0 }
            .map { it.activityInfo.packageName }
            .toSet()
        val settings = Intent(Settings.ACTION_SETTINGS).resolveActivity(pm)?.packageName
        return launchers - setOfNotNull(settings) + "com.android.systemui"
    }

    private companion object {
        const val TAG = "FreeDoom"

        /** Margen hacia atrás al leer un día o un intervalo. */
        const val LOOKBACK_MS = 12 * 3_600_000L

        /**
         * Solo se mira hacia atrás donde el sistema da DEVICE_SHUTDOWN/STARTUP (Android 10+). En Android
         * 8-9 un apagado con una app delante la dejaría "abierta" y sumaría horas de más; sin margen, solo
         * se pierde el rato de una app abierta desde antes de medianoche hasta su primera pausa.
         */
        val lookbackMs: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) LOOKBACK_MS else 0L

        val ACTIVITY_TYPES = setOf(
            UsageCalculator.ACTIVITY_RESUMED,
            UsageCalculator.ACTIVITY_PAUSED,
            UsageCalculator.ACTIVITY_STOPPED,
        )
    }
}
