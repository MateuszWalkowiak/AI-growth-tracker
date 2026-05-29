package com.MateuszW.aigrowthtracker

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.AiDailyTask
import com.MateuszW.aigrowthtracker.data.TaskEntity
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Pager wrapper — swipe between days like a photo gallery
// ─────────────────────────────────────────────────────────────────────────────

private const val PAGER_PAGE_COUNT = 731  // ~1 year each direction
private const val PAGER_INITIAL_PAGE = 365

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsPager(
    initialDay: Long,
    // Data provider callbacks — called per displayed page
    historyProvider: @Composable (day: Long) -> List<TaskHistoryEntity>,
    aiTasksProvider: @Composable (day: Long) -> List<AiDailyTask>,
    mandatoryTasksProvider: @Composable (day: Long) -> List<TaskEntity>,
    stepGoalsMap: Map<Int, Int>,
    todaySteps: Long,
    allDailyTasksCount: Int,
    weekPlanned: Boolean,
    onBack: () -> Unit,
    onAiTaskDone: (TaskHistoryEntity) -> Unit,
    onResumeRun: (entry: TaskHistoryEntity, remainingKm: Float) -> Unit = { _, _ -> },
    onMandatoryTaskDone: (TaskEntity, Long) -> Unit = { _, _ -> },
    onMandatoryRunStart: (TaskEntity) -> Unit = { },
    onStartMandatoryTask: (TaskEntity) -> Unit = { },
    onStartAiTask: (AiDailyTask) -> Unit = { }
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = PAGER_INITIAL_PAGE,
        pageCount = { PAGER_PAGE_COUNT }
    )

    // Compute the day timestamp for a given pager page
    val dayForPage: (Int) -> Long = remember(initialDay) {
        { page ->
            val dayOffset = page - PAGER_INITIAL_PAGE
            initialDay + dayOffset * 86_400_000L
        }
    }

    val currentDay by remember {
        derivedStateOf { dayForPage(pagerState.currentPage) }
    }

    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val dateFormatter = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pl")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val displayDay = dayForPage(pagerState.currentPage)
                        Text(
                            text = dateFormatter.format(Date(displayDay))
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val isToday = displayDay >= startOfToday && displayDay < startOfToday + 86_400_000L
                        if (isToday) {
                            Text(
                                "Dzisiaj",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrot")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Poprzedni dzien")
                    }
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Nastepny dzien")
                    }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val pageDay = dayForPage(page)
            val isToday = pageDay >= startOfToday && pageDay < startOfToday + 86_400_000L
            val isPast = pageDay < startOfToday

            val history = historyProvider(pageDay)
            val aiTasks = aiTasksProvider(pageDay)
            val mandatoryTasks = mandatoryTasksProvider(pageDay)

            DayDetailsContent(
                history = history,
                aiTasks = aiTasks,
                day = pageDay,
                weekPlanned = weekPlanned,
                allDailyTasksCount = allDailyTasksCount,
                todaySteps = todaySteps,
                stepGoalsMap = stepGoalsMap,
                isToday = isToday,
                isPast = isPast,
                mandatoryTasksForDay = mandatoryTasks,
                onAiTaskDone = onAiTaskDone,
                onResumeRun = onResumeRun,
                onMandatoryTaskDone = { task -> onMandatoryTaskDone(task, pageDay) },
                onMandatoryRunStart = onMandatoryRunStart,
                onStartMandatoryTask = onStartMandatoryTask,
                onStartAiTask = onStartAiTask
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DayDetailsContent — the actual day content (previously DayDetailsScreen body)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayDetailsContent(
    history: List<TaskHistoryEntity>,
    aiTasks: List<AiDailyTask>,
    day: Long,
    weekPlanned: Boolean,
    allDailyTasksCount: Int,
    todaySteps: Long,
    stepGoalsMap: Map<Int, Int>,
    isToday: Boolean,
    isPast: Boolean = false,
    mandatoryTasksForDay: List<TaskEntity> = emptyList(),
    onAiTaskDone: (TaskHistoryEntity) -> Unit,
    onResumeRun: (entry: TaskHistoryEntity, remainingKm: Float) -> Unit = { _, _ -> },
    onMandatoryTaskDone: (TaskEntity) -> Unit = { },
    onMandatoryRunStart: (TaskEntity) -> Unit = { },
    onStartMandatoryTask: (TaskEntity) -> Unit = { },
    onStartAiTask: (AiDailyTask) -> Unit = { }
) {
    val doneAiTaskNames = remember(history) {
        history
            .filter { it.isAIGenerated && it.aiMainName != "WEEKLY_SUMMARY" }
            .mapNotNull { it.aiMainName }
            .toSet()
    }

    val aiDoneCount = remember(history) {
        history.count { it.isAIGenerated && it.completed && it.aiMainName != "WEEKLY_SUMMARY" }
    }
    val nonAiDoneCount = remember(history) {
        history.count { !it.isAIGenerated && it.completed }
    }

    val deferredRunsForDay = remember(history) {
        history.filter { !it.completed && it.note?.contains("(odroczone)") == true }
    }

    val historyEntries = remember(history) {
        history.filter { entry ->
            !(entry.note?.contains("(odroczone)") == true && !entry.completed)
        }
    }

    val mandatoryStepsIds = remember(mandatoryTasksForDay) {
        mandatoryTasksForDay.filter { it.taskType == "STEPS" }.map { it.id }.toSet()
    }

    val stepsTasksForDay = remember(stepGoalsMap, isToday, mandatoryStepsIds) {
        if (!isToday) emptyList()
        else stepGoalsMap.entries.filter { it.key in mandatoryStepsIds || history.any { h -> h.taskId == it.key } }
    }

    // FIXME: poprzednio filtrowano !isAIGenerated — przez co mandatory+AI taski
    // nie były usuwane z planu dnia po wykonaniu. Teraz liczymy wszystkie ukończone.
    val doneMandatoryTaskIds = remember(history) {
        history.filter { it.completed }.map { it.taskId }.toSet()
    }
    val pendingMandatoryForDay = remember(mandatoryTasksForDay, doneMandatoryTaskIds, isToday) {
        mandatoryTasksForDay
            .filter { it.id !in doneMandatoryTaskIds }
            .filter { if (isToday) it.taskType != "STEPS" else true }
    }

    val unfinishedPlanItems: List<Triple<String, String, String>> =
        remember(isPast, mandatoryTasksForDay, doneMandatoryTaskIds, aiTasks, doneAiTaskNames) {
            if (!isPast) emptyList()
            else {
                val unfMandatory = mandatoryTasksForDay
                    .filter { it.id !in doneMandatoryTaskIds }
                    .map { task -> Triple(task.taskName, task.category, "mandatory") }
                val unfAi = aiTasks
                    .filter { !doneAiTaskNames.contains(it.mainName) }
                    .map { ai -> Triple(ai.description, ai.mainName, "ai") }
                unfMandatory + unfAi
            }
        }

    val planSize = remember(stepsTasksForDay, deferredRunsForDay, aiTasks, pendingMandatoryForDay) {
        stepsTasksForDay.size + deferredRunsForDay.size + aiTasks.size + pendingMandatoryForDay.size
    }
    // Cel dnia = liczba zadań w planie: AI + obowiązkowe na ten dzień + odroczone (np. bieg).
    // Mandatoryjne zadania krokowe są już w mandatoryTasksForDay, więc liczymy tylko te
    // krokowe, których tam NIE ma (wcześniej całe stepsTasksForDay liczyło się podwójnie).
    // mandatoryTasksForDay jest zdeduplikowane po id na poziomie providera.
    val extraStepsCount = remember(stepsTasksForDay, mandatoryStepsIds) {
        stepsTasksForDay.count { it.key !in mandatoryStepsIds }
    }
    val totalPlanSize = remember(deferredRunsForDay, aiTasks, mandatoryTasksForDay, extraStepsCount) {
        aiTasks.size + mandatoryTasksForDay.size + deferredRunsForDay.size + extraStepsCount
    }
    val hasPlan = planSize > 0 || mandatoryTasksForDay.isNotEmpty()

    val dailyGoal = when {
        totalPlanSize > 0 -> totalPlanSize
        else -> (allDailyTasksCount / 2).coerceAtLeast(1)
    }
    // Liczymy wszystkie unikalne ukończone zadania (AI + non-AI + mandatory+AI)
    val allCompletedCount = remember(history) {
        history.count { it.completed && it.aiMainName != "WEEKLY_SUMMARY" }
    }
    val progressCount = if (totalPlanSize > 0) {
        allCompletedCount
    } else {
        nonAiDoneCount
    }
    val progress = if (dailyGoal > 0) progressCount.toFloat() / dailyGoal else 0f

    val assistantText = when {
        dailyGoal == 0 -> "Zaplanuj tydzien z asystentem, zeby zobaczyc tu zadania!"
        progress > 1f -> "Wybitna robota! Przebiles dzienny cel - to rzadkosc. Pamietaj, zeby tez zadbac o regeneracje, bo ciaglosc to klucz."
        progress >= 0.75f -> "Prawie gotowe! Jeszcze jeden krok i dzien bedzie perfekcyjny."
        progress >= 0.5f -> "Dobry postep - jestes juz za polowa. Co mozesz zrobic, zeby zamknac go w 100%?"
        progress >= 0.25f -> "Niezly start! Kazdy krok sie liczy."
        else -> "Zaczynamy! Nawet jedno zadanie do przodu to sukces."
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Asystent
        item {
            AssistantSpeechBubble(
                painter = painterResource(R.drawable.assistant_day_details),
                speechText = assistantText
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            AnimatedProgressBar(progress = progress)
            Spacer(Modifier.height(4.dp))
            if (dailyGoal > 0) {
                Text(
                    "$progressCount / $dailyGoal zadan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Plan dnia ──────────────────────────────────────────────────
        if (!isPast && hasPlan) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Plan dnia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // 1. Kroki
            items(stepsTasksForDay) { (taskId, goal) ->
                val taskName = history.firstOrNull { it.taskId == taskId }?.taskName
                    ?: "Zadanie krokow #$taskId"
                val stepsDone = history.firstOrNull { it.taskId == taskId && it.completed }
                    ?.stepsCompleted
                val isCompleted = stepsDone != null

                StepsProgressCard(
                    taskName = taskName,
                    currentSteps = if (isCompleted) stepsDone!! else todaySteps,
                    goalSteps = goal,
                    isCompleted = isCompleted
                )
            }

            // 2. Odroczone biegi
            items(deferredRunsForDay) { entry ->
                val distanceKm = entry.note?.let {
                    Regex("Dystans: ([\\d.]+) km").find(it)?.groupValues?.get(1)?.toFloatOrNull()
                } ?: 0f
                val targetKm = entry.note?.let {
                    Regex("Cel: ([\\d.]+) km").find(it)?.groupValues?.get(1)?.toFloatOrNull()
                } ?: 0f
                val remainingKm = if (targetKm > 0f) (targetKm - distanceKm).coerceAtLeast(0f) else 0f
                val prog = if (targetKm > 0f) (distanceKm / targetKm).coerceIn(0f, 1f) else 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            entry.taskName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Wykonano: %.2f km".format(distanceKm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (targetKm > 0f) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { prog },
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Pozostalo: %.2f km".format(remainingKm),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onResumeRun(entry, remainingKm) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isToday
                        ) {
                            Text(
                                if (remainingKm > 0f)
                                    "Dokoncz (zostalo %.2f km)".format(remainingKm)
                                else
                                    "Dokoncz"
                            )
                        }
                    }
                }
            }

            // 3. Zadania AI
            itemsIndexed(aiTasks) { index, task ->
                val isDone = doneAiTaskNames.contains(task.mainName)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Zadanie ${index + 1}: ${task.mainName}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Wykonano",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                        if (!isDone) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${task.durationMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onStartAiTask(task) },
                                enabled = isToday
                            ) {
                                Text("Wykonaj")
                            }
                        }
                    }
                }
            }

            // 4. Mandatory pending
            items(pendingMandatoryForDay) { task ->
                val isRunningTaskCheck = task.taskName.contains("bieganie", ignoreCase = true)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    task.taskName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    task.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (isRunningTaskCheck) onMandatoryRunStart(task)
                                else if (task.taskType == "STEPS") onMandatoryTaskDone(task)
                                else onStartMandatoryTask(task)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isToday
                        ) {
                            Text(
                                when {
                                    isRunningTaskCheck -> "Rozpocznij bieg"
                                    task.taskType == "STEPS" -> "Wykonaj"
                                    else -> "Wykonaj"
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Historia ──────────────────────────────────────────────────
        if (historyEntries.isNotEmpty() || unfinishedPlanItems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Historia dnia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(unfinishedPlanItems) { (name, category, _) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Niewykonane",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(historyEntries) { entry ->
                val goal = stepGoalsMap[entry.taskId] ?: 0
                val isStepsTask = goal > 0
                val stepsCompleted = entry.stepsCompleted

                // Rozróżnienie: odroczone/niedokończone vs. niewykonane
                val isDeferred = entry.note?.contains("(odroczone)") == true
                val isIncomplete = !entry.completed && !isDeferred
                val cardColor = when {
                    isDeferred -> MaterialTheme.colorScheme.tertiaryContainer
                    !entry.completed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val labelColor = when {
                    isDeferred -> MaterialTheme.colorScheme.tertiary
                    !entry.completed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            entry.taskName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        if (!entry.completed && isStepsTask) {
                            Text(
                                "Nieosiagniete - ${stepsCompleted ?: 0}/$goal krokow",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = labelColor
                            )
                        } else if (isDeferred) {
                            Text(
                                "Odroczone",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = labelColor
                            )
                        } else if (isIncomplete) {
                            Text(
                                "Niedokonczone",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = labelColor
                            )
                        } else if (!entry.completed) {
                            Text(
                                "Niewykonane",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = labelColor
                            )
                        }

                        Text(
                            "Ocena: ${entry.rating ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isStepsTask) {
                            val steps = stepsCompleted ?: 0L
                            val prog = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
                            val animProg by animateFloatAsState(prog, tween(500), label = "histSteps")
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { animProg },
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                            Text(
                                "$steps / $goal krokow (${(prog * 100).toInt()}%)" +
                                if (stepsCompleted == null) " - brak zapisu" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!entry.note.isNullOrBlank()) {
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Karta krokow — zywe dane lub ukonczone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepsProgressCard(
    taskName: String,
    currentSteps: Long,
    goalSteps: Int,
    isCompleted: Boolean
) {
    val prog = if (goalSteps > 0) (currentSteps.toFloat() / goalSteps).coerceIn(0f, 1f) else 0f
    val animProg by animateFloatAsState(prog, tween(600), label = "daySteps")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(taskName, style = MaterialTheme.typography.titleSmall)
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animProg },
                modifier = Modifier.fillMaxWidth().height(10.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$currentSteps / $goalSteps krokow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(animProg * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AnimatedProgressBar
// ─────────────────────────────────────────────────────────────────────────────

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

    val infiniteTransition = rememberInfiniteTransition(label = "progressBar")

    val offset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing)),
        label = "barOffset"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "barPulse"
    )

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10000f,
        animationSpec = infiniteRepeatable(animation = tween(200000, easing = LinearEasing)),
        label = "barTime"
    )

    val flames = rememberFlames(28)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
    ) {
        val startColor = when {
            baseProgress < 0.5f -> {
                val t = baseProgress / 0.5f
                Color(red = 1f, green = t * 0.5f, blue = 0f)
            }
            else -> {
                val t = (baseProgress - 0.5f) / 0.5f
                Color(red = 1f - t, green = 0.5f + t * 0.5f, blue = 0f)
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
                    .graphicsLayer { alpha = pulse; scaleY = 1.1f }
                    .background(goldBrush)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(overProgress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent))
                    )
            )

            val waveOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                label = "barWave"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(overProgress)
                    .fillMaxHeight()
                    .graphicsLayer { translationY = waveOffset }
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)))
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = size.width * overProgress
                val startX = barWidth * 0.1f
                val range = barWidth - startX
                if (range <= 0f) return@Canvas
                flames.forEach { p ->
                    val speed = (0.015f + p.speed * 0.01f) * 0.7f
                    val particleProgress = ((time * speed + p.seed) % 1f)
                    val x = startX + particleProgress * range
                    val baseY = size.height * (0.3f + p.startY * 0.4f)
                    val y = baseY + sin(time + p.seed * 5f) * 1f
                    val scale = 0.5f + particleProgress * 0.8f
                    val alpha = 1f - particleProgress
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
