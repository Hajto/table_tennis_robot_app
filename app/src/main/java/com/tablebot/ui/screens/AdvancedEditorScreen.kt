package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.BallSettingsDropdowns
import com.tablebot.ui.components.rememberMotorConstraints
import com.tablebot.ui.components.StepSlider
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallColors
import com.tablebot.ui.components.buildCellBallNumbers

// ── State holder ────────────────────────────────────────────────────────

class AdvancedEditorState(
    initial: AdvancedTraining?,
    var id: Int,
) {
    var name by mutableStateOf(initial?.name ?: "")
    var repeatNum by mutableIntStateOf(initial?.repeatNum ?: 10)
    var repeatDelay by mutableIntStateOf(initial?.repeatDelay ?: 1)
    var ballList by mutableStateOf(
        initial?.ballList ?: listOf(
            BallEntry(ball = 1, spin = 2, power = 2, points = listOf(Point(8, 2)), ballTime = 9)
        )
    )
    var isFavourite by mutableIntStateOf(initial?.isFavourite ?: 0)
    var skillLevel: SkillLevel = initial?.skillLevel ?: SkillLevel()
    var tags by mutableStateOf(initial?.tags ?: emptyList())

    fun loadFrom(training: AdvancedTraining) {
        id = training.id
        name = training.name
        repeatNum = training.repeatNum
        repeatDelay = training.repeatDelay
        ballList = training.ballList
        isFavourite = training.isFavourite
        skillLevel = training.skillLevel
        tags = training.tags
    }

    fun toTraining(): AdvancedTraining = AdvancedTraining(
        id = id,
        name = name.ifBlank { "Quick Play Advanced" },
        repeatNum = repeatNum,
        repeatDelay = repeatDelay,
        ballList = ballList,
        isFavourite = isFavourite,
        skillLevel = skillLevel,
        tags = tags,
    )

    fun addBall() {
        ballList = ballList + BallEntry(
            ball = 1, spin = 2, power = 2,
            points = listOf(Point(8, 2)), ballTime = 9,
        )
    }

    fun updateBall(index: Int, entry: BallEntry) {
        ballList = ballList.toMutableList().apply { set(index, entry) }
    }

    fun removeBall(index: Int) {
        if (ballList.size > 1) {
            ballList = ballList.toMutableList().apply { removeAt(index) }
        }
    }
}

@Composable
fun rememberAdvancedEditorState(initial: AdvancedTraining?, id: Int): AdvancedEditorState =
    remember { AdvancedEditorState(initial, id) }

// ── Shared advanced editor content ──────────────────────────────────

@Composable
fun AdvancedEditorContent(
    state: AdvancedEditorState,
    motorConfig: MotorConfig?,
    showName: Boolean = true,
) {
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

        // Sequence overview grid
        val allPoints = state.ballList.flatMap { it.points }
        val overviewBallNumbers = remember(state.ballList) {
            buildCellBallNumbers(
                state.ballList.mapIndexed { i, entry -> (i + 1) to entry.points }
            )
        }
        val overviewBallColors = remember(state.ballList) {
            buildCellBallColors(state.ballList)
        }
        Text("Sequence Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TableGrid(
            selectedPoints = allPoints,
            cellBallNumbers = overviewBallNumbers,
            cellBallColors = overviewBallColors,
        )

        HorizontalDivider()

        // Ball entries
        Text("Ball Sequence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        state.ballList.forEachIndexed { index, entry ->
            BallEntryEditor(
                index = index,
                entry = entry,
                ballNumber = index + 1,
                onUpdate = { state.updateBall(index, it) },
                onRemove = if (state.ballList.size > 1) {
                    { state.removeBall(index) }
                } else null,
                motorConfig = motorConfig,
            )
        }

        OutlinedButton(
            onClick = { state.addBall() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Ball")
        }

        HorizontalDivider()

        // Repeat settings
        StepSlider("Repeat Count", state.repeatNum, 1..50) { state.repeatNum = it }
        StepSlider("Repeat Delay", state.repeatDelay, 0..10) { state.repeatDelay = it }
    }
}

// ── Advanced Editor Screen (save-focused wrapper) ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedEditorScreen(
    initial: AdvancedTraining?,
    onSave: (AdvancedTraining) -> Unit,
    onBack: () -> Unit,
    nextId: () -> Int,
    motorConfig: MotorConfig? = null,
) {
    val state = rememberAdvancedEditorState(initial, initial?.id ?: nextId())
    val isNew = initial == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Advanced Training" else "Edit Training") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave(state.toTraining()) },
                        enabled = state.name.isNotBlank() && state.ballList.isNotEmpty(),
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
            AdvancedEditorContent(state, motorConfig)
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Ball entry editor card ──────────────────────────────────────────

@Composable
private fun BallEntryEditor(
    index: Int,
    entry: BallEntry,
    ballNumber: Int,
    onUpdate: (BallEntry) -> Unit,
    onRemove: (() -> Unit)?,
    motorConfig: MotorConfig? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val constraints = rememberMotorConstraints(
        ball = entry.ball, spin = entry.spin, power = entry.power,
        points = entry.points, motorConfig = motorConfig,
        onSpinChange = { onUpdate(entry.copy(spin = it)) },
        onPowerChange = { onUpdate(entry.copy(power = it)) },
        onPointsChange = { onUpdate(entry.copy(points = it)) },
    )
    val availableSpins = constraints.validSpins
    val availablePowers = constraints.validPowers
    val enabledCells = constraints.enabledCells

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ball ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${BallType.fromValue(entry.ball).label} ${SpinType.fromValue(entry.spin).label} ${PowerType.fromValue(entry.power).label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Toggle",
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                BallSettingsDropdowns(
                    ball = entry.ball,
                    spin = entry.spin,
                    power = entry.power,
                    onBallChange = { onUpdate(entry.copy(ball = it)) },
                    onSpinChange = { onUpdate(entry.copy(spin = it)) },
                    onPowerChange = { onUpdate(entry.copy(power = it)) },
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

                StepSlider("Ball Interval", entry.ballTime, 2..30) { onUpdate(entry.copy(ballTime = it)) }

                Text("Target Points", style = MaterialTheme.typography.labelMedium)
                val entryBallNumbers = remember(entry.points, ballNumber) {
                    buildCellBallNumbers(listOf(ballNumber to entry.points))
                }
                TableGrid(
                    selectedPoints = entry.points,
                    onCellClick = { cellNum ->
                        val existing = entry.points.find { it.x == cellNum }
                        val newPoints = if (existing != null) {
                            entry.points.filter { it.x != cellNum }
                        } else {
                            entry.points + Point(cellNum, 2)
                        }
                        onUpdate(entry.copy(points = newPoints))
                    },
                    cellBallNumbers = entryBallNumbers,
                    enabledCells = enabledCells,
                )
            }
        }
    }
}
