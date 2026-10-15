package com.freedoom.app.ui

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    private val min = 60_000L
    private val hour = 60 * min

    @Test
    fun durationKeepsNumberAndUnitTogether() {
        assertEquals("2\u00A0h 15\u00A0min", formatDuration(2 * hour + 15 * min))
    }

    @Test
    fun wholeHoursHaveNoMinutes() {
        assertEquals("2\u00A0h", formatDuration(2 * hour))
        assertEquals("1\u00A0h", formatDuration(hour + 59_999))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("45\u00A0min", formatDuration(45 * min + 30_000))
        assertEquals("59\u00A0s", formatDuration(59_000))
        assertEquals("0\u00A0s", formatDuration(0))
    }

    @Test
    fun spokenDurationInWords() {
        assertEquals("2 horas y 15 minutos", spokenDuration(2 * hour + 15 * min))
        assertEquals("1 hora y 1 minuto", spokenDuration(hour + min))
        assertEquals("1 hora", spokenDuration(hour))
        assertEquals("45 minutos", spokenDuration(45 * min))
        assertEquals("1 segundo", spokenDuration(1_000))
        assertEquals("0 segundos", spokenDuration(0))
    }

    @Test
    fun momentIsRelativeToToday() {
        val zone = ZoneId.of("Europe/Madrid")
        fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
        val now = at(15, 19, 30)
        assertEquals("14:05", formatMoment(at(15, 14, 5), now, zone))
        assertEquals("ayer 22:10", formatMoment(at(14, 22, 10), now, zone))
        assertEquals("3/10 08:00", formatMoment(at(3, 8, 0), now, zone))
    }

    @Test
    fun readableNamesForSystemPackages() {
        assertEquals("Menú de compartir", systemPackageLabel("com.android.intentresolver"))
        assertEquals("Permisos del sistema", systemPackageLabel("com.google.android.permissioncontroller"))
        assertEquals("Permisos del sistema", systemPackageLabel("com.android.permissioncontroller"))
        assertEquals("Servicios de Google Play", systemPackageLabel("com.google.android.gms"))
        assertEquals("Componente del sistema", systemPackageLabel("com.android.phone"))
        assertNull(systemPackageLabel("com.example.desinstalada"))
    }
}
