package com.freedoom.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freedoom.app.data.DayRange
import com.freedoom.app.usage.UsageRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Guarda periódicamente el uso de hoy (y cierra el de ayer pasada la medianoche). */
class UsageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = UsageRepository(applicationContext)
        if (!repo.hasPermission()) return Result.success()
        val today = LocalDate.now()
        repo.refresh(DayRange.of(today.minusDays(1)))
        repo.refresh(DayRange.of(today))
        return Result.success()
    }

    companion object {
        private const val NAME = "usage-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
