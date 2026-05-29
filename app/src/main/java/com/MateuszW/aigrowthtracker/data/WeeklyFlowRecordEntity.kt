package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_flow_records")
data class WeeklyFlowRecordEntity(
    @PrimaryKey val weekKey: String,
    val stage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val reviewSummary: String? = null,
    val reviewFeedbackJson: String? = null,
    val selectedTopicsJson: String? = null,
    val goalDraftsJson: String? = null,
    val difficulty: Int? = null,
    val hiddenHolidayCount: Int = 0,
    val promptJson: String? = null,
    val aiResponseJson: String? = null
)