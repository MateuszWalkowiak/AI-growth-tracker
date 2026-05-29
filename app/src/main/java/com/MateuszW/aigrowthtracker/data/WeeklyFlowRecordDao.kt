package com.MateuszW.aigrowthtracker.data

import androidx.room.*

@Dao
interface WeeklyFlowRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WeeklyFlowRecordEntity)

    @Query("SELECT * FROM weekly_flow_records WHERE weekKey = :weekKey LIMIT 1")
    suspend fun getByWeekKey(weekKey: String): WeeklyFlowRecordEntity?

    @Query("DELETE FROM weekly_flow_records WHERE weekKey = :weekKey")
    suspend fun deleteByWeekKey(weekKey: String)

    @Query("SELECT * FROM weekly_flow_records ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WeeklyFlowRecordEntity>
}