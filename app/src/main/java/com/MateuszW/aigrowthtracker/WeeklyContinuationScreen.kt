package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.WeeklyGoalDraft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklyContinuationScreen(
    selectedTasks: List<String>,
    previousDifficulty: Int,
    previousGoalDrafts: Map<String, WeeklyGoalDraft> = emptyMap(),
    isPlanning: Boolean = false,
    onFinish: (Int, Map<String, WeeklyGoalDraft>) -> Unit,
    onPlanWithAI: (Int, Map<String, WeeklyGoalDraft>) -> Unit
) {
    var difficulty by remember { mutableStateOf(previousDifficulty) }

    val drafts = remember {
        mutableStateMapOf<String, WeeklyGoalDraft>().apply {
            selectedTasks.forEach { task ->
                val prev = previousGoalDrafts[task]
                put(task, WeeklyGoalDraft(goal = prev?.goal.orEmpty()))
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Poziom trudności i cele") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxSize()
        ) {
            AssistantSpeechBubble(
                painter = painterResource(R.drawable.assistant_continuation),
                speechText = "Świetny wybór! Powiedz mi jakie są Twoje cele w tych dziedzinach — im konkretniej, tym lepiej przygotuję Twój plan."
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Wybierz poziom trudności na następny tydzień",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))

            //  Gradient difficulty selector - 2 rzedy po 5, pelna szerokosc
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1..5, 6..10).forEach { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        range.forEach { level ->
                            val color = difficultyColor(level)
                            val isSelected = difficulty == level
                            Button(
                                onClick = { difficulty = level },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) color else color.copy(alpha = 0.18f),
                                    contentColor = if (isSelected) Color.White else color
                                ),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("$level", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (selectedTasks.isEmpty()) {
                Text(
                    "Nie wybrano żadnych aktywności. Wróć i zaznacz coś.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(selectedTasks) { task ->
                    val draft = drafts[task] ?: WeeklyGoalDraft()
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(task, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = draft.goal,
                                onValueChange = { drafts[task] = draft.copy(goal = it) },
                                label = { Text("Twoja wizja (big picture)") },
                                placeholder = { Text("Co chcesz osiągnąć długoterminowo?") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            //Przycisk AI - planuje tydzień i wstawia do kalendarza
            Button(
                onClick = { onPlanWithAI(difficulty, drafts.toMap()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedTasks.isNotEmpty() && !isPlanning
            ) {
                if (isPlanning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("Gemini planuje tydzień…")
                    }
                } else {
                    Text("Pozwól asystentowi zaplanować tydzień")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Przycisk bez AI - tylko zapisuje i wraca
            OutlinedButton(
                onClick = { onFinish(difficulty, drafts.toMap()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPlanning
            ) {
                Text("Zakończ")
            }
        }
    }
}
