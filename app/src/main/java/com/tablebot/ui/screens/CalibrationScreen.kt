package com.tablebot.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablebot.ble.BlePermissions
import com.tablebot.ble.ConnectionState
import com.tablebot.data.*
import com.tablebot.ui.components.BallSettingsDropdowns
import com.tablebot.ui.components.ConnectionBar
import com.tablebot.ui.components.StepSlider
import com.tablebot.viewmodel.RobotViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    robotVm: RobotViewModel,
    onBack: () -> Unit,
    activeProfileName: String? = null,
    seed: com.tablebot.viewmodel.CalibrationSeed? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission handling
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) robotVm.scan()
    }
    val requiredPermissions = remember { BlePermissions.required() }
    fun handleScan() {
        if (permissionsGranted) robotVm.scan() else permissionLauncher.launch(requiredPermissions)
    }

    val connectionState by robotVm.connectionState.collectAsState()
    val deviceName by robotVm.deviceName.collectAsState()
    val statusMessage by robotVm.statusMessage.collectAsState()
    val firmwareVersion by robotVm.firmwareVersion.collectAsState()
    val isPlaying by robotVm.isPlaying.collectAsState()
    val currentTrainingName by robotVm.currentTrainingName.collectAsState()
    val connected = connectionState == ConnectionState.CONNECTED

    // Selection state
    var ball by remember { mutableIntStateOf(1) }
    var spin by remember { mutableIntStateOf(2) }
    var power by remember { mutableIntStateOf(2) }
    var selectedCell by remember { mutableStateOf<Int?>(null) }

    // Current params for the selected cell
    var currentParams by remember { mutableStateOf<MotorParams?>(null) }

    // Editable motor values
    var m1speed by remember { mutableIntStateOf(0) }
    var m2speed by remember { mutableIntStateOf(0) }
    var xaxis by remember { mutableIntStateOf(0) }
    var yaxis by remember { mutableIntStateOf(0) }
    var zaxis by remember { mutableIntStateOf(0) }
    var dirty by remember { mutableStateOf(false) }

    // Observe the motorConfig to reactively derive calibrated cells
    val motorConfig = robotVm.motorConfigFlow.collectAsState().value

    // Load params when selection changes
    fun loadParams() {
        val cell = selectedCell ?: return
        val params = robotVm.motorConfig.lookup(ball, spin, power, cell)
        currentParams = params
        m1speed = params?.m1speed ?: 0
        m2speed = params?.m2speed ?: 0
        xaxis = params?.xaxis ?: 0
        yaxis = params?.yaxis ?: 0
        zaxis = params?.zaxis ?: 0
        dirty = false
    }

    LaunchedEffect(ball, spin, power, selectedCell) { loadParams() }

    // Seed the selection from the ball being edited (only when navigated in with context).
    LaunchedEffect(Unit) {
        seed?.let {
            ball = it.ball
            spin = it.spin
            power = it.power
            selectedCell = it.cell
        }
    }

    // File import/export
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(robotVm.motorConfig.exportJson().toByteArray())
                }
            }
        }
    }

    var importError by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val error = robotVm.motorConfig.importJson(uri)
                if (error != null) {
                    importError = error
                } else {
                    robotVm.reloadMotorConfig()
                    loadParams()
                }
            }
        }
    }

    if (importError != null) {
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import Failed") },
            text = { Text(importError!!) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text("OK") }
            },
        )
    }

    // Reset confirmation
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Defaults") },
            text = { Text("This will discard all calibration changes and restore the original motor configuration.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        robotVm.motorConfig.resetToDefaults()
                        robotVm.reloadMotorConfig()
                        loadParams()
                    }
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (activeProfileName != null) "Calibration — $activeProfileName" else "Calibration") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                )
                ConnectionBar(
                    state = connectionState,
                    deviceName = deviceName,
                    statusMessage = statusMessage,
                    firmwareVersion = firmwareVersion,
                    isPlaying = isPlaying,
                    currentTrainingName = currentTrainingName,
                    onScanClick = ::handleScan,
                    onDisconnectClick = { robotVm.disconnect() },
                    onStopClick = { robotVm.stop() },
                )
            }
        },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BallSettingsDropdowns(
                    ball = ball,
                    spin = spin,
                    power = power,
                    onBallChange = { ball = it },
                    onSpinChange = { spin = it },
                    onPowerChange = { power = it },
                )

                HorizontalDivider()

                // Table grid — tap cell to select for calibration
                Text(
                    "Select landing area to calibrate",
                    style = MaterialTheme.typography.labelLarge,
                )

                val calibratedCells = remember(ball, spin, power, motorConfig) {
                    motorConfig.validLandareas(ball, spin, power)
                }
                CalibrationGrid(
                    selectedCell = selectedCell,
                    calibratedCells = calibratedCells,
                    onCellClick = { selectedCell = it },
                )

                // Motor params editor (only when a cell is selected)
                if (selectedCell != null) {
                    HorizontalDivider()

                    val cellLabel = selectedCell.toString()
                    val ballLabel = BallType.entries.first { it.value == ball }.label
                    val spinLabel = SpinType.fromValue(spin).label
                    val powerLabel = PowerType.fromValue(power).label

                    Text(
                        "Cell $cellLabel / $ballLabel / $spinLabel / $powerLabel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    if (currentParams == null) {
                        Text(
                            "No config entry for this combination yet. Adjust values and accept to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // Motor sliders
                    // M1/M2: byte 0..36 = app units 0..8100 (scale ×225)
                    // X:     byte 0..40 = −20° to +20° (offset −20, 1°/step)
                    // Y:     byte 0..32 = −30° to +50° (offset −30, 2.5°/step)
                    // Z:     byte 0..20 = −45° to +45° (offset −45, 4.5°/step)
                    StepSlider("M1 Speed (top)", m1speed, 0..36,
                        displayValue = { "${it * 225}" }
                    ) { m1speed = it; dirty = true }
                    StepSlider("M2 Speed (bottom)", m2speed, 0..36,
                        displayValue = { "${it * 225}" }
                    ) { m2speed = it; dirty = true }
                    StepSlider("X Axis", xaxis, 0..40,
                        displayValue = { "${it - 20}°" }
                    ) { xaxis = it; dirty = true }
                    StepSlider("Y Axis", yaxis, 0..32,
                        displayValue = { "${"%.1f".format((it * 2.5) - 30)}°" }
                    ) { yaxis = it; dirty = true }
                    StepSlider("Z Axis", zaxis, 0..20,
                        displayValue = { "${"%.1f".format((it * 4.5) - 45)}°" }
                    ) { zaxis = it; dirty = true }

                    // Raw byte values summary (for debugging)
                    Text(
                        "raw: m1=$m1speed  m2=$m2speed  x=$xaxis  y=$yaxis  z=$zaxis",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(4.dp))

                    // Action buttons
                    fun buildParams(): MotorParams = MotorParams(
                        id = currentParams?.id ?: 0,
                        ball = ball,
                        spin = spin,
                        power = power,
                        landarea = selectedCell!!,
                        m1speed = m1speed,
                        m2speed = m2speed,
                        xaxis = xaxis,
                        yaxis = yaxis,
                        zaxis = zaxis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Test ball
                        Button(
                            onClick = {
                                robotVm.sendTestBall(buildParams())
                            },
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Test Ball")
                        }

                        // Accept / Save
                        Button(
                            onClick = {
                                val params = buildParams()
                                scope.launch {
                                    robotVm.saveMotorParams(params)
                                    robotVm.reloadMotorConfig()
                                }
                                dirty = false
                                currentParams = params
                            },
                            enabled = dirty || currentParams == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Accept")
                        }
                    }

                    // Stop button
                    if (isPlaying) {
                        Button(
                            onClick = { robotVm.stop() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("STOP")
                        }
                    }
                }

                HorizontalDivider()

                // File operations
                Text("Configuration", style = MaterialTheme.typography.labelLarge)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("motor-config-${activeProfileName ?: "tablebot"}.json") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Export to File")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Import from File")
                    }
                }

                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Reset to Factory Defaults")
                }

                if (robotVm.motorConfig.isCustomized()) {
                    Text(
                        "Using customized motor configuration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }

@Composable
private fun CalibrationGrid(
    selectedCell: Int?,
    calibratedCells: Set<Int>,
    onCellClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Net",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B5E20).copy(alpha = 0.15f))
                .border(2.dp, Color(0xFF1B5E20).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (col in 0..4) {
                        val cellNum = row * 5 + col + 1
                        val isSelected = cellNum == selectedCell
                        val isCalibrated = cellNum in calibratedCells

                        val bgColor = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isCalibrated -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                            else -> Color(0xFF2E7D32).copy(alpha = 0.25f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bgColor)
                                .clickable { onCellClick(cellNum) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$cellNum",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || isCalibrated) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else if (isCalibrated) MaterialTheme.colorScheme.onTertiary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Player",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
