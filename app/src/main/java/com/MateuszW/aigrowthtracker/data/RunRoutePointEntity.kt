package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_route_points")
data class RunRoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float
)
