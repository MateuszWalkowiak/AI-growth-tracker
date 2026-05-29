package com.MateuszW.aigrowthtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        TaskHistoryEntity::class,
        WeeklyTaskDb::class,
        WeeklyFlowRecordEntity::class,
        RunSessionEntity::class,
        RunRoutePointEntity::class,
        MandatoryScheduleEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskHistoryDao(): TaskHistoryDao
    abstract fun weeklyTaskDao(): WeeklyTaskDao
    abstract fun weeklyFlowRecordDao(): WeeklyFlowRecordDao
    abstract fun runDao(): RunDao
    abstract fun mandatoryScheduleDao(): MandatoryScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v10 → v11: usuwa zdublowane wpisy harmonogramu (zostawiając najstarszy)
         * i zakłada UNIQUE INDEX(taskId, date). Nazwa indeksu musi być zgodna z tą,
         * którą Room generuje dla @Index na MandatoryScheduleEntity.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM mandatory_schedule WHERE id NOT IN " +
                    "(SELECT MIN(id) FROM mandatory_schedule GROUP BY taskId, date)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_mandatory_schedule_taskId_date " +
                    "ON mandatory_schedule (taskId, date)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "growth_tracker_database"
                )
                    .addMigrations(MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
