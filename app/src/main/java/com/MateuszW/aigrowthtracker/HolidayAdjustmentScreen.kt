package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row

@Composable
fun HolidayAdjustmentScreen(
    holidaysCount: Int,
    previousDifficulty: Int,
    onConfirm: (Int) -> Unit,
    onSkip: () -> Unit
) {

    var difficulty by remember { mutableStateOf(previousDifficulty) }

    Column(Modifier.padding(16.dp)) {

        Text("W przyszłym tygodniu masz $holidaysCount dni świąt")

        Button(onClick = {}) {
            Text("TAK - zmień trudność")
        }

        Row {
            (1..10).forEach {
                Button(onClick = { difficulty = it }) {
                    Text("$it")
                }
            }
        }

        Button(onClick = { onConfirm(difficulty) }) {
            Text("Zastosuj")
        }

        Button(onClick = onSkip) {
            Text("NIE")
        }
    }
}