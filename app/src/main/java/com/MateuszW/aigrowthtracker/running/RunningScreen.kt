package com.MateuszW.aigrowthtracker.running

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationState
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.plugin.locationcomponent.location

@OptIn(ExperimentalMaterial3Api::class, MapboxExperimental::class)
@Composable
fun RunningScreen(
    viewModel: RunViewModel,
    taskId: Int,
    taskName: String,
    targetKm: Float,
    durationMinutes: Int = 0,
    aiMainName: String?,
    onRunCompleted: (aiMainName: String?) -> Unit,
    onRunDeferred: (aiMainName: String?, distanceKm: Float, targetKm: Float, daysOffset: Int) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val isRunning by viewModel.isRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val distanceMeters by viewModel.totalDistanceMeters.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()
    val speedKmh by viewModel.currentSpeedKmh.collectAsState()
    val paceSecPerKm by viewModel.avgPaceSecPerKm.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    var showStopDialog by remember { mutableStateOf(false) }
    var showDeferDialog by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf("") }
    var selectedDeferDate by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPermission && !isRunning) {
            viewModel.startRun(context, taskId, taskName, targetKm.takeIf { it > 0f }, aiMainName)
        }
    }

    LaunchedEffect(Unit) {
        if (!isRunning) {
            if (hasPermission) {
                viewModel.startRun(context, taskId, taskName, targetKm.takeIf { it > 0f }, aiMainName)
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            zoom(15.0)
            center(Point.fromLngLat(21.0, 52.0))
        }
    }

    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            val last = routePoints.last()
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(last.longitude, last.latitude))
                zoom(16.0)
            }
        }
    }

    val distanceKm = distanceMeters / 1000f
    val targetProgress = if (targetKm > 0f) (distanceKm / targetKm).coerceIn(0f, 1f) else null

    // Cel osiągnięty? Dla km - distanceKm >= targetKm. Dla czasu - durationSeconds >= goal
    val isGoalReached = when {
        targetKm > 0f -> distanceKm >= targetKm
        durationMinutes > 0 -> durationSeconds >= durationMinutes * 60L
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(taskName) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize()
        ) {
            // Mapa Mapbox
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MapboxMap(
                    modifier = Modifier.fillMaxSize(),
                    mapViewportState = mapViewportState
                ) {
                    MapEffect(Unit) { mapView ->
                        mapView.location.updateSettings {
                            enabled = true
                        }
                    }
                    if (routePoints.size >= 2) {
                        val polylineState = remember {
                            PolylineAnnotationState().apply {
                                lineColor = androidx.compose.ui.graphics.Color(0xFF2979FF.toInt())
                                lineWidth = 5.0
                            }
                        }
                        PolylineAnnotation(
                            points = routePoints.map {
                                Point.fromLngLat(it.longitude, it.latitude)
                            },
                            polylineAnnotationState = polylineState
                        )
                    }
                }

                if (!hasPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                "Wymagany dostęp do lokalizacji",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }) { Text("Nadaj uprawnienie") }
                        }
                    }
                }

                //  Liczba punktów GPS (debug badge w rogu)
                if (routePoints.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "GPS: ${routePoints.size}",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Panel statystyk
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (targetKm > 0f && targetProgress != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Cel: %.1f km".format(targetKm),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "%.0f%%".format(targetProgress * 100),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { targetProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                        val remainingKm = (targetKm - distanceKm).coerceAtLeast(0f)
                        Text(
                            "Pozostało: %.2f km".format(remainingKm),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    } else if (durationMinutes > 0) {
                        val remainingSecs = (durationMinutes * 60L - durationSeconds).coerceAtLeast(0L)
                        Text(
                            "Pozostało: ${viewModel.formatDuration(remainingSecs)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RunStatBox("%.2f km".format(distanceKm), "Dystans")
                        RunStatBox(viewModel.formatDuration(durationSeconds), "Czas")
                        RunStatBox(viewModel.formatPace(paceSecPerKm) + " /km", "Tempo")
                        RunStatBox("%.1f".format(speedKmh), "km/h")
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isPaused) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.resumeRun(context) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Wznów") }
                            OutlinedButton(
                                onClick = { showStopDialog = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Zakończ") }
                        }
                        // Defer button - ukryty po osiągnięciu celu (cel zrealizowany,
                        // nie ma sensu odraczać)
                        if (!isGoalReached) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { showDeferDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Dokończ zadanie później") }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.pauseRun(context) },
                                modifier = Modifier.weight(1f),
                                enabled = isRunning
                            ) { Text("Pauza") }
                            Button(
                                onClick = { showStopDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = isRunning
                            ) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }

    //  Dialog zakończenia biegu z oceną
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Zakończ bieg") },
            text = {
                Column {
                    Text("Dystans: %.2f km".format(distanceKm))
                    Text("Czas: ${viewModel.formatDuration(durationSeconds)}")
                    Text("Tempo: ${viewModel.formatPace(paceSecPerKm)} /km")
                    Spacer(Modifier.height(12.dp))
                    Text("Ocena:")
                    Row {
                        (1..5).forEach { star ->
                            IconButton(onClick = { rating = star }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (star <= rating)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
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
                Button(onClick = {
                    showStopDialog = false
                    viewModel.stopRun(context, rating, note, isGoalReached) {
                        onRunCompleted(aiMainName)
                    }
                }) { Text("Zapisz i zakończ") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopDialog = false }) { Text("Anuluj") }
            }
        )
    }

    // Dialog odroczenia biegu
    if (showDeferDialog) {
        val options = listOf(
            "Dzisiaj (wróć później)" to 0,
            "Jutro" to 1,
            "Za 2 dni" to 2,
            "Za 3 dni" to 3
        )
        AlertDialog(
            onDismissRequest = { showDeferDialog = false },
            title = { Text("Dokończ zadanie później") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kiedy chcesz wrócić do biegu?")
                    Spacer(Modifier.height(8.dp))
                    options.forEachIndexed { idx, (label, _) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedDeferDate == idx,
                                onClick = { selectedDeferDate = idx }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeferDialog = false
                    val daysOffset = options[selectedDeferDate].second
                    viewModel.stopRunDeferred(context, daysOffset, aiMainName) { distKm, tgtKm ->
                        onRunDeferred(aiMainName, distKm, tgtKm, daysOffset)
                    }
                }) { Text("Potwierdź") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeferDialog = false }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun RunStatBox(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun isRunningTask(taskName: String): Boolean =
    taskName.contains("bieganie", ignoreCase = true)
