package com.MateuszW.aigrowthtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.MateuszW.aigrowthtracker.data.AuthManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var user by remember { mutableStateOf(AuthManager.auth.currentUser) }

                LaunchedEffect(Unit) {
                    AuthManager.auth.addAuthStateListener { firebaseAuth ->
                        user = firebaseAuth.currentUser
                    }
                }

                if (user == null) {
                    LoginScreen()
                    return@MaterialTheme
                }
                val context = LocalContext.current
                val db = remember { AppDatabase.getDatabase(context) }
                LaunchedEffect(Unit) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val existing = db.taskHistoryDao().getAllHistoryOnce()

                        if (existing.isEmpty()) {
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
                    }
                }

                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return TaskViewModel(
                            taskDao = db.taskDao(),
                            taskHistoryDao = db.taskHistoryDao()
                        ) as T
                    }
                }
                val viewModel: TaskViewModel = viewModel(factory = factory)

                val navController = rememberNavController()

                val accountCreationDateState = remember { mutableStateOf<Long?>(null) }

                LaunchedEffect(Unit) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val date = db.taskHistoryDao().getRealFirstDate()
                        accountCreationDateState.value = date
                    }
                }

                NavHost(navController = navController, startDestination = "main_screen") {

                    composable("main_screen") {
                        val context = LocalContext.current
                        LaunchedEffect(Unit) {
                            viewModel.loadSettingsSmart(context)
                        }

                        val tasks by viewModel.allTasks.collectAsState(initial = emptyList())
                        val activeTask by viewModel.activeTask.collectAsState()
                        val remainingTime by viewModel.remainingTime.collectAsState()
                        val completedDates by viewModel.completedDates.collectAsState()
                        val holidays by viewModel.holidays
                        val showHolidays by viewModel.showHolidays
                        val showWeekends by viewModel.showWeekends

                        MainScreen(
                            viewModel = viewModel,
                            tasks = tasks,
                            activeTask = activeTask,
                            remainingTime = remainingTime,
                            dailyGoal = 5,
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
                            onSettingsClick = { navController.navigate("settings_screen") }
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
                        val tasks by viewModel.allTasks.collectAsState(initial = emptyList())
                        val activeTask by viewModel.activeTask.collectAsState()
                        val remainingTime by viewModel.remainingTime.collectAsState()

                        ExecuteTaskScreen(
                            tasks = tasks,
                            activeTask = activeTask,
                            remainingTime = remainingTime,
                            onStartTask = { task, context ->
                                viewModel.startTask(task, context)
                            },
                            onFinishTaskWithSummary = { rating, note ->
                                viewModel.finishTaskWithSummary(rating, note)
                            },
                            onFinishTask = { viewModel.finishTask() },
                            onCancelTask = { viewModel.cancelTask() },
                            onBack = { navController.popBackStack() }
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

                        val day = backStackEntry.arguments?.getString("day")!!.toLong()

                        val historyFlow = remember(day) {
                            viewModel.getHistoryForDay(day)
                        }
                        val history by historyFlow.collectAsState()

                        DayDetailsScreen(
                            history = history,
                            dailyGoal = 5,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings_screen") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onLogout = {
                                AuthManager.auth.signOut()
                            }
                        )
                    }
                }
            }
        }
    }
}


