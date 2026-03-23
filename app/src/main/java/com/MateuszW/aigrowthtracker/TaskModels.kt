package com.MateuszW.aigrowthtracker


import java.util.UUID

enum class TaskCategory(val displayName: String) {
    SPORT("Sport"),
    MIND("Umysł"),
    HEALTH("Zdrowie"),
    CREATIVITY("Kreatywność"),
    CUSTOM("Własna")
}

enum class FrequencyType {
    EVERYDAY,
    SPECIFIC_DAYS,
    X_TIMES_A_WEEK
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val category: TaskCategory,
    val customCategoryName: String? = null,
    val title: String,

    val isMandatory: Boolean = false,

    val frequencyType: FrequencyType = FrequencyType.EVERYDAY,
    val specificDays: List<Int>? = null,
    val timesPerWeek: Int? = null,

    val durationMinutes: Int,

    val isAIGenerated: Boolean = false,
    val aiPromptContext: AITaskContext? = null //dane dla gemini
)

//dane dla asystenta AI
data class AITaskContext(
    val currentLevel: String,
    val goals: String,
    val generatedSubTask: String? = null
)

data class DailyTaskRecord(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val dateEpochDays: Long,
    val isCompleted: Boolean = false,
    val aiFeedback: String? = null //feedback do poprawy zadan AI
)
class TaskModels {
}