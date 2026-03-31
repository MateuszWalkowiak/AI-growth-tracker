package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.WeeklyTaskEntity

@Composable
fun WeeklyPreviewScreen(
    tasks: List<WeeklyTaskEntity>,
    onConfirm: () -> Unit
) {

    Column(Modifier.padding(16.dp)) {

        Text("Twój tydzień wygląda tak:")

        tasks.forEach { task ->

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {

                    Text(task.name)

                    LinearProgressIndicator(
                        progress = 0f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Cel: ${task.targetValue}")
                }
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zatwierdź plan")
        }
    }
}