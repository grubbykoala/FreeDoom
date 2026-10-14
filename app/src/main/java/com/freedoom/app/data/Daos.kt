package com.freedoom.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortVideoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: ShortVideoEntity): Long

    @Query("UPDATE short_videos SET endedAt = :endedAt WHERE packageName = :packageName AND startedAt = :startedAt")
    suspend fun setEnded(packageName: String, startedAt: Long, endedAt: Long)

    @Query("SELECT COUNT(*) FROM short_videos WHERE startedAt >= :from AND startedAt < :to")
    fun observeCount(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM short_videos WHERE startedAt >= :from AND startedAt < :to")
    suspend fun count(from: Long, to: Long): Int

    /** Media en ms de los vídeos ya terminados del intervalo. */
    @Query(
        "SELECT AVG(endedAt - startedAt) FROM short_videos " +
            "WHERE startedAt >= :from AND startedAt < :to AND endedAt IS NOT NULL"
    )
    fun observeAverageWatchMs(from: Long, to: Long): Flow<Double?>

    @Query("DELETE FROM short_videos WHERE startedAt >= :from AND startedAt < :to")
    suspend fun deleteBetween(from: Long, to: Long)
}

@Dao
interface UsageDao {
    @Upsert
    suspend fun upsertSummary(summary: DailySummaryEntity)

    @Query("DELETE FROM app_usage_daily WHERE day = :day")
    suspend fun deleteAppUsage(day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(rows: List<AppUsageDailyEntity>)

    @Transaction
    suspend fun replaceDay(summary: DailySummaryEntity, rows: List<AppUsageDailyEntity>) {
        upsertSummary(summary)
        deleteAppUsage(summary.day)
        insertAppUsage(rows)
    }
}
