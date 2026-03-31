package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateMapOf

@Composable
fun WeeklyContinuationScreen(
    selectedTasks: List<String>,
    previousDifficulty: Int?,
    onDone: (Int, Map<String, String>) -> Unit,
    onUseAI: () -> Unit
) {

    var difficulty by remember { mutableStateOf(previousDifficulty ?: 5) }
    var useAI by remember { mutableStateOf(false) }

    val goalsMap = remember {
        mutableStateMapOf<String, String>().apply {
            selectedTasks.forEach { put(it, "") }
        }
    }

    Column(Modifier.padding(16.dp)) {

        Text("Wybierz poziom trudności")

        Row {
            (1..10).forEach {
                Button(onClick = { difficulty = it }) {
                    Text("$it")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { useAI = true; onUseAI() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pozwól asystentowi zdecydować")
        }

        Spacer(Modifier.height(12.dp))

        selectedTasks.forEach { task ->
            OutlinedTextField(
                value = goalsMap[task] ?: "",
                onValueChange = { newValue -> goalsMap[task] = newValue },
                label = { Text(task) },
                enabled = !useAI,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onDone(difficulty, goalsMap) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zakończ")
        }
    }
}