package com.MateuszW.aigrowthtracker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.Canvas
import android.R.attr.x



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsScreen(
    history: List<TaskHistoryEntity>,
    dailyGoal: Int,
    onBack: () -> Unit
) {

    val completed = history.count { it.completed }
    val progress = completed.toFloat() / dailyGoal

    val animatedProgress by animateFloatAsState(progress)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dzień") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {

            val completed = history.count { it.completed }
            val progress = completed.toFloat() / dailyGoal
            AnimatedProgressBar(progress = progress)
            Text("$completed / $dailyGoal")

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(history) { task ->
                    ListItem(
                        headlineContent = { Text(task.taskName) },
                        supportingContent = {
                            Text("Ocena: ${task.rating ?: "-"}")
                        }
                    )
                }
            }
        }
    }
}

// animacja progress bara
data class FlameParticle(
    val startY: Float,
    val speed: Float,
    val size: Float,
    val seed: Float
)

@Composable
fun rememberFlames(count: Int): List<FlameParticle> {
    return remember {
        List(count) {
            FlameParticle(
                startY = Random.nextFloat(),
                speed = 0.6f + Random.nextFloat() * 0.8f,
                size = 6f + Random.nextFloat() * 10f,
                seed = Random.nextFloat()
            )
        }
    }
}

@Composable
fun AnimatedProgressBar(progress: Float) {

    val baseProgress = progress.coerceAtMost(1f)
    val overProgress = (progress - 1f).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition()

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing)
        )
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        )
    )

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        )
    )

    val flames = rememberFlames(28) //ilosc plomieni

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp) //wysokość progress bara
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
    ) {

        //progres poniez 100%
        Box(
            modifier = Modifier
                .fillMaxWidth(baseProgress)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0.2f, 0.8f, 0.2f),
                            Color(0.4f, 1f, 0.4f)
                        )
                    )
                )
        )
        //powyzej 100%
        if (overProgress > 0f) {

            val goldBrush = Brush.linearGradient(
                colors = listOf(
                    Color(1f, 0.7f, 0f),
                    Color.White,
                    Color(1f, 0.9f, 0f),
                    Color(1f, 0.6f, 0f)
                ),
                start = Offset(offset, 0f),
                end = Offset(offset + 400f, 0f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(overProgress)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = pulse
                        scaleY = 1.1f
                    }
                    .background(goldBrush)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(overProgress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val waveOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(overProgress)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationY = waveOffset
                    }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {

                val barWidth = size.width * overProgress
                val flameStart = barWidth * 0.25f

                flames.forEach { p ->

                    if (p.size < 8f) return@forEach
                    val speed = p.speed * 0.7f
                    val life = ((time * speed) + p.seed) % 1f

                    val range = (barWidth - flameStart)

                    val normalizedX = (x - flameStart) / range
                    if (p.seed > normalizedX) return@forEach

                    val travel = (time * speed * range * 1.5f)
                    val base = p.seed * range

                    val x = flameStart + ((base + travel) % range)
                    val baseY = size.height * (0.35f + p.startY * 0.5f)
                    val y = baseY + sin(life * 6f + p.seed * 10f) * 3f

                    val alpha = (1f - life).let { it * it } * 0.9f
                    val scale = 0.6f + life * 0.9f

                    val height = p.size * 2.2f * scale
                    val width = p.size * 1.2f * scale

                    val path = Path().apply {
                        moveTo(x, y - height)
                        quadraticBezierTo(
                            x + width, y - height * 0.3f,
                            x, y
                        )
                        quadraticBezierTo(
                            x - width, y - height * 0.3f,
                            x, y - height
                        )
                        close()
                    }

                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(1f, 0.5f, 0f, alpha * 0.4f),
                                Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.Plus
                    )

                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = alpha),
                                Color(1f, 0.8f, 0.2f, alpha),
                                Color(1f, 0.4f, 0f, alpha * 0.7f),
                                Color.Transparent
                            )
                        )
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.8f),
                        radius = p.size * 0.25f * scale,
                        center = Offset(x, y - height * 0.4f),
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }
    }
}