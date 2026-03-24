package com.MateuszW.aigrowthtracker.data

data class TaskSummary(
    val taskId: Int,
    val taskName: String,
    val category: String,
    val totalCount: Int,
    val avgRating: Float?
)