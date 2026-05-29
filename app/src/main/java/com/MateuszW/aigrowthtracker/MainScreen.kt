package com.MateuszW.aigrowthtracker

import java.util.Calendar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.MateuszW.aigrowthtracker.data.TaskEntity
import com.MateuszW.aigrowthtracker.ui.theme.WeekendOrange
import com.MateuszW.aigrowthtracker.ui.theme.HolidayAmber
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.PaddingValues
import kotlin.math.sin

// ── Calendar day progress colors ────────────────────────────────────────────
// Pastelowy gradient: red → orange → lime

private val CalendarPastelRed    = Color(0xFFE8827C)  // 0%   — pastelowy czerwony
private val CalendarPastelOrange = Color(0xFFF2BB7A)  // 50%  — pastelowy pomarańczowy
private val CalendarPastelLime   = Color(0xFFB5E86B)  // 100% — pastelowy lime
private val CalendarGoldOver     = Color(0xFFFFD54F)  // >100% — ciepłe złoto (z animacją)
// Przyszlosc
private val CalendarFuture = Color(0xFFE8DDD0)        // warm beige
// Przed kontem
private val CalendarInactive = Color(0xFFB5A99A)      // warm gray
// Dzisiejszy dzien — ramka podswietlajaca
private val CalendarTodayBorder = Color(0xFFD4710A)   // primary orange


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: TaskViewModel,
    completedDates: List<Long>,
    holidays: List<Long>,
    showHolidays: Boolean,
    showWeekends: Boolean,
    onAddTaskClick: () -> Unit,
    onDayClick: (Long) -> Unit,
    onExecuteTaskClick: () -> Unit,
    onStatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeTask: TaskEntity?,
    remainingTime: Int,
    onFinishTask: () -> Unit,
    onFinishTaskWithSummary: (Int, String) -> Unit,
    onCancelTask: () -> Unit,
    // Dynamic progress data (same logic as DayDetailsScreen)
    todayHistory: List<com.MateuszW.aigrowthtracker.data.TaskHistoryEntity>,
    todayAiTasksCount: Int,
    todayMandatoryTasksCount: Int,
    allDailyTasksCount: Int
) {
    val context = LocalContext.current

    // Dialog ankiety po zakończeniu zadania (identyczny flow jak ExecuteTaskScreen)
    var showSummaryDialog by remember { mutableStateOf(false) }
    var summaryRating by remember { mutableStateOf(0) }
    var summaryNote by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.syncFromFirebase()
        viewModel.loadHolidaysSmart(context)
    }

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    // ── Dynamic progress (synced with DayDetailsScreen logic) ──────────
    val aiDoneCount = remember(todayHistory) {
        todayHistory.count { it.isAIGenerated && it.completed && it.aiMainName != "WEEKLY_SUMMARY" }
    }
    val nonAiDoneCount = remember(todayHistory) {
        todayHistory.count { !it.isAIGenerated && it.completed }
    }
    // Odroczone zadania przełożone na dziś (np. bieg) — niewykonane wpisy z notatką "(odroczone)".
    val deferredRunsCount = remember(todayHistory) {
        todayHistory.count { !it.completed && it.note?.contains("(odroczone)") == true }
    }
    // Cel dnia = AI + obowiązkowe na dziś + odroczone (spójne z DayDetailsScreen).
    val totalPlanSize = todayAiTasksCount + todayMandatoryTasksCount + deferredRunsCount
    val dailyGoal = when {
        totalPlanSize > 0 -> totalPlanSize
        else -> (allDailyTasksCount / 2).coerceAtLeast(1)
    }
    // Liczymy WSZYSTKIE ukończone zadania (AI + non-AI + mandatory+AI)
    val allCompletedCount = remember(todayHistory) {
        todayHistory.count { it.completed && it.aiMainName != "WEEKLY_SUMMARY" }
    }
    val progressCount = if (totalPlanSize > 0) {
        allCompletedCount
    } else {
        nonAiDoneCount
    }
    val progressFraction = if (dailyGoal > 0) progressCount.toFloat() / dailyGoal else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Growth Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onAddTaskClick) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj zadanie")
                    }
                    IconButton(onClick = { onSettingsClick() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                }
            )
        }
    ) { paddingValues ->

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Progress bar (identyczny styl jak DayDetailsScreen) ──────
            Text(
                text = "Dzisiejszy postep",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            AnimatedProgressBar(progress = progressFraction)

            Spacer(Modifier.height(4.dp))
            Text(
                "$progressCount / $dailyGoal zadan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // ── Kalendarz ────────────────────────────────────────────────
            var currentMonth by remember { mutableStateOf(java.util.Calendar.getInstance()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth = (currentMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, -1)
                    }
                }) {
                    Text("<", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                         color = MaterialTheme.colorScheme.primary)
                }

                val months = listOf(
                    "Styczen","Luty","Marzec","Kwiecien","Maj","Czerwiec",
                    "Lipiec","Sierpien","Wrzesien","Pazdziernik","Listopad","Grudzien"
                )
                val monthName = months[currentMonth.get(Calendar.MONTH)]

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        currentMonth.get(Calendar.YEAR).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        monthName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = {
                    currentMonth = (currentMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                    }
                }) {
                    Text(">", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                         color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(4.dp))

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

            // ── Aktywne zadanie (kompaktowy pasek) ────────────────────────
            activeTask?.let {
                val minutes = remainingTime / 60
                val seconds = remainingTime % 60

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Nazwa zadania (zajmuje dostepna przestrzen)
                        Text(
                            it.taskName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Timer
                        Text(
                            "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Zrobione — otwiera ankietę (ujednolicone z ExecuteTaskScreen)
                        Button(
                            onClick = { showSummaryDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Zrobione", style = MaterialTheme.typography.labelMedium)
                        }
                        // Anuluj
                        OutlinedButton(
                            onClick = onCancelTask,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Anuluj", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Przyciski ────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExecuteTaskClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("WYKONAJ ZADANIE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onStatsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("STATYSTYKI", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        } // close AnimatedVisibility
    }

    // ── Ankieta po zakończeniu zadania (ujednolicone z ExecuteTaskScreen) ──
    if (showSummaryDialog && activeTask != null) {
        val isAiTask = activeTask.isAIGenerated
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            title = { Text("Podsumuj zadanie") },
            text = {
                Column {
                    Text("Ocena:")
                    Row {
                        (1..5).forEach { star ->
                            IconButton(onClick = { summaryRating = star }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (star <= summaryRating)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = summaryNote,
                        onValueChange = { summaryNote = it },
                        label = { Text("Notatka (opcjonalnie)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFinishTaskWithSummary(summaryRating, summaryNote)
                        showSummaryDialog = false
                        summaryRating = 0
                        summaryNote = ""
                    },
                    enabled = summaryRating > 0 && (!isAiTask || summaryNote.isNotBlank())
                ) { Text("Zapisz") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSummaryDialog = false }) { Text("Anuluj") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kalendarz
// ─────────────────────────────────────────────────────────────────────────────

fun formatDay(time: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = time
    return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Pn"
        java.util.Calendar.TUESDAY -> "Wt"
        java.util.Calendar.WEDNESDAY -> "Sr"
        java.util.Calendar.THURSDAY -> "Cz"
        java.util.Calendar.FRIDAY -> "Pt"
        java.util.Calendar.SATURDAY -> "Sb"
        java.util.Calendar.SUNDAY -> "Nd"
        else -> ""
    }
}

/**
 * Interpoluje kolor dnia kalendarza na podstawie procentu wykonania zadan.
 * Ciepla paleta: terracotta-red -> amber -> sage-green -> forest-green.
 * Czytelna jakosciowo, ale wpisujaca sie w bear-theme.
 */
private fun calendarDayColor(percentage: Float): Color {
    if (percentage <= 0f) return CalendarPastelRed
    if (percentage >= 1f) return CalendarPastelLime

    return if (percentage < 0.5f) {
        lerpColor(CalendarPastelRed, CalendarPastelOrange, percentage / 0.5f)
    } else {
        lerpColor(CalendarPastelOrange, CalendarPastelLime, (percentage - 0.5f) / 0.5f)
    }
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f
    )
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
        // Naglowki dni tygodnia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Pn","Wt","Sr","Cz","Pt","Sb","Nd").forEach { dayName ->
                Text(
                    dayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(6.dp))

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

                    val count = completedDates.count { normalizeDay(it) == normalized }
                    val percentage = count.toFloat() / dailyGoal

                    val dayOfWeek = Calendar.getInstance().apply {
                        timeInMillis = dayMillis
                    }.get(Calendar.DAY_OF_WEEK)

                    val isWeekend = showWeekends && (
                            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
                    val isHoliday = showHolidays && holidays.contains(normalized)
                    val isToday = normalized == today

                    // ── Kolor dnia ───────────────────────────────────────
                    val baseColor = when {
                        // Swieta i weekendy: stonowane ciepłe akcenty
                        // ale JESLI maja progres, to blend z kolorem progresu
                        isHoliday && count > 0 -> lerpColor(HolidayAmber, calendarDayColor(percentage), 0.5f)
                        isHoliday -> HolidayAmber
                        isWeekend && count > 0 -> lerpColor(WeekendOrange, calendarDayColor(percentage), 0.5f)
                        isWeekend -> WeekendOrange
                        normalized < accountCreationDate -> CalendarInactive
                        normalized > today -> CalendarFuture
                        count == 0 -> CalendarPastelRed
                        percentage > 1f -> CalendarGoldOver
                        else -> calendarDayColor(percentage)
                    }

                    val color = if (!isCurrentMonth) {
                        baseColor.copy(alpha = 0.3f)
                    } else baseColor

                    val day = Calendar.getInstance().apply {
                        timeInMillis = dayMillis
                    }.get(Calendar.DAY_OF_MONTH)

                    val isOver100 = percentage > 1f && count > 0 && isCurrentMonth

                    // Glitter animation phase for >100%
                    val glitterPhase = if (isOver100) {
                        val inf = rememberInfiniteTransition(label = "glitter")
                        inf.animateFloat(
                            initialValue = 0f,
                            targetValue = 6.2832f, // 2*PI
                            animationSpec = infiniteRepeatable(
                                animation = tween(2400, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "sparkle"
                        ).value
                    } else 0f

                    // Ciemny tekst na pastelowych tłach
                    val textColor = when {
                        !isCurrentMonth -> Color(0xFF5A4A3A).copy(alpha = 0.5f)
                        normalized > today || normalized < accountCreationDate -> Color(0xFF6B5E52)
                        else -> Color(0xFF3A2A1A)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color, RoundedCornerShape(8.dp))
                            .then(
                                if (isToday && isCurrentMonth) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = CalendarTodayBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onDayClick(dayMillis)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.toString(),
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isToday && isCurrentMonth) FontWeight.Bold else FontWeight.Normal
                        )

                        // Glitter overlay for >100%
                        if (isOver100) {
                            val sparkleWhite = Color(0xFFFFFFFF)
                            val sparkleGold = Color(0xFFFFE082)
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val w = size.width
                                val h = size.height
                                // 6 sparkle points at fixed positions, phase-shifted
                                val sparkles = listOf(
                                    Offset(0.15f * w, 0.20f * h) to 0f,
                                    Offset(0.75f * w, 0.15f * h) to 1.0f,
                                    Offset(0.85f * w, 0.70f * h) to 2.1f,
                                    Offset(0.25f * w, 0.80f * h) to 3.2f,
                                    Offset(0.50f * w, 0.35f * h) to 4.0f,
                                    Offset(0.60f * w, 0.85f * h) to 5.1f
                                )
                                sparkles.forEach { (pos, offset) ->
                                    val alpha = ((sin(glitterPhase + offset) + 1f) / 2f)
                                        .coerceIn(0f, 1f)
                                    val radius = 1.2f + alpha * 1.5f
                                    val c = if (offset < 3f) sparkleWhite else sparkleGold
                                    drawCircle(
                                        color = c.copy(alpha = alpha * 0.85f),
                                        radius = radius,
                                        center = pos
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
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
