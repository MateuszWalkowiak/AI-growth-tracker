package com.MateuszW.aigrowthtracker.running

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.RunSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunHistoryScreen(
    viewModel: RunViewModel,
    onSessionClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val sessions by viewModel.completedSessions.collectAsState()

    var totalDistanceKm by remember { mutableStateOf(0f) }
    var completedCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        totalDistanceKm = viewModel.getTotalDistanceKm()
        completedCount = viewModel.getCompletedRunCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historia biegów") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .navigationBarsPadding()
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Brak historii biegów.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Zacznij biegać, by zobaczyć swoje wyniki!")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .navigationBarsPadding()
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$completedCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                                Text("Biegi", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("%.1f km".format(totalDistanceKm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                                Text("Łączny dystans", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val avgKm = if (completedCount > 0) totalDistanceKm / completedCount else 0f
                                Text("%.1f km".format(avgKm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                                Text("Śr. dystans", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                items(sessions) { session ->
                    RunSessionCard(
                        session = session,
                        viewModel = viewModel,
                        onClick = { onSessionClick(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RunSessionCard(
    session: RunSessionEntity,
    viewModel: RunViewModel,
    onClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
        .format(Date(session.startTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("%.2f km".format(session.totalDistanceMeters / 1000f), "Dystans")
                StatItem(viewModel.formatDuration(session.durationSeconds), "Czas")
                StatItem(viewModel.formatPace(session.avgPaceSecPerKm) + " /km", "Tempo")
                StatItem("%.1f km/h".format(session.avgSpeedKmh), "Prędkość")
            }
            if (session.rating != null && session.rating > 0) {
                Spacer(Modifier.height(6.dp))
                Text("⭐".repeat(session.rating), style = MaterialTheme.typography.bodySmall)
            }
            session.targetDistanceKm?.let { target ->
                if (target > 0f) {
                    Spacer(Modifier.height(4.dp))
                    val progress = (session.totalDistanceMeters / (target * 1000f)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Cel: %.1f km — %.0f%%".format(target, progress * 100),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
