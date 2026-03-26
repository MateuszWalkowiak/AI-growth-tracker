package com.MateuszW.aigrowthtracker

import java.util.Calendar
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
import com.MateuszW.aigrowthtracker.data.TaskEntity
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    tasks: List<TaskEntity>,
    dailyGoal: Int,
    completedDates: List<Long>,
    holidays: List<Long>,
    showHolidays: Boolean,
    showWeekends: Boolean,
    onAddTaskClick: () -> Unit,
    onDayClick: (Long) -> Unit,
    onExecuteTaskClick: () -> Unit,
    onStatsClick: () -> Unit,
    activeTask: TaskEntity?,
    remainingTime: Int
) {


    val completedTasks = tasks.count { it.isCompleted }

    val effectiveTotal = if (tasks.isNotEmpty()) {
        dailyGoal.coerceAtMost(tasks.size)
    } else 1

    val progress = (completedTasks.coerceAtMost(effectiveTotal) * 100) / effectiveTotal

    val progressColor = if (progress > 100) {
        Color(1f, 0.84f, 0f) //zloty
    } else {
        Color(
            red = 1f - (progress / 100f),
            green = (progress / 100f),
            blue = 0.2f
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Growth Tracker", fontWeight = FontWeight.Bold) },
                actions = {
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

            //Podsumowanie dnia
            Text(
                text = "Dzisiejszy postęp:",
                fontSize = 18.sp
            )

            Text(
                text = "$completedTasks / $effectiveTotal ($progress%)",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            var currentMonth by remember { mutableStateOf(java.util.Calendar.getInstance()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    currentMonth = (currentMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, -1)
                    }
                }) {
                    Text("<")
                }

                val months = listOf(
                    "Styczeń","Luty","Marzec","Kwiecień","Maj","Czerwiec",
                    "Lipiec","Sierpień","Wrzesień","Październik","Listopad","Grudzień"
                )

                val monthName = months[currentMonth.get(Calendar.MONTH)]

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentMonth.get(Calendar.YEAR).toString())
                    Text(monthName ?: "")
                }

                Button(onClick = {
                    currentMonth = (currentMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                    }
                }) {
                    Text(">")
                }
            }


            val today = normalizeDay(System.currentTimeMillis())
            val accountCreationDate = completedDates.minOrNull() ?: today

            RealCalendarWeek(
                completedDates = completedDates,
                dailyGoal = dailyGoal,
                currentMonth = currentMonth,
                accountCreationDate = accountCreationDate,
                holidays = holidays,
                showHolidays = showHolidays,
                showWeekends = showWeekends,
                onDayClick = onDayClick
            )

            Spacer(modifier = Modifier.weight(1f))

            activeTask?.let {

                val minutes = remainingTime / 60
                val seconds = remainingTime % 60

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Text(
                            "Aktywne zadanie",
                            style = MaterialTheme.typography.labelMedium
                        )

                        Text(
                            it.taskName,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Pozostało: %02d:%02d".format(minutes, seconds),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                //wykonaj zadnaie
                Button(
                    onClick = onExecuteTaskClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("WYKONAJ ZADANIE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                //statystki
                Button(
                    onClick = onStatsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("STATYSTYKI", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
fun formatDay(time: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = time

    return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Pn"
        java.util.Calendar.TUESDAY -> "Wt"
        java.util.Calendar.WEDNESDAY -> "Śr"
        java.util.Calendar.THURSDAY -> "Cz"
        java.util.Calendar.FRIDAY -> "Pt"
        java.util.Calendar.SATURDAY -> "Sb"
        java.util.Calendar.SUNDAY -> "Nd"
        else -> ""
    }
}
@Composable
fun RealCalendarWeek(
    completedDates: List<Long>,
    dailyGoal: Int,
    currentMonth: Calendar,
    accountCreationDate: Long,
    holidays: List<Long>,
    showHolidays: Boolean,
    showWeekends: Boolean,
    onDayClick: (Long) -> Unit
) {

    val startOfMonth = remember(currentMonth.timeInMillis) {
        Calendar.getInstance().apply {
            timeInMillis = currentMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val days = (1..startOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)).map { day ->
        Calendar.getInstance().apply {
            timeInMillis = startOfMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, day)
        }.timeInMillis
    }


    val today = normalizeDay(System.currentTimeMillis())


    Column {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Pn","Wt","Śr","Cz","Pt","Sb","Nd").forEach {
                Text(it, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        val firstDayOfWeek = startOfMonth.get(Calendar.DAY_OF_WEEK)
        val offset = (firstDayOfWeek + 5) % 7

        val prevMonth = Calendar.getInstance().apply {
            timeInMillis = startOfMonth.timeInMillis
            add(Calendar.MONTH, -1)
        }

        val prevMonthDays = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        val previousDays = (prevMonthDays - offset + 1..prevMonthDays).map { day ->
            Calendar.getInstance().apply {
                timeInMillis = prevMonth.timeInMillis
                set(Calendar.DAY_OF_MONTH, day)
            }.timeInMillis
        }

        val paddedDays = previousDays + days

        val remainder = paddedDays.size % 7
        val nextDays = if (remainder != 0) {
            val nextMonth = Calendar.getInstance().apply {
                timeInMillis = currentMonth.timeInMillis
                add(Calendar.MONTH, 1)
            }

            (1..(7 - remainder)).map { day ->
                Calendar.getInstance().apply {
                    timeInMillis = nextMonth.timeInMillis
                    set(Calendar.DAY_OF_MONTH, day)
                }.timeInMillis
            }
        } else emptyList()

        val fullDays = paddedDays + nextDays
        val weeks = fullDays.chunked(7)

        weeks.forEach { week ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                week.forEach { dayMillis ->

                    val normalized = normalizeDay(dayMillis)

                    val isCurrentMonth = Calendar.getInstance().apply {
                        timeInMillis = dayMillis
                    }.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)

                    val count = completedDates.count {
                        normalizeDay(it) == normalized
                    }

                    val percentage = count.toFloat() / dailyGoal

                    val dayOfWeek = Calendar.getInstance().apply {
                        timeInMillis = dayMillis
                    }.get(Calendar.DAY_OF_WEEK)

                    val isWeekend = showWeekends && (
                            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
                            )

                    val isHoliday = showHolidays && holidays.contains(normalized)

                    val baseColor = when {
                        isWeekend || isHoliday -> Color(0.4f, 0.6f, 1f)
                        normalized < accountCreationDate -> Color.Gray
                        normalized > today -> Color.LightGray
                        percentage > 1f -> Color(1f, 0.84f, 0f)
                        percentage == 1f -> Color(0.2f, 0.8f, 0.2f)
                        else -> Color(
                            red = 1f - percentage,
                            green = percentage,
                            blue = 0.2f
                        )
                    }
                    //wygaszenie dopelnienia tygodnia spoza aktualnego miesiaca
                    val color = if (!isCurrentMonth) {
                        baseColor.copy(alpha = 0.3f)
                    } else baseColor

                    val day = Calendar.getInstance().apply {
                        timeInMillis = dayMillis
                    }.get(Calendar.DAY_OF_MONTH)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .background(color, RoundedCornerShape(8.dp))
                            .clickable { onDayClick(dayMillis) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(day.toString(), color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

fun normalizeDay(time: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = time
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}