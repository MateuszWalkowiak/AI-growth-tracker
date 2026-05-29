package com.MateuszW.aigrowthtracker.running

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.RunSessionEntity
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, MapboxExperimental::class)
@Composable
fun RunDetailScreen(
    viewModel: RunViewModel,
    sessionId: Int,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<RunSessionEntity?>(null) }
    var routePoints by remember { mutableStateOf<List<RunPoint>>(emptyList()) }

    LaunchedEffect(sessionId) {
        session = viewModel.getSessionById(sessionId)
        val dbPoints = viewModel.getRoutePointsForSession(sessionId)
        routePoints = dbPoints.map { RunPoint(it.latitude, it.longitude) }
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            zoom(14.0)
            center(Point.fromLngLat(21.0, 52.0))
        }
    }

    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            val first = routePoints.first()
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(first.longitude, first.latitude))
                zoom(14.0)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Szczegóły biegu") },
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
            MapboxMap(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                mapViewportState = mapViewportState
            ) {
                if (routePoints.size >= 2) {
                    val polylineState = remember {
                        PolylineAnnotationState().apply {
                            lineColor = androidx.compose.ui.graphics.Color(0xFF2979FF.toInt())
                            lineWidth = 5.0
                        }
                    }
                    PolylineAnnotation(
                        points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) },
                        polylineAnnotationState = polylineState
                    )
                }
            }

            session?.let { s ->
                Surface(shadowElevation = 4.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dateStr = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
                            .format(Date(s.startTime))
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DetailStat("%.2f km".format(s.totalDistanceMeters / 1000f), "Dystans")
                            DetailStat(viewModel.formatDuration(s.durationSeconds), "Czas")
                            DetailStat(viewModel.formatPace(s.avgPaceSecPerKm) + " /km", "Tempo")
                            DetailStat("%.1f km/h".format(s.avgSpeedKmh), "Prędkość")
                        }

                        s.targetDistanceKm?.let { target ->
                            if (target > 0f) {
                                val progress =
                                    (s.totalDistanceMeters / (target * 1000f)).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "Cel: %.1f km — wykonano %.0f%%".format(target, progress * 100),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (s.rating != null && s.rating > 0) {
                            Text("Ocena: ${"⭐".repeat(s.rating)}")
                        }

                        s.note?.let {
                            if (it.isNotBlank()) {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DetailStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
