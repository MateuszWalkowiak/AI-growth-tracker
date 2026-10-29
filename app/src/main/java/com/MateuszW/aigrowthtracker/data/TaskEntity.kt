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
    val isCompleted: Boolean = false,

    //  "STEPS" = zadanie liczenia kroków (Health Connect), null = zadanie czasowe
    val taskType: String? = null,
    //Cel dzienny kroków dla zadań STEPS (np. 10000)
    val stepGoal: Int? = null,
    //  Cel dystansu w km dla zadań biegowych (0 = brak celu km, używa timera)
    val targetKm: Float = 0f,

    // ── Aktywne zadanie krokowe ──────────────────────────────────────────
    // null = zadanie nieaktywne; 0 = tryb od północy; >0 = tryb od startu (baseline kroków)
    val stepsBaseline: Long? = null,
    //Timestamp startu zadania (do wykrycia przekroczenia północy)
    val stepsStartedAt: Long? = null,
    // "FROM_MIDNIGHT" | "FROM_START" | null = nie dotyczy
    val stepsCountingMode: String? = null
)

enum class WeeklyTaskType {
    COUNTER,
    MILESTONE
}

data class WeeklyTaskEntity(
    val id: Int = 0,
    val name: String,
    val type: WeeklyTaskType,

    val targetValue: Float, 
    val currentValue: Float = 0f,

    val step: Float = 1f,
    val isAIGenerated: Boolean = false
)

fun TaskEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "taskName" to taskName,
        "category" to category,
        "durationMinutes" to durationMinutes,
        "isMandatory" to isMandatory,
        "isAIGenerated" to isAIGenerated,
        "isCompleted" to isCompleted,
        "taskType" to taskType,
        "stepGoal" to stepGoal,
        "targetKm" to targetKm,
        "recurrenceType" to recurrenceType,
        "recurrenceValue" to recurrenceValue,
        "aiCurrentLevel" to aiCurrentLevel,
        "aiGoals" to aiGoals,
        "stepsBaseline" to stepsBaseline,
        "stepsStartedAt" to stepsStartedAt,
        "stepsCountingMode" to stepsCountingMode
    )
}