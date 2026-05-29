package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.DayStats
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.WeeklyReviewInput

fun difficultyColor(level: Int): Color {
    val colors = listOf(
        Color(0xFF2E7D32), //ciemny zielony
        Color(0xFF43A047),
        Color(0xFF8BC34A),
        Color(0xFFCDDC39),
        Color(0xFFFFD600),
        Color(0xFFFFAB00),
        Color(0xFFFF6D00),
        Color(0xFFFF3D00),
        Color(0xFFD32F2F),
        Color(0xFF880808)  //ciemny czerwień
    )
    return colors.getOrElse(level - 1) { Color.Gray }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklySummaryScreen(
    summaryText: String?,
    weekQuality: String?,
    dayStats: List<DayStats>,
    aiTasksForReview: List<TaskHistoryEntity>,
    onDone: (List<WeeklyReviewInput>) -> Unit
) {
    val starsMap = remember { mutableStateMapOf<Int, Int>() }
    val difficultyMap = remember { mutableStateMapOf<Int, Int>() }
    val notesMap = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(aiTasksForReview) {
        aiTasksForReview.forEach { entry ->
            starsMap.putIfAbsent(entry.id, 5)
            difficultyMap.putIfAbsent(entry.id, 5)
            notesMap.putIfAbsent(entry.id, "")
        }
    }

    val totalPlanned = dayStats.sumOf { it.totalPlanned }
    val totalCompleted = dayStats.sumOf { it.completed }
    val totalUncompleted = dayStats.sumOf { it.uncompleted }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podsumowanie tygodnia") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // Grafika asystenta
            item {
                val painter = when (weekQuality) {
                    "bad"   -> painterResource(R.drawable.assistant_summary_bad)
                    "great" -> painterResource(R.drawable.assistant_summary_great)
                    else    -> painterResource(R.drawable.assistant_summary_neutral)
                }
                AssistantSpeechBubbleOrLoading(
                    painter = painter,
                    speechText = summaryText,
                    loadingLabel = "Asystent analizuje Twoj tydzien..."
                )
            }

            // ── Statystyki tygodnia ────────────────────────────────────
            if (dayStats.isNotEmpty()) {
                item {
                    Text(
                        "Statystyki tygodnia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            // Sumaryczne
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatChip("Zaplanowano", totalPlanned, MaterialTheme.colorScheme.primary)
                                StatChip("Wykonano", totalCompleted, Color(0xFF2E7D32))
                                StatChip("Niewykonane", totalUncompleted, MaterialTheme.colorScheme.error)
                            }

                            if (totalPlanned > 0) {
                                Spacer(Modifier.height(12.dp))
                                val overallProgress = totalCompleted.toFloat() / totalPlanned
                                LinearProgressIndicator(
                                    progress = { overallProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(10.dp),
                                    color = when {
                                        overallProgress >= 0.7f -> Color(0xFF2E7D32)
                                        overallProgress >= 0.4f -> Color(0xFFFFAB00)
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${(overallProgress * 100).toInt()}% zadan wykonanych",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Per-day breakdown
                items(dayStats, key = { it.dayMillis }) { day ->
                    val progress = if (day.totalPlanned > 0)
                        day.completed.toFloat() / day.totalPlanned else 0f

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    day.dayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${day.completed}/${day.totalPlanned}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = when {
                                    progress >= 0.7f -> Color(0xFF2E7D32)
                                    progress >= 0.4f -> Color(0xFFFFAB00)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Brak danych z historii planu dnia.\nZaplanuj tydzien z AI, aby zobaczyc tu statystyki.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Recenzja zadan AI ──────────────────────────────────────
            if (aiTasksForReview.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Recenzja zadan AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(aiTasksForReview, key = { it.id }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                entry.aiMainName ?: entry.taskName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (entry.completed) "Wykonane" else "Niewykonane",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (entry.completed)
                                    Color(0xFF2E7D32)
                                else
                                    MaterialTheme.colorScheme.error
                            )

                            Spacer(Modifier.height(12.dp))

                            // Ocena gwiazdkowa
                            Text("Ocena (1-5)", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                (1..5).forEach { star ->
                                    IconButton(onClick = { starsMap[entry.id] = star }) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if ((starsMap[entry.id] ?: 0) >= star)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Poziom trudnosci
                            Text("Poziom trudnosci (1-10)", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..10).forEach { d ->
                                    val isSelected = difficultyMap[entry.id] == d
                                    val color = difficultyColor(d)
                                    Button(
                                        onClick = { difficultyMap[entry.id] = d },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) color else color.copy(alpha = 0.18f),
                                            contentColor = if (isSelected) Color.White else color
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("$d", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = notesMap[entry.id].orEmpty(),
                                onValueChange = { notesMap[entry.id] = it },
                                label = { Text("Feedback slowny") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val result = aiTasksForReview.map { entry ->
                            WeeklyReviewInput(
                                historyId = entry.id,
                                stars = starsMap[entry.id] ?: 5,
                                difficulty = difficultyMap[entry.id] ?: 5,
                                note = notesMap[entry.id].orEmpty()
                            )
                        }
                        onDone(result)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dalej")
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
