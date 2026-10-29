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
import androidx.core.content.edit
import com.MateuszW.aigrowthtracker.data.AiWeeklyPlan
import com.MateuszW.aigrowthtracker.data.AiWeeklyTask
import com.MateuszW.aigrowthtracker.data.AuthManager
import com.MateuszW.aigrowthtracker.data.FirebaseRepository
import com.MateuszW.aigrowthtracker.data.WeeklyTaskDb
import com.MateuszW.aigrowthtracker.data.WeeklyTaskEntity
import com.MateuszW.aigrowthtracker.data.WeeklyTaskType
import com.MateuszW.aigrowthtracker.data.toMap
import kotlinx.coroutines.tasks.await
import com.MateuszW.aigrowthtracker.data.WeeklyTaskDao
import com.MateuszW.aigrowthtracker.data.AiDailyTask
import android.content.ContentValues
import android.provider.CalendarContract
import com.MateuszW.aigrowthtracker.data.WeeklyFlowRecordEntity
import com.MateuszW.aigrowthtracker.data.WeeklyFlowRecordDao
import com.MateuszW.aigrowthtracker.data.MandatoryScheduleEntity
import com.google.gson.Gson
import com.MateuszW.aigrowthtracker.data.WeeklyGoalDraft
import com.MateuszW.aigrowthtracker.data.WeeklyReviewInput
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Snapshot stanu Health Connect po ostatnim wywołaniu refreshTodaySteps().
 * Służy wyłącznie do diagnostyki - wyświetlany na ekranie debug
 */
data class HcDiagnostics(
    val sdkStatus: Int,                   // 3=available, 2=update required, 1=unavailable
    val grantedPermissions: Set<String>,
    val readPermRequired: String,
    val permissionGranted: Boolean,
    val startOfDay: String,
    val endTime: String,
    val zoneId: String,
    val aggregateRawValue: Long?,         //  null = brak źródła danych
    val todayStepsAfterUpdate: Long,
    val recordsCount: Int,                //  z readRecords (gdy aggregate=null/0)
    val firstRecord: String?,
    val lastRecord: String?,
    val exception: String?
)

data class AiTaskInstruction(
    val instructions: String,
    val suggestedMinutes: Int,
    val taskId: Int
)

