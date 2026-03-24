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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val db = remember { AppDatabase.getDatabase(context) }

                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return TaskViewModel(db.taskDao()) as T
                    }
                }
                val viewModel: TaskViewModel = viewModel(factory = factory)

                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "main_screen") {

                    composable("main_screen") {
                        val tasks by viewModel.allTasks.collectAsState()

                        MainScreen(
                            onAddTaskClick = { navController.navigate("add_task_screen") },
                            onExecuteTaskClick = { }
                        )
                    }
                    composable("add_task_screen") {
                        AddTaskScreen(
                            onSaveClick = { newTask ->
                                viewModel.saveTask(newTask)
                                navController.popBackStack()
                            },
                            onCancelClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}


