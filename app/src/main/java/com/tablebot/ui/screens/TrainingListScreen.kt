package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.PlayModeSelector
import com.tablebot.ui.components.formatDurationMmSs
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallNumbers


@Composable
fun BasicTrainingList(
    trainings: List<BasicTraining>,
    connected: Boolean,
    isPlaying: Boolean,
    onPlay: (BasicTraining) -> Unit,
    onUpdate: (BasicTraining) -> Unit,
    onStop: () -> Unit,
    onEdit: (BasicTraining) -> Unit,
    onDelete: (BasicTraining) -> Unit,
    onToggleFavourite: (BasicTraining) -> Unit,
) {
    if (trainings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No trainings found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(trainings, key = { it.id }) { training ->
            BasicTrainingCard(
                training = training,
                connected = connected,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onUpdate = onUpdate,
                onStop = onStop,
                onEdit = { onEdit(training) },
                onDelete = { onDelete(training) },
                onToggleFavourite = { onToggleFavourite(training) },
            )
        }
    }
}

@Composable
fun AdvancedTrainingList(
    trainings: List<AdvancedTraining>,
    connected: Boolean,
    isPlaying: Boolean,
    onPlay: (AdvancedTraining) -> Unit,
    onUpdate: (AdvancedTraining) -> Unit,
    onStop: () -> Unit,
    onEdit: (AdvancedTraining) -> Unit,
    onDelete: (AdvancedTraining) -> Unit,
    onToggleFavourite: (AdvancedTraining) -> Unit,
) {
    if (trainings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No trainings found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(trainings, key = { it.id }) { training ->
            AdvancedTrainingCard(
                training = training,
                connected = connected,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onUpdate = onUpdate,
                onStop = onStop,
                onEdit = { onEdit(training) },
                onDelete = { onDelete(training) },
                onToggleFavourite = { onToggleFavourite(training) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicTrainingCard(
    training: BasicTraining,
    connected: Boolean,
    isPlaying: Boolean,
    onPlay: (BasicTraining) -> Unit,
    onUpdate: (BasicTraining) -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editT by remember(training) { mutableStateOf(training) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        training.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        SkillLevelType.fromValue(training.skillLevel.id).label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (training.tags.isNotEmpty()) {
                        TagRow(training.tags)
                    }
                }

                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        if (training.isFavourite == 1) Icons.Default.Star else Icons.Default.StarBorder,
                        "Favourite",
                        tint = if (training.isFavourite == 1) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (connected && !isPlaying) {
                    IconButton(onClick = { onPlay(editT) }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "Play",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DetailChip("Ball", BallType.fromValue(training.ball).label)
                    DetailChip("Spin", SpinType.fromValue(training.spin).label)
                    DetailChip("Power", PowerType.fromValue(training.power).label)
                    DetailChip("Land", LandType.fromValue(training.landType).label)
                    when (PlayMode.fromValue(editT.playMode)) {
                        PlayMode.REPETITIONS -> DetailChip("Reps", "${editT.times}")
                        PlayMode.BALL_COUNT -> DetailChip("Balls", "${editT.ballCount}")
                        PlayMode.TIMED -> DetailChip("Time", formatDurationMmSs(editT.durationSec))
                    }
                }

                // Play mode
                Spacer(Modifier.height(8.dp))
                PlayModeSelector(
                    playMode = editT.playMode,
                    reps = editT.times,
                    ballCount = editT.ballCount,
                    durationSec = editT.durationSec,
                    ballsPerPattern = editT.points.size,
                    repsRange = 1..100,
                    onPlayModeChange = { editT = editT.copy(playMode = it); onUpdate(editT) },
                    onRepsChange = { editT = editT.copy(times = it); onUpdate(editT) },
                    onBallCountChange = { editT = editT.copy(ballCount = it); onUpdate(editT) },
                    onDurationChange = { editT = editT.copy(durationSec = it); onUpdate(editT) },
                )

                Spacer(Modifier.height(8.dp))
                val cellBallNumbers = remember(training.points) {
                    buildCellBallNumbers(
                        training.points.mapIndexed { i, pt -> (i + 1) to listOf(pt) }
                    )
                }
                TableGrid(
                    selectedPoints = training.points,
                    cellBallNumbers = cellBallNumbers,
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Training") },
            text = { Text("Delete \"${training.name}\"?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedTrainingCard(
    training: AdvancedTraining,
    connected: Boolean,
    isPlaying: Boolean,
    onPlay: (AdvancedTraining) -> Unit,
    onUpdate: (AdvancedTraining) -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editT by remember(training) { mutableStateOf(training) }

    val allPoints = training.steps.flatMap { it.balls }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        training.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${SkillLevelType.fromValue(training.skillLevel.id).label} · ${training.steps.size} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (training.tags.isNotEmpty()) {
                        TagRow(training.tags)
                    }
                }

                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        if (training.isFavourite == 1) Icons.Default.Star else Icons.Default.StarBorder,
                        "Favourite",
                        tint = if (training.isFavourite == 1) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (connected && !isPlaying) {
                    IconButton(onClick = { onPlay(editT) }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "Play",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Show each step
                training.steps.forEachIndexed { i, step ->
                    Text(
                        "Ball ${i + 1}: ${BallType.fromValue(step.ball).label}, " +
                            "${SpinType.fromValue(step.spin).label}, ${PowerType.fromValue(step.power).label}, " +
                            "Speed ${step.ballTime}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Play mode
                Spacer(Modifier.height(8.dp))
                PlayModeSelector(
                    playMode = editT.playMode,
                    reps = editT.repeatNum,
                    ballCount = editT.ballCount,
                    durationSec = editT.durationSec,
                    ballsPerPattern = editT.steps.sumOf { it.balls.size },
                    repsRange = 1..50,
                    onPlayModeChange = { editT = editT.copy(playMode = it); onUpdate(editT) },
                    onRepsChange = { editT = editT.copy(repeatNum = it); onUpdate(editT) },
                    onBallCountChange = { editT = editT.copy(ballCount = it); onUpdate(editT) },
                    onDurationChange = { editT = editT.copy(durationSec = it); onUpdate(editT) },
                )

                Spacer(Modifier.height(8.dp))
                val cellBallNumbers = remember(training.steps) {
                    buildCellBallNumbers(
                        training.steps.mapIndexed { i, step -> (i + 1) to step.balls }
                    )
                }
                TableGrid(
                    selectedPoints = allPoints,
                    cellBallNumbers = cellBallNumbers,
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Training") },
            text = { Text("Delete \"${training.name}\"?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun DetailChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(tags: List<String>) {
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = { },
                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(24.dp),
            )
        }
    }
}
