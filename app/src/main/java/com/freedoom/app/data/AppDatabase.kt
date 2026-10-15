package com.freedoom.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ShortVideoEntity::class, AppUsageDailyEntity::class, DailySummaryEntity::class],
    version = 1,
    // El esquema se guarda en app/schemas (ver room.schemaLocation en app/build.gradle.kts):
    // es la base para escribir migraciones cuando haya testers con datos.
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shortVideoDao(): ShortVideoDao
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "freedoom.db")
                    // Prototipo: si cambia el esquema se borran los datos en vez de migrar.
                    // Antes de la primera versión para testers: migraciones (AutoMigration) y
                    // fallbackToDestructiveMigrationOnDowngrade().
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
