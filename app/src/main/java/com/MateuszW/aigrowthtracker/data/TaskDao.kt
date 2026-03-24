package com.MateuszW.aigrowthtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("SELECT * FROM tasks_table")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT DISTINCT category FROM tasks_table")
    fun getUniqueCategories(): Flow<List<String>>

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}