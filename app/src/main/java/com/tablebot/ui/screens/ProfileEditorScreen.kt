package com.tablebot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tablebot.data.Profile
import com.tablebot.data.RobotPosition
import com.tablebot.data.RobotType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profile: Profile,
    onSave: (Profile) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var placement by remember(profile.id) { mutableStateOf(profile.robotPlacement) }
    var robotPosition by remember(profile.id) { mutableStateOf(profile.robotPosition) }
    var robotType by remember(profile.id) { mutableStateOf(profile.robotType) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showRecalibrationWarning by remember { mutableStateOf(false) }

    val nameChanged = name.trim() != profile.name
    val positionChanged = placement != profile.robotPlacement || robotPosition != profile.robotPosition
    val robotTypeChanged = robotType != profile.robotType
    val isDirty = nameChanged || positionChanged || robotTypeChanged

    fun handleBack() {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    fun performSave() {
        // If position changed but name didn't, show recalibration warning
        if (positionChanged && !showRecalibrationWarning) {
            showRecalibrationWarning = true
            return
        }
        onSave(
            profile.copy(
                name = name.trim(),
                robotPlacement = placement,
                robotPosition = robotPosition,
                robotType = robotType,
            )
        )
        onBack()
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing") }
            },
        )
    }

    // Recalibration warning dialog
    if (showRecalibrationWarning) {
        AlertDialog(
            onDismissRequest = { showRecalibrationWarning = false },
            title = { Text("Heads Up") },
            text = {
                Text(
                    "Changing the robot position updates the visual indicator only. " +
                    "If you've physically moved the robot, the existing calibration " +
                    "may no longer be accurate — you'll need to recalibrate manually."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRecalibrationWarning = false
                    onSave(
                        profile.copy(
                            name = name.trim(),
                            robotPlacement = placement,
                            robotPosition = robotPosition,
                        )
                    )
                    onBack()
                }) { Text("Save Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showRecalibrationWarning = false }) { Text("Cancel") }
            },
        )
    }

    // Delete dialog
    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Profile") },
            text = { Text("Delete \"${profile.name}\"? This will also delete its calibration data.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete profile",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    // Save button
                    IconButton(
                        onClick = ::performSave,
                        enabled = isDirty && name.isNotBlank(),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (isDirty && name.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
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
            // Profile name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text(
                "Robot Type",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                "Select your robot model. Infinity V1 robots use a different stop command.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RobotType.entries.forEach { type ->
                    FilterChip(
                        selected = robotType == type,
                        onClick = { robotType = type },
                        label = { Text(type.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider()

            Text(
                "Robot Position",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                "Drag the robot icon to where the robot is physically placed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Full table visualization with draggable robot
            TablePlacementView(
                placement = placement,
                robotPosition = robotPosition,
                onPlacementChange = { newPlacement, newPosition ->
                    placement = newPlacement
                    robotPosition = newPosition
                },
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * Full table visualization with draggable robot icon.
 * The drag gesture is attached to the icon itself, not the container.
 */
@Composable
private fun TablePlacementView(
    placement: String,
    robotPosition: RobotPosition,
    onPlacementChange: (String, RobotPosition) -> Unit,
) {
    var behindSize by remember { mutableStateOf(IntSize.Zero) }
    var behindY by remember { mutableStateOf(0f) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridY by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var isDragging by remember { mutableStateOf(false) }
    var iconCenter by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val iconSize = 32.dp
    val iconSizePx = with(density) { iconSize.toPx() }

    fun savedCenter(): Offset {
        return if (placement == "behind" && behindSize.width > 0) {
            Offset(
                robotPosition.x * behindSize.width,
                behindY + robotPosition.y * behindSize.height,
            )
        } else if (placement == "robot" && gridSize.width > 0) {
            Offset(
                robotPosition.x * gridSize.width,
                gridY + robotPosition.y * gridSize.height,
            )
        } else {
            Offset.Zero
        }
    }

    LaunchedEffect(placement, robotPosition, behindSize, gridSize, behindY, gridY) {
        val c = savedCenter()
        if (c != Offset.Zero && !isDragging) {
            iconCenter = c
        }
    }

    fun commitDrop() {
        val cx = iconCenter.x
        val cy = iconCenter.y
        if (behindSize.width > 0 && cy >= behindY && cy <= behindY + behindSize.height) {
            val normX = (cx / behindSize.width).coerceIn(0f, 1f)
            val normY = ((cy - behindY) / behindSize.height).coerceIn(0f, 1f)
            onPlacementChange("behind", RobotPosition(normX, normY))
            return
        }
        if (gridSize.width > 0 && cy >= gridY && cy <= gridY + gridSize.height) {
            val normX = (cx / gridSize.width).coerceIn(0f, 1f)
            val normY = ((cy - gridY) / gridSize.height).coerceIn(0f, 1f)
            onPlacementChange("robot", RobotPosition(normX, normY))
            return
        }
        iconCenter = savedCenter()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .onGloballyPositioned { containerSize = it.size },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Behind Table",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Color(0xFF616161).copy(alpha = 0.1f))
                        .border(
                            1.dp,
                            if (placement == "behind") MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else Color(0xFF616161).copy(alpha = 0.2f),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        )
                        .onGloballyPositioned {
                            behindSize = it.size
                            behindY = it.positionInParent().y
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isDragging && placement != "behind") {
                        Text(
                            "Behind Table",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        )
                    }
                }

                GrayedOutGrid(
                    highlighted = placement == "robot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            gridSize = it.size
                            gridY = it.positionInParent().y
                        },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                )
                Text(
                    "Net",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )

                GrayedOutGrid(
                    highlighted = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (iconCenter != Offset.Zero || containerSize.width > 0) {
                val displayCenter = iconCenter.let {
                    if (it == Offset.Zero) savedCenter() else it
                }
                if (displayCenter != Offset.Zero) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Robot position — drag to move",
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                translationX = displayCenter.x - iconSizePx / 2
                                translationY = displayCenter.y - iconSizePx / 2
                                if (isDragging) {
                                    scaleX = 1.15f
                                    scaleY = 1.15f
                                    shadowElevation = 8f
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        iconCenter = Offset(
                                            (iconCenter.x + amount.x).coerceIn(0f, containerSize.width.toFloat()),
                                            (iconCenter.y + amount.y).coerceIn(0f, containerSize.height.toFloat()),
                                        )
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        commitDrop()
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        iconCenter = savedCenter()
                                    },
                                )
                            },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun GrayedOutGrid(
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF9E9E9E).copy(alpha = 0.08f))
            .border(
                1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else Color(0xFF9E9E9E).copy(alpha = 0.15f),
                RoundedCornerShape(4.dp),
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (col in 0..4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF9E9E9E).copy(alpha = 0.1f)),
                    )
                }
            }
        }
    }
}
