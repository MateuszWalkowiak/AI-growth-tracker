package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HolidayAdjustmentScreen(
    holidaysCount: Int,
    previousDifficulty: Int,
    onConfirm: (Int) -> Unit,
    onSkip: () -> Unit
) {
    var difficulty by remember { mutableStateOf(previousDifficulty) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Święta w przyszłym tygodniu") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            //  Avatar z dynamicznym tekstem
            AssistantSpeechBubble(
                painter = painterResource(R.drawable.assistant_holiday),
                speechText = "W przyszłym tygodniu wypada $holidaysCount " +
                        "${if (holidaysCount == 1) "dzień świąteczny" else "dni świątecznych"} " +
                        "spoza weekendu. Czy chcesz zmienić poziom trudności?"
            )

            Text("Aktualny poziom trudności: $previousDifficulty")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..10).forEach { level ->
                    val color = difficultyColor(level)
                    val isSelected = difficulty == level
                    Button(
                        onClick = { difficulty = level },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) color else color.copy(alpha = 0.18f),
                            contentColor = if (isSelected) Color.White else color
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("$level", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { onConfirm(difficulty) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("TAK – zastosuj trudność $difficulty")
            }

            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("NIE – zostaw trudność $previousDifficulty")
            }
        }
    }
}
