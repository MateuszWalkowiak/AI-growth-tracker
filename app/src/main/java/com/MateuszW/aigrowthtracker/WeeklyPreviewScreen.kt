package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.TaskEntity

val SYSTEM_WEEKLY_TASKS = listOf(
    "Bieganie", "Szachy", "Joga", "Programowanie", "Fitness",
    "Czytanie", "Medytacja", "Pływanie", "Rysowanie", "Nauka języka",
    "Siłownia", "Rower", "Gotowanie", "Pisanie", "Muzyka", "Stretching"
)

private data class CustomTaskItem(
    val checked: Boolean = false,
    val text: String = "",
    val isError: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklyPreviewScreen(
    tasks: List<TaskEntity>,
    previouslySelected: Set<String>,
    onDone: (Set<String>) -> Unit
) {
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(previouslySelected) }
    }
    val customItems = remember { mutableStateListOf(CustomTaskItem()) }

    fun toggle(name: String) {
        if (selected.contains(name)) selected.remove(name) else selected.add(name)
    }

    fun validateAndSubmit() {
        // Walidacja: zaznaczone własne muszą mieć tekst
        var hasError = false
        customItems.forEachIndexed { index, item ->
            if (item.checked && item.text.isBlank()) {
                customItems[index] = item.copy(isError = true)
                hasError = true
            }
        }
        if (hasError) return

        val all = selected.toMutableSet()
        customItems.filter { it.checked && it.text.isNotBlank() }
            .forEach { all.add(it.text.trim()) }
        onDone(all)
    }

    val dailyPool = tasks.filter { !it.isMandatory }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wybór tematów tygodnia") }) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { validateAndSubmit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Dalej")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            //  ── Avatar ──
            item {
                AssistantSpeechBubble(
                    painter = painterResource(R.drawable.assistant_preview),
                    speechText = "Na czym chcesz się skupić w tym tygodniu?"
                )
            }

            //── Twoje zadnaia z puli ──
            if (dailyPool.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "Z Twoich zadań",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dailyPool.forEach { task ->
                                FilterChip(
                                    selected = selected.contains(task.taskName),
                                    onClick = { toggle(task.taskName) },
                                    label = { Text(task.taskName) }
                                )
                            }
                        }
                    }
                }
            }

            //── Popularne aktywności (systemowe, hardkodowane) ──
            item {
                Column {
                    Text(
                        "Popularne aktywności",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SYSTEM_WEEKLY_TASKS.forEach { task ->
                            FilterChip(
                                selected = selected.contains(task),
                                onClick = { toggle(task) },
                                label = { Text(task) }
                            )
                        }
                    }
                }
            }

            // ── Własne aktywności ──
            item {
                Text(
                    "Własne",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }

            itemsIndexed(customItems) { index, item ->
                Row(
                    verticalAlignment = if (item.checked) Alignment.Top else Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { isChecked ->
                            customItems[index] = item.copy(checked = isChecked, isError = false)
                            //Dodaj nowy pusty slot gdy ostatni jest zaznaczony
                            if (isChecked && index == customItems.lastIndex) {
                                customItems.add(CustomTaskItem())
                            }
                        },
                        modifier = Modifier.padding(top = if (item.checked) 4.dp else 0.dp)
                    )
                    Spacer(Modifier.width(8.dp))

                    if (item.checked) {
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { newText ->
                                customItems[index] = item.copy(text = newText, isError = false)
                            },
                            label = { Text("Nazwa aktywności") },
                            isError = item.isError,
                            supportingText = if (item.isError) {
                                { Text("Pole nie może być puste") }
                            } else null,
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    } else {
                        Text(
                            "Dodaj własną aktywność",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
