package com.MateuszW.aigrowthtracker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.AiDailyTask
import com.MateuszW.aigrowthtracker.data.TaskEntity
import com.MateuszW.aigrowthtracker.data.TaskHistoryEntity
import com.MateuszW.aigrowthtracker.data.WeeklyTaskEntity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

// ─────────────────────────────────────────────────────────────────────────────
// HoldButton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExecuteTaskScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecuteTaskScreen(
    tasks: List<TaskEntity>,
    weeklyTasks: List<WeeklyTaskEntity>,
    weekPlanned: Boolean,
    activeTask: TaskEntity?,
    remainingTime: Int,
    weeklySummary: String?,
    weeklyQuote: String?,
    // ── Kroki i "zadania w toku" ──
    todaySteps: Long,
    stepsBaselines: Map<Int, Long>,
    activeTodayTasks: List<TaskEntity>,
    aiTasksForToday: List<AiDailyTask>,
    todayHistory: List<TaskHistoryEntity>,
    hcPermissionNeeded: Boolean,
    hcNoDataSource: Boolean,
    hcRefreshing: Boolean,
    hcDiagnostics: HcDiagnostics?,
    // ── Callbacki ──
    onStartTask: (TaskEntity, android.content.Context) -> Unit,
    onFinishTask: () -> Unit,
    onCancelTask: () -> Unit,
    onBack: () -> Unit,
    onFinishTaskWithSummary: (Int, String) -> Unit,
    onIncrementWeekly: (Int) -> Unit,
    onDecrementWeekly: (Int) -> Unit,
    onPlanWeek: () -> Unit,
    onSetStepsBaseline: (Int) -> Unit,
    onStartStepsTask: (TaskEntity, String) -> Boolean,
    stepsCountingDontAsk: Boolean,
    stepsCountingMode: String,
    onSaveStepsCountingPrefs: (mode: String, dontAskAgain: Boolean) -> Unit,
    onRequestHcPermission: () -> Unit,
    onRefreshSteps: () -> Unit,
    // Osobny callback dla zadań STEPS — finishTaskWithSummary nie działa bo _activeTask=null
    onFinishStepsTask: (task: TaskEntity, stepsCompleted: Long, rating: Int, note: String) -> Unit,
    onAiTaskDone: (TaskHistoryEntity) -> Unit,
    onResumeRun: (entry: TaskHistoryEntity, remainingKm: Float) -> Unit = { _, _ -> },
    // ── AI Support ──
    plannedWeekStartMillis: Long? = null,
    aiTaskInstruction: AiTaskInstruction? = null,
    aiInstructionLoading: Boolean = false,
    aiInstructionLoadingTaskId: Int? = null,
    onRequestAiInstruction: (TaskEntity) -> Unit = {},
    onClearAiInstruction: () -> Unit = {},
    onStartWithAiTimer: (TaskEntity, Int) -> Unit = { _, _ -> }
) {
    var selectedCategoryForRoll by remember { mutableStateOf("Wszystkie") }
    val categories = listOf("Wszystkie") + tasks.map { it.category }.distinct()
    var rolledTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showRollDialog by remember { mutableStateOf(false) }

    // Dialog oceny – dla zadań timerowych
    var showTimerSummaryDialog by remember { mutableStateOf(false) }
    // Dialog oceny – dla zadań STEPS (ukończenie ręczne)
    var showStepsSummaryDialog by remember { mutableStateOf(false) }
    var summaryTargetTask by remember { mutableStateOf<TaskEntity?>(null) }

    // Dialog wyboru trybu krokomierza
    var showStepsModeDialog by remember { mutableStateOf(false) }
    var stepsModeTargetTask by remember { mutableStateOf<TaskEntity?>(null) }
    // Snackbar "od razu zaliczone"
    var showInstantCompletionSnack by remember { mutableStateOf(false) }

    var rating by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    val context = LocalContext.current

    // LOG #2 — wartość kroków docierająca do ekranu
    LaunchedEffect(todaySteps) {
        Log.d("Steps", "[ExecuteTaskScreen] todaySteps=$todaySteps stepsBaselines=$stepsBaselines")
    }

    // Zadania STEPS uruchomione ręcznie przez użytkownika (Room lub in-memory baseline)
    val manuallyStartedStepsTasks = remember(stepsBaselines, tasks) {
        tasks.filter { it.taskType == "STEPS" &&
            (it.stepsBaseline != null || stepsBaselines.containsKey(it.id)) }
    }

    // Odroczone biegi z dzisiejszej historii (completed=false z notatką "(odroczone)")
    val deferredRunsToday = remember(todayHistory) {
        todayHistory.filter { !it.completed && it.note?.contains("(odroczone)") == true }
    }

    // Tylko zadania STEPS w sekcji "Zadania w toku"
    val stepsActiveTodayTasks = remember(activeTodayTasks) {
        activeTodayTasks.filter { it.taskType == "STEPS" }
    }
    val stepsAiTasksForToday = remember(aiTasksForToday, tasks) {
        aiTasksForToday.filter { aiTask ->
            val matched = tasks.firstOrNull {
                it.taskName == aiTask.mainName || it.category == aiTask.mainName
            }
            matched?.taskType == "STEPS"
        }
    }

    val hasInProgress = stepsActiveTodayTasks.isNotEmpty()
            || stepsAiTasksForToday.isNotEmpty()
            || manuallyStartedStepsTasks.isNotEmpty()
            || deferredRunsToday.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wykonaj zadanie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrot")
                    }
                }
            )
        },
        bottomBar = {
            activeTask?.let { task ->
                Surface(shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Aktywne: ${task.taskName}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        val minutes = remainingTime / 60
                        val seconds = remainingTime % 60
                        Text(
                            "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showTimerSummaryDialog = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Zrobione") }
                            OutlinedButton(
                                onClick = onCancelTask,
                                modifier = Modifier.weight(1f)
                            ) { Text("Anuluj") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Cytat asystenta
            if (!weeklyQuote.isNullOrBlank()) {
                AssistantSpeechBubble(
                    painter = painterResource(R.drawable.assistant_execute),
                    speechText = weeklyQuote
                )
            }

            // Przycisk planowania
            if (!weekPlanned) {
                Button(onClick = onPlanWeek, modifier = Modifier.fillMaxWidth()) {
                    Text("Pozwól asystentowi zaplanować tydzień")
                }
            }

            // ── Baner uprawnień Health Connect ────────────────────────────
            if (hcPermissionNeeded) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Brak dostępu do kroków",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Zezwól na odczyt kroków z Health Connect",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = onRequestHcPermission) { Text("Zezwól") }
                    }
                }
            }

            // ── Baner: uprawnienie OK, ale HC nie ma źródła danych ────────
            if (!hcPermissionNeeded && hcNoDataSource) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Brak danych kroków z HC",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Podłącz aplikację fitness (np. Google Fit) do Health Connect, żeby śledzenie kroków działało.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (hcRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            OutlinedButton(onClick = onRefreshSteps) { Text("Odśwież") }
                        }
                    }
                }
            }

            // ── Sekcja "Zadania w toku" ───────────────────────────────────
            if (hasInProgress) {
                Text("Zadania w toku", style = MaterialTheme.typography.titleMedium)

                // ── Pasek kroków + przycisk odświeżania (w sekcji zadań) ──
                if (!hcPermissionNeeded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hcRefreshing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Czytam kroki…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                "Krokow dzis: $todaySteps",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onRefreshSteps,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Odswiez", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 0. Odroczone biegi (dzisiejsze, do wznowienia)
                deferredRunsToday.forEach { entry ->
                    val distanceKm = entry.note?.let {
                        Regex("Dystans: ([\\d.]+) km").find(it)?.groupValues?.get(1)?.toFloatOrNull()
                    } ?: 0f
                    val targetKm = entry.note?.let {
                        Regex("Cel: ([\\d.]+) km").find(it)?.groupValues?.get(1)?.toFloatOrNull()
                    } ?: 0f
                    val remainingKm = if (targetKm > 0f) (targetKm - distanceKm).coerceAtLeast(0f) else 0f
                    val progress = if (targetKm > 0f) (distanceKm / targetKm).coerceIn(0f, 1f) else 0f

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(entry.taskName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Wykonano: %.2f km".format(distanceKm),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (targetKm > 0f) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Pozostało: %.2f km".format(remainingKm),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onResumeRun(entry, remainingKm) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (remainingKm > 0f)
                                        "Wznów bieg (zostało %.2f km)".format(remainingKm)
                                    else
                                        "Wznów bieg"
                                )
                            }
                        }
                    }
                }

                // 1. Zadania obowiązkowe aktywne dziś (tylko STEPS)
                stepsActiveTodayTasks.forEach { task ->
                    val isSteps = task.taskType == "STEPS"
                    val goal = task.stepGoal?.toFloat() ?: 1f
                    val completed = todayHistory.any { it.taskId == task.id && it.completed }
                    val stepsStarted = isSteps && task.stepsBaseline != null

                    if (isSteps && !stepsStarted && !completed) {
                        // STEPS task nie uruchomione jeszcze → karta z przyciskiem "Rozpocznij"
                        InProgressCard(
                            title = task.taskName,
                            subtitle = "Kroków dziś: $todaySteps",
                            progress = 0f,
                            progressLabel = "Cel: ${task.stepGoal ?: 0} kroków",
                            isCompleted = false,
                            onExecute = {
                                if (stepsCountingDontAsk) {
                                    val instant = onStartStepsTask(task, stepsCountingMode)
                                    if (instant) showInstantCompletionSnack = true
                                } else {
                                    stepsModeTargetTask = task
                                    showStepsModeDialog = true
                                }
                            },
                            onDone = null
                        )
                    } else {
                        // Zadanie STEPS uruchomione lub zadanie nie-STEPS
                        val baseline = task.stepsBaseline ?: 0L
                        val isMidnight = !isSteps || task.stepsCountingMode == "FROM_MIDNIGHT" || baseline == 0L
                        val rawProgress = when {
                            isSteps && isMidnight -> if (goal > 0f) todaySteps.toFloat() / goal else 0f
                            isSteps -> if (goal > 0f) (todaySteps - baseline).coerceAtLeast(0L).toFloat() / goal else 0f
                            completed -> 1f
                            else -> 0f
                        }
                        val effectiveSteps = if (isSteps) {
                            if (isMidnight) todaySteps else (todaySteps - baseline).coerceAtLeast(0L)
                        } else 0L
                        val modeLabel = if (isSteps) {
                            if (isMidnight) "od północy" else "od startu ($baseline kr.)"
                        } else null

                        InProgressCard(
                            title = task.taskName,
                            subtitle = modeLabel,
                            progress = rawProgress,
                            progressLabel = if (isSteps)
                                "$effectiveSteps / ${task.stepGoal ?: 0} kroków"
                            else
                                if (completed) "Ukończono" else "Nie wykonano jeszcze",
                            isCompleted = completed,
                            onExecute = if (!isSteps && !completed) {
                                { onStartTask(task, context) }
                            } else null,
                            onDone = if (isSteps && !completed) {
                                {
                                    summaryTargetTask = task
                                    showStepsSummaryDialog = true
                                }
                            } else null
                        )
                    }
                }

                // 2. Zadania AI zaplanowane na dziś (tylko STEPS)
                stepsAiTasksForToday.forEach { aiTask ->
                    val matchingTask = tasks.firstOrNull {
                        it.taskName == aiTask.mainName || it.category == aiTask.mainName
                    }
                    val isSteps = matchingTask?.taskType == "STEPS"
                    val aiGoal = aiTask.targetValue ?: matchingTask?.stepGoal?.toFloat() ?: 1f
                    val isDone = todayHistory.any {
                        it.isAIGenerated && it.aiMainName == aiTask.mainName && it.completed
                    }
                    val rawProgress = when {
                        isSteps -> if (aiGoal > 0f) todaySteps.toFloat() / aiGoal else 0f
                        isDone -> 1f
                        else -> 0f
                    }

                    InProgressCard(
                        title = aiTask.mainName,
                        subtitle = aiTask.description,
                        progress = rawProgress,
                        progressLabel = when {
                            isSteps -> "$todaySteps / ${aiGoal.toInt()} kroków"
                            isDone -> "Ukończono ✓"
                            else -> "Nie wykonano jeszcze"
                        },
                        isCompleted = isDone,
                        onExecute = if (!isSteps && !isDone) {
                            {
                                onAiTaskDone(
                                    TaskHistoryEntity(
                                        taskId = -999,
                                        taskName = aiTask.description,
                                        category = aiTask.mainName,
                                        aiMainName = aiTask.mainName,
                                        isAIGenerated = true,
                                        date = System.currentTimeMillis(),
                                        completed = true
                                    )
                                )
                            }
                        } else null,
                        onDone = null
                    )
                }

                // 3. Zadania STEPS ręcznie uruchomione (nie zaplanowane na dziś)
                manuallyStartedStepsTasks.forEach { task ->
                    val baseline = task.stepsBaseline ?: stepsBaselines[task.id] ?: 0L
                    val isMidnight = task.stepsCountingMode == "FROM_MIDNIGHT" || baseline == 0L
                    val goal = task.stepGoal?.toFloat() ?: 1f
                    val effectiveSteps = if (isMidnight) todaySteps else (todaySteps - baseline).coerceAtLeast(0L)
                    val rawProgress = if (goal > 0f) effectiveSteps.toFloat() / goal else 0f
                    val completed = todayHistory.any { it.taskId == task.id && it.completed }

                    val modeLabel = if (isMidnight) "od północy" else "od startu ($baseline kr.)"
                    InProgressCard(
                        title = task.taskName,
                        subtitle = "Tryb: $modeLabel",
                        progress = rawProgress,
                        progressLabel = "$effectiveSteps / ${task.stepGoal ?: 0} kroków",
                        isCompleted = completed,
                        onExecute = null,
                        onDone = if (!completed) {
                            {
                                summaryTargetTask = task
                                showStepsSummaryDialog = true
                            }
                        } else null
                    )
                }

                HorizontalDivider()
            }

            // ── Zadania tygodniowe (rozwijana sekcja) ─────────────────────
            val isInPlannedWeek = remember(plannedWeekStartMillis) {
                val now = System.currentTimeMillis()
                plannedWeekStartMillis != null &&
                    now >= plannedWeekStartMillis &&
                    now < plannedWeekStartMillis + 7L * 24 * 60 * 60 * 1000
            }
            if (weeklyTasks.isNotEmpty() && isInPlannedWeek) {
                var weeklyExpanded by remember { mutableStateOf(false) }
                val doneCount = weeklyTasks.count { it.currentValue >= it.targetValue }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { weeklyExpanded = !weeklyExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Zadania tygodniowe ($doneCount/${weeklyTasks.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = if (weeklyExpanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (weeklyExpanded) "Zwiń" else "Rozwiń"
                    )
                }

                AnimatedVisibility(
                    visible = weeklyExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        weeklyTasks.forEach { task ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(task.name, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    val progress = (task.currentValue / task.targetValue).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp)
                                    )
                                    Text(
                                        "${task.currentValue.toInt()} / ${task.targetValue.toInt()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HoldButton("+", onClick = { onIncrementWeekly(task.id) })
                                        HoldButton("-", onClick = { onDecrementWeekly(task.id) }, outlined = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Losowanie ─────────────────────────────────────────────────
            Text("Losuj zadanie", style = MaterialTheme.typography.titleMedium)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedCategoryForRoll,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategoryForRoll = cat; expanded = false })
                    }
                }
            }
            Button(
                onClick = {
                    val filtered = tasks.filter {
                        !it.isCompleted && (selectedCategoryForRoll == "Wszystkie" || it.category == selectedCategoryForRoll)
                    }
                    if (filtered.isNotEmpty()) { rolledTask = filtered.random(); showRollDialog = true }
                },
                enabled = activeTask == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("LOSUJ") }

            rolledTask?.let {
                Text("Wylosowano: ${it.taskName}", color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider()

            // ── Wszystkie zadania ─────────────────────────────────────────
            Text("Wszystkie zadania", style = MaterialTheme.typography.titleMedium)
            tasks.forEach { task ->
                val isSteps = task.taskType == "STEPS"
                val isAlreadyActive = activeTodayTasks.any { it.id == task.id }
                val hasBaseline = stepsBaselines.containsKey(task.id)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.taskName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    task.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSteps) {
                                val isStarted = hasBaseline || task.stepsBaseline != null
                                val completed = todayHistory.any { it.taskId == task.id && it.completed }
                                Log.d("StepsDialog", "[ALL] task=${task.taskName} isStarted=$isStarted completed=$completed hasBaseline=$hasBaseline stepsBaseline=${task.stepsBaseline} dontAsk=$stepsCountingDontAsk")
                                if (!isStarted && !completed) {
                                    Button(
                                        onClick = {
                                            Log.d("StepsDialog", "[CLICK] task=${task.taskName} dontAsk=$stepsCountingDontAsk mode=$stepsCountingMode")
                                            if (stepsCountingDontAsk) {
                                                val instant = onStartStepsTask(task, stepsCountingMode)
                                                if (instant) showInstantCompletionSnack = true
                                            } else {
                                                stepsModeTargetTask = task
                                                showStepsModeDialog = true
                                                Log.d("StepsDialog", "[CLICK] showStepsModeDialog=true target=${task.taskName}")
                                            }
                                        },
                                        enabled = activeTask == null
                                    ) { Text("Wykonaj") }
                                } else {
                                    Text(
                                        "$todaySteps kr.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (task.isAIGenerated) {
                                        val isThisTaskLoading = aiInstructionLoading && aiInstructionLoadingTaskId == task.id
                                        OutlinedButton(
                                            onClick = { onRequestAiInstruction(task) },
                                            enabled = activeTask == null && !aiInstructionLoading,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            if (isThisTaskLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text("AI", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = { onStartTask(task, context) },
                                        enabled = activeTask == null
                                    ) { Text("Start") }
                                }
                            }
                        }

                        // AI Instruction card
                        if (task.isAIGenerated && aiTaskInstruction != null && aiTaskInstruction.taskId == task.id) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    AssistantSpeechBubble(
                                        painter = painterResource(R.drawable.assistant_execute),
                                        speechText = aiTaskInstruction.instructions,
                                        avatarSize = 64.dp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "Sugerowany czas: ${aiTaskInstruction.suggestedMinutes} min",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onStartWithAiTimer(task, aiTaskInstruction.suggestedMinutes)
                                                onClearAiInstruction()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Rozpocznij (${aiTaskInstruction.suggestedMinutes} min)")
                                        }
                                        OutlinedButton(
                                            onClick = onClearAiInstruction
                                        ) {
                                            Text("Zamknij")
                                        }
                                    }
                                }
                            }
                        }

                        // Progress bar dla zadań STEPS — renderuje się zawsze gdy stepGoal > 0
                        if (isSteps) {
                            val stepGoal = task.stepGoal ?: 0
                            val baseline = task.stepsBaseline ?: stepsBaselines[task.id] ?: 0L
                            val current = if (task.stepsCountingMode == "FROM_MIDNIGHT" || baseline == 0L) {
                                todaySteps
                            } else {
                                (todaySteps - baseline).coerceAtLeast(0L)
                            }
                            // LOG #3 — wyliczenie progress w liście zadań
                            LaunchedEffect(todaySteps, baseline) {
                                Log.d(
                                    "Steps",
                                    "[STEPS list] task=${task.taskName} " +
                                    "todaySteps=$todaySteps baseline=$baseline " +
                                    "current=$current goal=$stepGoal"
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            if (stepGoal > 0) {
                                val prog = (current.toFloat() / stepGoal).coerceIn(0f, 1f)
                                val animProg by animateFloatAsState(prog, tween(500), label = "steps")
                                LinearProgressIndicator(
                                    progress = { animProg },
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                Text(
                                    "$current / $stepGoal kroków (${(prog * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "Brak celu kroków — edytuj zadanie",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogi ──────────────────────────────────────────────────────────────

    if (showRollDialog && rolledTask != null) {
        AlertDialog(
            onDismissRequest = { showRollDialog = false },
            title = { Text("Wylosowano zadanie") },
            text = { Text("${rolledTask!!.taskName} (${rolledTask!!.category})") },
            confirmButton = {
                Button(onClick = { onStartTask(rolledTask!!, context); showRollDialog = false }) { Text("Rozpocznij") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRollDialog = false }) { Text("Anuluj") }
            }
        )
    }

    // Dialog po zakończeniu zadania timerowego
    if (showTimerSummaryDialog && activeTask != null) {
        SummaryDialog(
            isAiTask = activeTask.isAIGenerated,
            onConfirm = { r, n ->
                onFinishTaskWithSummary(r, n)
                showTimerSummaryDialog = false
                rating = 0; note = ""
            },
            onDismiss = { showTimerSummaryDialog = false },
            rating = rating,
            note = note,
            onRatingChange = { rating = it },
            onNoteChange = { note = it }
        )
    }

    // Dialog po ręcznym ukończeniu zadania STEPS
    if (showStepsSummaryDialog && summaryTargetTask != null) {
        val task = summaryTargetTask!!
        SummaryDialog(
            isAiTask = false,
            onConfirm = { r, n ->
                val baseline = task.stepsBaseline ?: stepsBaselines[task.id] ?: 0L
                val isMidnight = task.stepsCountingMode == "FROM_MIDNIGHT" || baseline == 0L
                val stepsCount = if (isMidnight) todaySteps else (todaySteps - baseline).coerceAtLeast(0L)
                // LOG #3 — wyliczenie stepsCount przy ukończeniu
                Log.d(
                    "Steps",
                    "[STEPS dialog confirm] task=${task.taskName} " +
                    "todaySteps=$todaySteps baseline=$baseline stepsCount=$stepsCount rating=$r"
                )
                // POPRAWKA: onFinishStepsTask zamiast onFinishTaskWithSummary
                // (onFinishTaskWithSummary używa _activeTask który = null dla STEPS)
                onFinishStepsTask(task, stepsCount, r, n)
                showStepsSummaryDialog = false
                summaryTargetTask = null
                rating = 0; note = ""
            },
            onDismiss = { showStepsSummaryDialog = false },
            rating = rating,
            note = note,
            onRatingChange = { rating = it },
            onNoteChange = { note = it }
        )
    }

    // Dialog wyboru trybu krokomierza
    Log.d("StepsDialog", "[RENDER] showStepsModeDialog=$showStepsModeDialog target=${stepsModeTargetTask?.taskName}")
    if (showStepsModeDialog && stepsModeTargetTask != null) {
        Log.d("StepsDialog", "[RENDER] SHOWING StepsModeDialog for ${stepsModeTargetTask!!.taskName}")
        StepsModeDialog(
            onConfirm = { mode, dontAskAgain ->
                if (dontAskAgain) {
                    onSaveStepsCountingPrefs(mode, true)
                }
                val instant = onStartStepsTask(stepsModeTargetTask!!, mode)
                if (instant) showInstantCompletionSnack = true
                showStepsModeDialog = false
                stepsModeTargetTask = null
            },
            onDismiss = {
                showStepsModeDialog = false
                stepsModeTargetTask = null
            }
        )
    }

    // Snackbar "od razu zaliczone"
    if (showInstantCompletionSnack) {
        AlertDialog(
            onDismissRequest = { showInstantCompletionSnack = false },
            title = { Text("Zadanie zaliczone!") },
            text = { Text("Cel kroków został już osiągnięty przed startem. Zadanie zapisane jako wykonane.") },
            confirmButton = {
                Button(onClick = { showInstantCompletionSnack = false }) { Text("OK") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog wyboru trybu krokomierza
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepsModeDialog(
    onConfirm: (mode: String, dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf("FROM_MIDNIGHT") }
    var dontAskAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jak liczyć kroki?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedMode == "FROM_MIDNIGHT",
                        onClick = { selectedMode = "FROM_MIDNIGHT" }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text("Licz od północy", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Uwzględnij dzisiejsze kroki",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedMode == "FROM_START",
                        onClick = { selectedMode = "FROM_START" }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text("Zacznij od zera", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Licz tylko kroki od teraz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it }
                    )
                    Text(
                        "Nie pytaj ponownie",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMode, dontAskAgain) }) { Text("Rozpocznij") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Karta zadania w toku — z widocznym progress barem
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InProgressCard(
    title: String,
    subtitle: String?,
    progress: Float,
    progressLabel: String,
    isCompleted: Boolean,
    onExecute: (() -> Unit)?,
    onDone: (() -> Unit)?
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "inProgressBar"
    )

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
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            // Wyraźny progress bar — 10 dp wysokości
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCompleted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (onExecute != null || onDone != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onExecute?.let {
                        Button(onClick = it, modifier = Modifier.weight(1f)) { Text("Wykonaj") }
                    }
                    onDone?.let {
                        OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("Ukończ") }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog oceny (wspólny dla timerowych i STEPS)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryDialog(
    isAiTask: Boolean,
    rating: Int,
    note: String,
    onRatingChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Podsumuj zadanie") },
        text = {
            Column {
                Text("Ocena:")
                Row {
                    (1..5).forEach { star ->
                        IconButton(onClick = { onRatingChange(star) }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (star <= rating)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Notatka (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rating, note) },
                enabled = rating > 0 && (!isAiTask || note.isNotBlank())
            ) { Text("Zapisz") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HcDebugCard — pełna diagnostyka Health Connect
// Usuń lub ukryj po zidentyfikowaniu problemu z krokami.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HcDebugCard(diag: HcDiagnostics?) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔍 HC Diagnostics",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (expanded) "Zwiń" else "Rozwiń",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (diag == null) {
                Text(
                    "Brak danych — refreshTodaySteps jeszcze nie wywołano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // Skrót zawsze widoczny
                val sdkLabel = when (diag.sdkStatus) {
                    3 -> "AVAILABLE (3)"
                    2 -> "UPDATE_REQUIRED (2)"
                    1 -> "UNAVAILABLE (1)"
                    -1 -> "NOT_CALLED (-1)"
                    else -> "UNKNOWN (${diag.sdkStatus})"
                }
                val stepsLabel = when {
                    diag.exception != null -> "BŁĄD: ${diag.exception}"
                    !diag.permissionGranted -> "BRAK UPRAWNIENIA"
                    diag.aggregateRawValue == null -> "null (brak źródła danych)"
                    else -> "${diag.aggregateRawValue} kroków"
                }
                Text(
                    "SDK: $sdkLabel  |  Permission: ${diag.permissionGranted}  |  Steps: $stepsLabel  |  _todaySteps: ${diag.todayStepsAfterUpdate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diag.exception != null || !diag.permissionGranted || diag.aggregateRawValue == null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    HcDebugRow("readPermRequired", diag.readPermRequired.ifEmpty { "—" })
                    HcDebugRow("grantedPermissions", diag.grantedPermissions.joinToString("\n").ifEmpty { "(puste)" })
                    HcDebugRow("zoneId", diag.zoneId.ifEmpty { "—" })
                    HcDebugRow("startOfDay", diag.startOfDay.ifEmpty { "—" })
                    HcDebugRow("endTime", diag.endTime.ifEmpty { "—" })
                    HcDebugRow("aggregate result", if (diag.aggregateRawValue != null) "${diag.aggregateRawValue}" else "null")
                    HcDebugRow("_todaySteps after update", "${diag.todayStepsAfterUpdate}")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "readRecords (gdy aggregate=null/0):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    HcDebugRow("records count", "${diag.recordsCount}")
                    HcDebugRow("first record", diag.firstRecord ?: "—")
                    HcDebugRow("last record", diag.lastRecord ?: "—")
                    if (diag.recordsCount == 0 && diag.aggregateRawValue == null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⚠ Brak danych kroków w Health Connect.\nPołącz Google Fit / Fitbit / Samsung Health z aplikacją Health Connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (diag.exception != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Exception: ${diag.exception}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HcDebugRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 160.dp)
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}
