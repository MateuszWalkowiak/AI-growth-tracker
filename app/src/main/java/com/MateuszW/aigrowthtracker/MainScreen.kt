package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAddTaskClick: () -> Unit,
    onExecuteTaskClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Growth Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    //dodaj nowe zadanie
                    IconButton(onClick = onAddTaskClick) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj zadanie")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //podsumowanie dnia
            Text(
                text = "Dzisiejszy postęp: 60%", //TO DO
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            //widok kalendarza
            CalendarWeekMock()

            Spacer(modifier = Modifier.weight(1f))

            //opcja wykonania zadania
            Button(
                onClick = onExecuteTaskClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("WYKONAJ ZADANIE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

//mock kalendarza
@Composable
fun CalendarWeekMock() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        val days = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")
        val completions = listOf(0.1f, 0.4f, 0.8f, 1.0f, 0.0f, 0.6f, 0.9f)

        days.forEachIndexed { index, day ->
            val percentage = completions[index]

            val dayColor = Color(
                red = 1f - percentage,
                green = percentage,
                blue = 0.2f,
                alpha = 1f
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = day, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(dayColor, shape = RoundedCornerShape(8.dp))
                )
            }
        }
    }
}