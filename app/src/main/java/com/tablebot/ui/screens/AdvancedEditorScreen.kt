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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.BallSettingsDropdowns
import com.tablebot.ui.components.StepSlider
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallNumbers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedEditorScreen(
    initial: AdvancedTraining?,
    onSave: (AdvancedTraining) -> Unit,
    onBack: () -> Unit,
    nextId: () -> Int,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var repeatNum by remember { mutableIntStateOf(initial?.repeatNum ?: 10) }
    var repeatDelay by remember { mutableIntStateOf(initial?.repeatDelay ?: 1) }
    var ballList by remember {
        mutableStateOf(
            initial?.ballList ?: listOf(
                BallEntry(ball = 1, spin = 2, power = 2, points = listOf(Point(8, 2)), ballTime = 9)
            )
        )
    }

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
                        onClick = {
                            val training = AdvancedTraining(
                                id = initial?.id ?: nextId(),
                                name = name.ifBlank { "Custom Advanced" },
                                repeatNum = repeatNum,
                                repeatDelay = repeatDelay,
                                ballList = ballList,
                                isFavourite = initial?.isFavourite ?: 0,
                                skillLevel = initial?.skillLevel ?: SkillLevel(),
                            )
                            onSave(training)
                        },
                        enabled = name.isNotBlank() && ballList.isNotEmpty(),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Training Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            StepSlider("Repeat Count", repeatNum, 1..50) { repeatNum = it }

            StepSlider("Repeat Delay", repeatDelay, 0..10) { repeatDelay = it }

            HorizontalDivider()

            // Sequence overview grid
            val allPoints = ballList.flatMap { it.points }
            val overviewBallNumbers = remember(ballList) {
                buildCellBallNumbers(
                    ballList.mapIndexed { i, entry -> (i + 1) to entry.points }
                )
            }
            Text("Sequence Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TableGrid(
                selectedPoints = allPoints,
                cellBallNumbers = overviewBallNumbers,
            )

            HorizontalDivider()

            // Ball entries
            Text("Ball Sequence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            ballList.forEachIndexed { index, entry ->
                BallEntryEditor(
                    index = index,
                    entry = entry,
                    ballNumber = index + 1,
                    onUpdate = { updated ->
                        ballList = ballList.toMutableList().apply { set(index, updated) }
                    },
                    onRemove = if (ballList.size > 1) {
                        { ballList = ballList.toMutableList().apply { removeAt(index) } }
                    } else null,
                )
            }

            OutlinedButton(
                onClick = {
                    ballList = ballList + BallEntry(
                        ball = 1, spin = 2, power = 2,
                        points = listOf(Point(8, 2)), ballTime = 9,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Ball")
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun BallEntryEditor(
    index: Int,
    entry: BallEntry,
    ballNumber: Int,
    onUpdate: (BallEntry) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                )

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
                )
            }
        }
    }
}
