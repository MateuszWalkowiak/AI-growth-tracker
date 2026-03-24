package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.TaskSummary
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    summaries: List<TaskSummary>,
    history: List<TaskHistoryEntity>,
    onTaskClick: (Int) -> Unit,
    onBack: () -> Unit
){

    val avgRating = history.mapNotNull { it.rating }.average().let {
        if (it.isNaN()) 0.0 else it
    }

    val totalTasks = history.size

    val bestTasks = history
        .filter { it.rating != null }
        .groupBy { it.taskName }
        .mapValues { entry ->
            entry.value.mapNotNull { it.rating }.average()
        }
        .toList()
        .sortedByDescending { it.second }
        .take(3)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statystyki") }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            //podsumowanie
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Podsumowanie", style = MaterialTheme.typography.titleMedium)

                    Text("Wykonane zadania: $totalTasks")
                    Text("Średnia ocena: %.2f ⭐".format(avgRating))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            //najlepsze zadania
            if (bestTasks.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Najlepsze zadania", style = MaterialTheme.typography.titleMedium)

                        bestTasks.forEach {
                            Text("${it.first} - %.2f ⭐".format(it.second))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Historia", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                items(summaries) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        onClick = { onTaskClick(task.taskId) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Text(task.taskName, fontWeight = FontWeight.Bold)

                            Text("Wykonania: ${task.totalCount}")

                            Text(
                                "Średnia ocena: ${
                                    task.avgRating?.let { "%.1f ⭐".format(it) } ?: "Brak"
                                }"
                            )
                        }
                    }
                }
            }
        }
    }
}