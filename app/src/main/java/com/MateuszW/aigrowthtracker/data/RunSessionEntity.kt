package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "run_sessions")
data class RunSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val taskId: Int = -999,
    val taskName: String = "Bieganie",
    val aiMainName: String? = null,
    val targetDistanceKm: Float? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val durationSeconds: Long = 0,
    val avgSpeedKmh: Float = 0f,
    val avgPaceSecPerKm: Float = 0f,
    val isCompleted: Boolean = false,
    val rating: Int? = null,
    val note: String? = null
)
