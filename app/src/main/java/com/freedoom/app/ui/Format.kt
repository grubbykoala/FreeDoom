package com.freedoom.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Espacio no separable: "2 h" y "15 min" no se parten entre dos líneas. */
internal const val NBSP = '\u00A0'

/** "2 h 15 min", "2 h", "45 min" o "30 s". */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m == 0L -> "$h${NBSP}h"
        h > 0 -> "$h${NBSP}h $m${NBSP}min"
        totalMinutes > 0 -> "$m${NBSP}min"
        else -> "${ms / 1000}${NBSP}s"
    }
}

/** La misma duración en palabras, para TalkBack: "2 horas y 15 minutos". */
fun spokenDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    fun plural(n: Long, one: String, many: String) = if (n == 1L) "1 $one" else "$n $many"
    return when {
        h > 0 && m == 0L -> plural(h, "hora", "horas")
        h > 0 -> plural(h, "hora", "horas") + " y " + plural(m, "minuto", "minutos")
        totalMinutes > 0 -> plural(m, "minuto", "minutos")
        else -> plural(ms / 1000, "segundo", "segundos")
    }
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("d/M")

/** Hora de [at] respecto a [now]: "14:05", "ayer 22:10" o "3/10 14:05". */
fun formatMoment(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val clock = clockFormat.format(time)
    return when (time.toLocalDate()) {
        today -> clock
        today.minusDays(1) -> "ayer $clock"
        else -> "${dateFormat.format(time)} $clock"
    }
}

/**
 * Nombre legible para paquetes del sistema que Android no deja consultar o que ya no existen.
 * null si no es uno de los conocidos.
 */
fun systemPackageLabel(packageName: String): String? = when {
    packageName == "com.android.intentresolver" -> "Menú de compartir"
    packageName.endsWith(".permissioncontroller") -> "Permisos del sistema"
    packageName == "com.google.android.gms" -> "Servicios de Google Play"
    packageName.endsWith(".packageinstaller") -> "Instalador de apps"
    packageName == "android" || packageName.startsWith("com.android.") -> "Componente del sistema"
    else -> null
}
