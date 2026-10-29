package com.MateuszW.aigrowthtracker

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import com.MateuszW.aigrowthtracker.ui.theme.ThemeMode
import com.MateuszW.aigrowthtracker.ui.theme.ThemeState
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onTriggerWeeklyFlow: () -> Unit = {}
) {
    val categories by viewModel.existingCategories.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val tasks by viewModel.allTasks.collectAsState(initial = emptyList())

    var apiKey by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("holiday_api_key", "") ?: ""
        )
    }
    var geminiKey by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "") ?: ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrot")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            //  ── Sekcja: Wygląd ──────────────────────────────────────────
            SettingsSectionHeader("Wyglad")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Motyw",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    val currentTheme = ThemeState.themeMode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.LIGHT -> "Jasny"
                                ThemeMode.DARK -> "Ciemny"
                                ThemeMode.SYSTEM -> "Systemowy"
                            }
                            FilterChip(
                                selected = currentTheme == mode,
                                onClick = { viewModel.setThemeMode(context, mode) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            //  ── Sekcja: Planowanie ───────────────────────────────────────
            SettingsSectionHeader("Planowanie")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsToggleRow(
                        title = "Uwzgledniaj weekendy",
                        subtitle = "Planuj zadania rowniez w soboty i niedziele",
                        checked = viewModel.showWeekends.value,
                        onCheckedChange = { viewModel.toggleWeekends(context, it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsToggleRow(
                        title = "Uwzgledniaj swieta",
                        subtitle = "Planuj zadania w dni swiateczne",
                        checked = viewModel.showHolidays.value,
                        onCheckedChange = { viewModel.toggleHolidays(context, it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsToggleRow(
                        title = "Dodaj plan do kalendarza",
                        subtitle = "Zadania pojawia sie w kalendarzu systemowym",
                        checked = viewModel.insertToSystemCalendar.value,
                        onCheckedChange = { viewModel.toggleSystemCalendar(context, it) }
                    )

                    if (viewModel.insertToSystemCalendar.value) {
                        val currentHour = viewModel.calendarEventHour.value
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Godzina dodawania wydarzen",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Zadania beda widoczne o %d:00".format(currentHour),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (currentHour > 0)
                                            viewModel.setCalendarEventHour(context, currentHour - 1)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "%02d:00".format(currentHour),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (currentHour < 23)
                                            viewModel.setCalendarEventHour(context, currentHour + 1)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ── Sekcja: Krokomierz ──────────────────────────────────────
            SettingsSectionHeader("Krokomierz")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                val stepsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                var askEveryTime by remember {
                    mutableStateOf(!stepsPrefs.getBoolean("steps_counting_dont_ask_again", false))
                }
                var defaultMode by remember {
                    mutableStateOf(stepsPrefs.getString("steps_counting_mode", "FROM_MIDNIGHT") ?: "FROM_MIDNIGHT")
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsToggleRow(
                        title = "Pytaj o tryb przy kazdym zadaniu",
                        subtitle = "Wybor trybu liczenia krokow",
                        checked = askEveryTime,
                        onCheckedChange = { newVal ->
                            askEveryTime = newVal
                            viewModel.saveStepsCountingPrefs(context, defaultMode, !newVal)
                        }
                    )

                    if (!askEveryTime) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Text(
                            "Domyslny tryb:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = defaultMode == "FROM_MIDNIGHT",
                                    onClick = {
                                        defaultMode = "FROM_MIDNIGHT"
                                        viewModel.saveStepsCountingPrefs(context, "FROM_MIDNIGHT", true)
                                    }
                                )
                                Text("Od polnocy")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = defaultMode == "FROM_START",
                                    onClick = {
                                        defaultMode = "FROM_START"
                                        viewModel.saveStepsCountingPrefs(context, "FROM_START", true)
                                    }
                                )
                                Text("Od zera")
                            }
                        }
                    }
                }
            }

            // ── Sekcja: Klucze API ──────────────────────────────────────
            SettingsSectionHeader("Klucze API")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("API KEY Gemini") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            FilledTonalButton(
                                onClick = {
                                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                        .edit { putString("gemini_api_key", geminiKey) }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Zapisz")
                            }
                        }
                    )

                    if (viewModel.showHolidays.value) {
                        var statusMessage by remember { mutableStateOf("") }
                        var isError by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API KEY Calendarific (swieta)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                FilledTonalButton(
                                    onClick = {
                                        if (apiKey.isBlank()) {
                                            statusMessage = "Wpisz API key"
                                            isError = true
                                            return@FilledTonalButton
                                        }
                                        viewModel.onApiKeyConfirmed(context, apiKey)
                                        statusMessage = "Dane swiat pobrane"
                                        isError = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text("Zapisz")
                                }
                            }
                        )
                        if (statusMessage.isNotEmpty()) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            //── Sekcja: Zadania ─────────────────────────────────────────
            SettingsSectionHeader("Zadania")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = LocalIndication.current,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { expanded = !expanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Modyfikuj zadania",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (expanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        tasks.forEach { task ->
                            TaskSettingsRow(task, categories, viewModel)
                        }
                    }
                }
            }

            // ── Sekcja: Narzędzia deweloperskie ────────────────────────
            var showDevTools by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = LocalIndication.current,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showDevTools = !showDevTools }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Narzedzia deweloperskie",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showDevTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showDevTools) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsToggleRow(
                                title = "Tryb testowy (mock AI)",
                                subtitle = "Gemini zastapiony gotowymi danymi",
                                checked = viewModel.isTestMode.value,
                                onCheckedChange = { viewModel.isTestMode.value = it }
                            )

                            Button(
                                onClick = {
                                    viewModel.resetWeeklyFlowForTesting(context)
                                    onTriggerWeeklyFlow()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text("Wywolaj cotygodniowy flow")
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearFutureCalendarEvents(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Wyczysc kalendarz w przod")
                            }

                        }
                    }
                }
            }

            // ── Wyloguj ─────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Wyloguj sie", color = MaterialTheme.colorScheme.onError)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

//─────────────────────────────────────────────────────────────────────────────
// Pomocnicze komponenty
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSettingsRow(
    task: com.MateuszW.aigrowthtracker.data.TaskEntity,
    categories: List<String>,
    viewModel: TaskViewModel
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(task.taskName) }
    var newCategory by remember { mutableStateOf(task.category) }
    var newTime by remember { mutableStateOf(task.durationMinutes.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.taskName, style = MaterialTheme.typography.bodyMedium)
            Text(
                task.category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { showEditDialog = true }) {
            Icon(Icons.Default.Edit, contentDescription = "Edytuj", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { showTimeDialog = true }) {
            Icon(Icons.Default.Schedule, contentDescription = "Czas", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                Icons.Default.Delete, contentDescription = "Usun",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    var expandedCategories by remember { mutableStateOf(false) }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateTask(task.copy(taskName = newName, category = newCategory))
                    showEditDialog = false
                }) { Text("Zapisz") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditDialog = false }) { Text("Anuluj") }
            },
            title = { Text("Edytuj zadanie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                    if (time != null) viewModel.updateTask(task.copy(durationMinutes = time))
                    showTimeDialog = false
                }) { Text("Zapisz") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimeDialog = false }) { Text("Anuluj") }
            },
            title = { Text("Zmien czas (minuty)") },
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
                Button(
                    onClick = {
                        viewModel.deleteTask(task)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Usun") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
            },
            title = { Text("Usun zadanie") },
            text = { Text("Na pewno usunac '${task.taskName}'?") }
        )
    }
}
