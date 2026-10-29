package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val taskId: Int,
    val taskName: String,
    val category: String,
    val date: Long,
    val completed: Boolean,
    val rating: Int? = null,
    val note: String? = null,
    val isAIGenerated: Boolean = false,
    val aiMainName: String? = null,
    val difficulty: Int? = null,
    //  Liczba kroków wykonanych przy ukończeniu zadania STEPS (null dla innych typów)
    val stepsCompleted: Long? = null,
    //Tekst instrukcji wygenerowanych przez AI (zapisywany przy starcie zadania z AI support)
    val aiInstructions: String? = null
)

fun TaskHistoryEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "syncId" to syncId,
        "taskId" to taskId,
        "taskName" to taskName,
        "category" to category,
        "date" to date,
        "completed" to completed,
        "rating" to rating,
        "note" to note,
        "isAIGenerated" to isAIGenerated,
        "aiMainName" to aiMainName,
        "difficulty" to difficulty,
        "stepsCompleted" to stepsCompleted,
        "aiInstructions" to aiInstructions
    )
}
