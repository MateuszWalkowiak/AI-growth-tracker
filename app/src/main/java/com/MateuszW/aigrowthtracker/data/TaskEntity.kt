package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks_table")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val category: String,
    val taskName: String,

    val durationMinutes: Int,
    val isMandatory: Boolean,

    val isAIGenerated: Boolean,
    val aiCurrentLevel: String? = null,
    val aiGoals: String? = null,
    val isCompleted: Boolean = false
)