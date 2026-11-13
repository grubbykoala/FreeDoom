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
    /** Tiempo con alguna app en primer plano (sin contar dos veces la pantalla dividida). */
    val totalMs: Long,
    /** null si el sistema no da el dato (Android 8.x). */
    val unlocks: Int?,
    /** true si el móvil no tiene bloqueo de pantalla y [unlocks] cuenta encendidos de pantalla. */
    val unlocksAreScreenOns: Boolean = false,
)

/**
 * Convierte la secuencia de eventos de un día en tiempo por app y desbloqueos.
 * Lógica pura (sin Android) para poder probarla con tests normales.
 *
 * Los eventos pueden empezar antes del día (para saber qué estaba abierto a medianoche):
 * solo cuenta lo que cae dentro de [dayStart, until].
 */
object UsageCalculator {
    // Valores de android.app.usage.UsageEvents.Event
    const val ACTIVITY_RESUMED = 1 // antes MOVE_TO_FOREGROUND
    const val ACTIVITY_PAUSED = 2 // antes MOVE_TO_BACKGROUND
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val ACTIVITY_STOPPED = 23 // Android 10+; el sistema guarda aquí también los DESTROYED
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27

    /** Tipos de evento que hay que copiar de UsageStatsManager. */
    val EVENT_TYPES = setOf(
        ACTIVITY_RESUMED, ACTIVITY_PAUSED, ACTIVITY_STOPPED,
        SCREEN_INTERACTIVE, SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, KEYGUARD_HIDDEN,
        DEVICE_SHUTDOWN, DEVICE_STARTUP,
    )

    fun compute(
        events: List<RawUsageEvent>,
        dayStart: Long,
        until: Long,
        excludedPackages: Set<String>,
        unlocksSupported: Boolean,
    ): DayUsage {
        val sorted = events.sortedBy { it.timestamp }
        val intervals = foregroundIntervals(sorted, until).mapNotNull { it.clip(dayStart, until) }

        val totals = HashMap<String, Long>()
        for (i in intervals) totals[i.pkg] = (totals[i.pkg] ?: 0L) + (i.end - i.start)
        val perApp = totals
            .filterKeys { it !in excludedPackages }
            .filterValues { it >= 1_000 }
            .toList()
            .sortedByDescending { it.second }
        val shown = perApp.mapTo(HashSet()) { it.first }

        var unlocks = 0
        var screenOns = 0
        var keyguardEvents = 0
        for (e in sorted) {
            if (e.timestamp > until) break
            val today = e.timestamp >= dayStart
            when (e.type) {
                KEYGUARD_HIDDEN -> {
                    keyguardEvents++
                    if (today) unlocks++
                }
                KEYGUARD_SHOWN -> keyguardEvents++
                SCREEN_INTERACTIVE -> if (today) screenOns++
            }
        }
        // Con el bloqueo en "Ninguno" Android no emite eventos de keyguard: contamos encendidos.
        // Se miran también los eventos de antes del día para no confundirlo con un bloqueo que tarda en saltar.
        val noLockScreen = unlocksSupported && keyguardEvents == 0 && screenOns > 0

        return DayUsage(
            perApp = perApp,
            totalMs = unionMs(intervals.filter { it.pkg in shown }),
            unlocks = when {
                !unlocksSupported -> null
                noLockScreen -> screenOns
                else -> unlocks
            },
            unlocksAreScreenOns = noLockScreen,
        )
    }

    /** Milisegundos en primer plano de [packageName] dentro de [from, until]. */
    fun foregroundMs(events: List<RawUsageEvent>, packageName: String, from: Long, until: Long): Long {
        if (until <= from) return 0L
        return foregroundIntervals(events.sortedBy { it.timestamp }, until)
            .filter { it.pkg == packageName }
            .mapNotNull { it.clip(from, until) }
            .sumOf { it.end - it.start }
    }

