package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsScreen(
    history: List<TaskHistoryEntity>,
    onBack: () -> Unit
) {

    val avgRating = history.mapNotNull { it.rating }.average().let {
        if (it.isNaN()) 0.0 else it
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Szczegóły zadania") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text("Podsumowanie", style = MaterialTheme.typography.titleMedium)

                    Text("Wykonań: ${history.size}")
                    Text("Średnia ocena: %.2f ⭐".format(avgRating))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Historia:", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                items(history) { item ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Text("Ocena: ${item.rating ?: "-"} ⭐")

                            item.note?.let {
                                Text("Notatka: $it")
                            }

                            Text(
                                text = Date(item.date).toString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}