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

    val recurrenceType: String? = null,
    val recurrenceValue: String? = null,

    val isAIGenerated: Boolean,
    val aiCurrentLevel: String? = null,
    val aiGoals: String? = null,
    val isCompleted: Boolean = false

)

fun TaskEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "taskName" to taskName,
        "category" to category,
        "durationMinutes" to durationMinutes,
        "isMandatory" to isMandatory,
        "isAIGenerated" to isAIGenerated,
        "isCompleted" to isCompleted
    )
}