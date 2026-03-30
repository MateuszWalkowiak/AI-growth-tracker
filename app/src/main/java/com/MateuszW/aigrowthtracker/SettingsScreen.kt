package com.MateuszW.aigrowthtracker

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {

    val categories by viewModel.existingCategories.collectAsState(initial = emptyList())

    val context = LocalContext.current

    var expanded by remember { mutableStateOf(false) }

    val tasks by viewModel.allTasks.collectAsState(initial = emptyList())

    var apiKey by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("holiday_api_key", "") ?: ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // wl/wyl weekendy
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uwzględniaj weekendy", modifier = Modifier.weight(1f))
                Switch(
                    checked = viewModel.showWeekends.value,
                    onCheckedChange = {
                        viewModel.toggleWeekends(context, it)
                    }
                )
            }

            // wl/wyl swieta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uwzględniaj święta", modifier = Modifier.weight(1f))
                Switch(
                    checked = viewModel.showHolidays.value,
                    onCheckedChange = {
                        viewModel.toggleHolidays(context, it)
                    }
                )
            }

            // miejsce do wpisywania API KEY calendarific
            if (viewModel.showHolidays.value) {

                var statusMessage by remember { mutableStateOf("") }
                var isError by remember { mutableStateOf(false) }
                var isLoading by remember { mutableStateOf(false) }

                Column {

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Wpisz swój darmowy klucz API Calendarific") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    if (apiKey.isBlank()) {
                                        statusMessage = "Wpisz API key"
                                        isError = true
                                        return@IconButton
                                    }
                                    isLoading = true
                                    statusMessage = ""

                                    viewModel.onApiKeyConfirmed(context, apiKey)

                                    isLoading = false
                                    statusMessage = "Dane świąt pobrane"
                                    isError = false
                                }) {
                                    Text("OK")
                                }
                            }
                        }
                    )
                    if (statusMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage,
                            color = if (isError) Color.Red else Color(0xFF4CAF50)
                        )
                    }
                }
            }
            //wyloguj sie
            Button(
                onClick = {
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wyloguj się", color = Color.White)
            }

            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = LocalIndication.current,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        expanded = !expanded
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Modyfikuj zadania",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (expanded) {

                LazyColumn {
                    items(tasks) { task ->

                        var showEditDialog by remember { mutableStateOf(false) }
                        var showTimeDialog by remember { mutableStateOf(false) }
                        var showDeleteDialog by remember { mutableStateOf(false) }

                        var newName by remember { mutableStateOf(task.taskName) }
                        var newCategory by remember { mutableStateOf(task.category) }
                        var newTime by remember { mutableStateOf(task.durationMinutes.toString()) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = task.taskName,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edytuj")
                            }


                            IconButton(onClick = { showTimeDialog = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Czas")
                            }

                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Usuń")
                            }
                        }

                        var expandedCategories by remember { mutableStateOf(false) }

                        if (showEditDialog) {
                            AlertDialog(
                                onDismissRequest = { showEditDialog = false },
                                confirmButton = {
                                    Button(onClick = {
                                        viewModel.updateTask(
                                            task.copy(
                                                taskName = newName,
                                                category = newCategory
                                            )
                                        )
                                        showEditDialog = false
                                    }) {
                                        Text("Zapisz")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { showEditDialog = false }) {
                                        Text("Anuluj")
                                    }
                                },
                                title = { Text("Edytuj zadanie") },
                                text = {
                                    Column {

                                        //zmiana kategorii zadania
                                        ExposedDropdownMenuBox(
                                            expanded = expandedCategories,
                                            onExpandedChange = { expandedCategories = !expandedCategories }
                                        ) {

                                            OutlinedTextField(
                                                value = newCategory,
                                                onValueChange = { newCategory = it },
                                                label = { Text("Kategoria") },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor()
                                            )

                                            ExposedDropdownMenu(
                                                expanded = expandedCategories,
                                                onDismissRequest = { expandedCategories = false }
                                            ) {
                                                categories.forEach { category ->
                                                    DropdownMenuItem(
                                                        text = { Text(category) },
                                                        onClick = {
                                                            newCategory = category
                                                            expandedCategories = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        //zmiany nazwy zadania
                                        OutlinedTextField(
                                            value = newName,
                                            onValueChange = { newName = it },
                                            label = { Text("Nazwa") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        }

                        if (showTimeDialog) {
                            AlertDialog(
                                onDismissRequest = { showTimeDialog = false },
                                confirmButton = {
                                    Button(onClick = {
                                        val time = newTime.toIntOrNull()
                                        if (time != null) {
                                            viewModel.updateTask(
                                                task.copy(durationMinutes = time)
                                            )
                                        }
                                        showTimeDialog = false
                                    }) {
                                        Text("Zapisz")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { showTimeDialog = false }) {
                                        Text("Anuluj")
                                    }
                                },
                                title = { Text("Zmień czas (minuty)") },
                                text = {
                                    OutlinedTextField(
                                        value = newTime,
                                        onValueChange = { newTime = it },
                                        label = { Text("Minuty") }
                                    )
                                }
                            )
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                confirmButton = {
                                    Button(onClick = {
                                        viewModel.deleteTask(task)
                                        showDeleteDialog = false
                                    }) {
                                        Text("Usuń")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { showDeleteDialog = false }) {
                                        Text("Anuluj")
                                    }
                                },
                                title = { Text("Usuń zadanie") },
                                text = { Text("Na pewno usunąć '${task.taskName}'?") }
                            )
                        }
                    }
                }
            }
        }
    }
}