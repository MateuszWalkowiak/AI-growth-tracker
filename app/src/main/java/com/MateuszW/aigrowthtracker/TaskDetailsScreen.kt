package com.MateuszW.aigrowthtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import java.text.SimpleDateFormat
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

    val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pl"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Szczegoly zadania") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
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
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Podsumowanie",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Wykonan: ${history.size}")
                        Text("Srednia ocena: %.2f / 5".format(avgRating))
                    }
                }
            }

            item {
                Text(
                    "Historia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(history) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Ocena: ${item.rating ?: "-"} / 5",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                dateFormat.format(Date(item.date)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        item.note?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Instrukcje AI zapisane przy wykonywaniu zadania
                        item.aiInstructions?.takeIf { it.isNotBlank() }?.let { instructions ->
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            Spacer(Modifier.height(8.dp))

                            var expanded by remember { mutableStateOf(false) }
                            val lineCount = instructions.count { it == '\n' } + 1
                            val isLong = lineCount > 2 || instructions.length > 120

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isLong) Modifier.clickable { expanded = !expanded }
                                        else Modifier
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Polecenia AI",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isLong) {
                                    Text(
                                        if (expanded) "Zwiń" else "Rozwiń",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = parseBoldMarkdown(instructions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isLong && !expanded) 2 else Int.MAX_VALUE,
                                overflow = if (isLong && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip,
                                modifier = Modifier.animateContentSize()
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}