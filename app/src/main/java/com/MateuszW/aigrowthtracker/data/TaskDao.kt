package com.MateuszW.aigrowthtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    //kategorie zadan wpisane przez uzytkownika
    @Query("SELECT DISTINCT category FROM tasks_table")
    fun getAllCustomCategories(): Flow<List<String>>

    @Query("SELECT * FROM tasks_table")
    fun getAllTasks(): Flow<List<TaskEntity>>
}