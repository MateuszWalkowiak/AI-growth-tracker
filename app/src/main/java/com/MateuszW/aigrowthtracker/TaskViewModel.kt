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
import com.MateuszW.aigrowthtracker.data.TaskSummary
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.Calendar
import com.MateuszW.aigrowthtracker.BuildConfig

class TaskViewModel(
    private val taskDao: TaskDao,
    private val taskHistoryDao: TaskHistoryDao,
) : ViewModel() {
    private var timerJob: kotlinx.coroutines.Job? = null
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://calendarific.com/api/v2/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val holidayService = retrofit.create(HolidayService::class.java)

    val holidays = mutableStateOf<List<Long>>(emptyList())
    val showHolidays = mutableStateOf(true)
    val showWeekends = mutableStateOf(true)

    init {
        if (holidays.value.isEmpty()) {
            loadHolidays()
        }
    }

    val taskSummaries: StateFlow<List<TaskSummary>> =
        taskHistoryDao.getTaskSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<TaskHistoryEntity>> =
        taskHistoryDao.getAllHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTask = MutableStateFlow<TaskEntity?>(null)
    val activeTask: StateFlow<TaskEntity?> = _activeTask

    private val _remainingTime = MutableStateFlow(0)
    val remainingTime: StateFlow<Int> = _remainingTime
    val allTasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10000), emptyList())

    val existingCategories: StateFlow<List<String>> = taskDao.getUniqueCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDates: StateFlow<List<Long>> =
        taskHistoryDao.getCompletedDates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accountCreatedAt = System.currentTimeMillis()
    private val _aiTaskSuggestion = MutableStateFlow<String?>(null)
    val aiTaskSuggestion: StateFlow<String?> = _aiTaskSuggestion

    //zapis zadan do bazy
    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.insertTask(task)
        }
    }
    fun startTask(task: TaskEntity, context: Context) {

        timerJob?.cancel()
        _activeTask.value = task
        _remainingTime.value = task.durationMinutes * 60
        timerJob = viewModelScope.launch {
            while (_remainingTime.value > 0 && _activeTask.value != null) {
                delay(1000)
                _remainingTime.value -= 1
            }

            if (_activeTask.value != null) {
                NotificationHelper.showNotification(
                    context,
                    "Czas minął",
                    "Sprawdź swoje zadanie!"
                )
            }
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
        timerJob?.cancel()
        _activeTask.value = null
    }

    fun finishTaskWithSummary(rating: Int, note: String) {
        timerJob?.cancel()
        _activeTask.value?.let { task ->
            viewModelScope.launch {

                taskDao.insertTask(task.copy(isCompleted = true))

                //zapis historii
                val history = TaskHistoryEntity(
                    taskId = task.id,
                    taskName = task.taskName,
                    category = task.category,
                    date = System.currentTimeMillis(),
                    completed = true,
                    rating = rating,
                    note = note
                )

                taskHistoryDao.insert(history)
            }
        }

        _activeTask.value = null
    }
    fun getTaskHistory(taskId: Int): StateFlow<List<TaskHistoryEntity>> =
        taskHistoryDao.getHistoryForTask(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dayStart(time: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getHistoryForDay(day: Long): StateFlow<List<TaskHistoryEntity>> {

        val start = dayStart(day)
        val end = start + 86400000

        return taskHistoryDao.getHistoryForDay(start, end)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun loadHolidays() {
        viewModelScope.launch {
            try {
                val holidaysResponse = holidayService.getHolidays(
                    key = BuildConfig.HOLIDAY_API_KEY,
                    year = Calendar.getInstance().get(Calendar.YEAR)
                )

                val parsed = holidaysResponse.response.holidays
                    //filtrowanie swiat
                    .filter { holiday ->

                        val types = holiday.type

                        val isRealHoliday =
                            types.contains("National holiday") ||
                                    types.contains("Public holiday")

                        val isImportantFallback =
                            holiday.name.contains("Easter", ignoreCase = true) ||
                                    holiday.name.contains("Christmas", ignoreCase = true) ||
                                    holiday.name.contains("New Year", ignoreCase = true) ||
                                    holiday.name.contains("Wielkanoc", ignoreCase = true) ||
                                    holiday.name.contains("Boże Narodzenie", ignoreCase = true) ||
                                    holiday.name.contains("Sylwester", ignoreCase = true)

                        isRealHoliday || isImportantFallback
                    }
                    .map { holiday ->
                        normalizeDay(
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .parse(holiday.date.iso)!!
                                .time
                        )
                    }

                holidays.value = parsed

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
    fun normalizeDay(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}