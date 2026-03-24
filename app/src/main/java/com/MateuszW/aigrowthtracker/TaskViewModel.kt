package com.MateuszW.aigrowthtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.MateuszW.aigrowthtracker.data.TaskDao
import com.MateuszW.aigrowthtracker.data.TaskEntity
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val taskDao: TaskDao) : ViewModel() {

    val allTasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10000), emptyList())

    private val _aiTaskSuggestion = MutableStateFlow<String?>(null)
    val aiTaskSuggestion: StateFlow<String?> = _aiTaskSuggestion

    //zapis zadan do bazy
    fun saveTask(task: TaskEntity) {
        viewModelScope.launch { taskDao.insertTask(task) }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch { taskDao.insertTask(task.copy(isCompleted = !task.isCompleted)) }
    }

    fun generateTaskWithGemini(task: TaskEntity) {
        viewModelScope.launch {
            try {
                _aiTaskSuggestion.value = "Gemini analizuje Twój profil i generuje zadanie..."

                val apiKey = "TU_WKLEJ_SWOJ_KLUCZ_API"//TO DO
                val model = GenerativeModel("gemini-1.5-flash", apiKey)

                //PROMPT WYSYŁANY DO GEMINI (OUTPUT=ZADNIE)
                val prompt = """
                    Jesteś wirtualnym trenerem rozwoju osobistego. 
                    Użytkownik ma do wykonania zadanie z kategorii: ${task.category} (${task.taskName}).
                    Chce na to poświęcić ${task.durationMinutes} minut.
                    Jego poziom zaawansowania to: ${task.aiCurrentLevel ?: "Brak danych"}.
                    Jego główny cel to: ${task.aiGoals ?: "Rozwój ogólny"}.
                    
                    Wygeneruj JEDNO bardzo konkretne, ciekawe i praktyczne zadanie na teraz, 
                    które idealnie wpasuje się w ten czas i poziom. Bądź bezpośredni i motywujący.
                """.trimIndent()

                val response = model.generateContent(prompt)
                _aiTaskSuggestion.value = response.text
            } catch (e: Exception) {
                _aiTaskSuggestion.value = "Błąd połączenia z AI: ${e.message}"
            }
        }
    }

    fun clearAiSuggestion() {
        _aiTaskSuggestion.value = null
    }
}