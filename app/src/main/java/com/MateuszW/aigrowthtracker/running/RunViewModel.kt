package com.MateuszW.aigrowthtracker.running

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.MateuszW.aigrowthtracker.data.RunDao
import com.MateuszW.aigrowthtracker.data.RunRoutePointEntity
import com.MateuszW.aigrowthtracker.data.RunSessionEntity
import com.MateuszW.aigrowthtracker.data.TaskHistoryDao
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.AuthManager
import com.MateuszW.aigrowthtracker.data.FirebaseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RunViewModel(
    private val runDao: RunDao,
    private val taskHistoryDao: TaskHistoryDao
) : ViewModel() {

    val isRunning: StateFlow<Boolean> = RunningRepository.isRunning
    val isPaused: StateFlow<Boolean> = RunningRepository.isPaused
    val totalDistanceMeters: StateFlow<Float> = RunningRepository.totalDistanceMeters
    val durationSeconds: StateFlow<Long> = RunningRepository.durationSeconds
    val currentSpeedKmh: StateFlow<Float> = RunningRepository.currentSpeedKmh
    val avgPaceSecPerKm: StateFlow<Float> = RunningRepository.avgPaceSecPerKm
    val routePoints: StateFlow<List<RunPoint>> = RunningRepository.routePoints
    val currentSessionId: StateFlow<Int?> = RunningRepository.currentSessionId

    val completedSessions = runDao.getCompletedSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun startRun(context: Context, taskId: Int, taskName: String, targetKm: Float?, aiMainName: String?) {
        val intent = Intent(context, RunningService::class.java).apply {
            action = RunningService.ACTION_START
            putExtra(RunningService.EXTRA_TASK_ID, taskId)
            putExtra(RunningService.EXTRA_TASK_NAME, taskName)
            putExtra(RunningService.EXTRA_AI_MAIN_NAME, aiMainName)
            targetKm?.let { putExtra(RunningService.EXTRA_TARGET_KM, it) }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseRun(context: Context) {
        context.startService(Intent(context, RunningService::class.java).apply {
            action = RunningService.ACTION_PAUSE
        })
    }

    fun resumeRun(context: Context) {
        context.startService(Intent(context, RunningService::class.java).apply {
            action = RunningService.ACTION_RESUME
        })
    }

    fun stopRun(context: Context, rating: Int, note: String, goalReached: Boolean, onComplete: () -> Unit) {
        context.startService(Intent(context, RunningService::class.java).apply {
            action = RunningService.ACTION_STOP
        })
        viewModelScope.launch {
            val sessionId = RunningRepository.currentSessionId.value ?: return@launch
            val session = runDao.getSessionById(sessionId) ?: return@launch

            val distanceKm = RunningRepository.totalDistanceMeters.value / 1000f
            val duration = RunningRepository.durationSeconds.value

            runDao.updateSession(
                session.copy(
                    rating = rating,
                    note = note,
                    isCompleted = goalReached,
                    endTime = System.currentTimeMillis(),
                    totalDistanceMeters = RunningRepository.totalDistanceMeters.value,
                    durationSeconds = duration,
                    avgSpeedKmh = RunningRepository.currentSpeedKmh.value,
                    avgPaceSecPerKm = RunningRepository.avgPaceSecPerKm.value
                )
            )

            val noteText = buildString {
                append("Dystans: %.2f km".format(distanceKm))
                append(" | Czas: ${formatDuration(duration)}")
                append(" | Tempo: ${formatPace(RunningRepository.avgPaceSecPerKm.value)} /km")
                if (session.targetDistanceKm != null && session.targetDistanceKm > 0f) {
                    append(" | Cel: %.2f km".format(session.targetDistanceKm))
                }
                if (note.isNotBlank()) append("\n$note")
            }

            val isGoalReached = goalReached

            // Usuń stary wpis odroczonego biegu (completed=false + "(odroczone)") z dzisiejszej historii
            // — po ukończeniu biegu nie ma sensu trzymać dwóch wpisów
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86_400_000L

            val todayHistory = taskHistoryDao.getHistoryBetween(dayStart, dayEnd)
            todayHistory
                .filter { it.taskId == session.taskId && !it.completed && it.note?.contains("(odroczone)") == true }
                .forEach { deferred ->
                    taskHistoryDao.deleteById(deferred.id)
                    // Usuń też z Firebase
                    val userId = AuthManager.getUserId()
                    if (userId != null) {
                        FirebaseRepository.db
                            .collection("users").document(userId)
                            .collection("history").document(deferred.syncId)
                            .delete()
                    }
                }

            val historyEntry = TaskHistoryEntity(
                taskId = session.taskId,
                syncId = UUID.randomUUID().toString(),
                taskName = session.taskName,
                category = "Sport",
                date = System.currentTimeMillis(),
                completed = isGoalReached,
                rating = rating,
                note = noteText,
                isAIGenerated = session.aiMainName != null,
                aiMainName = session.aiMainName
            )
            taskHistoryDao.insert(historyEntry)
            syncHistoryToFirebase(historyEntry)
            syncRunToFirebase(session.copy(rating = rating, note = note, isCompleted = true))
            onComplete()
        }
    }

    private fun syncHistoryToFirebase(history: TaskHistoryEntity) {
        viewModelScope.launch {
            val userId = AuthManager.getUserId() ?: return@launch
            val data = mapOf(
                "taskId" to history.taskId,
                "syncId" to history.syncId,
                "taskName" to history.taskName,
                "category" to history.category,
                "date" to history.date,
                "completed" to history.completed,
                "rating" to history.rating,
                "note" to history.note,
                "isAIGenerated" to history.isAIGenerated,
                "aiMainName" to history.aiMainName
            )
            FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("history")
                .document(history.syncId)
                .set(data)
        }
    }

    fun stopRunPartial(context: Context) {
        context.startService(Intent(context, RunningService::class.java).apply {
            action = RunningService.ACTION_STOP
        })
    }

    fun stopRunDeferred(
        context: Context,
        daysOffset: Int,
        aiMainName: String?,
        onComplete: (distanceKm: Float, targetKm: Float) -> Unit
    ) {
        context.startService(Intent(context, RunningService::class.java).apply {
            action = RunningService.ACTION_STOP
        })
        viewModelScope.launch {
            val sessionId = RunningRepository.currentSessionId.value ?: return@launch
            val session = runDao.getSessionById(sessionId) ?: return@launch
            val distanceKm = RunningRepository.totalDistanceMeters.value / 1000f
            val duration = RunningRepository.durationSeconds.value
            val targetKm = session.targetDistanceKm ?: 0f

            runDao.updateSession(
                session.copy(
                    totalDistanceMeters = RunningRepository.totalDistanceMeters.value,
                    durationSeconds = duration,
                    isCompleted = false
                )
            )

            val noteText = buildString {
                append("Dystans: %.2f km".format(distanceKm))
                if (targetKm > 0f) append(" | Cel: %.2f km".format(targetKm))
                append(" | Czas: ${formatDuration(duration)} (odroczone)")
            }

            // Wpis odroczonego biegu na nowy termin (move semantics — nie klonuj)
            if (daysOffset > 0) {
                val cal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, daysOffset)
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val deferredEntry = TaskHistoryEntity(
                    taskId = session.taskId,
                    syncId = UUID.randomUUID().toString(),
                    taskName = session.taskName,
                    category = "Sport",
                    date = cal.timeInMillis,
                    completed = false,
                    note = noteText,
                    isAIGenerated = session.aiMainName != null,
                    aiMainName = session.aiMainName ?: aiMainName
                )
                taskHistoryDao.insert(deferredEntry)
                syncHistoryToFirebase(deferredEntry)
            } else {
                // daysOffset == 0 ("dzisiaj") — zachowaj jako odroczone na dziś
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val deferredEntry = TaskHistoryEntity(
                    taskId = session.taskId,
                    syncId = UUID.randomUUID().toString(),
                    taskName = session.taskName,
                    category = "Sport",
                    date = cal.timeInMillis,
                    completed = false,
                    note = noteText,
                    isAIGenerated = session.aiMainName != null,
                    aiMainName = session.aiMainName ?: aiMainName
                )
                taskHistoryDao.insert(deferredEntry)
                syncHistoryToFirebase(deferredEntry)
            }
            // Nie tworzymy osobnego wpisu "niedokończone" na dziś — bieg przeniesiony
            onComplete(distanceKm, targetKm)
        }
    }

    fun scheduleRunForLater(context: Context, dateMillis: Long, taskName: String) {
        viewModelScope.launch {
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, "Bieganie: $taskName")
                    put(CalendarContract.Events.DTSTART, dateMillis)
                    put(CalendarContract.Events.DTEND, dateMillis + 3_600_000L)
                    put(CalendarContract.Events.CALENDAR_ID, 1L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    put(CalendarContract.Events.DESCRIPTION, "Zaplanowany bieg — dokończ zadanie z aplikacji AI Growth Tracker")
                }
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            } catch (_: Exception) {}
        }
    }

    private fun syncRunToFirebase(session: RunSessionEntity) {
        viewModelScope.launch {
            val userId = AuthManager.getUserId() ?: return@launch
            val data = mapOf(
                "id" to session.id,
                "syncId" to session.syncId,
                "taskId" to session.taskId,
                "taskName" to session.taskName,
                "aiMainName" to session.aiMainName,
                "targetDistanceKm" to session.targetDistanceKm,
                "startTime" to session.startTime,
                "endTime" to session.endTime,
                "totalDistanceMeters" to session.totalDistanceMeters,
                "durationSeconds" to session.durationSeconds,
                "avgSpeedKmh" to session.avgSpeedKmh,
                "avgPaceSecPerKm" to session.avgPaceSecPerKm,
                "rating" to session.rating,
                "note" to session.note,
                "isCompleted" to session.isCompleted
            )
            FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("run_sessions")
                .document(session.syncId)
                .set(data)
        }
    }

    /**
     * Pobiera historię biegów z Firebase i wpisuje do lokalnej bazy.
     * Idempotentne — istniejące sesje (po syncId) są pomijane.
     * Wywoływać przy starcie aplikacji jeśli lokalna baza jest pusta.
     */
    fun loadRunsFromFirebase() {
        viewModelScope.launch {
            val userId = AuthManager.getUserId() ?: return@launch
            // Sync tylko jeśli lokalna baza jest pusta — żeby nie nadpisywać aktywnych sesji
            if (runDao.getSessionCount() > 0) return@launch
            try {
                val snapshot = FirebaseRepository.db
                    .collection("users").document(userId)
                    .collection("run_sessions")
                    .get().await()
                snapshot.documents.forEach { doc ->
                    val syncId = doc.getString("syncId") ?: return@forEach
                    if (runDao.getSessionBySyncId(syncId) != null) return@forEach
                    val session = RunSessionEntity(
                        id = 0, // Room auto-generate nowe id lokalnie
                        syncId = syncId,
                        taskId = (doc.getLong("taskId") ?: -999L).toInt(),
                        taskName = doc.getString("taskName") ?: "Bieganie",
                        aiMainName = doc.getString("aiMainName"),
                        targetDistanceKm = doc.getDouble("targetDistanceKm")?.toFloat(),
                        startTime = doc.getLong("startTime") ?: System.currentTimeMillis(),
                        endTime = doc.getLong("endTime"),
                        totalDistanceMeters = doc.getDouble("totalDistanceMeters")?.toFloat() ?: 0f,
                        durationSeconds = doc.getLong("durationSeconds") ?: 0L,
                        avgSpeedKmh = doc.getDouble("avgSpeedKmh")?.toFloat() ?: 0f,
                        avgPaceSecPerKm = doc.getDouble("avgPaceSecPerKm")?.toFloat() ?: 0f,
                        isCompleted = doc.getBoolean("isCompleted") ?: false,
                        rating = doc.getLong("rating")?.toInt(),
                        note = doc.getString("note")
                    )
                    runDao.insertSession(session)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun getRoutePointsForSession(sessionId: Int): List<RunRoutePointEntity> =
        runDao.getRoutePointsForSession(sessionId)

    suspend fun getSessionById(sessionId: Int): RunSessionEntity? =
        runDao.getSessionById(sessionId)

    suspend fun getTotalDistanceKm(): Float =
        runDao.getTotalDistanceMeters() / 1000f

    suspend fun getCompletedRunCount(): Int =
        runDao.getCompletedRunCount()

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    fun formatPace(secPerKm: Float): String {
        if (secPerKm <= 0f) return "--:--"
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d".format(min, sec)
    }
}
