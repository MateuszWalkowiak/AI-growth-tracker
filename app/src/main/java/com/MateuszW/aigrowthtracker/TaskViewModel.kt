package com.MateuszW.aigrowthtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.MateuszW.aigrowthtracker.data.TaskDao
import com.MateuszW.aigrowthtracker.data.TaskEntity
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.TaskHistoryDao
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Context

class TaskViewModel(
    private val taskDao: TaskDao,
    private val taskHistoryDao: TaskHistoryDao
) : ViewModel() {

    private val _activeTask = MutableStateFlow<TaskEntity?>(null)
    val activeTask: StateFlow<TaskEntity?> = _activeTask

    private val _remainingTime = MutableStateFlow(0)
    val remainingTime: StateFlow<Int> = _remainingTime
    val allTasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10000), emptyList())

    val existingCategories: StateFlow<List<String>> = taskDao.getUniqueCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiTaskSuggestion = MutableStateFlow<String?>(null)
    val aiTaskSuggestion: StateFlow<String?> = _aiTaskSuggestion

    //zapis zadan do bazy
    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.insertTask(task)
        }
    }
    fun startTask(task: TaskEntity, context: Context) {
        _activeTask.value = task
        _remainingTime.value = task.durationMinutes * 60

        viewModelScope.launch {
            while (_remainingTime.value > 0) {
                delay(1000)
                _remainingTime.value -= 1
            }

            NotificationHelper.showNotification(
                context,
                "Czas minął",
                "Sprawdź swoje zadanie!"
            )
        }
    }

    fun finishTask() {
        _activeTask.value?.let { task ->
            viewModelScope.launch {

                //zapis wykonania
                taskDao.insertTask(task.copy(isCompleted = true))

                //zapis historii
                val history = TaskHistoryEntity(
                    taskId = task.id,
                    taskName = task.taskName,
                    category = task.category,
                    date = System.currentTimeMillis(),
                    completed = true
                )

                taskHistoryDao.insert(history)
            }
        }
        _activeTask.value = null
    }

    fun cancelTask() {
        _activeTask.value = null
    }

    fun generateTaskWithGemini(task: TaskEntity) {
        viewModelScope.launch {
            try {
                _aiTaskSuggestion.value = "Gemini analizuje Twój profil..."

                val apiKey = "TU_WKLEJ_SWOJ_KLUCZ_API"//TO DO
                val model = GenerativeModel("gemini-1.5-flash", apiKey)
                //PROMPT WYSYŁANY DO GEMINI (OUTPUT=ZADNIE)
                val prompt = """
                    Jesteś wirtualnym trenerem rozwoju osobistego. 
                    Użytkownik ma zadanie: ${task.taskName} (${task.category})
                    Czas: ${task.durationMinutes} min
                    Poziom: ${task.aiCurrentLevel ?: "Brak"}
                    Cel: ${task.aiGoals ?: "Rozwój"}
                    
                    Daj 1 konkretne zadanie.
                """.trimIndent()

                val response = model.generateContent(prompt)
                _aiTaskSuggestion.value = response.text

            } catch (e: Exception) {
                _aiTaskSuggestion.value = "Błąd: ${e.message}"
            }
        }
    }

    fun clearAiSuggestion() {
        _aiTaskSuggestion.value = null
    }
}