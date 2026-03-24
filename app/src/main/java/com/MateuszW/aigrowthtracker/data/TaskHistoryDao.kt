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
}