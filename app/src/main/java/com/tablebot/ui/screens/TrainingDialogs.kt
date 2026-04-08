package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.data.SkillLevelType

// ── Save training dialog with tag editor ────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SaveTrainingDialog(
    initialName: String,
    initialTags: List<String>,
    allKnownTags: Set<String>,
    loadedOriginalName: String?,
    onSaveOverwrite: (name: String, tags: List<String>) -> Unit,
    onSaveCopy: (name: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var saveName by remember { mutableStateOf(initialName.ifBlank { "" }) }
    var tags by remember { mutableStateOf(initialTags.toMutableList()) }
    var newTagText by remember { mutableStateOf("") }

    val isLoaded = loadedOriginalName != null
    val nameChanged = isLoaded && saveName != loadedOriginalName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLoaded) "Save Training" else "Save as New Training") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Training Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Tags", style = MaterialTheme.typography.labelLarge)

                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags = tags.toMutableList().also { it.remove(tag) } },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                val availableTags = allKnownTags.filter { it !in tags }
                if (availableTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        availableTags.forEach { tag ->
                            SuggestionChip(
                                onClick = { tags = (tags + tag).toMutableList() },
                                label = { Text(tag) },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val trimmed = newTagText.trim()
                            if (trimmed.isNotBlank() && trimmed !in tags) {
                                tags = (tags + trimmed).toMutableList()
                                newTagText = ""
                            }
                        },
                        enabled = newTagText.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Add, "Add tag")
                    }
                }
            }
        },
        confirmButton = {
            when {
                isLoaded && nameChanged -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onSaveOverwrite(saveName, tags) },
                            enabled = saveName.isNotBlank(),
                        ) { Text("Rename") }
                        TextButton(
                            onClick = { onSaveCopy(saveName, tags) },
                            enabled = saveName.isNotBlank(),
                        ) { Text("Save as Copy") }
                    }
                }
                isLoaded -> {
                    TextButton(
                        onClick = { onSaveOverwrite(saveName, tags) },
                        enabled = saveName.isNotBlank(),
                    ) { Text("Save") }
                }
                else -> {
                    TextButton(
                        onClick = { onSaveCopy(saveName, tags) },
                        enabled = saveName.isNotBlank(),
                    ) { Text("Save") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Filter dialog (skill level + tags) ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterDialog(
    minLevel: Int,
    maxLevel: Int,
    selectedTags: Set<String>,
    allTags: Set<String>,
    onApply: (min: Int, max: Int, tags: Set<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rangeStart by remember { mutableFloatStateOf(minLevel.toFloat()) }
    var rangeEnd by remember { mutableFloatStateOf(maxLevel.toFloat()) }
    var tags by remember { mutableStateOf(selectedTags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Skill Level", style = MaterialTheme.typography.labelLarge)
                val startLabel = SkillLevelType.fromValue(rangeStart.toInt()).label
                val endLabel = SkillLevelType.fromValue(rangeEnd.toInt()).label
                Text(
                    if (rangeStart.toInt() == rangeEnd.toInt()) startLabel
                    else "$startLabel — $endLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                RangeSlider(
                    value = rangeStart..rangeEnd,
                    onValueChange = { range ->
                        rangeStart = range.start
                        rangeEnd = range.endInclusive
                    },
                    valueRange = 0f..4f,
                    steps = 3,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SkillLevelType.entries.forEach { level ->
                        Text(
                            level.label.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (allTags.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Tags", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tag in tags,
                                onClick = {
                                    tags = if (tag in tags) tags - tag else tags + tag
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(rangeStart.toInt(), rangeEnd.toInt(), tags) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Reset")
            }
        },
    )
}
