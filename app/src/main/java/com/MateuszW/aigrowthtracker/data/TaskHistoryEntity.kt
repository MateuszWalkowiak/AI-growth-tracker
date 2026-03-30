package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val taskName: String,
    val category: String,
    val date: Long, // timestamp
    val completed: Boolean,
    val rating: Int? = null,
    val note: String? = null
)
fun TaskHistoryEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "taskId" to taskId,
        "taskName" to taskName,
        "category" to category,
        "date" to date,
        "completed" to completed,
        "rating" to rating,
        "note" to note
    )
}