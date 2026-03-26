package com.MateuszW.aigrowthtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskHistoryDao {

    @Insert
    suspend fun insert(history: TaskHistoryEntity)

    @Query("SELECT * FROM task_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<TaskHistoryEntity>>
    @Query("""
    SELECT 
        taskId,
        taskName,
        category,
        COUNT(*) as totalCount,
        AVG(rating) as avgRating
    FROM task_history
    WHERE completed = 1
    GROUP BY taskId
""")
    fun getTaskSummaries(): Flow<List<TaskSummary>>
    @Query("SELECT * FROM task_history WHERE taskId = :taskId ORDER BY date DESC")
    fun getHistoryForTask(taskId: Int): Flow<List<TaskHistoryEntity>>

    @Query("""
    SELECT date FROM task_history 
    WHERE completed = 1
""")
    fun getCompletedDates(): Flow<List<Long>>

    @Query("""
    SELECT * FROM task_history 
    WHERE date BETWEEN :start AND :end
""")
    fun getHistoryForDay(start: Long, end: Long): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history")
    suspend fun getAllHistoryOnce(): List<TaskHistoryEntity>

    @Query("SELECT MIN(date) FROM task_history WHERE taskId != -1")
    suspend fun getRealFirstDate(): Long? //funkcja pomocnicza tymczasowa
}