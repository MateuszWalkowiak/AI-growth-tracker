package com.MateuszW.aigrowthtracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.MateuszW.aigrowthtracker.ui.theme.AIGrowthTrackerTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.MateuszW.aigrowthtracker.data.AppDatabase
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.TaskEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import com.MateuszW.aigrowthtracker.data.AuthManager
import com.MateuszW.aigrowthtracker.data.FirebaseRepository
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import com.MateuszW.aigrowthtracker.data.WeeklyGoalDraft
import com.MateuszW.aigrowthtracker.data.WeeklyReviewInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.net.Uri
import com.MateuszW.aigrowthtracker.running.RunViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.MateuszW.aigrowthtracker.running.RunningScreen
import com.MateuszW.aigrowthtracker.running.RunHistoryScreen
import com.MateuszW.aigrowthtracker.running.RunDetailScreen
import com.MateuszW.aigrowthtracker.running.isRunningTask
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.READ_CALENDAR)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.WRITE_CALENDAR)
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        }
        enableEdgeToEdge()
        setContent {
            AIGrowthTrackerTheme {
                var user by remember { mutableStateOf(AuthManager.auth.currentUser) }

                LaunchedEffect(Unit) {
                    AuthManager.auth.addAuthStateListener { firebaseAuth ->
                        user = firebaseAuth.currentUser
                    }
                }

                if (user == null) {
                    LoginScreen()
                    return@AIGrowthTrackerTheme
                }
                val context = LocalContext.current
                val db = remember { AppDatabase.getDatabase(context) }
                // Sync historii z Firebase — ZAWSZE przy zmianie konta (lub starcie)
                // Czyści lokalną historię i wczytuje aktualną z Firebase,
                // żeby uniknąć pozostałości z poprzedniego konta.
                LaunchedEffect(user?.uid) {
                    val userId = AuthManager.getUserId()
                    if (userId != null) {
                        try {
                            val remote = FirebaseRepository.db
                                .collection("users")
                                .document(userId)
                                .collection("history")
                                .get()
                                .await()

                            // Wyczyść lokalną historię i wstaw dane z Firebase
                            db.taskHistoryDao().deleteAll()

                            if (!remote.isEmpty) {
                                val historyList = remote.documents.map { doc ->
                                    TaskHistoryEntity(
                                        taskId = (doc.getLong("taskId") ?: -1).toInt(),
                                        syncId = doc.getString("syncId") ?: java.util.UUID.randomUUID().toString(),
                                        taskName = doc.getString("taskName") ?: "",
                                        category = doc.getString("category") ?: "",
                                        date = doc.getLong("date") ?: System.currentTimeMillis(),
                                        completed = doc.getBoolean("completed") ?: false,
                                        rating = doc.getLong("rating")?.toInt(),
                                        note = doc.getString("note"),
                                        isAIGenerated = doc.getBoolean("isAIGenerated") ?: false,
                                        aiMainName = doc.getString("aiMainName"),
                                        difficulty = doc.getLong("difficulty")?.toInt(),
                                        stepsCompleted = doc.getLong("stepsCompleted"),
                                        aiInstructions = doc.getString("aiInstructions")
                                    )
                                }
                                db.taskHistoryDao().insertAll(historyList)
                            } else {
                                // Nowe konto bez historii → wpis powitalny
                                db.taskHistoryDao().insert(
                                    TaskHistoryEntity(
                                        taskId = -1,
                                        taskName = "Witamy w aplikacji",
                                        category = "System",
                                        date = System.currentTimeMillis(),
                                        completed = true,
                                        note = "Trzymamy za Ciebie kciuki!"
                                    )
                                )
                            }
                        } catch (_: Exception) {
                            // Offline — zachowaj lokalne dane (jeśli jakieś istnieją)
                        }
                    }
                }

                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return when {
                            modelClass.isAssignableFrom(TaskViewModel::class.java) ->
                                TaskViewModel(
                                    taskDao = db.taskDao(),
                                    taskHistoryDao = db.taskHistoryDao(),
                                    weeklyTaskDao = db.weeklyTaskDao(),
                                    weeklyFlowRecordDao = db.weeklyFlowRecordDao(),
                                    mandatoryScheduleDao = db.mandatoryScheduleDao()
                                ) as T
                            modelClass.isAssignableFrom(RunViewModel::class.java) ->
                                RunViewModel(
                                    runDao = db.runDao(),
                                    taskHistoryDao = db.taskHistoryDao()
                                ) as T
                            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                        }
                    }
                }
                val viewModel: TaskViewModel = viewModel(factory = factory)
                val runViewModel: RunViewModel = viewModel(factory = factory)

                val navController = rememberNavController()

                val accountCreationDateState = remember { mutableStateOf<Long?>(null) }

                LaunchedEffect(user?.uid) {
                    accountCreationDateState.value = db.taskHistoryDao().getRealFirstDate()
                    runViewModel.loadRunsFromFirebase()

                    // Sync weekly tasks z Firebase — zawsze przy zmianie konta
                    try {
                        val remoteWeekly = viewModel.loadWeeklyFromFirebase()
                        db.weeklyTaskDao().clearAll()
                        if (remoteWeekly.isNotEmpty()) {
                            viewModel.setWeeklyTasks(remoteWeekly)
                            viewModel.saveWeeklyTasksLocal()
                        }
                    } catch (_: Exception) {
                        // Offline — załaduj lokalne dane
                        val local = viewModel.loadWeeklyFromLocal()
                        if (local.isNotEmpty()) viewModel.setWeeklyTasks(local)
                    }
                }
                val flowState by viewModel.weeklyFlowState.collectAsState()


                NavHost(navController = navController, startDestination = "main_screen") {

                    composable("main_screen") {
                        val context = LocalContext.current
                        LaunchedEffect(Unit) {
                            viewModel.loadSettingsSmart(context)
                            viewModel.restoreAiPlan(context)

                            // Weekly tasks ładowane przy sync (LaunchedEffect uid),
                            // tu tylko fallback z local jeśli jeszcze puste
                            if (viewModel.weeklyTasks.value.isEmpty()) {
                                val local = viewModel.loadWeeklyFromLocal()
                                if (local.isNotEmpty()) viewModel.setWeeklyTasks(local)
                            }

                            if (viewModel.shouldOpenWeeklyFlow(context)) {
                                navController.navigate("weekly_review") {
                                    launchSingleTop = true
                                }
                            }
                        }

                        val tasks by viewModel.allTasks.collectAsState(initial = emptyList())
                        val activeTask by viewModel.activeTask.collectAsState()
                        val remainingTime by viewModel.remainingTime.collectAsState()
                        val completedDates by viewModel.completedDates.collectAsState()
                        val holidays by viewModel.holidays
                        val showHolidays by viewModel.showHolidays
                        val showWeekends by viewModel.showWeekends

                        // ── Dane do dynamicznego progresu (ta sama logika co DayDetailsScreen) ──
                        val aiDailyTasksAll by viewModel.aiDailyTasks.collectAsState()
                        val plannedWeekStart by viewModel.plannedWeekStartMillis.collectAsState()

                        val todayStart = remember {
                            Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }
                        val todayHistoryFlow = remember(todayStart) {
                            viewModel.getHistoryForDay(todayStart)
                        }
                        val todayHistory by todayHistoryFlow.collectAsState()

                        val todayDayName = remember { viewModel.getTodayDayName() }
                        val todayAiTasks = remember(aiDailyTasksAll, plannedWeekStart, todayStart) {
                            val weekEnd = plannedWeekStart?.let { it + 7L * 24 * 60 * 60 * 1000 }
                            if (plannedWeekStart != null && todayStart >= plannedWeekStart!! && todayStart < weekEnd!!) {
                                aiDailyTasksAll[todayDayName] ?: emptyList()
                            } else emptyList()
                        }

                        val todayMandatoryFlow = remember(todayStart) {
                            viewModel.getMandatoryScheduleForDay(todayStart)
                        }
                        val todayMandatorySchedule by todayMandatoryFlow.collectAsState(initial = emptyList())
                        val todayMandatoryCount = remember(todayMandatorySchedule, tasks) {
                            val taskMap = tasks.associateBy { it.id }
                            todayMandatorySchedule
                                .map { it.taskId }
                                .distinct()
                                .count { taskMap.containsKey(it) }
                        }

                        val allDailyCount = remember(tasks) { tasks.count { !it.isMandatory } }

                        MainScreen(
                            viewModel = viewModel,
                            activeTask = activeTask,
                            remainingTime = remainingTime,
                            completedDates = completedDates,
                            holidays = holidays,
                            showHolidays = showHolidays,
                            showWeekends = showWeekends,
                            onAddTaskClick = { navController.navigate("add_task_screen") },
                            onExecuteTaskClick = { navController.navigate("execute_task_screen") },
                            onStatsClick = { navController.navigate("stats_screen") },
                            onDayClick = { day ->
                                navController.navigate("day_details/$day")
                            },
                            onSettingsClick = { navController.navigate("settings_screen") },
                            onFinishTask = { viewModel.finishTask() },
                            onFinishTaskWithSummary = { r, n -> viewModel.finishTaskWithSummary(r, n) },
                            onCancelTask = { viewModel.cancelTask() },
                            todayHistory = todayHistory,
                            todayAiTasksCount = todayAiTasks.size,
                            todayMandatoryTasksCount = todayMandatoryCount,
                            allDailyTasksCount = allDailyCount
                        )
                    }
                    composable("add_task_screen") {
                        val categories by viewModel.existingCategories.collectAsState(initial = emptyList())

                        AddTaskScreen(
                            categoriesFromDb = categories,
                            onSaveClick = { newTask ->
                                viewModel.saveTask(newTask)
                                navController.popBackStack()
                            },
                            onCancelClick = { navController.popBackStack() }
                        )
                    }
                    composable("execute_task_screen") {
                        val ctx = LocalContext.current

                        // ── Health Connect: launcher uprawnień ────────────────────────
                        val hcPermissions = remember {
                            setOf(HealthPermission.getReadPermission(StepsRecord::class))
                        }
                        // Używamy właściwego kontraktu HC — launch przyjmuje Set<String>, nie Intent
                        val hcPermissionLauncher = rememberLauncherForActivityResult(
                            PermissionController.createRequestPermissionResultContract()
                        ) { _ ->
                            // Po powrocie z ekranu uprawnień → odczytaj kroki
                            viewModel.refreshTodaySteps(ctx)
                        }

                        // ── Przy wejściu na ekran: sprawdź uprawnienie, potem odczytaj ─
                        LaunchedEffect(Unit) {
                            // Rozwiąż zadania STEPS z poprzedniego dnia (po restarcie apki)
                            viewModel.resolveStaleStepsTasks(ctx)

                            if (HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE) {
                                val client = HealthConnectClient.getOrCreate(ctx)
                                val readPerm = HealthPermission.getReadPermission(StepsRecord::class)
                                val granted = client.permissionController.getGrantedPermissions()
                                if (readPerm in granted) {
                                    viewModel.refreshTodaySteps(ctx)
                                } else {
                                    // Właściwe wywołanie z Set<String>, nie Intent
                                    hcPermissionLauncher.launch(hcPermissions)
                                }
                            }
                        }

                        // ── Odświeżaj kroki co 30 sekund ─────────────────────────────
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(30_000L)
                                viewModel.refreshTodaySteps(ctx)
                            }
                        }

                        val tasks by viewModel.allTasks.collectAsState(initial = emptyList())
                        val weeklyTasks by viewModel.weeklyTasks.collectAsState()
                        val weekPlanned by viewModel.weekPlanned.collectAsState()
                        val activeTask by viewModel.activeTask.collectAsState()
                        val remainingTime by viewModel.remainingTime.collectAsState()
                        val aiPlan by viewModel.aiPlan.collectAsState()
                        val todaySteps by viewModel.todaySteps.collectAsState()
                        val stepsBaselines by viewModel.stepsBaselines.collectAsState()
                        val aiDailyTasksAll by viewModel.aiDailyTasks.collectAsState()
                        val hcPermissionNeeded by viewModel.hcPermissionNeeded.collectAsState()
                        val hcNoDataSource by viewModel.hcNoDataSource.collectAsState()
                        val hcRefreshing by viewModel.hcRefreshing.collectAsState()
                        val hcDiagnostics by viewModel.hcDiagnostics.collectAsState()
                        val plannedWeekStart by viewModel.plannedWeekStartMillis.collectAsState()
                        val aiTaskInstruction by viewModel.aiTaskInstruction.collectAsState()
                        val aiInstructionLoading by viewModel.aiInstructionLoading.collectAsState()
                        val aiInstructionLoadingTaskId by viewModel.aiInstructionLoadingTaskId.collectAsState()

                        // LOG #2 — przekazanie kroków do ExecuteTaskScreen
                        LaunchedEffect(todaySteps) {
                            android.util.Log.d(
                                "Steps",
                                "[MainActivity→ExecuteTask] todaySteps=$todaySteps " +
                                "stepsBaselines=$stepsBaselines"
                            )
                        }

                        // Zadania obowiązkowe aktywne dziś
                        val activeTodayTasks = remember(tasks) {
                            tasks.filter { viewModel.isActiveToday(it) }
                        }

                        // Zadania AI zaplanowane na dziś
                        val todayDayName = remember { viewModel.getTodayDayName() }
                        val aiTasksForToday = remember(aiDailyTasksAll) {
                            aiDailyTasksAll[todayDayName] ?: emptyList()
                        }

                        // Historia dzisiejszego dnia
                        val todayStart = remember {
                            Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }
                        val todayHistoryFlow = remember(todayStart) {
                            viewModel.getHistoryForDay(todayStart)
                        }
                        val todayHistory by todayHistoryFlow.collectAsState()

                        LaunchedEffect(tasks) {
                            tasks.filter { it.taskType == "STEPS" }.forEach { t ->
                                Log.d("Steps", "[MainActivity→ExecuteTaskScreen] STEPS task=${t.id}/${t.taskName} stepGoal=${t.stepGoal}")
                            }
                            if (tasks.none { it.taskType == "STEPS" }) {
                                Log.d("Steps", "[MainActivity→ExecuteTaskScreen] no STEPS tasks in list (total=${tasks.size})")
                            }
                        }

                        ExecuteTaskScreen(
                            tasks = tasks,
                            weeklyTasks = weeklyTasks,
                            weekPlanned = weekPlanned,
                            activeTask = activeTask,
                            remainingTime = remainingTime,
                            weeklySummary = aiPlan?.summary,
                            weeklyQuote = aiPlan?.quote,
                            todaySteps = todaySteps,
                            stepsBaselines = stepsBaselines,
                            activeTodayTasks = activeTodayTasks,
                            aiTasksForToday = aiTasksForToday,
                            todayHistory = todayHistory,
                            hcPermissionNeeded = hcPermissionNeeded,
                            hcNoDataSource = hcNoDataSource,
                            hcRefreshing = hcRefreshing,
                            hcDiagnostics = hcDiagnostics,
                            onStartTask = { task, c ->
                                if (isRunningTask(task.taskName)) {
                                    navController.navigate(
                                        "running_screen?taskId=${task.id}" +
                                        "&taskName=${Uri.encode(task.taskName)}" +
                                        "&targetKm=${task.targetKm}" +
                                        "&durationMinutes=${task.durationMinutes}" +
                                        "&aiMainName="
                                    )
                                } else {
                                    viewModel.startTask(task, c)
                                }
                            },
                            onFinishTaskWithSummary = { r, n -> viewModel.finishTaskWithSummary(r, n) },
                            onFinishTask = { viewModel.finishTask() },
                            onCancelTask = { viewModel.cancelTask() },
                            onBack = { navController.popBackStack() },
                            onIncrementWeekly = { viewModel.incrementWeeklyTask(it) },
                            onDecrementWeekly = { viewModel.decrementWeeklyTask(it) },
                            onPlanWeek = { navController.navigate("weekly_review") { launchSingleTop = true } },
                            onSetStepsBaseline = { taskId -> viewModel.setStepsBaseline(taskId) },
                            onStartStepsTask = { task, mode -> viewModel.startStepsTask(task, mode) },
                            stepsCountingDontAsk = viewModel.loadStepsCountingDontAsk(ctx),
                            stepsCountingMode = viewModel.loadStepsCountingMode(ctx),
                            onSaveStepsCountingPrefs = { mode, dontAsk ->
                                viewModel.saveStepsCountingPrefs(ctx, mode, dontAsk)
                            },
                            onRequestHcPermission = {
                                if (HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE) {
                                    hcPermissionLauncher.launch(hcPermissions)
                                }
                            },
                            onRefreshSteps = { viewModel.refreshTodaySteps(ctx) },
                            onFinishStepsTask = { task, stepsCompleted, rating, note ->
                                viewModel.finishStepsTask(task, stepsCompleted, rating, note)
                            },
                            onAiTaskDone = { historyEntry ->
                                viewModel.insertHistory(historyEntry)
                                viewModel.incrementWeeklyTaskByName(historyEntry.aiMainName ?: "")
                            },
                            onResumeRun = { entry, remainingKm ->
                                navController.navigate(
                                    "running_screen?taskId=${entry.taskId}" +
                                    "&taskName=${Uri.encode(entry.taskName)}" +
                                    "&targetKm=$remainingKm" +
                                    "&durationMinutes=0" +
                                    "&aiMainName=${Uri.encode(entry.aiMainName ?: "")}"
                                )
                            },
                            plannedWeekStartMillis = plannedWeekStart,
                            aiTaskInstruction = aiTaskInstruction,
                            aiInstructionLoading = aiInstructionLoading,
                            aiInstructionLoadingTaskId = aiInstructionLoadingTaskId,
                            onRequestAiInstruction = { task ->
                                viewModel.generateAiTaskInstruction(task, ctx)
                            },
                            onClearAiInstruction = { viewModel.clearAiInstruction() },
                            onStartWithAiTimer = { task, minutes ->
                                val adjustedTask = task.copy(durationMinutes = minutes)
                                viewModel.startTask(adjustedTask, ctx)
                            }
                        )
                    }
                    composable("stats_screen") {
                        val history by viewModel.history.collectAsState(initial = emptyList())

                        val summaries by viewModel.taskSummaries.collectAsState()

                        StatisticsScreen(
                            summaries = summaries,
                            history = history,
                            onTaskClick = { taskId ->
                                navController.navigate("task_details/$taskId")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("task_details/{taskId}") { backStackEntry ->
                        val taskId = backStackEntry.arguments?.getString("taskId")!!.toInt()
                        val historyFlow = remember(taskId) {
                            viewModel.getTaskHistory(taskId)
                        }
                        val history by historyFlow.collectAsState()

                        TaskDetailsScreen(
                            history = history,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("day_details/{day}") { backStackEntry ->
                        val dayDetailsContext = LocalContext.current
                        val day = backStackEntry.arguments?.getString("day")!!.toLong()

                        val aiTasksAll by viewModel.aiDailyTasks.collectAsState()
                        val plannedWeekStart by viewModel.plannedWeekStartMillis.collectAsState()
                        val weekPlanned by viewModel.weekPlanned.collectAsState()
                        val allTasks by viewModel.allTasks.collectAsState()
                        val todayStepsDayDetails by viewModel.todaySteps.collectAsState()

                        val stepGoalsMap = remember(allTasks) {
                            allTasks
                                .filter { it.taskType == "STEPS" && (it.stepGoal ?: 0) > 0 }
                                .associate { it.id to it.stepGoal!! }
                        }

                        val allDailyCount = allTasks.count { !it.isMandatory }

                        fun getDayName(timestamp: Long): String {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = timestamp
                            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                                Calendar.MONDAY -> "MONDAY"
                                Calendar.TUESDAY -> "TUESDAY"
                                Calendar.WEDNESDAY -> "WEDNESDAY"
                                Calendar.THURSDAY -> "THURSDAY"
                                Calendar.FRIDAY -> "FRIDAY"
                                Calendar.SATURDAY -> "SATURDAY"
                                Calendar.SUNDAY -> "SUNDAY"
                                else -> "MONDAY"
                            }
                        }

                        DayDetailsPager(
                            initialDay = day,
                            historyProvider = { pageDay ->
                                val flow = remember(pageDay) { viewModel.getHistoryForDay(pageDay) }
                                flow.collectAsState().value
                            },
                            aiTasksProvider = { pageDay ->
                                val dayName = getDayName(pageDay)
                                val plannedWeekEnd = plannedWeekStart?.let { it + 7L * 24 * 60 * 60 * 1000 }
                                if (plannedWeekStart != null &&
                                    pageDay >= plannedWeekStart!! &&
                                    pageDay < plannedWeekEnd!!
                                ) {
                                    aiTasksAll[dayName] ?: emptyList()
                                } else {
                                    emptyList()
                                }
                            },
                            mandatoryTasksProvider = { pageDay ->
                                val scheduleFlow = remember(pageDay) { viewModel.getMandatoryScheduleForDay(pageDay) }
                                val scheduleForDay by scheduleFlow.collectAsState(initial = emptyList())
                                remember(scheduleForDay, allTasks) {
                                    val taskMap = allTasks.associateBy { it.id }
                                    scheduleForDay
                                        .mapNotNull { taskMap[it.taskId] }
                                        .distinctBy { it.id }
                                }
                            },
                            stepGoalsMap = stepGoalsMap,
                            todaySteps = todayStepsDayDetails,
                            allDailyTasksCount = allDailyCount,
                            weekPlanned = weekPlanned,
                            onBack = { navController.popBackStack() },
                            onAiTaskDone = { historyEntry ->
                                viewModel.insertHistory(historyEntry)
                                viewModel.incrementWeeklyTaskByName(historyEntry.aiMainName ?: "")
                            },
                            onResumeRun = { entry, remainingKm ->
                                navController.navigate(
                                    "running_screen?taskId=${entry.taskId}" +
                                    "&taskName=${Uri.encode(entry.taskName)}" +
                                    "&targetKm=$remainingKm" +
                                    "&durationMinutes=0" +
                                    "&aiMainName=${Uri.encode(entry.aiMainName ?: "")}"
                                )
                            },
                            onMandatoryTaskDone = { task, pageDay ->
                                val saveDate = pageDay + 12 * 3600 * 1000L
                                viewModel.insertHistory(
                                    TaskHistoryEntity(
                                        taskId = task.id,
                                        taskName = task.taskName,
                                        category = task.category,
                                        date = saveDate,
                                        completed = true,
                                        isAIGenerated = false,
                                        aiMainName = null
                                    )
                                )
                            },
                            onMandatoryRunStart = { task ->
                                navController.navigate(
                                    "running_screen?taskId=${task.id}" +
                                    "&taskName=${Uri.encode(task.taskName)}" +
                                    "&targetKm=${task.targetKm}" +
                                    "&durationMinutes=${task.durationMinutes}" +
                                    "&aiMainName="
                                )
                            },
                            onStartMandatoryTask = { task ->
                                viewModel.startTask(task, dayDetailsContext)
                                navController.navigate("main_screen") {
                                    popUpTo("main_screen") { inclusive = false }
                                }
                            },
                            onStartAiTask = { aiTask ->
                                val tempTask = TaskEntity(
                                    id = -999,
                                    category = aiTask.mainName,
                                    taskName = aiTask.description,
                                    durationMinutes = aiTask.durationMinutes,
                                    isMandatory = false,
                                    isAIGenerated = true,
                                    isCompleted = false,
                                    taskType = "TIMER"
                                )
                                viewModel.startTask(tempTask, dayDetailsContext)
                                navController.navigate("main_screen") {
                                    popUpTo("main_screen") { inclusive = false }
                                }
                            }
                        )
                    }

                    composable("settings_screen") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onLogout = {
                                viewModel.viewModelScope.launch {
                                    // 1. Wyczyść lokalną bazę Room (wszystkie tabele)
                                    withContext(Dispatchers.IO) {
                                        db.clearAllTables()
                                    }
                                    // 2. Wyczyść SharedPreferences z danymi użytkownika
                                    listOf("settings", "holidays", "ai_plan",
                                        "weekly_topics", "weekly_flow", "weekly_draft"
                                    ).forEach { name ->
                                        context.getSharedPreferences(name, Context.MODE_PRIVATE)
                                            .edit().clear().apply()
                                    }
                                    // 3. Reset stanu in-memory ViewModel
                                    viewModel.resetForLogout()
                                    // 4. Wyloguj z Firebase
                                    AuthManager.auth.signOut()
                                }
                            },
                            onTriggerWeeklyFlow = {
                                navController.navigate("weekly_review") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable("weekly_review") {
                        val context = LocalContext.current
                        val summary by viewModel.weeklyReviewSummary.collectAsState()
                        val weekQuality by viewModel.weekQuality.collectAsState()

                        var dayStats by remember { mutableStateOf<List<com.MateuszW.aigrowthtracker.data.DayStats>>(emptyList()) }
                        var aiTasksForReview by remember { mutableStateOf<List<com.MateuszW.aigrowthtracker.data.TaskHistoryEntity>>(emptyList()) }

                        LaunchedEffect(Unit) {
                            // Najpierw persystuj niewykonane zadania z planu
                            viewModel.persistUnfinishedPlanItems(context)
                            // Potem generuj podsumowanie i laduj dane
                            if (summary.isNullOrBlank()) viewModel.generateWeeklyReviewSummary(context)
                            dayStats = viewModel.getWeeklyDayStats()
                            aiTasksForReview = viewModel.getAiTasksForReview()
                        }

                        WeeklySummaryScreen(
                            summaryText = summary,
                            weekQuality = weekQuality,
                            dayStats = dayStats,
                            aiTasksForReview = aiTasksForReview,
                            onDone = { feedback ->
                                viewModel.saveWeeklyReviewBundle(summary.orEmpty(), feedback)
                                navController.navigate("weekly_preview")
                            }
                        )
                    }

                    composable("weekly_preview") {
                        WeeklyPreviewScreen(
                            tasks = viewModel.allTasks.collectAsState(initial = emptyList()).value,
                            previouslySelected = viewModel.loadSelectedWeeklyTopics(context),
                            onDone = { selected ->
                                viewModel.saveSelectedWeeklyTopics(context, selected)
                                viewModel.saveWeeklyFlowStage("TOPICS", selectedTasks = selected)
                                navController.navigate("weekly_continuation")
                            }
                        )
                    }

                    composable("weekly_continuation") {
                        val isPlanning by viewModel.isPlanningWeek.collectAsState()
                        WeeklyContinuationScreen(
                            selectedTasks = viewModel.loadSelectedWeeklyTopics(context).toList(),
                            previousDifficulty = viewModel.loadDraftDifficulty(context),
                            previousGoalDrafts = viewModel.loadGoalDrafts(context),
                            isPlanning = isPlanning,
                            onFinish = { difficulty, drafts ->
                                viewModel.saveDraftDifficulty(context, difficulty)
                                viewModel.saveGoalDrafts(context, drafts)
                                viewModel.markWeeklyFlowCompleted(context)
                                navController.popBackStack("main_screen", false)
                            },
                            onPlanWithAI = { difficulty, drafts ->
                                viewModel.saveDraftDifficulty(context, difficulty)
                                viewModel.saveGoalDrafts(context, drafts)
                                val hiddenHolidayCount = viewModel.countHiddenHolidayDaysForNextWeek()
                                if (hiddenHolidayCount > 0) {
                                    viewModel.saveWeeklyFlowStage("HOLIDAY", hiddenHolidayCount = hiddenHolidayCount)
                                    navController.navigate("holiday_adjustment")
                                } else {
                                    viewModel.markWeeklyFlowCompleted(context)
                                    viewModel.planWeek(context, difficulty, drafts)
                                    navController.popBackStack("main_screen", false)
                                }
                            }
                        )
                    }

                    composable("holiday_adjustment") {
                        HolidayAdjustmentScreen(
                            holidaysCount = viewModel.countHiddenHolidayDaysForNextWeek(),
                            previousDifficulty = viewModel.loadDraftDifficulty(context),
                            onConfirm = { newDifficulty ->
                                viewModel.saveDraftDifficulty(context, newDifficulty)
                                viewModel.markWeeklyFlowCompleted(context)
                                viewModel.planWeek(
                                    context,
                                    newDifficulty,
                                    viewModel.loadGoalDrafts(context)
                                )
                                navController.popBackStack("main_screen", false)
                            },
                            onSkip = {
                                viewModel.markWeeklyFlowCompleted(context)
                                viewModel.planWeek(
                                    context,
                                    viewModel.loadDraftDifficulty(context),
                                    viewModel.loadGoalDrafts(context)
                                )
                                navController.popBackStack("main_screen", false)
                            }
                        )
                    }

                    // ── BIEGANIE ─────────────────────────────────────────

                    composable(
                        route = "running_screen?taskId={taskId}&taskName={taskName}&targetKm={targetKm}&durationMinutes={durationMinutes}&aiMainName={aiMainName}",
                        arguments = listOf(
                            navArgument("taskId") { type = NavType.IntType; defaultValue = -999 },
                            navArgument("taskName") { type = NavType.StringType; defaultValue = "Bieganie" },
                            navArgument("targetKm") { type = NavType.FloatType; defaultValue = 0f },
                            navArgument("durationMinutes") { type = NavType.IntType; defaultValue = 0 },
                            navArgument("aiMainName") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val taskId = backStackEntry.arguments?.getInt("taskId") ?: -999
                        val taskName = backStackEntry.arguments?.getString("taskName") ?: "Bieganie"
                        val targetKm = backStackEntry.arguments?.getFloat("targetKm") ?: 0f
                        val durationMinutes = backStackEntry.arguments?.getInt("durationMinutes") ?: 0
                        val aiMainName = backStackEntry.arguments?.getString("aiMainName")

                        RunningScreen(
                            viewModel = runViewModel,
                            taskId = taskId,
                            taskName = taskName,
                            targetKm = targetKm,
                            durationMinutes = durationMinutes,
                            aiMainName = aiMainName,
                            onRunCompleted = { completedAiMainName ->
                                if (!completedAiMainName.isNullOrBlank()) {
                                    viewModel.incrementWeeklyTaskByName(completedAiMainName)
                                }
                                navController.popBackStack()
                            },
                            onRunDeferred = { deferredAiMainName, distKm, tgtKm, daysOffset ->
                                viewModel.addDeferredRunToCalendar(daysOffset, taskName, deferredAiMainName, distKm, tgtKm)
                                navController.navigate("main_screen") {
                                    popUpTo("main_screen") { inclusive = false }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("run_history_screen") {
                        RunHistoryScreen(
                            viewModel = runViewModel,
                            onSessionClick = { sessionId ->
                                navController.navigate("run_detail_screen/$sessionId")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "run_detail_screen/{sessionId}",
                        arguments = listOf(
                            navArgument("sessionId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getInt("sessionId") ?: 0
                        RunDetailScreen(
                            viewModel = runViewModel,
                            sessionId = sessionId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}


