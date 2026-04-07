package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.BallSettingsDropdowns
import com.tablebot.ui.components.LabeledDropdown
import com.tablebot.ui.components.MAX_BALLS_PER_CELL
import com.tablebot.ui.components.StepSlider
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallNumbers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicEditorScreen(
    initial: BasicTraining?,
    onSave: (BasicTraining) -> Unit,
    onBack: () -> Unit,
    nextId: () -> Int,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ball by remember { mutableIntStateOf(initial?.ball ?: 1) }
    var spin by remember { mutableIntStateOf(initial?.spin ?: 2) }
    var power by remember { mutableIntStateOf(initial?.power ?: 2) }
    var ballTime by remember { mutableIntStateOf(initial?.ballTime ?: 9) }
    var times by remember { mutableIntStateOf(initial?.times ?: 20) }
    var landType by remember { mutableIntStateOf(initial?.landType ?: 0) }
    var points by remember { mutableStateOf(initial?.points ?: listOf(Point(8, 2))) }
    var adjustSpin by remember { mutableIntStateOf(initial?.adjustSpin ?: 0) }
    var adjustPosition by remember { mutableIntStateOf(initial?.adjustPosition ?: 0) }

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
                        onClick = {
                            val training = BasicTraining(
                                id = initial?.id ?: nextId(),
                                name = name.ifBlank { "Custom Training" },
                                ball = ball,
                                spin = spin,
                                power = power,
                                ballTime = ballTime,
                                times = times,
                                landType = landType,
                                points = points,
                                adjustSpin = adjustSpin,
                                adjustPosition = adjustPosition,
                                isFavourite = initial?.isFavourite ?: 0,
                                skillLevel = initial?.skillLevel ?: SkillLevel(),
                            )
                            onSave(training)
                        },
                        enabled = name.isNotBlank() && points.isNotEmpty(),
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

            BallSettingsDropdowns(
                ball = ball,
                spin = spin,
                power = power,
                onBallChange = { ball = it },
                onSpinChange = { spin = it },
                onPowerChange = { power = it },
            )

            // Ball interval
            StepSlider("Ball Interval", ballTime, 2..30) { ballTime = it }

            // Repetitions
            StepSlider("Repetitions", times, 1..100) { times = it }

            // Land type
            LabeledDropdown(
                label = "Landing Pattern",
                entries = LandType.entries.toList(),
                selected = LandType.fromValue(landType),
                labelOf = { it.label },
                onSelect = { landType = it.value },
            )

            val isLoopMode = landType == LandType.LOOP.value

            // Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Target Points (${points.size} selected)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (isLoopMode && points.isNotEmpty()) {
                    IconButton(onClick = { points = points.dropLast(1) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                    }
                    IconButton(onClick = { points = emptyList() }) {
                        Icon(Icons.Default.Refresh, "Reset")
                    }
                }
            }
            val cellBallNumbers = remember(points) {
                buildCellBallNumbers(
                    points.mapIndexed { i, pt -> (i + 1) to listOf(pt) }
                )
            }
            TableGrid(
                selectedPoints = points,
                onCellClick = { cellNum ->
                    if (isLoopMode) {
                        val countOnCell = points.count { it.x == cellNum }
                        if (countOnCell >= MAX_BALLS_PER_CELL) {
                            points = points.filter { it.x != cellNum }
                        } else {
                            points = points + Point(cellNum, 2)
                        }
                    } else {
                        val existing = points.find { it.x == cellNum }
                        points = if (existing != null) {
                            points.filter { it.x != cellNum }
                        } else {
                            points + Point(cellNum, 2)
                        }
                    }
                },
                cellBallNumbers = if (isLoopMode) cellBallNumbers else null,
            )

            // Depth selector for selected points
            if (points.isNotEmpty()) {
                Text("Depth", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Short", 2 to "Medium", 3 to "Long").forEach { (depth, label) ->
                        FilterChip(
                            selected = points.all { it.y == depth },
                            onClick = { points = points.map { it.copy(y = depth) } },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Adjust flags
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row {
                    Checkbox(
                        checked = adjustSpin == 1,
                        onCheckedChange = { adjustSpin = if (it) 1 else 0 },
                    )
                    Text("Adjust Spin", modifier = Modifier.padding(top = 14.dp))
                }
                Row {
                    Checkbox(
                        checked = adjustPosition == 1,
                        onCheckedChange = { adjustPosition = if (it) 1 else 0 },
                    )
                    Text("Adjust Position", modifier = Modifier.padding(top = 14.dp))
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
