package com.freedoom.app.data

import java.time.LocalDate
import java.time.ZoneId

/** Límites de un día en la zona horaria del móvil, en milisegundos epoch. */
data class DayRange(val date: LocalDate, val start: Long, val end: Long) {
    val epochDay: Long get() = date.toEpochDay()

    companion object {
        fun of(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = DayRange(
            date = date,
            start = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        fun today() = of(LocalDate.now())
    }
}