    /** Último momento posterior a [after] en que [packageName] pasó a primer plano, o null. */
    fun lastResumeAfter(events: List<RawUsageEvent>, packageName: String, after: Long): Long? =
        events.asSequence()
            .filter { it.type == ACTIVITY_RESUMED && it.packageName == packageName && it.timestamp > after }
            .maxOfOrNull { it.timestamp }

    private class Interval(val pkg: String, val start: Long, val end: Long) {
        fun clip(from: Long, to: Long): Interval? {
            val s = maxOf(start, from)
            val e = minOf(end, to)
            return if (e > s) Interval(pkg, s, e) else null
        }
    }

    /** Tramos en primer plano de cada app. Lo que sigue abierto se cierra en [until]. */
    private fun foregroundIntervals(sorted: List<RawUsageEvent>, until: Long): List<Interval> {
        val result = ArrayList<Interval>()
        // Por app: actividades en primer plano y desde cuándo.
        val openActivities = HashMap<String, MutableSet<String>>()
        val openSince = HashMap<String, Long>()
        // PAUSED que aún esperan su STOPPED, por "paquete/clase".
        val pendingStops = HashMap<String, Int>()

        fun close(pkg: String, at: Long) {
            val since = openSince.remove(pkg) ?: return
            if (at > since) result += Interval(pkg, since, at)
        }

        fun closeAll(at: Long) {
            for (pkg in openSince.keys.toList()) close(pkg, at)
            openActivities.clear()
        }

        for (e in sorted) {
            if (e.timestamp > until) break
            val cls = e.className ?: ""
            when (e.type) {
                ACTIVITY_RESUMED -> {
                    val set = openActivities.getOrPut(e.packageName) { mutableSetOf() }
                    if (set.isEmpty()) openSince[e.packageName] = e.timestamp
                    set += cls
                }
                ACTIVITY_PAUSED -> {
                    val key = "${e.packageName}/$cls"
                    pendingStops[key] = (pendingStops[key] ?: 0) + 1
                    // Un PAUSED sin RESUMED antes (ventana PiP que vuelve a verse...) no es tiempo en primer plano.
                    val set = openActivities[e.packageName]
                    if (!set.isNullOrEmpty()) {
                        set -= cls
                        // Si no conocemos la clase exacta, cerramos la app entera.
                        if (e.className == null) set.clear()
                        if (set.isEmpty()) close(e.packageName, e.timestamp)
                    }
                }
                ACTIVITY_STOPPED -> {
                    // Solo cierra si la actividad se destruyó sin pasar por PAUSED (la app se colgó en pantalla).
                    // Si un PAUSED se queda sin su STOPPED (diálogo encima), esta detección se apaga para esa clase.
                    val key = "${e.packageName}/$cls"
                    val pending = pendingStops[key] ?: 0
                    if (pending > 0) {
                        if (pending == 1) pendingStops.remove(key) else pendingStops[key] = pending - 1
                    } else {
                        val set = openActivities[e.packageName]
                        if (set != null && set.remove(cls) && set.isEmpty()) close(e.packageName, e.timestamp)
                    }
                }
                SCREEN_NON_INTERACTIVE -> closeAll(e.timestamp)
                DEVICE_SHUTDOWN -> {
                    closeAll(e.timestamp)
                    pendingStops.clear()
                }
                DEVICE_STARTUP -> {
                    // Lo que seguía abierto (apagado sin aviso) se descarta: no se sabe cuándo terminó.
                    openSince.clear()
                    openActivities.clear()
                    pendingStops.clear()
                }
            }
        }
        closeAll(until)
        return result
    }

    /** Suma de la unión de los tramos: lo que se solapa cuenta una vez. */
    private fun unionMs(intervals: List<Interval>): Long {
        var total = 0L
        var curStart = 0L
        var curEnd = 0L
        var any = false
        for (i in intervals.sortedBy { it.start }) {
            if (!any || i.start > curEnd) {
                if (any) total += curEnd - curStart
                curStart = i.start
                curEnd = i.end
                any = true
            } else if (i.end > curEnd) {
                curEnd = i.end
            }
        }
        if (any) total += curEnd - curStart
        return total
    }
}
