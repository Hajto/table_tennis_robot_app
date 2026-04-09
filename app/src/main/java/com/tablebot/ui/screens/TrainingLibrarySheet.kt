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
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallColors
import com.tablebot.ui.components.buildCellBallNumbers

// ── Unified training item for mixed list ────────────────────────────

internal sealed class TrainingItem(
    val id: Int,
    val name: String,
    val isFavourite: Int,
    val skillLevelId: Int,
    val tags: List<String>,
    val typeLabel: String,
    val isDefault: Boolean,
) {
    class Basic(val training: BasicTraining) : TrainingItem(
        training.id, training.name, training.isFavourite, training.skillLevel.id, training.tags, "Basic", training.isDefault
    )
    class Dynamic(val training: AdvancedTraining) : TrainingItem(
        training.id, training.name, training.isFavourite, training.skillLevel.id, training.tags, "Dynamic", training.isDefault
    )
}

// ── Training list bottom sheet (unified) ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TrainingBottomSheet(
    onDismiss: () -> Unit,
    basicTrainings: List<BasicTraining>,
    advancedTrainings: List<AdvancedTraining>,
    onLoadBasic: (BasicTraining) -> Unit,
    onLoadAdvanced: (AdvancedTraining) -> Unit,
    onDeleteBasic: (BasicTraining) -> Unit,
    onToggleBasicFavourite: (BasicTraining) -> Unit,
    onDeleteAdvanced: (AdvancedTraining) -> Unit,
    onToggleAdvancedFavourite: (AdvancedTraining) -> Unit,
    connected: Boolean,
    isPlaying: Boolean,
    onStop: () -> Unit,
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }

    var minSkillLevel by remember { mutableIntStateOf(0) }
    var maxSkillLevel by remember { mutableIntStateOf(4) }
    var selectedTags by remember { mutableStateOf(emptySet<String>()) }

    val allTags = remember(basicTrainings, advancedTrainings) {
        (basicTrainings.flatMap { it.tags } + advancedTrainings.flatMap { it.tags })
            .toSortedSet()
    }

    val hasActiveFilter = minSkillLevel > 0 || maxSkillLevel < 4 || selectedTags.isNotEmpty()

    if (showFilterDialog) {
        FilterDialog(
            minLevel = minSkillLevel,
            maxLevel = maxSkillLevel,
            selectedTags = selectedTags,
            allTags = allTags,
            onApply = { min, max, tags ->
                minSkillLevel = min
                maxSkillLevel = max
                selectedTags = tags
                showFilterDialog = false
            },
            onReset = {
                minSkillLevel = 0
                maxSkillLevel = 4
                selectedTags = emptySet()
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false },
        )
    }

    // Build unified list
    val q = searchQuery.lowercase()
    val items = remember(basicTrainings, advancedTrainings, q, minSkillLevel, maxSkillLevel, selectedTags) {
        val all = basicTrainings.map { TrainingItem.Basic(it) } +
            advancedTrainings.map { TrainingItem.Dynamic(it) }
        all.filter { item ->
            item.skillLevelId in minSkillLevel..maxSkillLevel &&
                (q.isBlank() || item.name.lowercase().contains(q)) &&
                (selectedTags.isEmpty() || selectedTags.any { it in item.tags })
        }.sortedWith(
            compareByDescending<TrainingItem> { it.isFavourite }
                .thenBy { it.skillLevelId }
                .thenBy { it.name }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.85f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search drills...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                if (onImport != null) {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Default.FileUpload, "Import drills")
                    }
                }
                if (onExport != null) {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileDownload, "Export drills")
                    }
                }
                IconButton(onClick = { showFilterDialog = true }) {
                    BadgedBox(
                        badge = {
                            if (hasActiveFilter) { Badge { } }
                        }
                    ) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No drills found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(items, key = { "${it.typeLabel}-${it.id}" }) { item ->
                        UnifiedTrainingCard(
                            item = item,
                            onLoad = {
                                when (item) {
                                    is TrainingItem.Basic -> onLoadBasic(item.training)
                                    is TrainingItem.Dynamic -> onLoadAdvanced(item.training)
                                }
                            },
                            onDelete = {
                                when (item) {
                                    is TrainingItem.Basic -> onDeleteBasic(item.training)
                                    is TrainingItem.Dynamic -> onDeleteAdvanced(item.training)
                                }
                            },
                            onToggleFavourite = {
                                when (item) {
                                    is TrainingItem.Basic -> onToggleBasicFavourite(item.training)
                                    is TrainingItem.Dynamic -> onToggleAdvancedFavourite(item.training)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnifiedTrainingCard(
    item: TrainingItem,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onLoad,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val subtitle = buildString {
                        append(item.typeLabel)
                        append(" · ")
                        append(SkillLevelType.fromValue(item.skillLevelId).label)
                        if (item.isDefault) append(" · Default")
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp),
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Details",
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                when (item) {
                    is TrainingItem.Basic -> {
                        val training = item.training
                        val cellBallNumbers = remember(training.points) {
                            buildCellBallNumbers(
                                training.points.mapIndexed { i, pt -> (i + 1) to listOf(pt) }
                            )
                        }
                        TableGrid(
                            selectedPoints = training.points,
                            cellBallNumbers = if (training.landType == LandType.LOOP.value) cellBallNumbers else null,
                        )
                    }
                    is TrainingItem.Dynamic -> {
                        val training = item.training
                        val allPoints = training.ballList.flatMap { it.points }
                        val cellBallNumbers = remember(training.ballList) {
                            buildCellBallNumbers(
                                training.ballList.mapIndexed { i, entry -> (i + 1) to entry.points }
                            )
                        }
                        val cellBallColorMap = remember(training.ballList) {
                            buildCellBallColors(training.ballList)
                        }
                        TableGrid(
                            selectedPoints = allPoints,
                            cellBallNumbers = cellBallNumbers,
                            cellBallColors = cellBallColorMap,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onToggleFavourite,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (item.isFavourite == 1) Icons.Default.Star else Icons.Default.StarBorder,
                            null,
                            Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.isFavourite == 1) "Unfavourite" else "Favourite")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
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
            text = { Text("Delete \"${item.name}\"?") },
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
