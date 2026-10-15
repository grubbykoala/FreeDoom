package com.freedoom.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un vídeo corto visto. Solo app y marcas de tiempo: nunca contenido de la pantalla.
 * [endedAt] es null mientras el vídeo sigue en pantalla y también cuando su duración es
 * desconocida (p. ej. el servicio se paró antes de cerrarlo). Esas filas cuentan como
 * vídeo visto, pero no entran en la media de segundos por vídeo.
 */
@Entity(
    tableName = "short_videos",
    indices = [Index(value = ["packageName", "startedAt"], unique = true), Index("startedAt")],
)
data class ShortVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

/** Tiempo en primer plano de una app durante un día ([day] = LocalDate.toEpochDay()). */
@Entity(tableName = "app_usage_daily", primaryKeys = ["day", "packageName"])
data class AppUsageDailyEntity(
    val day: Long,
    val packageName: String,
    val foregroundMs: Long,
)

/** Totales del día. [unlocks] es null en Android 8.x, que no expone ese evento. */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val day: Long,
    val totalScreenMs: Long,
    val unlocks: Int?,
    val updatedAt: Long,
)
