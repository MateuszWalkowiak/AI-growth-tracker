package com.MateuszW.aigrowthtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.TaskSummary
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    summaries: List<TaskSummary>,
    history: List<TaskHistoryEntity>,
    onTaskClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    // ── Computed stats ──────────────────────────────────────────────────────
    val totalTasks = history.size
    val completedTasks = history.count { it.completed }
    val completionRate = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f

    val ratings = history.mapNotNull { it.rating }
    val avgRating = ratings.average().let { if (it.isNaN()) 0.0 else it }

    val ratingDistribution = (1..5).associateWith { star -> ratings.count { it == star } }
    val maxRatingCount = ratingDistribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    // Tylko pula "daily" — wykluczamy zadania wygenerowane przez AI.
    val bestTasks = history
        .filter { it.rating != null && !it.isAIGenerated }
        .groupBy { it.taskName }
        .mapValues { entry -> entry.value.mapNotNull { it.rating }.average() }
        .toList()
        .sortedByDescending { it.second }
        .take(3)

    val categoryStats = history
        .groupBy { it.category }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val totalByCategory = categoryStats.sumOf { it.second }.coerceAtLeast(1)

    // ── Last 7 days streak ──────────────────────────────────────────────────
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -6)
    val weekAgoStart = cal.timeInMillis

    val last7DaysCount = history.count { it.date >= weekAgoStart && it.completed }
    val activeDays = history
        .filter { it.date >= weekAgoStart && it.completed }
        .map {
            val d = Calendar.getInstance().apply { timeInMillis = it.date }
            d.get(Calendar.DAY_OF_YEAR)
        }
        .distinct()
        .size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statystyki") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrot")
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── Podsumowanie ────────────────────────────────────────────
            item {
                StatsSectionHeader("Podsumowanie")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top row — two stat chips side by side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatChip(
                                label = "Wykonane",
                                value = "$completedTasks / $totalTasks",
                                modifier = Modifier.weight(1f)
                            )
                            StatChip(
                                label = "Srednia ocena",
                                value = "%.1f / 5".format(avgRating),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Completion rate bar
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Wskaznik ukonczenia",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "%.0f%%".format(completionRate * 100),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { completionRate },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                drawStopIndicator = {}
                            )
                        }

                        // Last 7 days
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatChip(
                                label = "Ostatnie 7 dni",
                                value = "$last7DaysCount zadan",
                                modifier = Modifier.weight(1f)
                            )
                            StatChip(
                                label = "Aktywne dni",
                                value = "$activeDays / 7",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Rozklad ocen ────────────────────────────────────────────
            if (ratings.isNotEmpty()) {
                item {
                    StatsSectionHeader("Rozklad ocen")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (5 downTo 1).forEach { star ->
                                val count = ratingDistribution[star] ?: 0
                                val fraction = count.toFloat() / maxRatingCount
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(32.dp)
                                    ) {
                                        Text(
                                            "$star",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp),
                                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        drawStopIndicator = {}
                                    )
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Najlepsze zadania ───────────────────────────────────────
            if (bestTasks.isNotEmpty()) {
                item {
                    StatsSectionHeader("Najlepsze zadania")
                }
                item {
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
                            bestTasks.forEachIndexed { index, (name, _) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Medal position
                                    val medal = when (index) {
                                        0 -> "1."
                                        1 -> "2."
                                        else -> "3."
                                    }
                                    Text(
                                        medal,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (index < bestTasks.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Kategorie ───────────────────────────────────────────────
            if (categoryStats.isNotEmpty()) {
                item {
                    StatsSectionHeader("Kategorie")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryStats.forEach { (category, count) ->
                                val fraction = count.toFloat() / totalByCategory
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            category,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "$count (%.0f%%)".format(fraction * 100),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        drawStopIndicator = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Historia zadan ──────────────────────────────────────────
            item {
                StatsSectionHeader("Historia zadan")
            }

            items(summaries) { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    onClick = { onTaskClick(task.taskId) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.taskName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${task.category}  |  ${task.totalCount} wykonan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (task.avgRating != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "%.1f".format(task.avgRating),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                "Brak ocen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
