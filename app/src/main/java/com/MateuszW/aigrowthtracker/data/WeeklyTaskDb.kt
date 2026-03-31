package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_tasks")
data class WeeklyTaskDb(
    @PrimaryKey val id: Int,
    val name: String,
    val type: String,
    val targetValue: Float,
    val currentValue: Float,
    val step: Float,
    val isAIGenerated: Boolean
)