package com.MateuszW.aigrowthtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MandatoryScheduleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: MandatoryScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<MandatoryScheduleEntity>)

    @Query("SELECT * FROM mandatory_schedule WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun getScheduleBetween(start: Long, end: Long): Flow<List<MandatoryScheduleEntity>>

    @Query("SELECT * FROM mandatory_schedule WHERE date BETWEEN :start AND :end")
    suspend fun getScheduleBetweenOnce(start: Long, end: Long): List<MandatoryScheduleEntity>

    @Query("SELECT MAX(date) FROM mandatory_schedule")
    suspend fun getLastScheduledDate(): Long?

    @Query("DELETE FROM mandatory_schedule WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query("DELETE FROM mandatory_schedule WHERE taskId = :taskId AND date >= :fromDate")
    suspend fun deleteForTaskFromDate(taskId: Int, fromDate: Long)

    @Query("DELETE FROM mandatory_schedule")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM mandatory_schedule WHERE taskId = :taskId AND date = :date")
    suspend fun countForTaskOnDay(taskId: Int, date: Long): Int
}
