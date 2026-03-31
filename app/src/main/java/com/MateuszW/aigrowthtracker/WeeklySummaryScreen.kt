package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.WeeklyTaskEntity
import com.MateuszW.aigrowthtracker.data.WeeklyTaskType
import androidx.compose.ui.Alignment

@Composable
fun WeeklySummaryScreen(
    onDone: (List<WeeklyTaskEntity>) -> Unit
) {
    val selectedTasks = remember { mutableStateListOf<String>() }
    val customTasks = remember { mutableStateListOf("") }

    Column(Modifier.padding(16.dp)) {

        Text("Na czym chcesz się skupić?")

        listOf("Siłownia", "Bieganie", "Programowanie", "Czytanie").forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedTasks.contains(task),
                    onCheckedChange = {
                        if (it) selectedTasks.add(task)
                        else selectedTasks.remove(task)
                    }
                )
                Text(task)
            }
        }

        Text("Własne:")

        customTasks.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = {
                    customTasks[index] = it

                    if (index == customTasks.lastIndex && it.isNotBlank()) {
                        customTasks.add("")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {

                val all = selectedTasks + customTasks.filter { it.isNotBlank() }

                val weekly = all.mapIndexed { i, name ->
                    WeeklyTaskEntity(
                        id = i,
                        name = name,
                        type = WeeklyTaskType.COUNTER,
                        targetValue = 5f
                    )
                }

                onDone(weekly)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dalej")
        }
    }
}
