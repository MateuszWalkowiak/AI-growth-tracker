package com.MateuszW.aigrowthtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.MateuszW.aigrowthtracker.ui.theme.AIGrowthTrackerTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "main_screen") {

                //ekran główny
                composable("main_screen") {
                    MainScreen(
                        onAddTaskClick = {
                            navController.navigate("add_task_screen")
                        },
                        onExecuteTaskClick = {}
                    )
                }
                //dodawanie zadania
                composable("add_task_screen") {
                    AddTaskScreen(
                        onSaveClick = {
                            //zapisanie danych do bazy TO DO
                            navController.popBackStack()
                        },
                        onCancelClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AIGrowthTrackerTheme {
        Greeting("Android")
    }
}