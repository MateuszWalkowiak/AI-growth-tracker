package com.MateuszW.aigrowthtracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pojedyncze wystąpienie obowiązkowego zadania w konkretnym dniu kalendarza.
 * Generowane przez algorytm scheduling w TaskViewModel.
 * date = start-of-day (północ) w milisekundach lokalnej strefy.
 *
 * UNIQUE(taskId, date) gwarantuje że jedno zadanie ma maks. jeden wpis na dany dzień -
 * bez tego OnConflictStrategy.IGNORE nie miało żadnego efektu i równoległe rozpisywanie
 * (sync + regenerate) tworzyło zdublowane wiersze
 */
@Entity(
    tableName = "mandatory_schedule",
    indices = [Index(value = ["taskId", "date"], unique = true)]
)
data class MandatoryScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val date: Long
)