class TaskViewModel(
    private val taskDao: TaskDao,
    private val taskHistoryDao: TaskHistoryDao,
    private val weeklyTaskDao: WeeklyTaskDao,
    private val weeklyFlowRecordDao: WeeklyFlowRecordDao,
    private val mandatoryScheduleDao: com.MateuszW.aigrowthtracker.data.MandatoryScheduleDao
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

    private val _aiTaskSuggestion = MutableStateFlow<String?>(null)
    val aiTaskSuggestion: StateFlow<String?> = _aiTaskSuggestion

    private val _aiTaskInstruction = MutableStateFlow<AiTaskInstruction?>(null)
    val aiTaskInstruction: StateFlow<AiTaskInstruction?> = _aiTaskInstruction

    private val _aiInstructionLoading = MutableStateFlow(false)
    val aiInstructionLoading: StateFlow<Boolean> = _aiInstructionLoading

    private val _aiInstructionLoadingTaskId = MutableStateFlow<Int?>(null)
    val aiInstructionLoadingTaskId: StateFlow<Int?> = _aiInstructionLoadingTaskId

    private val _weeklyTasks = MutableStateFlow<List<WeeklyTaskEntity>>(emptyList())
    val weeklyTasks: StateFlow<List<WeeklyTaskEntity>> = _weeklyTasks

    private val _weekPlanned = MutableStateFlow(false)
    val weekPlanned: StateFlow<Boolean> = _weekPlanned

    private val _plannedWeekStartMillis = MutableStateFlow<Long?>(null)
    val plannedWeekStartMillis: StateFlow<Long?> = _plannedWeekStartMillis

    private val _aiDailyTasks = MutableStateFlow<Map<String, List<AiDailyTask>>>(emptyMap())
    val aiDailyTasks: StateFlow<Map<String, List<AiDailyTask>>> = _aiDailyTasks
    private val _aiPlan = MutableStateFlow<AiWeeklyPlan?>(null)
    val aiPlan: StateFlow<AiWeeklyPlan?> = _aiPlan

    private val _weeklyReviewSummary = MutableStateFlow<String?>(null)
    val weeklyReviewSummary: StateFlow<String?> = _weeklyReviewSummary

    // "bad" | "neutral" | "great" - ocena tygodnia przez Gemini
    private val _weekQuality = MutableStateFlow<String?>(null)
    val weekQuality: StateFlow<String?> = _weekQuality

    private val _weeklyFlowState = MutableStateFlow<String?>(null)
    val weeklyFlowState: StateFlow<String?> = _weeklyFlowState

    val isTestMode = mutableStateOf(false)
    val insertToSystemCalendar = mutableStateOf(false)
    val calendarEventHour = mutableStateOf(17) //Domyślna godzina dodawania do kalendarza

    private val _isPlanningWeek = MutableStateFlow(false)
    val isPlanningWeek: StateFlow<Boolean> = _isPlanningWeek

    //  ── Kroki (Health Connect) ──────────────────────────────────────────────
    private val _todaySteps = MutableStateFlow(0L)
    val todaySteps: StateFlow<Long> = _todaySteps

    //  Punkt startowy kroków dla zadań STEPS uruchomionych ręcznie (taskId → baseline)
    private val _stepsBaselines = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val stepsBaselines: StateFlow<Map<Int, Long>> = _stepsBaselines

    //true → UI powinno poprosić użytkownika o uprawnienie Health Connect
    private val _hcPermissionNeeded = MutableStateFlow(false)
    val hcPermissionNeeded: StateFlow<Boolean> = _hcPermissionNeeded

    //  true → HC działa, uprawnienia OK, ale brak źródła danych (np. brak podłączonej app fitness)
    private val _hcNoDataSource = MutableStateFlow(false)
    val hcNoDataSource: StateFlow<Boolean> = _hcNoDataSource

    //true → trwa właśnie odczyt kroków (spinner)
    private val _hcRefreshing = MutableStateFlow(false)
    val hcRefreshing: StateFlow<Boolean> = _hcRefreshing

    // Pełna diagnostyka HC po ostatnim odczycie (null = jeszcze nie wywołano)
    private val _hcDiagnostics = MutableStateFlow<HcDiagnostics?>(null)
    val hcDiagnostics: StateFlow<HcDiagnostics?> = _hcDiagnostics

    // zapis zadan do bazy
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

            // Rozpisz mandatory task do najbliższej niedzieli
            if (newTask.isMandatory) {
                val today = normalizeDay(System.currentTimeMillis())
                val sunday = upcomingSundayInclusive(today)
                scheduleMandatoryRange(newTask, today, sunday)
            }
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

    fun cancelTask() {
        timerJob?.cancel()
        _activeTask.value = null
    }
    fun getTaskHistory(taskId: Int): StateFlow<List<TaskHistoryEntity>> =
        taskHistoryDao.getHistoryForTask(taskId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getHistoryForDay(day: Long): StateFlow<List<TaskHistoryEntity>> {
        val start = normalizeDay(day)
        val end = start + 86_400_000L
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

    fun generateTaskWithGemini(task: TaskEntity, context: Context) {
        viewModelScope.launch {
            try {
                _aiTaskSuggestion.value = "Gemini analizuje Twój profil..."

                val apiKey = getGeminiKey(context)
                if (apiKey.isBlank()) {
                    _aiTaskSuggestion.value = "Brak klucza API Gemini — dodaj go w Ustawieniach."
                    return@launch
                }
                val model = GenerativeModel("gemini-2.5-flash", apiKey)
                // PROMPT WYSYŁANY DO GEMINI (OUTPUT=ZADNIE)
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

    fun generateAiTaskInstruction(task: TaskEntity, context: Context) {
        viewModelScope.launch {
            _aiInstructionLoading.value = true
            _aiInstructionLoadingTaskId.value = task.id
            _aiTaskInstruction.value = null
            try {
                val apiKey = getGeminiKey(context)
                if (apiKey.isBlank()) {
                    _aiTaskInstruction.value = AiTaskInstruction(
                        instructions = "Brak klucza API Gemini — dodaj go w Ustawieniach.",
                        suggestedMinutes = task.durationMinutes,
                        taskId = task.id
                    )
                    return@launch
                }

                val historyJson = buildHistoryForAI()
                val cal = Calendar.getInstance()
                val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "poniedzialek"
                    Calendar.TUESDAY -> "wtorek"
                    Calendar.WEDNESDAY -> "sroda"
                    Calendar.THURSDAY -> "czwartek"
                    Calendar.FRIDAY -> "piatek"
                    Calendar.SATURDAY -> "sobota"
                    Calendar.SUNDAY -> "niedziela"
                    else -> "dzien"
                }
                val currentHour = cal.get(Calendar.HOUR_OF_DAY)

                val prompt = """
Jestes elitarnym trenerem rozwoju osobistego i asystentem AI.

Uzytkownik ma zadanie: ${task.taskName}
Kategoria: ${task.category}
Domyslny czas: ${task.durationMinutes} min
Aktualny poziom: ${task.aiCurrentLevel ?: "Nie podano"}
Cel: ${task.aiGoals ?: "Rozwoj osobisty"}
Dzien tygodnia: $dayOfWeek
Godzina: $currentHour

Historia (28 dni): $historyJson

Na podstawie tych informacji:
1. Zaproponuj KONKRETNE instrukcje krok po kroku do wykonania tego zadania TERAZ
2. Dostosuj trudnosc i czas do poziomu uzytkownika, pory dnia i historii
3. Badz motywujacy ale konkretny — uzytkownik ma wiedziec DOKLADNIE co robic

Zwroc TYLKO poprawny JSON (bez markdown):
{
  "instructions": "Konkretne instrukcje krok po kroku po polsku...",
  "suggestedMinutes": number
}
""".trimIndent()

                val model = GenerativeModel("gemini-2.5-flash", apiKey)
                val response = model.generateContent(prompt)
                val rawText = response.text?.trim() ?: ""

                val json = rawText
                    .let { if (it.startsWith("```")) it.substringAfter("\n") else it }
                    .removeSuffix("```").trim()

                try {
                    val obj = org.json.JSONObject(json)
                    _aiTaskInstruction.value = AiTaskInstruction(
                        instructions = obj.getString("instructions"),
                        suggestedMinutes = obj.optInt("suggestedMinutes", task.durationMinutes),
                        taskId = task.id
                    )
                } catch (_: Exception) {
                    // Fallback: use raw text as instructions
                    _aiTaskInstruction.value = AiTaskInstruction(
                        instructions = rawText.ifBlank { "Brak odpowiedzi od AI." },
                        suggestedMinutes = task.durationMinutes,
                        taskId = task.id
                    )
                }
            } catch (e: Exception) {
                _aiTaskInstruction.value = AiTaskInstruction(
                    instructions = "Blad AI: ${e.message}",
                    suggestedMinutes = task.durationMinutes,
                    taskId = task.id
                )
            } finally {
                _aiInstructionLoading.value = false
                _aiInstructionLoadingTaskId.value = null
            }
        }
    }

    fun clearAiInstruction() {
        _aiTaskInstruction.value = null
        _aiInstructionLoading.value = false
        _aiInstructionLoadingTaskId.value = null
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
        regenerateMandatoryScheduleFromToday()
    }

    fun toggleHolidays(context: Context, value: Boolean) {
        showHolidays.value = value

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("show_holidays", value) }

        saveSettingsToFirebase(context)
        regenerateMandatoryScheduleFromToday()
    }

    fun toggleSystemCalendar(context: Context, value: Boolean) {
        insertToSystemCalendar.value = value
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putBoolean("insert_system_calendar", value) }
    }

    fun setThemeMode(context: Context, mode: com.MateuszW.aigrowthtracker.ui.theme.ThemeMode) {
        com.MateuszW.aigrowthtracker.ui.theme.ThemeState.themeMode = mode
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putString("theme_mode", mode.name) }
    }

    fun setCalendarEventHour(context: Context, hour: Int) {
        calendarEventHour.value = hour
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putInt("calendar_event_hour", hour) }
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
            mandatoryScheduleDao.deleteForTask(task.id)

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

            // Reschedule mandatory: usuń przyszłe wpisy i rozpisz na nowo
            val today = normalizeDay(System.currentTimeMillis())
            mandatoryScheduleDao.deleteForTaskFromDate(task.id, today)
            if (task.isMandatory) {
                val sunday = upcomingSundayInclusive(today)
                scheduleMandatoryRange(task, today, sunday)
            }

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

            val tasksSnapshot = try {
                FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("tasks")
                    .get()
                    .await()
            } catch (_: Exception) { return@launch }

            // Wyczyść lokalną bazę przed sync - bez tego usunięte w Firebase taski
            // zostają w Room na zawsze (brak hard-delete sync)
            taskDao.deleteAllTasks()

            tasksSnapshot.documents.forEach { doc ->
                val taskType = doc.getString("taskType")
                val stepGoal = doc.getLong("stepGoal")?.toInt()
                val task = TaskEntity(
                    id = doc.getLong("id")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "",
                    taskName = doc.getString("taskName") ?: "",
                    durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
                    isMandatory = doc.getBoolean("isMandatory") ?: false,
                    isAIGenerated = doc.getBoolean("isAIGenerated") ?: false,
                    isCompleted = doc.getBoolean("isCompleted") ?: false,
                    taskType = taskType,
                    stepGoal = stepGoal,
                    recurrenceType = doc.getString("recurrenceType"),
                    recurrenceValue = doc.getString("recurrenceValue"),
                    aiCurrentLevel = doc.getString("aiCurrentLevel"),
                    aiGoals = doc.getString("aiGoals"),
                    stepsBaseline = doc.getLong("stepsBaseline"),
                    stepsStartedAt = doc.getLong("stepsStartedAt"),
                    stepsCountingMode = doc.getString("stepsCountingMode"),
                    targetKm = doc.getDouble("targetKm")?.toFloat() ?: 0f
                )
                Log.d("Steps", "[syncFromFirebase] task=${task.id}/${task.taskName} taskType=$taskType stepGoal=$stepGoal")
                taskDao.insertTask(task)
            }
            //Po sync rozpisz mandatory tasks na bieżący (i ewentualnie kolejny) tydzień
            ensureMandatoryScheduledForWeekSuspend()
        }
    }

    /**
     * Wyrzuca harmonogram mandatory tasks od dziś włącznie i rozpisuje na nowo
     * (z aktualnymi ustawieniami weekend/holiday). Wywoływać po: zmianie ustawień,
     * załadowaniu świąt, załadowaniu prefs
     */
    fun regenerateMandatoryScheduleFromToday() {
        viewModelScope.launch {
            val today = normalizeDay(System.currentTimeMillis())
            val all = taskDao.getAllTasksOnce()
            all.forEach { task ->
                mandatoryScheduleDao.deleteForTaskFromDate(task.id, today)
            }
            ensureMandatoryScheduledForWeekSuspend()
        }
    }

    //Wersja suspend - używana po syncFromFirebase żeby pokryć tasks z Firebase
    private suspend fun ensureMandatoryScheduledForWeekSuspend() {
        val today = normalizeDay(System.currentTimeMillis())
        val sunday = upcomingSundayInclusive(today)
        val lastScheduled = mandatoryScheduleDao.getLastScheduledDate() ?: 0L
        val allMandatory = taskDao.getAllTasksOnce().filter { it.isMandatory }
        allMandatory.forEach { task ->
            scheduleMandatoryRange(task, today, sunday)
        }
        if (lastScheduled >= sunday) {
            val nextMonday = Calendar.getInstance().apply {
                timeInMillis = sunday; add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            val nextSunday = Calendar.getInstance().apply {
                timeInMillis = nextMonday; add(Calendar.DAY_OF_YEAR, 6)
            }.timeInMillis
            allMandatory.forEach { task ->
                scheduleMandatoryRange(task, nextMonday, nextSunday)
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

        return try {
            val doc = FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("holidays")
                .document("current")
                .get()
                .await()

            (doc.get("data") as? List<*>)?.mapNotNull {
                (it as? Number)?.toLong()
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
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

        return try {
            val snapshot = FirebaseRepository.db
                .collection("users")
                .document(userId)
                .collection("weekly_tasks")
                .get()
                .await()

            snapshot.documents.map {
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
        } catch (_: Exception) { emptyList() }
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

            // pobierz liste swiat z lokalnej bazy
            val local = loadHolidaysFromLocal(context)

            if (local.isNotEmpty()) {
                holidays.value = local
                regenerateMandatoryScheduleFromToday()
                return@launch
            }

            // pobierz liste swiat z zewnetrznej bazy
            val remote = loadHolidaysFromFirebase()

            if (remote.isNotEmpty()) {
                holidays.value = remote
                saveHolidaysLocally(context, remote)
                regenerateMandatoryScheduleFromToday()
                return@launch
            }

            // pobierz liste swiat z calendarific
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("holiday_api_key", "") ?: ""

            if (apiKey.isNotBlank()) {
                val api = fetchFromApi(apiKey)

                holidays.value = api
                saveHolidaysLocally(context, api)
                saveHolidaysToFirebase(api)
                regenerateMandatoryScheduleFromToday()
            }
        }
    }

    fun loadSettingsSmart(context: Context) {
        viewModelScope.launch {

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            val localWeekends = prefs.getBoolean("show_weekends", true)
            val localHolidays = prefs.getBoolean("show_holidays", true)

            showWeekends.value = localWeekends
            showHolidays.value = localHolidays
            insertToSystemCalendar.value = prefs.getBoolean("insert_system_calendar", false)

            // Załaduj motyw
            val themeStr = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
            com.MateuszW.aigrowthtracker.ui.theme.ThemeState.themeMode =
                com.MateuszW.aigrowthtracker.ui.theme.ThemeMode.valueOf(themeStr)

            //Załaduj godzięn kalendarza
            calendarEventHour.value = prefs.getInt("calendar_event_hour", 17)

            val isFirstRun = !prefs.contains("show_weekends") || !prefs.contains("show_holidays")

            if (isFirstRun) {
                try {
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
                } catch (_: Exception) { /* offline */ }
            }
            // Po załadowaniu settings - przerysuj harmonogram mandatory z aktualnymi flagami
            regenerateMandatoryScheduleFromToday()
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
        persistWeeklyTasks()
    }

    fun decrementWeeklyTask(id: Int) {
        _weeklyTasks.value = _weeklyTasks.value.map {
            if (it.id == id) it.copy(currentValue = (it.currentValue - it.step).coerceAtLeast(0f))
            else it
        }
        persistWeeklyTasks()
    }

    fun incrementWeeklyTaskByName(name: String) {
        if (name.isBlank()) return
        _weeklyTasks.value = _weeklyTasks.value.map {
            if (it.name == name) it.copy(currentValue = it.currentValue + it.step)
            else it
        }
        persistWeeklyTasks()
    }

    // Zapis weekly tasks do Room + Firebase (po każdej zmianie +/-)
    private fun persistWeeklyTasks() {
        saveWeeklyTasksLocal()
        saveWeeklyTasksToFirebase()
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

    fun saveWeeklyTasksToFirebase() {
        viewModelScope.launch {
            val userId = AuthManager.getUserId() ?: return@launch
            _weeklyTasks.value.forEach { task ->
                val map = mapOf(
                    "id" to task.id,
                    "name" to task.name,
                    "type" to task.type.name,
                    "targetValue" to task.targetValue,
                    "currentValue" to task.currentValue,
                    "step" to task.step,
                    "isAIGenerated" to task.isAIGenerated
                )
                FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("weekly_tasks")
                    .document(task.id.toString())
                    .set(map)
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

    fun getGeminiKey(context: Context): String {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
    }




    suspend fun buildHistoryForAI(): String {
        val history = taskHistoryDao.getAllHistoryOnce()
        val fourWeeksAgo = System.currentTimeMillis() - (28L * 24 * 60 * 60 * 1000)
        val recent = history.filter { it.date >= fourWeeksAgo && it.taskId != -1000 }

        val aiTasks = recent.filter { it.isAIGenerated }
        val dailyTasks = recent.filter { !it.isAIGenerated }

        val aiCompletionRate =
            if (aiTasks.isEmpty()) 0.0 else aiTasks.count { it.completed }.toDouble() / aiTasks.size

        val dailyCompletionRate =
            if (dailyTasks.isEmpty()) 0.0 else dailyTasks.count { it.completed }.toDouble() / dailyTasks.size

        val grouped = recent.groupBy { it.aiMainName ?: it.taskName }.mapValues { (_, items) ->
            items.map {
                mapOf(
                    "taskName" to it.taskName,
                    "category" to it.category,
                    "completed" to it.completed,
                    "rating" to it.rating,
                    "difficulty" to it.difficulty,
                    "note" to it.note,
                    "isAIGenerated" to it.isAIGenerated
                )
            }
        }

        val payload = mapOf(
            "aiCompletionRate" to aiCompletionRate,
            "dailyCompletionRate" to dailyCompletionRate,
            "groupedTasks" to grouped,
            "raw" to recent.map {
                mapOf(
                    "taskName" to it.taskName,
                    "mainName" to it.aiMainName,
                    "category" to it.category,
                    "completed" to it.completed,
                    "rating" to it.rating,
                    "difficulty" to it.difficulty,
                    "note" to it.note,
                    "isAIGenerated" to it.isAIGenerated
                )
            }
        )

        return Gson().toJson(payload)
    }

    private fun getNextWeekDays(): List<Pair<String, Long>> {
        val start = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return listOf(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
        ).mapIndexed { index, dayName ->
            val cal = start.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, index)
            dayName to cal.timeInMillis
        }
    }

    fun calculateActiveDaysForNextWeek(): Int {
        return getNextWeekDays().count { (_, millis) ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = showWeekends.value &&
                    (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

            val isHoliday = !showHolidays.value && holidays.value.contains(normalizeDay(millis))

            !isWeekend && !isHoliday
        }
    }

    fun countHiddenHolidayDaysForNextWeek(): Int {
        return getNextWeekDays().count { (_, millis) ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val isHoliday = holidays.value.contains(normalizeDay(millis))
            !showHolidays.value && isHoliday && !isWeekend
        }
    }

    private suspend fun saveHistoryBoth(history: TaskHistoryEntity) {
        taskHistoryDao.insert(history)

        val userId = AuthManager.getUserId() ?: return
        FirebaseRepository.db
            .collection("users")
            .document(userId)
            .collection("history")
            .document(history.syncId)
            .set(history.toMap())
    }

    fun insertHistory(history: TaskHistoryEntity) {
        viewModelScope.launch {
            saveHistoryBoth(history)
        }
    }

    fun finishTask() {
        timerJob?.cancel()
        _activeTask.value?.let { task ->
            val aiInstr = _aiTaskInstruction.value?.takeIf { it.taskId == task.id }?.instructions
            viewModelScope.launch {
                val history = TaskHistoryEntity(
                    taskId = task.id,
                    taskName = task.taskName,
                    category = task.category,
                    date = System.currentTimeMillis(),
                    completed = true,
                    isAIGenerated = task.isAIGenerated,
                    aiMainName = if (task.isAIGenerated) task.category else null,
                    aiInstructions = aiInstr
                )
                saveHistoryBoth(history)
            }
            if (task.isAIGenerated) {
                incrementWeeklyTaskByName(task.category)
            }
            if (aiInstr != null) _aiTaskInstruction.value = null
        }
        _activeTask.value = null
    }

    fun finishTaskWithSummary(rating: Int, note: String) {
        timerJob?.cancel()
        _activeTask.value?.let { task ->
            //  Zapisz instrukcje AI jeśli były wygenerowane dla tego zadania
            val aiInstr = _aiTaskInstruction.value?.takeIf { it.taskId == task.id }?.instructions
            viewModelScope.launch {
                Log.d("Steps", "[finishTaskWithSummary] timer task=${task.taskName} rating=$rating aiInstr=${aiInstr?.take(50)}")
                val history = TaskHistoryEntity(
                    taskId = task.id,
                    taskName = task.taskName,
                    category = task.category,
                    date = System.currentTimeMillis(),
                    completed = true,
                    rating = rating,
                    note = note,
                    isAIGenerated = task.isAIGenerated,
                    aiMainName = if (task.isAIGenerated) task.category else null,
                    stepsCompleted = null,
                    aiInstructions = aiInstr
                )
                saveHistoryBoth(history)
            }
            if (task.isAIGenerated) {
                incrementWeeklyTaskByName(task.category)
            }
            // Wyczyść instrukcje po użyciu
            if (aiInstr != null) _aiTaskInstruction.value = null
        }
        _activeTask.value = null
    }

    /**
     * Sprawdza czy któraś z aktywnych STEPS tasks osiągnęła cel. Jeśli tak, auto-zapisuje
     * wykonanie do historii bez wymuszania ratingu (rating = null, note = "Auto").
     * Wywoływane przy każdym refreshTodaySteps po aktualizacji _todaySteps
     */
    private fun checkAutoCompleteSteps(currentSteps: Long) {
        viewModelScope.launch {
            val today = normalizeDay(System.currentTimeMillis())
            val tomorrow = today + 86_400_000L
            val todayHistory = taskHistoryDao.getHistoryBetween(today, tomorrow)
            val allTasks = taskDao.getAllTasksOnce()

            // Aktywne STEPS taski: mają baseline LUB są zaplanowane jako mandatory na dziś
            val scheduledToday = mandatoryScheduleDao.getScheduleBetweenOnce(today, tomorrow - 1L)
                .map { it.taskId }.toSet()

            allTasks
                .filter { it.taskType == "STEPS" && (it.stepGoal ?: 0) > 0 }
                .filter { task ->
                    // Wykluczamy te już ukończone dziś
                    todayHistory.none { it.taskId == task.id && it.completed }
                }
                .filter { task ->
                    // Aktywne: ma baseline (manual start) lub wpisane jako mandatory na dziś
                    task.stepsBaseline != null || task.id in scheduledToday
                }
                .forEach { task ->
                    val baseline = task.stepsBaseline ?: 0L
                    val isMidnight = task.stepsCountingMode == "FROM_MIDNIGHT" || baseline == 0L
                    val effective = if (isMidnight) currentSteps
                        else (currentSteps - baseline).coerceAtLeast(0L)
                    val goal = task.stepGoal ?: 0
                    if (goal > 0 && effective >= goal) {
                        Log.d("Steps", "[autoComplete] task=${task.taskName} effective=$effective goal=$goal")
                        val history = TaskHistoryEntity(
                            taskId = task.id,
                            taskName = task.taskName,
                            category = task.category,
                            date = System.currentTimeMillis(),
                            completed = true,
                            rating = null,
                            note = "Cel osiągnięty automatycznie",
                            isAIGenerated = task.isAIGenerated,
                            aiMainName = if (task.isAIGenerated) task.category else null,
                            stepsCompleted = effective
                        )
                        saveHistoryBoth(history)
                        //  Sprzątanie baseline
                        if (task.stepsBaseline != null) {
                            _stepsBaselines.value = _stepsBaselines.value - task.id
                            val cleared = task.copy(
                                stepsBaseline = null,
                                stepsStartedAt = null,
                                stepsCountingMode = null
                            )
                            taskDao.updateTask(cleared)
                            AuthManager.getUserId()?.let { uid ->
                                FirebaseRepository.db.collection("users").document(uid)
                                    .collection("tasks").document(task.id.toString())
                                    .set(cleared.toMap())
                            }
                        }
                    }
                }
        }
    }

    /**
     * Zapisuje ukończenie zadania STEPS. Oddzielna funkcja od [finishTaskWithSummary],
     * bo zadania STEPS nie używają _activeTask (nie mają timera).
     * Usuwa baseline z [_stepsBaselines], żeby zadanie zniknęło z "Zadania w toku"
     */
    fun finishStepsTask(task: TaskEntity, stepsCompleted: Long, rating: Int, note: String) {
        viewModelScope.launch {
            Log.d(
                "Steps",
                "[finishStepsTask] task=${task.id}/${task.taskName} " +
                "stepsCompleted=$stepsCompleted rating=$rating"
            )
            val history = TaskHistoryEntity(
                taskId = task.id,
                taskName = task.taskName,
                category = task.category,
                date = System.currentTimeMillis(),
                completed = true,
                rating = rating,
                note = note.ifBlank { null },
                isAIGenerated = task.isAIGenerated,
                aiMainName = if (task.isAIGenerated) task.category else null,
                stepsCompleted = stepsCompleted
            )
            saveHistoryBoth(history)
            // Usuń baseline - zadanie ukończone, znika z "Zadania w toku"
            _stepsBaselines.value = _stepsBaselines.value - task.id
            //Wyczyść pola aktywności w Room + Firebase
            val cleared = task.copy(stepsBaseline = null, stepsStartedAt = null, stepsCountingMode = null)
            taskDao.updateTask(cleared)
            val userId = AuthManager.getUserId()
            if (userId != null) {
                FirebaseRepository.db.collection("users").document(userId)
                    .collection("tasks").document(task.id.toString())
                    .set(cleared.toMap())
            }
            Log.d("Steps", "[finishStepsTask] history saved, baseline removed for taskId=${task.id}")
        }
    }

    fun updateWeeklyReviewFeedback(items: List<WeeklyReviewInput>) {
        viewModelScope.launch {
            val all = taskHistoryDao.getAllHistoryOnce()

            items.forEach { input ->
                val current = all.firstOrNull { it.id == input.historyId } ?: return@forEach
                val updated = current.copy(
                    rating = input.stars,
                    difficulty = input.difficulty,
                    note = input.note
                )

                taskHistoryDao.update(updated)

                val userId = AuthManager.getUserId() ?: return@forEach
                FirebaseRepository.db
                    .collection("users")
                    .document(userId)
                    .collection("history")
                    .document(updated.syncId)
                    .set(updated.toMap())
            }
        }
    }

    fun getWeeklyReviewHistory(): List<TaskHistoryEntity> {
        val since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        return history.value
            .filter { it.date >= since }
            .filterNot { it.taskId == -1000 }
            .sortedByDescending { it.date }
    }

    suspend fun buildWeeklyReviewHistoryJson(): String {
        val since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val recent = taskHistoryDao.getAllHistoryOnce().filter { it.date >= since && it.taskId != -1000 }
        val mandatoryIds = taskDao.getAllTasksOnce().filter { it.isMandatory }.map { it.id }.toSet()

        // Filtruj tylko taski z planu dnia (AI, mandatory, odroczone)
        val planTasks = recent.filter { entry ->
            entry.isAIGenerated ||
            entry.taskId in mandatoryIds ||
            entry.note?.contains("(odroczone)") == true
        }

        val grouped = planTasks.groupBy { it.aiMainName ?: it.taskName }.mapValues { (_, items) ->
            items.map {
                mapOf(
                    "taskName" to it.taskName,
                    "category" to it.category,
                    "completed" to it.completed,
                    "rating" to it.rating,
                    "difficulty" to it.difficulty,
                    "note" to it.note,
                    "isAIGenerated" to it.isAIGenerated
                )
            }
        }

        val aiTasks = planTasks.filter { it.isAIGenerated }
        val aiCompletionRate =
            if (aiTasks.isEmpty()) 0.0 else aiTasks.count { it.completed }.toDouble() / aiTasks.size

        val mandatoryTasks = planTasks.filter { it.taskId in mandatoryIds }
        val mandatoryCompletionRate =
            if (mandatoryTasks.isEmpty()) 0.0 else mandatoryTasks.count { it.completed }.toDouble() / mandatoryTasks.size

        val totalCompleted = planTasks.count { it.completed }
        val totalPlanned = planTasks.size
        val overallCompletionRate =
            if (totalPlanned == 0) 0.0 else totalCompleted.toDouble() / totalPlanned

        // Per-day stats
        val todayStart = normalizeDay(System.currentTimeMillis())
        val dayStats = (0 until 7).map { i ->
            val dayStart = normalizeDay(since) + i * 86_400_000L
            val dayEnd = dayStart + 86_400_000L
            val dayEntries = planTasks.filter { it.date in dayStart until dayEnd }
            mapOf(
                "day" to SimpleDateFormat("EEEE", Locale("pl")).format(java.util.Date(dayStart)),
                "planned" to dayEntries.size,
                "completed" to dayEntries.count { it.completed },
                "uncompleted" to dayEntries.count { !it.completed }
            )
        }.filter { (it["planned"] as Int) > 0 }

        val payload = mapOf(
            "overallCompletionRate" to overallCompletionRate,
            "aiCompletionRate" to aiCompletionRate,
            "mandatoryCompletionRate" to mandatoryCompletionRate,
            "totalPlanned" to totalPlanned,
            "totalCompleted" to totalCompleted,
            "dayStats" to dayStats,
            "taskGroups" to grouped,
            "raw" to planTasks.map {
                mapOf(
                    "taskName" to it.taskName,
                    "mainName" to it.aiMainName,
                    "category" to it.category,
                    "completed" to it.completed,
                    "rating" to it.rating,
                    "difficulty" to it.difficulty,
                    "note" to it.note,
                    "isAIGenerated" to it.isAIGenerated
                )
            }
        )

        return Gson().toJson(payload)
    }

    suspend fun buildWeeklyReviewPrompt(): String {
        val historyJson = buildWeeklyReviewHistoryJson()

        return """
Jesteś asystentem analizującym miniony tydzień użytkownika. Bądź obiektywny — nie musisz być zawsze pozytywny.

DANE (tylko zadania z planu dnia: asystent AI, obowiązkowe, przełożone):
- historia ostatniego tygodnia: $historyJson

WAŻNE: Opieraj się TYLKO na zadaniach z planu dnia (wpisane przez asystenta AI, zadania obowiązkowe, zadania przełożone).
Dane zawierają statystyki per dzień (dayStats) — ile zadań zaplanowano, ile wykonano, ile niewykonanych.

ZADANIE:
1. Oceń jakość tygodnia:
   - "bad"     = ukończono mniej niż 40% zaplanowanych zadań LUB oceny były niskie
   - "neutral" = ukończono 40–70% zaplanowanych zadań LUB mieszane wyniki
   - "great"   = ukończono ponad 70% zaplanowanych zadań I widać regularność
2. Napisz krótkie podsumowanie po polsku (3–5 zdań):
   - opisz co poszło dobrze i co można poprawić (nawet przy "great" wskaż co można ulepszyć)
   - odnieś się do konkretnych dni i zadań z danych
   - nie bądź przesadnie entuzjastyczny — skup się na faktach

ZWRÓĆ TYLKO poprawny JSON (bez markdown, bez backticks):
{
  "summary": "tekst podsumowania po polsku",
  "weekQuality": "bad" | "neutral" | "great"
}
""".trimIndent()
    }

    fun generateWeeklyReviewSummary(context: Context) {
        viewModelScope.launch {
            try {
                if (isTestMode.value) {
                    _weeklyReviewSummary.value = MOCK_REVIEW_SUMMARY
                    _weekQuality.value = "great"
                    return@launch
                }
                val apiKey = getGeminiKey(context)
                if (apiKey.isBlank()) {
                    _weeklyReviewSummary.value = ""   //pusty string → UI pokaże "Brak klucza API"
                    return@launch
                }

                val model = GenerativeModel("gemini-2.5-flash", apiKey)
                val prompt = buildWeeklyReviewPrompt()
                Log.d("AI_REVIEW", "Wysyłam prompt podsumowania (${prompt.length} znaków)")
                val response = model.generateContent(prompt)
                val rawText = response.text?.trim().orEmpty()
                Log.d("AI_REVIEW", "Podsumowanie od Gemini:\n$rawText")

                // Parsuj JSON
                val json = rawText
                    .let { if (it.startsWith("```")) it.substringAfter("\n") else it }
                    .removeSuffix("```").trim()

                val text: String
                val quality: String
                try {
                    val obj = org.json.JSONObject(json)
                    text = obj.getString("summary")
                    quality = obj.optString("weekQuality", "neutral")
                        .let { if (it in listOf("bad", "neutral", "great")) it else "neutral" }
                } catch (e: Exception) {
                    // Gemini zwrócił czysty tekst zamiast JSON - wróć do starego zachowania
                    Log.w("AI_REVIEW", "Nie udało się sparsować JSON, traktuje jako tekst: ${e.message}")
                    _weeklyReviewSummary.value = rawText
                    _weekQuality.value = "neutral"
                    return@launch
                }

                _weeklyReviewSummary.value = text
                _weekQuality.value = quality
                Log.d("AI_REVIEW", "weekQuality=$quality")

                if (text.isNotBlank()) {
                    val summaryRow = TaskHistoryEntity(
                        taskId = -1000,
                        taskName = "Podsumowanie tygodnia",
                        category = "AI_SUMMARY",
                        date = System.currentTimeMillis(),
                        completed = true,
                        note = text,
                        isAIGenerated = true,
                        aiMainName = "WEEKLY_SUMMARY"
                    )
                    saveHistoryBoth(summaryRow)
                }
            } catch (e: Exception) {
                Log.e("AI", "Weekly summary error: ${e.message}")
            }
        }
    }

    suspend fun buildPrompt(
        context: Context,
        difficulty: Int,
        goalDrafts: Map<String, WeeklyGoalDraft>,
        historyJson: String
    ): String {
        val dailyPoolTasks = allTasks.value.map {
            mapOf(
                "name" to it.taskName,
                "category" to it.category,
                "isMandatory" to it.isMandatory,
                "isAIGenerated" to it.isAIGenerated,
                "isCompleted" to it.isCompleted
            )
        }

        val activeDays = getNextWeekDays().filter { (_, millis) ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val weekendBlocked = showWeekends.value &&
                    (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            val holidayBlocked = !showHolidays.value && holidays.value.contains(normalizeDay(millis))
            !weekendBlocked && !holidayBlocked
        }.map { it.first }

        val selectedTasksJson = Gson().toJson(goalDrafts)
        val dailyPoolJson = Gson().toJson(dailyPoolTasks)

        return """
Jesteś elitarnym trenerem rozwoju osobistego.

Masz przygotować plan na następny tydzień po polsku.

WEJŚCIE:
- zamierzona trudność tygodnia: $difficulty
- aktywne dni tygodnia: ${Gson().toJson(activeDays)}
- liczba aktywnych dni: ${activeDays.size}
- historia z ostatnich 4 tygodni: $historyJson
- wszystkie taski daily/system: $dailyPoolJson
- wybrane cele użytkownika: $selectedTasksJson

ZASADY:
- zwróć tylko poprawny JSON
- weeklyTasks muszą być głównymi celami tygodniowymi
- dailyPlan ma zawierać wyłącznie aktywne dni z listy aktywnych dni
- KRYTYCZNE: KAŻDY dzień z listy aktywnych dni MUSI mieć co najmniej jeden task w dailyPlan — bez wyjątków
- KRYTYCZNE: jeśli SATURDAY lub SUNDAY jest na liście aktywnych dni, ABSOLUTNIE MUSI pojawić się w dailyPlan z co najmniej jednym taskiem — weekend to pełnoprawny dzień aktywności!
- na weekend planuj zadania odpowiednie do weekendu (lekki trening, spacer, stretching, reading, hobby) ale muszą być zaplanowane
- każdy aktywny dzień ma mieć listę tasków
- daily taski muszą wynikać z weeklyTasks
- jeśli weekly task ma cel liczbowy, suma targetValue z dailyPlan dla tego tasku ma dać weekly goal
- uwzględnij historię 4 tygodni, ukończenie zadań, feedback słowny i poziom trudności
- nie używaj gwiazdek jako głównego sygnału sterującego planem
- quote ma być motywacyjny i po polsku
- summary ma opisać poprzedni tydzień
- durationMinutes: sugerowany czas trwania zadania w minutach (5-90, domyślnie 30)

ZWROT TYLKO JSON:
{
  "weeklyTasks": [
    {
      "name": "string",
      "type": "counter|milestone",
      "goal": number
    }
  ],
  "dailyPlan": {
    "MONDAY": [
      {
        "mainName": "string",
        "description": "string",
        "targetValue": number,
        "durationMinutes": number
      }
    ]
  },
  "summary": "string",
  "quote": "string"
}
""".trimIndent()
    }

    fun parseAiResponse(json: String): AiWeeklyPlan {

        val obj = org.json.JSONObject(json)

        val weekly = obj.getJSONArray("weeklyTasks")
        val weeklyTasks = (0 until weekly.length()).map {
            val item = weekly.getJSONObject(it)
            AiWeeklyTask(
                name = item.getString("name"),
                type = item.getString("type"),
                goal = item.getDouble("goal").toFloat()
            )
        }

        val dailyObj = obj.getJSONObject("dailyPlan")
        val dailyPlan = mutableMapOf<String, List<AiDailyTask>>()

        dailyObj.keys().forEach { key ->
            val arr = dailyObj.getJSONArray(key)

            dailyPlan[key] = (0 until arr.length()).map {
                val objTask = arr.getJSONObject(it)

                val rawDuration = if (objTask.has("durationMinutes"))
                    objTask.getInt("durationMinutes") else 0
                AiDailyTask(
                    mainName = objTask.getString("mainName"),
                    description = objTask.getString("description"),
                    targetValue = if (objTask.has("targetValue") && !objTask.isNull("targetValue"))
                        objTask.getDouble("targetValue").toFloat() else null,
                    //Fallback: jeśli AI zwróci duration <= 0, ustaw domyślnie 30 min
                    durationMinutes = if (rawDuration > 0) rawDuration else 30
                )
            }
        }

        return AiWeeklyPlan(
            weeklyTasks = weeklyTasks,
            dailyPlan = dailyPlan,
            summary = obj.getString("summary"),
            quote = obj.getString("quote")
        )
    }

    fun applyAiPlan(plan: AiWeeklyPlan) {

        val weekly = plan.weeklyTasks.mapIndexed { i, task ->
            WeeklyTaskEntity(
                id = i,
                name = task.name,
                type = if (task.type == "milestone")
                    WeeklyTaskType.MILESTONE
                else WeeklyTaskType.COUNTER,
                targetValue = task.goal,
                step = if (task.type == "milestone") 5f else 1f,
                isAIGenerated = true
            )
        }

        _weeklyTasks.value = weekly
        _aiDailyTasks.value = plan.dailyPlan
    }

    fun planWeek(
        context: Context,
        difficulty: Int,
        goalDrafts: Map<String, WeeklyGoalDraft>
    ) {
        viewModelScope.launch {
            _isPlanningWeek.value = true
            Log.d("AI_PLAN", "planWeek start | difficulty=$difficulty | drafts=${goalDrafts.keys}")
            try {
                if (isTestMode.value) {
                    Log.d("AI_PLAN", "TRYB TESTOWY - pomijam Gemini")
                    val plan = MOCK_WEEKLY_PLAN
                    _aiPlan.value = plan
                    applyAiPlan(plan)
                    saveAiPlanLocal(context, plan)
                    saveWeeklyTasksLocal()
                    saveWeeklyTasksToFirebase()
                    saveConfiguredWeeklyTopics(context, goalDrafts.keys)
                    markWeeklyFlowCompleted(context)
                    insertPlanToCalendar(context, plan)
                    Log.d("AI_PLAN", "TRYB TESTOWY - plan zastosowany")
                    return@launch
                }

                val apiKey = getGeminiKey(context)
                if (apiKey.isBlank()) {
                    Log.e("AI_PLAN", "Brak klucza API Gemini - przerwano")
                    return@launch
                }
                Log.d("AI_PLAN", "Klucz API OK (${apiKey.take(8)}...)")

                val historyJson = buildHistoryForAI()
                Log.d("AI_PLAN", "Historia zbudowana (${historyJson.length} znaków)")

                val prompt = buildPrompt(context, difficulty, goalDrafts, historyJson)
                Log.d("AI_PLAN", "Prompt zbudowany (${prompt.length} znaków)")

                val model = GenerativeModel("gemini-2.5-flash", apiKey)
                Log.d("AI_PLAN", "Wysyłam prompt do Gemini...")

                val response = model.generateContent(prompt)
                val rawText = response.text
                Log.d("AI_PLAN", "Odpowiedź Gemini (raw):\n$rawText")

                if (rawText == null) {
                    Log.e("AI_PLAN", "Gemini zwróciło null - brak treści odpowiedzi")
                    return@launch
                }

                val json = rawText.trim()
                    .let { if (it.startsWith("```")) it.substringAfter("\n") else it }
                    .removeSuffix("```").trim()
                Log.d("AI_PLAN", "JSON po oczyszczeniu:\n$json")

                val parsed = parseAiResponse(json)
                Log.d("AI_PLAN", "Sparsowano: weeklyTasks=${parsed.weeklyTasks.size}, dni=${parsed.dailyPlan.keys}")

                _aiPlan.value = parsed
                applyAiPlan(parsed)
                saveAiPlanLocal(context, parsed)
                saveWeeklyTasksLocal()
                saveWeeklyTasksToFirebase()
                saveConfiguredWeeklyTopics(context, goalDrafts.keys)
                markWeeklyFlowCompleted(context)
                Log.d("AI_PLAN", "Plan zapisany lokalnie i Firebase")

                insertPlanToCalendar(context, parsed)

            } catch (e: Exception) {
                Log.e("AI_PLAN", "BLAD w planWeek: ${e::class.simpleName}: ${e.message}")
                Log.e("AI_PLAN", android.util.Log.getStackTraceString(e))
            } finally {
                _isPlanningWeek.value = false
            }
        }
    }

    private fun getNextWeekStartMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun saveAiPlanLocal(context: Context, plan: AiWeeklyPlan) {
        val nextWeekStart = getNextWeekStartMillis()
        val prefs = context.getSharedPreferences("ai_plan", Context.MODE_PRIVATE)
        prefs.edit {
            putString("current_plan", Gson().toJson(plan))
            putLong("planned_week_start", nextWeekStart)
        }
        _plannedWeekStartMillis.value = nextWeekStart
    }

    suspend fun loadAiPlanLocal(context: Context): AiWeeklyPlan? {
        val prefs = context.getSharedPreferences("ai_plan", Context.MODE_PRIVATE)
        val json = prefs.getString("current_plan", null) ?: return null
        return runCatching { Gson().fromJson(json, AiWeeklyPlan::class.java) }.getOrNull()
    }

    fun restoreAiPlan(context: Context) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("ai_plan", Context.MODE_PRIVATE)
            val savedWeekStart = prefs.getLong("planned_week_start", -1L)
            if (savedWeekStart != -1L) {
                _plannedWeekStartMillis.value = savedWeekStart
            }
            val local = loadAiPlanLocal(context)
            if (local != null) {
                applyAiPlan(local)
                _aiPlan.value = local
                _weekPlanned.value = true
            }
        }
    }

    /**
     * Persystuje niewykonane zadania z planu dnia do historii.
     * Dla kazdego minionego dnia sprawdza, co bylo zaplanowane (AI + mandatory)
     * i co faktycznie wykonane. Brakujace wpisy wstawia z completed=false.
     * Zwraca liczbe wstawionych wpisow
     */
    suspend fun persistUnfinishedPlanItems(context: Context): Int {
        val plan = loadAiPlanLocal(context) ?: return 0
        val prefs = context.getSharedPreferences("ai_plan", Context.MODE_PRIVATE)
        val weekStart = prefs.getLong("planned_week_start", -1L)
        if (weekStart == -1L) return 0

        val todayStart = normalizeDay(System.currentTimeMillis())
        val weekEnd = weekStart + 7L * 86_400_000L
        val endDay = minOf(todayStart, weekEnd) // nie wlaczaj dzisiejszego dnia ani dni poza tygodniem

        val allTasksList = taskDao.getAllTasksOnce()
        val taskMap = allTasksList.associateBy { it.id }
        var insertedCount = 0

        var dayMillis = weekStart
        while (dayMillis < endDay) {
            val dayEnd = dayMillis + 86_400_000L

            // Nazwa dnia (MONDAY, TUESDAY, ...)
            val cal = Calendar.getInstance().apply { timeInMillis = dayMillis }
            val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "MONDAY"; Calendar.TUESDAY -> "TUESDAY"
                Calendar.WEDNESDAY -> "WEDNESDAY"; Calendar.THURSDAY -> "THURSDAY"
                Calendar.FRIDAY -> "FRIDAY"; Calendar.SATURDAY -> "SATURDAY"
                Calendar.SUNDAY -> "SUNDAY"; else -> "MONDAY"
            }

            // Pobierz historie dnia
            val dayHistory = taskHistoryDao.getHistoryBetween(dayMillis, dayEnd)

            // 1. AI taski zaplanowane na ten dzien
            val aiTasksForDay = plan.dailyPlan[dayName] ?: emptyList()
            for (aiTask in aiTasksForDay) {
                val alreadyInHistory = dayHistory.any {
                    it.isAIGenerated && it.aiMainName == aiTask.mainName
                }
                if (!alreadyInHistory) {
                    val entry = TaskHistoryEntity(
                        taskId = -2, //  marker: zaplanowany AI task niewykonany
                        taskName = aiTask.description,
                        category = aiTask.mainName,
                        date = dayMillis + 12 * 3600_000L, //noon
                        completed = false,
                        note = "Niewykonane",
                        isAIGenerated = true,
                        aiMainName = aiTask.mainName
                    )
                    saveHistoryBoth(entry)
                    insertedCount++
                }
            }

            // 2. Mandatory taski zaplanowane na ten dzien
            val mandatorySchedule = mandatoryScheduleDao.getScheduleBetweenOnce(dayMillis, dayEnd - 1)
            for (schedule in mandatorySchedule) {
                val task = taskMap[schedule.taskId] ?: continue
                val alreadyInHistory = dayHistory.any { it.taskId == task.id }
                if (!alreadyInHistory) {
                    val entry = TaskHistoryEntity(
                        taskId = task.id,
                        taskName = task.taskName,
                        category = task.category,
                        date = dayMillis + 12 * 3600_000L, // noon
                        completed = false,
                        note = "Niewykonane",
                        isAIGenerated = false
                    )
                    saveHistoryBoth(entry)
                    insertedCount++
                }
            }

            dayMillis += 86_400_000L
        }
        return insertedCount
    }

    /**
     * Zwraca statystyki per dzien z ostatniego tygodnia
     * na bazie historii (tylko taski z planu: AI, mandatory, odroczone)
     */
    suspend fun getWeeklyDayStats(): List<com.MateuszW.aigrowthtracker.data.DayStats> {
        val since = normalizeDay(System.currentTimeMillis()) - 7L * 86_400_000L
        val allHistory = taskHistoryDao.getAllHistoryOnce()
        val mandatoryIds = taskDao.getAllTasksOnce().filter { it.isMandatory }.map { it.id }.toSet()

        val dateFormat = SimpleDateFormat("EEEE dd.MM", Locale("pl"))
        val stats = mutableListOf<com.MateuszW.aigrowthtracker.data.DayStats>()

        for (i in 0 until 7) {
            val dayStart = since + i * 86_400_000L
            val dayEnd = dayStart + 86_400_000L

            val dayEntries = allHistory.filter { it.date in dayStart until dayEnd && it.taskId != -1000 }
            // Tylko taski z planu dnia
            val planEntries = dayEntries.filter { entry ->
                entry.isAIGenerated ||
                entry.taskId in mandatoryIds ||
                entry.note?.contains("(odroczone)") == true
            }

            if (planEntries.isEmpty()) continue

            val completed = planEntries.count { it.completed }
            val uncompleted = planEntries.size - completed

            stats.add(
                com.MateuszW.aigrowthtracker.data.DayStats(
                    dayMillis = dayStart,
                    dayLabel = dateFormat.format(java.util.Date(dayStart))
                        .replaceFirstChar { it.uppercase() },
                    totalPlanned = planEntries.size,
                    completed = completed,
                    uncompleted = uncompleted
                )
            )
        }
        return stats
    }

    /**
     * Zwraca unikalne AI taski z historii ostatniego tygodnia do recenzji.
     * Grupuje po aiMainName, zwraca jeden wpis per kategoria AI
     */
    suspend fun getAiTasksForReview(): List<TaskHistoryEntity> {
        val since = normalizeDay(System.currentTimeMillis()) - 7L * 86_400_000L
        val allHistory = taskHistoryDao.getAllHistoryOnce()

        return allHistory
            .filter { it.date >= since && it.isAIGenerated && it.taskId != -1000 && it.aiMainName != "WEEKLY_SUMMARY" }
            .groupBy { it.aiMainName }
            .mapNotNull { (_, entries) -> entries.maxByOrNull { it.date } }
            .sortedByDescending { it.date }
    }

    fun saveSelectedWeeklyTopics(context: Context, topics: Set<String>) {
        context.getSharedPreferences("weekly_topics", Context.MODE_PRIVATE)
            .edit { putStringSet("selected", topics) }
        viewModelScope.launch {
            val userId = AuthManager.getUserId() ?: return@launch
            FirebaseRepository.db
                .collection("users").document(userId)
                .collection("settings").document("weekly_topics")
                .set(mapOf("selected" to topics.toList()))
        }
    }

    fun loadSelectedWeeklyTopics(context: Context): Set<String> {
        return context.getSharedPreferences("weekly_topics", Context.MODE_PRIVATE)
            .getStringSet("selected", emptySet()) ?: emptySet()
    }

    fun loadSelectedTopicsSmart(context: Context, onResult: (Set<String>) -> Unit) {
        viewModelScope.launch {
            val local = loadSelectedWeeklyTopics(context)
            if (local.isNotEmpty()) { onResult(local); return@launch }
            try {
                val userId = AuthManager.getUserId() ?: return@launch
                val doc = FirebaseRepository.db
                    .collection("users").document(userId)
                    .collection("settings").document("weekly_topics")
                    .get().await()
                @Suppress("UNCHECKED_CAST")
                val remote = (doc.get("selected") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                if (remote.isNotEmpty()) {
                    saveSelectedWeeklyTopics(context, remote)
                    onResult(remote)
                } else {
                    onResult(emptySet())
                }
            } catch (e: Exception) {
                onResult(local)
            }
        }
    }

    fun saveConfiguredWeeklyTopics(context: Context, topics: Set<String>) {
        context.getSharedPreferences("weekly_topics", Context.MODE_PRIVATE)
            .edit {
                putStringSet("configured", topics)
            }
    }

    fun loadConfiguredWeeklyTopics(context: Context): Set<String> {
        return context.getSharedPreferences("weekly_topics", Context.MODE_PRIVATE)
            .getStringSet("configured", emptySet()) ?: emptySet()
    }

    // In-memory guard - ustawiana natychmiast po wejściu w weekly flow,
    // zapobiega ponownemu otwarciu nawet jeśli SharedPreferences nie zdążyły się zapisać
    @Volatile
    private var _weeklyFlowOpenedThisSession = false

    fun shouldOpenWeeklyFlow(context: Context): Boolean {
        //  Guard: jeśli flow już był otwarty w tej sesji - nigdy ponownie
        if (_weeklyFlowOpenedThisSession) return false

        val cal = Calendar.getInstance()
        val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        if (!isSunday) return false

        val weekKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.WEEK_OF_YEAR)}"
        val prefs = context.getSharedPreferences("weekly_flow", Context.MODE_PRIVATE)
        val lastKey = prefs.getString("last_opened", "") ?: ""
        if (lastKey == weekKey) return false

        //Ustaw OBYDWA guardy NATYCHMIAST - in-memory + SharedPreferences
        // Dzięki temu nawet restart apki nie wywoła flow ponownie
        _weeklyFlowOpenedThisSession = true
        prefs.edit { putString("last_opened", weekKey) }
        return true
    }

    fun markWeeklyFlowCompleted(context: Context) {
        _weeklyFlowOpenedThisSession = true
        val cal = Calendar.getInstance()
        val weekKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.WEEK_OF_YEAR)}"
        context.getSharedPreferences("weekly_flow", Context.MODE_PRIVATE)
            .edit {
                putString("last_opened", weekKey)
            }
    }

    fun getDayOffset(day: String): Int {
        return when (day) {
            "MONDAY" -> 0
            "TUESDAY" -> 1
            "WEDNESDAY" -> 2
            "THURSDAY" -> 3
            "FRIDAY" -> 4
            "SATURDAY" -> 5
            "SUNDAY" -> 6
            else -> 0
        }
    }

    private suspend fun saveWeeklyFlowBoth(record: WeeklyFlowRecordEntity) {
        weeklyFlowRecordDao.upsert(record)

        val userId = AuthManager.getUserId() ?: return
        val map = mapOf(
            "weekKey" to record.weekKey,
            "stage" to record.stage,
            "createdAt" to record.createdAt,
            "updatedAt" to record.updatedAt,
            "reviewSummary" to record.reviewSummary,
            "reviewFeedbackJson" to record.reviewFeedbackJson,
            "selectedTopicsJson" to record.selectedTopicsJson,
            "goalDraftsJson" to record.goalDraftsJson,
            "difficulty" to record.difficulty,
            "hiddenHolidayCount" to record.hiddenHolidayCount,
            "promptJson" to record.promptJson,
            "aiResponseJson" to record.aiResponseJson
        )
        FirebaseRepository.db
            .collection("users")
            .document(userId)
            .collection("weekly_flow")
            .document(record.weekKey)
            .set(map)
    }

    fun saveWeeklyReviewBundle(
        summary: String,
        feedback: List<WeeklyReviewInput>
    ) {
        viewModelScope.launch {
            val weekKey = currentWeekKey()
            val record = WeeklyFlowRecordEntity(
                weekKey = weekKey,
                stage = "REVIEW",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                reviewSummary = summary,
                reviewFeedbackJson = Gson().toJson(feedback)
            )
            saveWeeklyFlowBoth(record)
        }
    }

    private fun currentWeekKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.WEEK_OF_YEAR)}"
    }

    fun saveWeeklyFlowStage(
        stage: String,
        selectedTasks: Set<String>? = null,
        hiddenHolidayCount: Int? = null,
        difficulty: Int? = null
    ) {
        viewModelScope.launch {
            try {
                val weekKey = currentWeekKey()
                val existing = weeklyFlowRecordDao.getByWeekKey(weekKey)
                val record = WeeklyFlowRecordEntity(
                    weekKey = weekKey,
                    stage = stage,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    reviewSummary = existing?.reviewSummary,
                    reviewFeedbackJson = existing?.reviewFeedbackJson,
                    selectedTopicsJson = selectedTasks?.let { Gson().toJson(it) }
                        ?: existing?.selectedTopicsJson,
                    goalDraftsJson = existing?.goalDraftsJson,
                    difficulty = difficulty ?: existing?.difficulty,
                    hiddenHolidayCount = hiddenHolidayCount ?: existing?.hiddenHolidayCount ?: 0
                )
                _weeklyFlowState.value = stage
                saveWeeklyFlowBoth(record)
            } catch (e: Exception) {
                Log.e("WeeklyFlow", "Error saving stage: ${e.message}")
            }
        }
    }

    fun saveDraftDifficulty(context: Context, difficulty: Int) {
        context.getSharedPreferences("weekly_draft", Context.MODE_PRIVATE)
            .edit { putInt("difficulty", difficulty) }
    }

    fun loadDraftDifficulty(context: Context): Int {
        return context.getSharedPreferences("weekly_draft", Context.MODE_PRIVATE)
            .getInt("difficulty", 5)
    }

    fun saveGoalDrafts(context: Context, drafts: Map<String, WeeklyGoalDraft>) {
        context.getSharedPreferences("weekly_draft", Context.MODE_PRIVATE)
            .edit { putString("goal_drafts", Gson().toJson(drafts)) }
    }

    fun loadGoalDrafts(context: Context): Map<String, WeeklyGoalDraft> {
        val json = context.getSharedPreferences("weekly_draft", Context.MODE_PRIVATE)
            .getString("goal_drafts", null) ?: return emptyMap()
        return runCatching {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, WeeklyGoalDraft>>() {}.type
            Gson().fromJson<Map<String, WeeklyGoalDraft>>(json, type)
        }.getOrDefault(emptyMap())
    }

    private suspend fun insertPlanToCalendar(context: Context, plan: AiWeeklyPlan) {
        if (!insertToSystemCalendar.value) {
            Log.d("Calendar", "Kalendarz systemowy wylaczony w ustawieniach - pomijam")
            return
        }

        try {
            val resolver = context.contentResolver

            val calCursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null, null, null
            )
            val calendarId = calCursor?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            } ?: run {
                Log.w("Calendar", "Brak kalendarza na urządzeniu")
                return
            }

            val nextWeekDays = getNextWeekDays()
            val tz = java.util.TimeZone.getDefault().id

            plan.dailyPlan.forEach { (dayName, tasks) ->
                val dayMillis = nextWeekDays.firstOrNull { it.first == dayName }?.second ?: return@forEach
                tasks.forEach { task ->
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, task.mainName)
                        put(CalendarContract.Events.DESCRIPTION, "[AIGrowthTracker] ${task.description}")
                        val eventHour = calendarEventHour.value
                        put(CalendarContract.Events.DTSTART, dayMillis + eventHour * 3600_000L)
                        put(CalendarContract.Events.DTEND,   dayMillis + (eventHour + 1) * 3600_000L)
                        put(CalendarContract.Events.EVENT_TIMEZONE, tz)
                    }
                    resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                }
            }
            Log.d("Calendar", "Wstawiono plan do kalendarza systemowego (17:00)")
        } catch (e: SecurityException) {
            Log.e("Calendar", "Brak uprawnień do kalendarza: ${e.message}")
        } catch (e: Exception) {
            Log.e("Calendar", "Błąd wstawiania do kalendarza: ${e.message}")
        }
    }

    fun clearFutureCalendarEvents(context: Context) {
        viewModelScope.launch {
            try {
                val resolver = context.contentResolver
                val now = System.currentTimeMillis()

                val cursor = resolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION),
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DELETED} != 1",
                    arrayOf(now.toString()),
                    null
                )

                var deleted = 0
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val description = c.getString(1) ?: ""
                        if (description.contains("[AIGrowthTracker]")) {
                            val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
                                .appendPath(id.toString()).build()
                            resolver.delete(uri, null, null)
                            deleted++
                        }
                    }
                }
                Log.d("Calendar", "Usunieto $deleted przyszlych eventow [AIGrowthTracker]")
            } catch (e: SecurityException) {
                Log.e("Calendar", "Brak uprawnien do kalendarza: ${e.message}")
            } catch (e: Exception) {
                Log.e("Calendar", "Blad czyszczenia kalendarza: ${e.message}")
            }
        }
    }

    fun resetWeeklyFlowForTesting(context: Context) {
        context.getSharedPreferences("weekly_flow", Context.MODE_PRIVATE)
            .edit { remove("last_opened") }
        _weeklyFlowOpenedThisSession = false
        _weeklyReviewSummary.value = null
    }

    //Czyści stan in-memory przy wylogowaniu. Room/SharedPreferences czyszczone osobno
    fun resetForLogout() {
        _weeklyFlowOpenedThisSession = false
        _weeklyReviewSummary.value = null
    }

    /**
     * DEV: Generuje zadania AI na pozostałe dni bieżącego tygodnia (od dziś do niedzieli).
     * Wymaga wcześniejszego zaplanowania tygodnia (bo bazuje na wybranych celach).
     * Uzupełnia istniejący plan o brakujące dni bez nadpisywania tych już zaplanowanych
     */
    fun generateRemainingDays(context: Context) {
        viewModelScope.launch {
            _isPlanningWeek.value = true
            try {
                val apiKey = getGeminiKey(context)
                if (apiKey.isBlank()) {
                    Log.e("AI_PLAN", "generateRemainingDays: brak klucza Gemini")
                    return@launch
                }

                //Wyczyść poprzedni plan
                _aiDailyTasks.value = emptyMap()
                _aiPlan.value = null
                _weeklyTasks.value = emptyList()
                _weekPlanned.value = false

                //Załaduj dane z poprzedniego tygodnia jako domyślne
                val difficulty = loadDraftDifficulty(context)
                val goalDrafts = loadGoalDrafts(context)

                if (goalDrafts.isEmpty()) {
                    Log.e("AI_PLAN", "generateRemainingDays: brak zapisanych celów z poprzedniego tygodnia")
                    return@launch
                }

                Log.d("AI_PLAN", "generateRemainingDays: difficulty=$difficulty drafts=${goalDrafts.keys}")

                val historyJson = buildHistoryForAI()
                val prompt = buildPromptForCurrentWeek(context, difficulty, goalDrafts, historyJson)

                val model = GenerativeModel("gemini-2.5-flash", apiKey)
                val response = model.generateContent(prompt)
                val rawText = response.text ?: return@launch

                val json = rawText.trim()
                    .let { if (it.startsWith("```")) it.substringAfter("\n") else it }
                    .removeSuffix("```").trim()

                Log.d("AI_PLAN", "generateRemainingDays JSON:\n$json")

                val parsed = parseAiResponse(json)

                // Ustaw plannedWeekStartMillis na początek BIEŻĄCEGO tygodnia
                val thisWeekStart = getThisWeekStartMillis()
                _plannedWeekStartMillis.value = thisWeekStart

                _aiPlan.value = parsed
                applyAiPlan(parsed)

                // Zapisz plan lokalnie + Firebase
                val prefs = context.getSharedPreferences("ai_plan", Context.MODE_PRIVATE)
                prefs.edit {
                    putString("current_plan", Gson().toJson(parsed))
                    putLong("planned_week_start", thisWeekStart)
                }
                saveWeeklyTasksLocal()
                saveWeeklyTasksToFirebase()
                saveGoalDrafts(context, goalDrafts)

                Log.d("AI_PLAN", "generateRemainingDays: plan zastosowany, dni=${parsed.dailyPlan.keys}")
            } catch (e: Exception) {
                Log.e("AI_PLAN", "generateRemainingDays BLAD: ${e.message}")
            } finally {
                _isPlanningWeek.value = false
            }
        }
    }

    private fun getThisWeekStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private suspend fun buildPromptForCurrentWeek(
        context: Context,
        difficulty: Int,
        goalDrafts: Map<String, WeeklyGoalDraft>,
        historyJson: String
    ): String {
        val allDays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val todayIndex = allDays.indexOf(getTodayDayName()).coerceAtLeast(0)
        val remainingDays = allDays.subList(todayIndex, allDays.size)

        val dailyPoolTasks = allTasks.value.map {
            mapOf(
                "name" to it.taskName, "category" to it.category,
                "isMandatory" to it.isMandatory, "isAIGenerated" to it.isAIGenerated,
                "isCompleted" to it.isCompleted
            )
        }

        val selectedTasksJson = Gson().toJson(goalDrafts)
        val dailyPoolJson = Gson().toJson(dailyPoolTasks)

        return """
Jesteś elitarnym trenerem rozwoju osobistego.

Masz przygotować plan na BIEŻĄCY tydzień (od dziś) po polsku.

WEJŚCIE:
- zamierzona trudność tygodnia: $difficulty
- aktywne dni (od dziś do końca tygodnia): ${Gson().toJson(remainingDays)}
- liczba aktywnych dni: ${remainingDays.size}
- historia z ostatnich 4 tygodni: $historyJson
- wszystkie taski daily/system: $dailyPoolJson
- wybrane cele użytkownika: $selectedTasksJson

ZASADY:
- zwróć tylko poprawny JSON
- weeklyTasks muszą być głównymi celami tygodniowymi
- dailyPlan ma zawierać WYŁĄCZNIE dni z listy aktywnych dni
- KRYTYCZNE: KAŻDY dzień z listy aktywnych dni MUSI mieć co najmniej jeden task
- daily taski muszą wynikać z weeklyTasks
- durationMinutes: sugerowany czas (5-90, domyślnie 30)
- quote ma być motywacyjny i po polsku
- summary ma opisać ogólny stan

ZWROT TYLKO JSON:
{
  "weeklyTasks": [
    {
      "name": "string",
      "type": "counter|milestone",
      "goal": number
    }
  ],
  "dailyPlan": {
    "DAYNAME": [
      {
        "mainName": "string",
        "description": "string",
        "targetValue": number,
        "durationMinutes": number
      }
    ]
  },
  "summary": "string",
  "quote": "string"
}
""".trimIndent()
    }

    //  ── Health Connect - kroki ─────────────────────────────────────────────

    /**
     * Pobiera sumę kroków za dzisiejszy dzień z Health Connect i aktualizuje [todaySteps].
     * Rozróżnia trzy przypadki:
     *   - HC niedostępny → nic nie robi
     *   - Brak uprawnienia → [hcPermissionNeeded] = true
     *   - Uprawnienie OK → czyta kroki; jeśli brak źródła (0) → [hcNoDataSource] = true
     */
    fun refreshTodaySteps(context: Context) {
        viewModelScope.launch {
            _hcRefreshing.value = true

            // Zmienne diagnostyczne - zbierane przez cały flow, zapisywane w finally
            var diagSdkStatus = -1
            var diagGranted = emptySet<String>()
            var diagReadPerm = ""
            var diagPermGranted = false
            var diagStartOfDay = ""
            var diagEndTime = ""
            var diagZoneId = ""
            var diagAggregateRaw: Long? = null
            var diagRecordsCount = 0
            var diagFirstRecord: String? = null
            var diagLastRecord: String? = null
            var diagException: String? = null

            try {
                //  ── [1] SDK status ────────────────────────────────────────────
                val sdkStatus = HealthConnectClient.getSdkStatus(context)
                diagSdkStatus = sdkStatus
                Log.d("HC", "[1] getSdkStatus=$sdkStatus  (SDK_AVAILABLE=${HealthConnectClient.SDK_AVAILABLE})")
                if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                    Log.w("HC", "[1] Health Connect niedostępny — abort (status=$sdkStatus)")
                    return@launch
                }

                val hc = HealthConnectClient.getOrCreate(context)

                //  ── [2] Uprawnienia ───────────────────────────────────────────
                val readPerm = HealthPermission.getReadPermission(StepsRecord::class)
                diagReadPerm = readPerm
                val granted = hc.permissionController.getGrantedPermissions()
                diagGranted = granted
                val permGranted = readPerm in granted
                diagPermGranted = permGranted
                Log.d("HC", "[2] readPermRequired=$readPerm")
                Log.d("HC", "[2] grantedPermissions=$granted")
                Log.d("HC", "[2] permissionGranted=$permGranted")

                if (!permGranted) {
                    Log.w("HC", "[2] Brak READ_STEPS — ustawiam hcPermissionNeeded=true")
                    _hcPermissionNeeded.value = true
                    _hcNoDataSource.value = false
                    return@launch
                }
                _hcPermissionNeeded.value = false

                //  ── [3] Zakres czasu ──────────────────────────────────────────
                val zoneId = ZoneId.systemDefault()
                diagZoneId = zoneId.toString()
                val startOfDay = LocalDateTime.now()
                    .toLocalDate()
                    .atStartOfDay()
                    .atZone(zoneId)
                    .toInstant()
                val endTime = Instant.now()
                diagStartOfDay = startOfDay.toString()
                diagEndTime = endTime.toString()
                Log.d("HC", "[3] zoneId=$zoneId")
                Log.d("HC", "[3] startOfDay=$startOfDay")
                Log.d("HC", "[3] endTime=$endTime")
                Log.d("HC", "[3] AggregateRequest: metrics=[COUNT_TOTAL] range=[$startOfDay .. $endTime]")

                // ── [4] aggregate() ───────────────────────────────────────────
                val response = hc.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endTime)
                    )
                )
                val rawMetric = response[StepsRecord.COUNT_TOTAL]
                diagAggregateRaw = rawMetric
                Log.d("HC", "[4] aggregate response=$response")
                Log.d("HC", "[4] StepsRecord.COUNT_TOTAL=$rawMetric  (null=brak źródła danych w HC)")

                val steps = rawMetric ?: 0L
                _todaySteps.value = steps
                _hcNoDataSource.value = (rawMetric == null)
                Log.d("HC", "[4] _todaySteps ustawione na=$steps  hcNoDataSource=${_hcNoDataSource.value}")

                // Auto-complete: sprawdź czy któraś z aktywnych STEPS tasks osiągnęła cel
                checkAutoCompleteSteps(steps)

                //── [5] readRecords() gdy aggregate null lub 0 - deep diagnostics ──
                if (rawMetric == null || rawMetric == 0L) {
                    Log.d("HC", "[5] aggregate=$rawMetric → uruchamiam readRecords<StepsRecord> dla diagnostyki")
                    try {
                        val result = hc.readRecords(
                            ReadRecordsRequest(
                                recordType = StepsRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(startOfDay, endTime)
                            )
                        )
                        diagRecordsCount = result.records.size
                        Log.d("HC", "[5] readRecords count=${result.records.size}")
                        if (result.records.isNotEmpty()) {
                            val first = result.records.first()
                            val last = result.records.last()
                            diagFirstRecord = "count=${first.count} start=${first.startTime} end=${first.endTime}"
                            diagLastRecord  = "count=${last.count}  start=${last.startTime}  end=${last.endTime}"
                            Log.d("HC", "[5] first record: $diagFirstRecord")
                            Log.d("HC", "[5] last  record: $diagLastRecord")
                        } else {
                            Log.w("HC", "[5] readRecords EMPTY — zero rekordów kroków za dzisiaj")
                            Log.w("HC", "[5] => Brak danych kroków w Health Connect.")
                            Log.w("HC", "[5] => Połącz Google Fit / Fitbit / Samsung Health z Health Connect.")
                        }
                    } catch (re: Exception) {
                        Log.e("HC", "[5] readRecords exception: ${re::class.simpleName}: ${re.message}")
                    }
                }

            } catch (e: SecurityException) {
                diagException = "SecurityException: ${e.message}"
                Log.w("HC", "[ERR] SecurityException: ${e.message}")
                _hcPermissionNeeded.value = true
                _hcNoDataSource.value = false
            } catch (e: Exception) {
                diagException = "${e::class.simpleName}: ${e.message}"
                Log.e("HC", "[ERR] ${e::class.simpleName}: ${e.message}")
            } finally {
                _hcRefreshing.value = false
                val diag = HcDiagnostics(
                    sdkStatus = diagSdkStatus,
                    grantedPermissions = diagGranted,
                    readPermRequired = diagReadPerm,
                    permissionGranted = diagPermGranted,
                    startOfDay = diagStartOfDay,
                    endTime = diagEndTime,
                    zoneId = diagZoneId,
                    aggregateRawValue = diagAggregateRaw,
                    todayStepsAfterUpdate = _todaySteps.value,
                    recordsCount = diagRecordsCount,
                    firstRecord = diagFirstRecord,
                    lastRecord = diagLastRecord,
                    exception = diagException
                )
                _hcDiagnostics.value = diag
                Log.d("HC", "[FINAL] _todaySteps=${_todaySteps.value}")
                Log.d("HC", "[FINAL] diagnostics=$diag")
            }

            //  Po odczycie kroków - sprawdź czy jakieś zadania przekroczyły północ
            checkMidnightCrossing(context)
        }
    }

    // Wywołaj po przyznaniu uprawnienia HC - czyści flagę i odczytuje kroki
    fun onHealthConnectPermissionGranted(context: Context) {
        _hcPermissionNeeded.value = false
        _hcNoDataSource.value = false
        refreshTodaySteps(context)
    }

    /**
     * Ustawia punkt startowy kroków dla zadania STEPS uruchamianego ręcznie
     * (gdy zadanie nie jest zaplanowane na dziś). Punkt = aktualna liczba kroków
     */
    fun setStepsBaseline(taskId: Int) {
        val baseline = _todaySteps.value
        _stepsBaselines.value = _stepsBaselines.value + (taskId to baseline)
        Log.d("Steps", "[setStepsBaseline] taskId=$taskId baseline=$baseline todaySteps=${_todaySteps.value}")
    }

    // ── Preferencje trybu krokomierza ─────────────────────────────────────

    fun loadStepsCountingDontAsk(context: Context): Boolean {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("steps_counting_dont_ask_again", false)
    }

    fun loadStepsCountingMode(context: Context): String {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("steps_counting_mode", "FROM_MIDNIGHT") ?: "FROM_MIDNIGHT"
    }

    fun saveStepsCountingPrefs(context: Context, mode: String, dontAskAgain: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit {
                putString("steps_counting_mode", mode)
                putBoolean("steps_counting_dont_ask_again", dontAskAgain)
            }
    }

    /**
     * Startuje zadanie STEPS z wybranym trybem. Persystuje do Room + Firebase.
     * Zwraca true jeśli zadanie zostało od razu zaliczone (FROM_MIDNIGHT + todaySteps >= goal)
     */
    fun startStepsTask(task: TaskEntity, mode: String): Boolean {
        val now = System.currentTimeMillis()
        val baseline = if (mode == "FROM_MIDNIGHT") 0L else _todaySteps.value
        val updated = task.copy(
            stepsBaseline = baseline,
            stepsStartedAt = now,
            stepsCountingMode = mode
        )
        //  Aktualizuj też in-memory _stepsBaselines (dopóki UI go używa)
        _stepsBaselines.value = _stepsBaselines.value + (task.id to baseline)
        updateTask(updated)
        Log.d("Steps", "[startStepsTask] task=${task.id}/${task.taskName} mode=$mode baseline=$baseline todaySteps=${_todaySteps.value}")

        //  Sprawdź natychmiastowe zaliczenie (FROM_MIDNIGHT + cel osiągnięty)
        val goal = task.stepGoal ?: 0
        if (mode == "FROM_MIDNIGHT" && goal > 0 && _todaySteps.value >= goal) {
            Log.d("Steps", "[startStepsTask] od razu zaliczone: todaySteps=${_todaySteps.value} >= goal=$goal")
            finishStepsTask(task, _todaySteps.value, 5, "Cel osiągnięty przed startem zadania")
            return true
        }
        return false
    }

    /**
     * Rozwiązuje zadania STEPS które przetrwały z poprzedniego dnia (po restarcie apki).
     * Odczytuje kroki z HC za dzień startu zadania i zapisuje wynik do historii
     */
    fun resolveStaleStepsTasks(context: Context) {
        viewModelScope.launch {
            val tasks = allTasks.value.filter { it.stepsBaseline != null && it.stepsStartedAt != null }
            if (tasks.isEmpty()) return@launch

            val todayStart = normalizeDay(System.currentTimeMillis())
            val staleTasks = tasks.filter { normalizeDay(it.stepsStartedAt!!) < todayStart }
            if (staleTasks.isEmpty()) return@launch

            Log.d("Steps", "[resolveStaleStepsTasks] found ${staleTasks.size} stale tasks")

            for (task in staleTasks) {
                val startedAt = task.stepsStartedAt!!
                val taskDayStart = normalizeDay(startedAt)
                val taskDayEnd = taskDayStart + 86_400_000L

                //Odczytaj kroki z HC za dzień w którym zadanie było aktywne
                val stepsForDay = readStepsFromHc(context, taskDayStart, taskDayEnd)
                val goal = task.stepGoal ?: 0
                val baseline = task.stepsBaseline ?: 0L
                val effectiveSteps = if (task.stepsCountingMode == "FROM_MIDNIGHT") {
                    stepsForDay
                } else {
                    (stepsForDay - baseline).coerceAtLeast(0L)
                }
                val completed = goal > 0 && effectiveSteps >= goal

                Log.d("Steps", "[resolveStaleStepsTasks] task=${task.id}/${task.taskName} " +
                    "stepsForDay=$stepsForDay baseline=$baseline effective=$effectiveSteps goal=$goal completed=$completed")

                val history = TaskHistoryEntity(
                    taskId = task.id,
                    taskName = task.taskName,
                    category = task.category,
                    date = startedAt,
                    completed = completed,
                    stepsCompleted = effectiveSteps,
                    note = if (!completed) "Cel nieosiągnięty ($effectiveSteps/$goal kroków)" else null,
                    isAIGenerated = task.isAIGenerated,
                    aiMainName = if (task.isAIGenerated) task.category else null
                )
                saveHistoryBoth(history)

                // Wyczyść pola aktywności
                val cleared = task.copy(stepsBaseline = null, stepsStartedAt = null, stepsCountingMode = null)
                taskDao.updateTask(cleared)
                _stepsBaselines.value = _stepsBaselines.value - task.id

                val userId = AuthManager.getUserId()
                if (userId != null) {
                    FirebaseRepository.db.collection("users").document(userId)
                        .collection("tasks").document(task.id.toString())
                        .set(cleared.toMap())
                }
            }
        }
    }

    /**
     * Sprawdza w runtime czy któreś aktywne zadania STEPS przekroczyły północ.
     * Wywoływane przy każdym refreshTodaySteps
     */
    private suspend fun checkMidnightCrossing(context: Context) {
        val todayStart = normalizeDay(System.currentTimeMillis())
        val tasks = allTasks.value.filter {
            it.stepsBaseline != null && it.stepsStartedAt != null &&
                normalizeDay(it.stepsStartedAt!!) < todayStart
        }
        if (tasks.isEmpty()) return
        // Deleguj do pełnego resolve
        resolveStaleStepsTasks(context)
    }

    /**
     * Pomocnicza: odczytuje sumę kroków z Health Connect za podany zakres czasu
     */
    private suspend fun readStepsFromHc(context: Context, startMillis: Long, endMillis: Long): Long {
        return try {
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) return 0L
            val hc = HealthConnectClient.getOrCreate(context)
            val readPerm = HealthPermission.getReadPermission(StepsRecord::class)
            if (readPerm !in hc.permissionController.getGrantedPermissions()) return 0L

            val zoneId = ZoneId.systemDefault()
            val startInstant = Instant.ofEpochMilli(startMillis)
            val endInstant = Instant.ofEpochMilli(endMillis)
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            Log.e("HC", "[readStepsFromHc] error: ${e.message}")
            0L
        }
    }

    //─────────────────────────────────────────────────────────────────────────
    //  Mandatory task scheduling
    // ─────────────────────────────────────────────────────────────────────────

    // Mapowanie Calendar.DAY_OF_WEEK → kod używany w recurrenceValue ("Pn", "Wt", ...)
    private fun dayCodePl(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY    -> "Pn"
        Calendar.TUESDAY   -> "Wt"
        Calendar.WEDNESDAY -> "Śr"
        Calendar.THURSDAY  -> "Cz"
        Calendar.FRIDAY    -> "Pt"
        Calendar.SATURDAY  -> "Sb"
        Calendar.SUNDAY    -> "Nd"
        else               -> ""
    }

    //  True jeśli ten konkretny dzień jest "pomijany" w ustawieniach (weekend lub święto)
    private fun isDaySkipped(timeMillis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
        if (isWeekend && showWeekends.value) return true
        val normalized = normalizeDay(timeMillis)
        val isHoliday = holidays.value.any { normalizeDay(it) == normalized }
        if (isHoliday && !showHolidays.value) return true
        return false
    }

    /**
     * Czy zadanie obowiązkowe wypada w konkretnym dniu?
     * Logika dla każdego recurrenceType. Uwzględnia weekend/holiday skip.
     * X_TIMES_WEEK używa deterministycznego algorytmu na poziomie tygodnia
     */
    private fun mandatoryFallsOn(task: TaskEntity, dayStart: Long): Boolean {
        if (!task.isMandatory) return false
        if (isDaySkipped(dayStart)) return false
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        return when (task.recurrenceType) {
            "DAILY" -> true
            "SPECIFIC_DAYS" -> {
                val code = dayCodePl(cal.get(Calendar.DAY_OF_WEEK))
                task.recurrenceValue?.split(",")?.map { it.trim() }?.contains(code) == true
            }
            "X_TIMES_WEEK" -> {
                val timesPerWeek = task.recurrenceValue?.toIntOrNull() ?: return false
                if (timesPerWeek <= 0) return false
                val activeDays = activeDaysInWeek(dayStart)
                if (activeDays.isEmpty()) return false
                val capped = minOf(timesPerWeek, activeDays.size)
                // równomierny rozkład: wybieramy co (activeDays.size / capped) dzień
                val r = (activeDays.size.toFloat() / capped).coerceAtLeast(1f)
                val chosen = (0 until capped).map { i ->
                    activeDays[(i * r).toInt().coerceAtMost(activeDays.size - 1)]
                }
                chosen.contains(normalizeDay(dayStart))
            }
            else -> false
        }
    }

    // Zwraca listę dat (start-of-day) w tygodniu zawierającym [anyDayMillis], które NIE są pomijane
    private fun activeDaysInWeek(anyDayMillis: Long): List<Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anyDayMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val result = mutableListOf<Long>()
        for (i in 0..6) {
            val day = cal.timeInMillis
            if (!isDaySkipped(day)) result.add(normalizeDay(day))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    //  Zwraca date startu najbliższej (lub bieżącej) niedzieli włącznie, jako start-of-day
    private fun upcomingSundayInclusive(fromMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /**
     * Rozpisuje pojedyncze zadanie obowiązkowe od `fromDay` (włącznie) do `toDay` (włącznie).
     * Idempotentne - dzięki UNIQUE check w DAO duplikaty są pomijane
     */
    private suspend fun scheduleMandatoryRange(task: TaskEntity, fromDay: Long, toDay: Long) {
        if (!task.isMandatory) return
        val entries = mutableListOf<MandatoryScheduleEntity>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromDay
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endNorm = normalizeDay(toDay)
        while (normalizeDay(cal.timeInMillis) <= endNorm) {
            val day = normalizeDay(cal.timeInMillis)
            if (mandatoryFallsOn(task, day)) {
                val existing = mandatoryScheduleDao.countForTaskOnDay(task.id, day)
                if (existing == 0) {
                    entries += MandatoryScheduleEntity(taskId = task.id, date = day)
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (entries.isNotEmpty()) mandatoryScheduleDao.insertAll(entries)
    }

    // Rozpisuje JEDNO obowiązkowe zadanie od dziś do najbliższej niedzieli włącznie
    fun scheduleMandatoryUntilSunday(task: TaskEntity) {
        if (!task.isMandatory) return
        viewModelScope.launch {
            val today = normalizeDay(System.currentTimeMillis())
            val sunday = upcomingSundayInclusive(today)
            scheduleMandatoryRange(task, today, sunday)
        }
    }

    /**
     * Wywoływane przy starcie aplikacji. Jeśli ostatnia zaplanowana data jest wcześniejsza niż
     * najbliższa niedziela, rozpisz cały kolejny tydzień (Pn-Nd) dla wszystkich mandatory tasks.
     * To pokrywa scenariusz "user nie używa apki w niedzielę"
     */
    fun ensureMandatoryScheduledForWeek() {
        viewModelScope.launch {
            val today = normalizeDay(System.currentTimeMillis())
            val sunday = upcomingSundayInclusive(today)
            val lastScheduled = mandatoryScheduleDao.getLastScheduledDate() ?: 0L

            // Zawsze zapewnij że bieżący tydzień (do najbliższej niedzieli) jest pokryty
            val allMandatory = taskDao.getAllTasksOnce().filter { it.isMandatory }
            allMandatory.forEach { task ->
                scheduleMandatoryRange(task, today, sunday)
            }

            //Jeśli już mamy harmonogram do tej niedzieli, rozpisz kolejny tydzień (Pn-Nd)
            if (lastScheduled >= sunday) {
                val nextMonday = Calendar.getInstance().apply {
                    timeInMillis = sunday
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                val nextSunday = Calendar.getInstance().apply {
                    timeInMillis = nextMonday
                    add(Calendar.DAY_OF_YEAR, 6)
                }.timeInMillis
                allMandatory.forEach { task ->
                    scheduleMandatoryRange(task, nextMonday, nextSunday)
                }
            }
        }
    }

    fun getMandatoryScheduleForDay(day: Long): kotlinx.coroutines.flow.Flow<List<MandatoryScheduleEntity>> {
        val start = normalizeDay(day)
        val end = start + 86_399_999L
        return mandatoryScheduleDao.getScheduleBetween(start, end)
    }

    fun clearMandatoryScheduleForTask(taskId: Int) {
        viewModelScope.launch { mandatoryScheduleDao.deleteForTask(taskId) }
    }

    fun isActiveToday(task: TaskEntity): Boolean {
        if (!task.isMandatory) return false
        val today = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "Pn"
            Calendar.TUESDAY   -> "Wt"
            Calendar.WEDNESDAY -> "Śr"
            Calendar.THURSDAY  -> "Cz"
            Calendar.FRIDAY    -> "Pt"
            Calendar.SATURDAY  -> "Sb"
            Calendar.SUNDAY    -> "Nd"
            else -> ""
        }
        return when (task.recurrenceType) {
            "DAILY" -> true
            "SPECIFIC_DAYS" ->
                task.recurrenceValue?.split(",")?.map { it.trim() }?.contains(today) == true
            else -> false   // X_TIMES_WEEK - nie da się ustalić bez historii; użytkownik startuje ręcznie
        }
    }

    fun getTodayDayName(): String = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY    -> "MONDAY"
        Calendar.TUESDAY   -> "TUESDAY"
        Calendar.WEDNESDAY -> "WEDNESDAY"
        Calendar.THURSDAY  -> "THURSDAY"
        Calendar.FRIDAY    -> "FRIDAY"
        Calendar.SATURDAY  -> "SATURDAY"
        Calendar.SUNDAY    -> "SUNDAY"
        else               -> "MONDAY"
    }

    fun addDeferredRunToCalendar(dayOffset: Int, taskName: String, aiMainName: String?, distanceKm: Float, targetKm: Float) {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
        val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "MONDAY"; Calendar.TUESDAY -> "TUESDAY"
            Calendar.WEDNESDAY -> "WEDNESDAY"; Calendar.THURSDAY -> "THURSDAY"
            Calendar.FRIDAY -> "FRIDAY"; Calendar.SATURDAY -> "SATURDAY"
            Calendar.SUNDAY -> "SUNDAY"; else -> "MONDAY"
        }
        val name = aiMainName ?: taskName
        val remaining = if (targetKm > 0) (targetKm - distanceKm).coerceAtLeast(0.5f) else 0f
        val deferredTask = com.MateuszW.aigrowthtracker.data.AiDailyTask(
            mainName = name,
            description = "Kontynuacja biegu — wykonano %.2f km%s".format(
                distanceKm,
                if (targetKm > 0) " z %.1f km celu".format(targetKm) else ""
            ),
            targetValue = if (remaining > 0) remaining else null,
            doneValue = distanceKm
        )
        val current = _aiDailyTasks.value.toMutableMap()
        val dayTasks = (current[dayName] ?: emptyList()).toMutableList()
        dayTasks.removeAll { it.mainName == name }
        dayTasks.add(deferredTask)
        current[dayName] = dayTasks
        _aiDailyTasks.value = current
    }

    companion object {
        const val MOCK_REVIEW_SUMMARY =
            "[TRYB TESTOWY] Świetny tydzień! Ukończyłeś 4 z 5 zaplanowanych sesji biegowych " +
            "i wszystkie zadania programistyczne. Widać regularność i zaangażowanie — " +
            "szczególnie imponujące są czwartkowe interwały. Tak trzymaj!"

        val MOCK_WEEKLY_PLAN = AiWeeklyPlan(
            weeklyTasks = listOf(
                AiWeeklyTask("Bieganie", "counter", 25f),
                AiWeeklyTask("Programowanie", "milestone", 5f)
            ),
            dailyPlan = mapOf(
                "MONDAY" to listOf(
                    AiDailyTask("Bieganie", "Poranny bieg 5 km w spokojnym tempie", 5f),
                    AiDailyTask("Programowanie", "Lekcja algorytmów — sortowanie")
                ),
                "TUESDAY" to listOf(
                    AiDailyTask("Bieganie", "Interwały 6×400 m na bieżni", 5f)
                ),
                "WEDNESDAY" to listOf(
                    AiDailyTask("Bieganie", "Regeneracyjny bieg 5 km", 5f),
                    AiDailyTask("Programowanie", "Projekt: implementacja drzewa BST")
                ),
                "THURSDAY" to listOf(
                    AiDailyTask("Bieganie", "Bieg z podbiegnięciami 5 km", 5f)
                ),
                "FRIDAY" to listOf(
                    AiDailyTask("Bieganie", "Lekki bieg finałowy 5 km", 5f),
                    AiDailyTask("Programowanie", "Code review i dokumentacja")
                )
            ),
            summary = MOCK_REVIEW_SUMMARY,
            quote = "Każdy wielki biegacz zaczynał od jednego kroku. Ty robisz kolejny."
        )
    }

}