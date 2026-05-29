package com.MateuszW.aigrowthtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RunSessionEntity): Long

    @Update
    suspend fun updateSession(session: RunSessionEntity)

    @Insert
    suspend fun insertRoutePoint(point: RunRoutePointEntity)

    @Query("SELECT * FROM run_sessions WHERE isCompleted = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<RunSessionEntity>>

    @Query("SELECT * FROM run_route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getRoutePointsForSession(sessionId: Int): List<RunRoutePointEntity>

    @Query("SELECT * FROM run_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Int): RunSessionEntity?

    @Query("SELECT COUNT(*) FROM run_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT * FROM run_sessions WHERE syncId = :syncId LIMIT 1")
    suspend fun getSessionBySyncId(syncId: String): RunSessionEntity?

    @Query("SELECT COALESCE(SUM(totalDistanceMeters), 0) FROM run_sessions WHERE isCompleted = 1")
    suspend fun getTotalDistanceMeters(): Float

    @Query("SELECT COUNT(*) FROM run_sessions WHERE isCompleted = 1")
    suspend fun getCompletedRunCount(): Int
}
