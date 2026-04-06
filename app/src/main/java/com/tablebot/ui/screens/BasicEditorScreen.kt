package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.TableGrid

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

            // Ball type
            Text("Ball Type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BallType.entries.forEach { type ->
                    FilterChip(
                        selected = ball == type.value,
                        onClick = { ball = type.value },
                        label = { Text(type.label) },
                    )
                }
            }

            // Spin
            Text("Spin: ${SpinType.fromValue(spin).label}", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SpinType.entries.forEach { type ->
                    FilterChip(
                        selected = spin == type.value,
                        onClick = { spin = type.value },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Power
            Text("Power: ${PowerType.fromValue(power).label}", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerType.entries.forEach { type ->
                    FilterChip(
                        selected = power == type.value,
                        onClick = { power = type.value },
                        label = { Text(type.label) },
                    )
                }
            }

            // Ball interval
            Text("Ball Interval: $ballTime", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = ballTime.toFloat(),
                onValueChange = { ballTime = it.toInt() },
                valueRange = 2f..30f,
                steps = 27,
            )

            // Repetitions
            Text("Repetitions: $times", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = times.toFloat(),
                onValueChange = { times = it.toInt() },
                valueRange = 1f..100f,
                steps = 98,
            )

            // Land type
            Text("Landing Pattern", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LandType.entries.forEach { type ->
                    FilterChip(
                        selected = landType == type.value,
                        onClick = { landType = type.value },
                        label = { Text(type.label) },
                    )
                }
            }

            // Grid
            Text(
                "Target Points (tap to toggle, ${points.size} selected)",
                style = MaterialTheme.typography.labelLarge,
            )
            TableGrid(
                selectedPoints = points,
                onCellClick = { cellNum ->
                    val existing = points.find { it.x == cellNum }
                    points = if (existing != null) {
                        points.filter { it.x != cellNum }
                    } else {
                        points + Point(cellNum, 2)
                    }
                },
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
