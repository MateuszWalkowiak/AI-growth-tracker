package com.MateuszW.aigrowthtracker

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
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsScreen(
    history: List<TaskHistoryEntity>,
    dailyGoal: Int,
    onBack: () -> Unit
) {

    val completed = history.count { it.completed }
    val progress = (completed.toFloat() / dailyGoal).coerceAtMost(1f)

    val color = when {
        progress >= 1f -> Color(1f, 0.84f, 0f)
        progress == 0f -> Color(0.8f, 0.2f, 0.2f)
        progress < 0.5f -> Color(1f, 0.6f, 0.2f)
        progress < 1f -> Color(0.4f, 0.8f, 0.2f)
        else -> Color(1f, 0.84f, 0f)
    }
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

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                color = color,
                trackColor = Color.DarkGray
            )

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