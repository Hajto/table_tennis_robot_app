package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.BallSettingsDropdowns
import com.tablebot.ui.components.rememberMotorConstraints
import com.tablebot.ui.components.MAX_BALLS_PER_CELL
import com.tablebot.ui.components.StepSlider
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallNumbers

// ── State holder ────────────────────────────────────────────────────────

class DrillEditorState(
    initial: BasicTraining?,
    var id: Int,
) {
    var name by mutableStateOf(initial?.name ?: "")
    var ball by mutableIntStateOf(initial?.ball ?: 1)
    var spin by mutableIntStateOf(initial?.spin ?: 2)
    var power by mutableIntStateOf(initial?.power ?: 2)
    var ballTime by mutableIntStateOf(initial?.ballTime ?: 9)
    var times by mutableIntStateOf(initial?.times ?: 20)
    var landType by mutableIntStateOf(initial?.landType ?: 0)
    var points by mutableStateOf(initial?.points ?: listOf(Point(8, 2)))
    var adjustSpin by mutableIntStateOf(initial?.adjustSpin ?: 0)
    var adjustPosition by mutableIntStateOf(initial?.adjustPosition ?: 0)
    var isFavourite by mutableIntStateOf(initial?.isFavourite ?: 0)
    var skillLevel: SkillLevel = initial?.skillLevel ?: SkillLevel()
    var tags by mutableStateOf(initial?.tags ?: emptyList())

    fun loadFrom(training: BasicTraining) {
        id = training.id
        name = training.name
        ball = training.ball
        spin = training.spin
        power = training.power
        ballTime = training.ballTime
        times = training.times
        landType = training.landType
        points = training.points
        adjustSpin = training.adjustSpin
        adjustPosition = training.adjustPosition
        isFavourite = training.isFavourite
        skillLevel = training.skillLevel
        tags = training.tags
    }

    fun toTraining(): BasicTraining = BasicTraining(
        id = id,
        name = name.ifBlank { "Quick Play" },
        ball = ball, spin = spin, power = power,
        ballTime = ballTime, times = times, landType = landType,
        points = points, adjustSpin = adjustSpin, adjustPosition = adjustPosition,
        isFavourite = isFavourite, skillLevel = skillLevel, tags = tags,
    )
}

@Composable
fun rememberDrillEditorState(initial: BasicTraining?, id: Int): DrillEditorState =
    remember { DrillEditorState(initial, id) }

// ── Shared editor content (stateless – driven by DrillEditorState) ──

@Composable
fun DrillEditorContent(
    state: DrillEditorState,
    motorConfig: MotorConfig?,
    showName: Boolean = true,
) {
    val constraints = rememberMotorConstraints(
        ball = state.ball, spin = state.spin, power = state.power,
        points = state.points, motorConfig = motorConfig,
        onSpinChange = { state.spin = it },
        onPowerChange = { state.power = it },
        onPointsChange = { state.points = it },
    )
    val availableSpins = constraints.validSpins
    val availablePowers = constraints.validPowers
    val enabledCells = constraints.enabledCells

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showName) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = { Text("Training Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Ball / Spin / Power
        BallSettingsDropdowns(
            ball = state.ball,
            spin = state.spin,
            power = state.power,
            onBallChange = { state.ball = it },
            onSpinChange = { state.spin = it },
            onPowerChange = { state.power = it },
            validSpins = availableSpins,
            validPowers = availablePowers,
        )

        if (enabledCells != null && enabledCells.isEmpty()) {
            Text(
                "This ball/spin/power combination is not available.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val mode = LandType.fromValue(state.landType)

        // 1. Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Target Points (${state.points.size} selected)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (mode != LandType.STATIC) {
                IconButton(
                    onClick = { state.points = state.points.dropLast(1) },
                    enabled = state.points.isNotEmpty(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                }
                IconButton(
                    onClick = { state.points = emptyList() },
                    enabled = state.points.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Refresh, "Reset")
                }
            }
        }
        val cellBallNumbers = remember(state.points) {
            buildCellBallNumbers(
                state.points.mapIndexed { i, pt -> (i + 1) to listOf(pt) }
            )
        }
        TableGrid(
            selectedPoints = state.points,
            onCellClick = { cellNum ->
                when (mode) {
                    LandType.STATIC -> {
                        val existing = state.points.find { it.x == cellNum }
                        state.points = if (existing != null) emptyList()
                        else listOf(Point(cellNum, 2))
                    }
                    LandType.LOOP -> {
                        val countOnCell = state.points.count { it.x == cellNum }
                        if (countOnCell >= MAX_BALLS_PER_CELL) {
                            state.points = state.points.filter { it.x != cellNum }
                        } else {
                            state.points = state.points + Point(cellNum, 2)
                        }
                    }
                    LandType.RANDOM -> {
                        val existing = state.points.find { it.x == cellNum }
                        state.points = if (existing != null) {
                            state.points.filter { it.x != cellNum }
                        } else {
                            state.points + Point(cellNum, 2)
                        }
                    }
                }
            },
            cellBallNumbers = if (mode == LandType.LOOP) cellBallNumbers else null,
            enabledCells = enabledCells,
        )

        // 2. Operation Mode
        var showOperationModeDialog by remember { mutableStateOf(false) }
        val currentMode = LandType.fromValue(state.landType)

        Text("Operation Mode", style = MaterialTheme.typography.labelLarge)
        Card(
            onClick = { showOperationModeDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        currentMode.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        currentMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Change",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showOperationModeDialog) {
            AlertDialog(
                onDismissRequest = { showOperationModeDialog = false },
                title = { Text("Operation Mode") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LandType.entries.forEach { mode ->
                            Card(
                                onClick = {
                                    state.landType = mode.value
                                    showOperationModeDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (mode == currentMode)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        mode.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mode == currentMode)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (mode == currentMode)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }

        // 3. Ball Interval
        StepSlider("Ball Interval", state.ballTime, 2..30) { state.ballTime = it }

        // 4. Play Mode
        Text("Play Mode", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = true,
                onClick = { },
                label = { Text("Count") },
            )
            FilterChip(
                selected = false,
                onClick = { },
                enabled = false,
                label = { Text("Time (coming soon)") },
            )
        }

        // 5. Repetitions
        StepSlider("Repetitions", state.times, 1..100) { state.times = it }

        // 6. Ball Adjustments (disabled)
        Text("Ball Adjustments", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.adjustSpin == 1,
                    onCheckedChange = null,
                    enabled = false,
                )
                Text(
                    "Adjust Spin",
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.adjustPosition == 1,
                    onCheckedChange = null,
                    enabled = false,
                )
                Text(
                    "Adjust Position",
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
        Text(
            "Coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

// ── Basic Editor Screen (save-focused wrapper) ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicEditorScreen(
    initial: BasicTraining?,
    onSave: (BasicTraining) -> Unit,
    onBack: () -> Unit,
    nextId: Int,
    motorConfig: MotorConfig? = null,
    onTestBall: ((TestBallRequest) -> Unit)? = null,
    connected: Boolean = false,
) {
    val state = rememberDrillEditorState(initial, nextId)
    val isNew = initial == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Basic Training" else "Edit Training") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave(state.toTraining()) },
                        enabled = state.name.isNotBlank() && state.points.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Check, "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DrillEditorContent(state, motorConfig)
            Spacer(Modifier.height(48.dp))
        }
    }
}
