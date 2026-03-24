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
                val context = LocalContext.current
                val db = remember { AppDatabase.getDatabase(context) }

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



                NavHost(navController = navController, startDestination = "main_screen") {

                    composable("main_screen") {
                        val tasks by viewModel.allTasks.collectAsState(initial = emptyList())
                        val activeTask by viewModel.activeTask.collectAsState()
                        val remainingTime by viewModel.remainingTime.collectAsState()

                        MainScreen(
                            tasks = tasks,
                            activeTask = activeTask,
                            remainingTime = remainingTime,
                            dailyGoal = 5,
                            onAddTaskClick = { navController.navigate("add_task_screen") },
                            onExecuteTaskClick = { navController.navigate("execute_task_screen") },
                            onStatsClick = { navController.navigate("stats_screen") }
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
                        val history by viewModel.getTaskHistory(taskId).collectAsState()

                        TaskDetailsScreen(
                            history = history,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}


