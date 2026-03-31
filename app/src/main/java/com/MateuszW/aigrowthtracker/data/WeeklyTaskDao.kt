package com.MateuszW.aigrowthtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeeklyTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<WeeklyTaskDb>)

    @Query("SELECT * FROM weekly_tasks")
    suspend fun getAll(): List<WeeklyTaskDb>

    @Query("DELETE FROM weekly_tasks")
    suspend fun clearAll()
}