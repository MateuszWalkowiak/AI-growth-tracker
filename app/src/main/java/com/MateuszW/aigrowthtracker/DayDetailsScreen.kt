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
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing)
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
        targetValue = 10000f,
        animationSpec = infiniteRepeatable(
            animation = tween(200000, easing = LinearEasing)
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
        val startColor = when {
            baseProgress < 0.5f -> {
                // czerwony -> pomarańczowy
                val t = baseProgress / 0.5f
                Color(
                    red = 1f,
                    green = t * 0.5f,
                    blue = 0f
                )
            }
            else -> {
                // pomarańczowy -> zielony
                val t = (baseProgress - 0.5f) / 0.5f
                Color(
                    red = 1f - t,
                    green = 0.5f + t * 0.5f,
                    blue = 0f
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(baseProgress)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            startColor.copy(alpha = 0.6f),
                            startColor,
                            startColor.copy(alpha = 0.9f)
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

            Canvas(modifier = Modifier.fillMaxSize()) {

                val barWidth = size.width * overProgress
                val startX = barWidth * 0.1f
                val range = barWidth - startX

                if (range <= 0f) return@Canvas

                flames.forEach { p ->

                    val speed = (0.015f + p.speed * 0.01f)* 0.7f

                    val progress = ((time * speed + p.seed) % 1f)

                    val x = startX + progress * range

                    val baseY = size.height * (0.3f + p.startY * 0.4f)

                    val y = baseY + sin(time + p.seed * 5f) * 1f

                    val scale = 0.5f + progress * 0.8f
                    val alpha = 1f - progress

                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.8f),
                        radius = p.size * scale,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}