package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.TaskEntity
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecuteTaskScreen(
    tasks: List<TaskEntity>,
    onTaskSelected: (TaskEntity) -> Unit,
    onBack: () -> Unit
) {
    var selectedCategoryForRoll by remember { mutableStateOf("Wszystkie") }
    val categories = listOf("Wszystkie") + tasks.map { it.category }.distinct()
    var rolledTask by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wykonaj zadanie") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            //sekcja losowania
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Losuj zadanie", style = MaterialTheme.typography.titleMedium)

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategoryForRoll,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategoria losowania") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = { selectedCategoryForRoll = cat; expanded = false }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val filtered = if (selectedCategoryForRoll == "Wszystkie") tasks
                            else tasks.filter { it.category == selectedCategoryForRoll }
                            if (filtered.isNotEmpty()) {
                                rolledTask = filtered[Random.nextInt(filtered.size)]
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                    ) {
                        Text("LOSUJ")
                    }

                    rolledTask?.let {
                        Text("Wylosowano: ${it.taskName}",
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text("Lub wybierz z listy:", style = MaterialTheme.typography.titleSmall)

            LazyColumn {
                items(tasks) { task ->
                    ListItem(
                        headlineContent = { Text(task.taskName) },
                        supportingContent = { Text(task.category) },
                        trailingContent = {
                            Button(onClick = { onTaskSelected(task) }) { Text("Start") }
                        }
                    )
                }
            }
        }
    }
}