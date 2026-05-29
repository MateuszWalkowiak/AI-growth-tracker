package com.MateuszW.aigrowthtracker.data

data class AiWeeklyPlan(
    val weeklyTasks: List<AiWeeklyTask>,
    val dailyPlan: Map<String, List<AiDailyTask>>,
    val summary: String,
    val quote: String
)

data class AiWeeklyTask(
    val name: String,
    val type: String,
    val goal: Float
)

data class AiDailyTask(
    val mainName: String,
    val description: String,
    val targetValue: Float? = null,
    val doneValue: Float? = null,
    val durationMinutes: Int = 30
)

data class WeeklyReviewInput(
    val historyId: Int,
    val stars: Int,
    val difficulty: Int,
    val note: String
)

data class WeeklyGoalDraft(
    val startState: String = "",
    val goal: String = ""
)

data class DayStats(
    val dayMillis: Long,
    val dayLabel: String,
    val totalPlanned: Int,
    val completed: Int,
    val uncompleted: Int
)