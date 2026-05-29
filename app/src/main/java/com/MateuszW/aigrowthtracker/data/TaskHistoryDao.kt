package com.MateuszW.aigrowthtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import androidx.room.Update

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

    @Query("DELETE FROM task_history WHERE taskId = :taskId")
    suspend fun deleteHistoryForTask(taskId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<TaskHistoryEntity>)

    @Update
    suspend fun update(history: TaskHistoryEntity)

    @Query("""
        SELECT * FROM task_history
        WHERE date BETWEEN :start AND :end
        ORDER BY date DESC
    """)
    suspend fun getHistoryBetween(start: Long, end: Long): List<TaskHistoryEntity>

    @Query("DELETE FROM task_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM task_history")
    suspend fun deleteAll()
}