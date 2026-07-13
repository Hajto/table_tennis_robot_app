package com.tablebot.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    var playMode by mutableIntStateOf(initial?.playMode ?: 0)
    var ballCount by mutableIntStateOf(initial?.ballCount ?: 30)
    var durationSec by mutableIntStateOf(initial?.durationSec ?: 60)

    // Indices of ball entries whose settings panel is expanded (hoisted from BallEntryEditor
    // so expansion survives navigation and drives the calibration seed).
    private val _expandedIndices = mutableStateListOf<Int>()
    val expandedIndices: List<Int> get() = _expandedIndices

    fun isExpanded(index: Int): Boolean = index in _expandedIndices
    fun toggleExpanded(index: Int) {
        if (!_expandedIndices.remove(index)) _expandedIndices.add(index)
    }
    fun lastExpandedIndex(): Int? = _expandedIndices.maxOrNull()

    fun loadFrom(training: AdvancedTraining) {
        id = training.id
        name = training.name
        repeatNum = training.repeatNum
        repeatDelay = training.repeatDelay
        ballList = training.ballList
        _expandedIndices.clear()
        isFavourite = training.isFavourite
        skillLevel = training.skillLevel
        tags = training.tags
        playMode = training.playMode
        ballCount = training.ballCount
        durationSec = training.durationSec
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
        playMode = playMode, ballCount = ballCount, durationSec = durationSec,
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
            // Remove the deleted ball's flag and shift higher indices down by one.
            val shifted = _expandedIndices.filter { it != index }.map { if (it > index) it - 1 else it }
            _expandedIndices.clear(); _expandedIndices.addAll(shifted)
        }
    }

    fun moveBall(from: Int, to: Int) {
        if (from !in ballList.indices || to !in ballList.indices || from == to) return
        ballList = ballList.toMutableList().apply {
            java.util.Collections.swap(this, from, to)
        }
        val fromExp = from in _expandedIndices
        val toExp = to in _expandedIndices
        _expandedIndices.remove(from); _expandedIndices.remove(to)
        if (toExp) _expandedIndices.add(from)
        if (fromExp) _expandedIndices.add(to)
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

        // Ball entries with drag-to-reorder
        Text("Ball Sequence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Long press and drag to reorder",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Drag-to-reorder state: live swap during drag
        var draggingIndex by remember { mutableStateOf(-1) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }
        val itemHeights = remember { mutableStateMapOf<Int, Int>() }
        val isDraggingAny = draggingIndex >= 0

        state.ballList.forEachIndexed { index, entry ->
            val isDragging = draggingIndex == index

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .onGloballyPositioned { coords ->
                        itemHeights[index] = coords.size.height
                    }
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                        scaleX = if (isDragging) 1.03f else 1f
                        scaleY = if (isDragging) 1.03f else 1f
                        shadowElevation = if (isDragging) 12f else 0f
                        alpha = if (isDraggingAny && !isDragging) 0.5f else 1f
                    }
                    .pointerInput(state.ballList.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y

                                // Live swap: when dragged past half an item height, swap immediately
                                val avgHeight = itemHeights.values
                                    .takeIf { it.isNotEmpty() }
                                    ?.average()?.toFloat() ?: return@detectDragGesturesAfterLongPress
                                val threshold = avgHeight * 0.5f
                                if (dragOffsetY > threshold && draggingIndex < state.ballList.lastIndex) {
                                    state.moveBall(draggingIndex, draggingIndex + 1)
                                    draggingIndex++
                                    dragOffsetY -= avgHeight
                                } else if (dragOffsetY < -threshold && draggingIndex > 0) {
                                    state.moveBall(draggingIndex, draggingIndex - 1)
                                    draggingIndex--
                                    dragOffsetY += avgHeight
                                }
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffsetY = 0f
                            },
                        )
                    },
            ) {
                BallEntryEditor(
                    index = index,
                    entry = entry,
                    ballNumber = index + 1,
                    onUpdate = { state.updateBall(index, it) },
                    onRemove = if (state.ballList.size > 1) {
                        { state.removeBall(index) }
                    } else null,
                    onMoveUp = if (index > 0) {
                        { state.moveBall(index, index - 1) }
                    } else null,
                    onMoveDown = if (index < state.ballList.lastIndex) {
                        { state.moveBall(index, index + 1) }
                    } else null,
                    motorConfig = motorConfig,
                    expanded = state.isExpanded(index),
                    onToggleExpanded = { state.toggleExpanded(index) },
                )
            }
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
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    motorConfig: MotorConfig? = null,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
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
                // Drag handle + up/down arrows
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(20.dp))
                    }
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(20.dp))
                    }
                }
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
                IconButton(onClick = onToggleExpanded) {
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
