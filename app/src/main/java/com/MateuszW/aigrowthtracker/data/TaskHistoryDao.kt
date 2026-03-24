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
}