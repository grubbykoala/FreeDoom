package com.freedoom.app.usage

/**
 * Evento de UsageStatsManager reducido a lo que necesitamos.
 * Los tipos usan los mismos valores que UsageEvents.Event.
 */
data class RawUsageEvent(
    val timestamp: Long,
    val type: Int,
    val packageName: String,
    val className: String?,
)

data class DayUsage(
    /** Milisegundos en primer plano por app, de mayor a menor. */
    val perApp: List<Pair<String, Long>>,
    val totalMs: Long,
    /** null si el sistema no da el dato (Android 8.x). */
    val unlocks: Int?,
)

/**
 * Convierte la secuencia de eventos de un día en tiempo por app y desbloqueos.
 * Lógica pura (sin Android) para poder probarla con tests normales.
 */
object UsageCalculator {
    // Valores de android.app.usage.UsageEvents.Event
    const val ACTIVITY_RESUMED = 1 // antes MOVE_TO_FOREGROUND
    const val ACTIVITY_PAUSED = 2 // antes MOVE_TO_BACKGROUND
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_HIDDEN = 18
    const val DEVICE_SHUTDOWN = 26

    fun compute(
        events: List<RawUsageEvent>,
        dayStart: Long,
        until: Long,
        excludedPackages: Set<String>,
        unlocksSupported: Boolean,
    ): DayUsage {
        val totals = HashMap<String, Long>()
        // Por app: actividades en primer plano y desde cuándo.
        val openActivities = HashMap<String, MutableSet<String>>()
        val openSince = HashMap<String, Long>()
        val seen = HashSet<String>()
        var unlocks = 0

        fun add(pkg: String, from: Long, to: Long) {
            val start = maxOf(from, dayStart)
            val end = minOf(to, until)
            if (end > start) totals[pkg] = (totals[pkg] ?: 0L) + (end - start)
        }

        fun closeAll(at: Long) {
            for ((pkg, since) in openSince) add(pkg, since, at)
            openSince.clear()
            openActivities.clear()
        }

        for (e in events.sortedBy { it.timestamp }) {
            when (e.type) {
                ACTIVITY_RESUMED -> {
                    val set = openActivities.getOrPut(e.packageName) { mutableSetOf() }
                    if (set.isEmpty()) openSince[e.packageName] = e.timestamp
                    set += e.className ?: ""
                    seen += e.packageName
                }
                ACTIVITY_PAUSED -> {
                    val set = openActivities[e.packageName]
                    if (set.isNullOrEmpty()) {
                        // Ya estaba en primer plano al empezar el día.
                        if (e.packageName !in seen) add(e.packageName, dayStart, e.timestamp)
                    } else {
                        set -= e.className ?: ""
                        // Si no conocemos la clase exacta, cerramos la app entera.
                        if (e.className == null) set.clear()
                        if (set.isEmpty()) {
                            add(e.packageName, openSince.remove(e.packageName) ?: e.timestamp, e.timestamp)
                        }
                    }
                    seen += e.packageName
                }
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> closeAll(e.timestamp)
                KEYGUARD_HIDDEN -> unlocks++
            }
        }
        closeAll(until)

        val perApp = totals
            .filterKeys { it !in excludedPackages }
            .filterValues { it >= 1_000 }
            .toList()
            .sortedByDescending { it.second }
        return DayUsage(
            perApp = perApp,
            totalMs = perApp.sumOf { it.second },
            unlocks = if (unlocksSupported) unlocks else null,
        )
    }
}
