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
import android.util.Log
import com.MateuszW.aigrowthtracker.data.TaskSummary
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.Calendar
import com.MateuszW.aigrowthtracker.BuildConfig
import androidx.core.content.edit
import com.MateuszW.aigrowthtracker.data.AuthManager
import com.MateuszW.aigrowthtracker.data.FirebaseRepository
import com.MateuszW.aigrowthtracker.data.WeeklyTaskDb
import com.MateuszW.aigrowthtracker.data.WeeklyTaskEntity
import com.MateuszW.aigrowthtracker.data.WeeklyTaskType
import com.MateuszW.aigrowthtracker.data.toMap
import kotlinx.coroutines.tasks.await
import com.MateuszW.aigrowthtracker.data.WeeklyTaskDao

class TaskViewModel(
    private val taskDao: TaskDao,
    private val taskHistoryDao: TaskHistoryDao,
    private val weeklyTaskDao: WeeklyTaskDao
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

    val taskSummaries: StateFlow<List<TaskSummary>> =
        taskHistoryDao.getTaskSummaries()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<TaskHistoryEntity>> =
        taskHistoryDao.getAllHistory()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeTask = MutableStateFlow<TaskEntity?>(null)
    val activeTask: StateFlow<TaskEntity?> = _activeTask

    private val _remainingTime = MutableStateFlow(0)
    val remainingTime: StateFlow<Int> = _remainingTime
    val allTasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val existingCategories: StateFlow<List<String>> = taskDao.getUniqueCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val completedDates: StateFlow<List<Long>> =
        taskHistoryDao.getCompletedDates()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accountCreatedAt = System.currentTimeMillis()
    private val _aiTaskSuggestion = MutableStateFlow<String?>(null)
    val aiTaskSuggestion: StateFlow<String?> = _aiTaskSuggestion

    private val _weeklyTasks = MutableStateFlow<List<WeeklyTaskEntity>>(emptyList())
    val weeklyTasks: StateFlow<List<WeeklyTaskEntity>> = _weeklyTasks

    private val _weekPlanned = MutableStateFlow(false)
    val weekPlanned: StateFlow<Boolean> = _weekPlanned

    private val _aiDailyTasks = MutableStateFlow<List<String>>(emptyList())
    val aiDailyTasks: StateFlow<List<String>> = _aiDailyTasks

    fun setAiDailyTasks(tasks: List<String>) {
        _aiDailyTasks.value = tasks
    }




    //zapis zadan do bazy
    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {

            val userId = AuthManager.getUserId() ?: return@launch

            val newId = taskDao.insertTask(task)
            val newTask = task.copy(id = newId.toInt())

            FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("tasks")
                .document(newTask.id.toString())
                .set(newTask.toMap())
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
                val userId = AuthManager.getUserId() ?: return@launch

                FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("history")
                    .add(history.toMap())
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

                val userId = AuthManager.getUserId()

                if (userId == null) {
                    return@launch
                }

                FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("history")
                    .add(history.toMap())
            }
        }

        _activeTask.value = null
    }
    fun getTaskHistory(taskId: Int): StateFlow<List<TaskHistoryEntity>> =
        taskHistoryDao.getHistoryForTask(taskId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    suspend fun fetchFromApi(key: String): List<Long> {
        return try {
            val response = holidayService.getHolidays(
                key = key,
                year = Calendar.getInstance().get(Calendar.YEAR)
            )

            response.response.holidays
                .filter { holiday ->

                    val name = holiday.name.lowercase()

                    val isImportantByName =
                        name.contains("easter") ||
                                name.contains("christmas") ||
                                name.contains("new year's eve") ||
                                name.contains("new year") ||
                                name.contains("all saints") ||
                                name.contains("independence") ||
                                name.contains("constitution")

                    val isImportantByType =
                        holiday.type.contains("National holiday") ||
                                holiday.type.contains("Christian")

                    val isPolishNames =
                        name.contains("boże narodzenie") ||
                                name.contains("wielkanoc") ||
                                name.contains("sylwester") ||
                                name.contains("nowy rok")

                    isImportantByName || isImportantByType || isPolishNames
                }
                .map {
                    normalizeDay(
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .parse(it.date.iso)!!.time
                    )
                }

        } catch (e: Exception) {
            Log.e("API", "FETCH ERROR: ${e.message}")
            emptyList()
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

    fun toggleWeekends(context: Context, value: Boolean) {
        showWeekends.value = value

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("show_weekends", value) }

        saveSettingsToFirebase(context)
    }

    fun toggleHolidays(context: Context, value: Boolean) {
        showHolidays.value = value

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("show_holidays", value) }

        saveSettingsToFirebase(context)
    }

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit {
                putString("holiday_api_key", key)
            }
    }


    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
            taskHistoryDao.deleteHistoryForTask(task.id)

            val userId = AuthManager.getUserId() ?: return@launch

            FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("tasks")
                .document(task.id.toString())
                .delete()
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task)

            val userId = AuthManager.getUserId() ?: return@launch

            FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("tasks")
                .document(task.id.toString())
                .set(task.toMap())
        }
    }
    fun saveSettingsToFirebase(context: Context) {
        val userId = AuthManager.getUserId() ?: return

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        val data = mapOf(
            "showWeekends" to prefs.getBoolean("show_weekends", true),
            "showHolidays" to prefs.getBoolean("show_holidays", true)
        )

        FirebaseRepository.db
            .collection("users")
            .document(userId)
            .collection("settings")
            .document("main")
            .set(data)
    }

    fun syncFromFirebase() {
        viewModelScope.launch {

            val userId = AuthManager.getUserId() ?: return@launch

            val tasksSnapshot = FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("tasks")
                .get()
                .await()

            tasksSnapshot.documents.forEach { doc ->
                val task = TaskEntity(
                    id = doc.getLong("id")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "",
                    taskName = doc.getString("taskName") ?: "",
                    durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
                    isMandatory = doc.getBoolean("isMandatory") ?: false,
                    isAIGenerated = doc.getBoolean("isAIGenerated") ?: false,
                    isCompleted = doc.getBoolean("isCompleted") ?: false
                )

                taskDao.insertTask(task)
            }
        }
    }

    fun saveHolidaysLocally(context: Context, list: List<Long>) {
        val prefs = context.getSharedPreferences("holidays", Context.MODE_PRIVATE)
        prefs.edit { putString("data", list.joinToString(",")) }
    }

    fun loadHolidaysFromLocal(context: Context): List<Long> {
        val prefs = context.getSharedPreferences("holidays", Context.MODE_PRIVATE)
        val data = prefs.getString("data", "") ?: ""
        return if (data.isEmpty()) emptyList()
        else data.split(",").map { it.toLong() }
    }

    suspend fun loadHolidaysFromFirebase(): List<Long> {
        val userId = AuthManager.getUserId() ?: return emptyList()

        val doc = FirebaseRepository.db
            .collection("users")
            .document(userId)
            .collection("holidays")
            .document("current")
            .get()
            .await()

        val list = (doc.get("data") as? List<*>)?.mapNotNull {
            (it as? Number)?.toLong()
        } ?: emptyList()
        return list
    }

    suspend fun loadWeeklyFromLocal(): List<WeeklyTaskEntity> {
        return weeklyTaskDao.getAll().map {
            WeeklyTaskEntity(
                id = it.id,
                name = it.name,
                type = WeeklyTaskType.valueOf(it.type),
                targetValue = it.targetValue,
                currentValue = it.currentValue,
                step = it.step,
                isAIGenerated = it.isAIGenerated
            )
        }
    }

    suspend fun loadWeeklyFromFirebase(): List<WeeklyTaskEntity> {
        val userId = AuthManager.getUserId() ?: return emptyList()

        val snapshot = FirebaseRepository.db
            .collection("users")
            .document(userId)
            .collection("weekly_tasks")
            .get()
            .await()

        return snapshot.documents.map {
            WeeklyTaskEntity(
                id = it.getLong("id")?.toInt() ?: 0,
                name = it.getString("name") ?: "",
                type = WeeklyTaskType.valueOf(it.getString("type") ?: "COUNTER"),
                targetValue = it.getDouble("targetValue")?.toFloat() ?: 0f,
                currentValue = it.getDouble("currentValue")?.toFloat() ?: 0f,
                step = it.getDouble("step")?.toFloat() ?: 1f,
                isAIGenerated = it.getBoolean("isAIGenerated") ?: false
            )
        }
    }

    fun saveHolidaysToFirebase(list: List<Long>) {
        val userId = AuthManager.getUserId() ?: return

        FirebaseRepository.db
            .collection("users")
            .document(userId)
            .collection("holidays")
            .document("current")
            .set(mapOf("data" to list))
    }

    fun onApiKeyConfirmed(context: Context, key: String) {
        if (key.isBlank()) {
            Log.e("API", "EMPTY API KEY")
            return
        }
        saveApiKey(context, key)
        loadHolidaysSmart(context)
    }

    fun loadHolidaysSmart(context: Context) {
        viewModelScope.launch {

            //pobierz liste swiat z lokalnej bazy
            val local = loadHolidaysFromLocal(context)

            if (local.isNotEmpty()) {
                holidays.value = local
                return@launch
            }

            //pobierz liste swiat z zewnetrznej bazy
            val remote = loadHolidaysFromFirebase()

            if (remote.isNotEmpty()) {
                holidays.value = remote
                saveHolidaysLocally(context, remote)
                return@launch
            }

            //pobierz liste swiat z calendarific
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("holiday_api_key", "") ?: ""

            if (apiKey.isNotBlank()) {
                val api = fetchFromApi(apiKey)

                holidays.value = api
                saveHolidaysLocally(context, api)
                saveHolidaysToFirebase(api)
            }
        }
    }

    fun loadSettingsSmart(context: Context) {
        viewModelScope.launch {

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            val localWeekends = prefs.getBoolean("show_weekends", false)
            val localHolidays = prefs.getBoolean("show_holidays", false)

            showWeekends.value = localWeekends
            showHolidays.value = localHolidays

            val isFirstRun = !prefs.contains("show_weekends") || !prefs.contains("show_holidays")

            if (isFirstRun) {
                val userId = AuthManager.getUserId() ?: return@launch

                val doc = FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("settings")
                    .document("main")
                    .get()
                    .await()

                val fbWeekends = doc.getBoolean("showWeekends")
                val fbHolidays = doc.getBoolean("showHolidays")

                if (fbWeekends != null && fbHolidays != null) {

                    showWeekends.value = fbWeekends
                    showHolidays.value = fbHolidays

                    prefs.edit {
                        putBoolean("show_weekends", fbWeekends)
                            .putBoolean("show_holidays", fbHolidays)
                    }
                }
            }
        }
    }

    fun setWeeklyTasks(tasks: List<WeeklyTaskEntity>) {
        _weeklyTasks.value = tasks
        _weekPlanned.value = true
    }

    fun incrementWeeklyTask(id: Int) {
        _weeklyTasks.value = _weeklyTasks.value.map {
            if (it.id == id) it.copy(currentValue = it.currentValue + it.step)
            else it
        }
    }

    fun decrementWeeklyTask(id: Int) {
        _weeklyTasks.value = _weeklyTasks.value.map {
            if (it.id == id) it.copy(currentValue = (it.currentValue - it.step).coerceAtLeast(0f))
            else it
        }
    }

    fun createWeeklyTasks(
        difficulty: Int,
        goals: Map<String, String>
    ) {
        val tasks = goals.entries.mapIndexed { index, entry ->
            val name = entry.key
            val goal = entry.value

            WeeklyTaskEntity(
                id = index,
                name = name,
                type = WeeklyTaskType.COUNTER,
                targetValue = difficulty.toFloat(),
                step = 1f,
                isAIGenerated = false
            )
        }

        _weeklyTasks.value = tasks
        _weekPlanned.value = true
    }

    fun checkNextWeekHolidays(): Boolean {
        val nextWeek = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis

        return holidays.value.any { it >= nextWeek }
    }

    fun WeeklyTaskEntity.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "type" to type.name,
            "targetValue" to targetValue,
            "currentValue" to currentValue,
            "step" to step,
            "isAIGenerated" to isAIGenerated
        )
    }

    fun saveWeeklyTasksToFirebase() {
        viewModelScope.launch {

            val userId = AuthManager.getUserId() ?: return@launch

            _weeklyTasks.value.forEach { task ->

                FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("weekly_tasks")
                    .document(task.id.toString())
                    .set(task.toMap())
            }
        }
    }

    fun saveWeeklyTasksLocal() {
        viewModelScope.launch {

            val dbTasks = _weeklyTasks.value.map {
                WeeklyTaskDb(
                    id = it.id,
                    name = it.name,
                    type = it.type.name,
                    targetValue = it.targetValue,
                    currentValue = it.currentValue,
                    step = it.step,
                    isAIGenerated = it.isAIGenerated
                )
            }

            weeklyTaskDao.clearAll()
            weeklyTaskDao.insertAll(dbTasks)
        }
    }

}