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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecuteTaskScreen(
    tasks: List<TaskEntity>,
    activeTask: TaskEntity?,
    remainingTime: Int,
    onStartTask: (TaskEntity, android.content.Context) -> Unit,
    onFinishTask: () -> Unit,
    onCancelTask: () -> Unit,
    onBack: () -> Unit,
    onFinishTaskWithSummary: (Int, String) -> Unit,
) {
    var selectedCategoryForRoll by remember { mutableStateOf("Wszystkie") }
    val categories = listOf("Wszystkie") + tasks.map { it.category }.distinct()
    var rolledTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showSummaryDialog by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
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
                            val filtered = if (selectedCategoryForRoll == "Wszystkie") {
                                tasks.filter { !it.isCompleted }//nie losuje wykonanych zadan
                            } else {
                                tasks.filter { it.category == selectedCategoryForRoll && !it.isCompleted }
                            }

                            if (filtered.isNotEmpty()) {
                                rolledTask = filtered.random()
                                showDialog = true
                            }
                        },
                        enabled = activeTask == null
                    ){
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

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(tasks) { task ->
                    ListItem(
                        headlineContent = { Text(task.taskName) },
                        supportingContent = { Text(task.category) },
                        trailingContent = {
                            Button(
                                onClick = { onStartTask(task, context) },
                                enabled = activeTask == null //blokowanie staru wielu zadan jednoczesnie
                            ) {
                                Text("Start")
                            }
                        }
                    )
                }
            }
            //timer czasu na zadnie
            activeTask?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Text("Aktywne zadanie: ${it.taskName}")

                        val minutes = remainingTime / 60
                        val seconds = remainingTime % 60

                        Text(
                            text = "Pozostało: %02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Row {
                            Button(onClick = { showSummaryDialog = true }) {
                                Text("Zrobione")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = onCancelTask) {
                                Text("Anuluj")
                            }
                        }
                    }
                }
            }
            if (showDialog && rolledTask != null) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Wylosowano zadanie") },
                    text = { Text("${rolledTask!!.taskName} (${rolledTask!!.category})") },
                    confirmButton = {
                        Button(onClick = {
                            onStartTask(rolledTask!!, context)
                            showDialog = false
                        }) {
                            Text("Rozpocznij")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showDialog = false }) {
                            Text("Anuluj")
                        }
                    }
                )
            }
            if (showSummaryDialog && activeTask != null) {
                AlertDialog(
                    onDismissRequest = { showSummaryDialog = false },
                    title = { Text("Podsumuj zadanie") },
                    text = {
                        Column {

                            Text("Ocena:")

                            Row {
                                (1..5).forEach { star ->
                                    IconButton(onClick = { rating = star }) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (star <= rating)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Notatka (opcjonalnie)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        val isAiTask = activeTask?.isAIGenerated == true

                        Button(
                            onClick = {
                                onFinishTaskWithSummary(rating, note)
                                showSummaryDialog = false
                                rating = 0
                                note = ""
                            },
                            //ocena wymagana dla każdego zadnia, feedback tylko da zadan generowanych przez ai
                            enabled = rating > 0 && (!isAiTask || note.isNotBlank())
                        ) {
                            Text("Zapisz")
                            if (isAiTask && note.isBlank()) {
                                Text(
                                    "Dodaj feedback dla AI",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showSummaryDialog = false }) {
                            Text("Anuluj")
                        }
                    }
                )
            }
        }
    }
}