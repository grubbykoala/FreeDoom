package com.freedoom.app.usage

import android.content.Context
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.AppUsageDailyEntity
import com.freedoom.app.data.DailySummaryEntity
import com.freedoom.app.data.DayRange

/** Lee el uso del día y guarda el agregado en Room. */
class UsageRepository(context: Context) {
    private val reader = UsageReader(context)
    private val dao = AppDatabase.get(context).usageDao()

    fun hasPermission() = reader.hasPermission()

    /** Devuelve null si falta el permiso de acceso a uso. */
    suspend fun refresh(day: DayRange = DayRange.today()): DayUsage? {
        if (!reader.hasPermission()) return null
        val usage = reader.read(day)
        dao.replaceDay(
            DailySummaryEntity(day.epochDay, usage.totalMs, usage.unlocks, System.currentTimeMillis()),
            usage.perApp.map { (pkg, ms) -> AppUsageDailyEntity(day.epochDay, pkg, ms) },
        )
        return usage
    }
}
