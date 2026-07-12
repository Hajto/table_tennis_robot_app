package com.tablebot.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.ble.BlePermissions
import com.tablebot.ble.ConnectionState
import com.tablebot.ble.RobotProtocol
import com.tablebot.ui.components.ConnectionBar
import com.tablebot.ui.components.StepSlider
import com.tablebot.viewmodel.RobotViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(robotVm: RobotViewModel) {
    val connectionState by robotVm.connectionState.collectAsState()
    val deviceName by robotVm.deviceName.collectAsState()
    val statusMessage by robotVm.statusMessage.collectAsState()
    val firmwareVersion by robotVm.firmwareVersion.collectAsState()
    val isPlaying by robotVm.isPlaying.collectAsState()
    val currentTrainingName by robotVm.currentTrainingName.collectAsState()
    val scope = rememberCoroutineScope()

    var cmdByte by remember { mutableIntStateOf(0x98) }
    var m1 by remember { mutableIntStateOf(20) }
    var m2 by remember { mutableIntStateOf(20) }
    var xAxis by remember { mutableIntStateOf(20) }
    var yAxis by remember { mutableIntStateOf(15) }
    var zAxis by remember { mutableIntStateOf(10) }
    var ballTime by remember { mutableIntStateOf(15) }
    var spin by remember { mutableIntStateOf(2) }
    var posX by remember { mutableIntStateOf(8) }
    var posY by remember { mutableIntStateOf(2) }

    val connected = connectionState == ConnectionState.CONNECTED

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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Raw Motor Debug") })
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Adjust values and send directly to robot", style = MaterialTheme.typography.bodySmall)

            // Command byte selector
            StepSlider("Cmd Byte (0x${"%02x".format(cmdByte)})", cmdByte, 0..255) { cmdByte = it }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0x01 to "PAT", 0x02 to "02", 0x03 to "CAL", 0x04 to "04", 0x05 to "STP", 0x06 to "06", 0x07 to "07", 0x0A to "0A").forEach { (cmd, label) ->
                    FilterChip(
                        selected = cmdByte == cmd,
                        onClick = { cmdByte = cmd },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            HorizontalDivider()

            StepSlider("M1 Speed", m1, 0..255) { m1 = it }
            StepSlider("M2 Speed", m2, 0..255) { m2 = it }
            StepSlider("X Axis", xAxis, 0..255) { xAxis = it }
            StepSlider("Y Axis", yAxis, 0..255) { yAxis = it }
            StepSlider("Z Axis", zAxis, 0..255) { zAxis = it }
            StepSlider("Ball Time", ballTime, 1..60) { ballTime = it }
            StepSlider("Spin", spin, 0..4) { spin = it }
            StepSlider("Pos X (1-15)", posX, 1..15) { posX = it }
            StepSlider("Pos Y (depth)", posY, 1..3) { posY = it }

            HorizontalDivider()

            // Show the raw bytes that will be sent
            val payload = buildRawPayload(m1, m2, xAxis, yAxis, zAxis, ballTime, spin, posX, posY)
            Text(
                "Payload: ${payload.joinToString(" ") { "%02x".format(it) }}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val frame = RobotProtocol.buildFrame(
                                robotVm.robotManager.deviceId,
                                cmdByte.toByte(),
                                payload,
                            )
                            robotVm.robotManager.sendFrame(frame)
                        }
                    },
                    enabled = connected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("fec7")
                }

                Button(
                    onClick = {
                        scope.launch {
                            val frame = RobotProtocol.buildFrame(
                                robotVm.robotManager.deviceId,
                                cmdByte.toByte(),
                                payload,
                            )
                            robotVm.robotManager.sendToDataChar(frame)
                        }
                    },
                    enabled = connected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("fed5")
                }

                Button(
                    onClick = { scope.launch { robotVm.stop() } },
                    enabled = connected,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("STOP")
                }
            }

            HorizontalDivider()
            Text("Try: Send on fec7 first, then fed5. Or vice versa.", style = MaterialTheme.typography.bodySmall)

            // Also try raw payload without frame wrapping (just the bytes)
            Button(
                onClick = {
                    scope.launch {
                        robotVm.robotManager.sendRawToDataChar(payload)
                    }
                },
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("RAW to fed5 (no frame)")
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun buildRawPayload(
    m1: Int, m2: Int, x: Int, y: Int, z: Int,
    ballTime: Int, spin: Int, posX: Int, posY: Int,
): ByteArray {
    val buf = ByteArray(16) // 12 per point + 4 trailer
    buf[0] = (m1 and 0xFF).toByte()
    buf[1] = (m2 and 0xFF).toByte()
    buf[2] = (x and 0xFF).toByte()
    buf[3] = (y and 0xFF).toByte()
    buf[4] = (z and 0xFF).toByte()
    buf[5] = ((ballTime shr 8) and 0xFF).toByte()
    buf[6] = (ballTime and 0xFF).toByte()
    buf[7] = (spin and 0xFF).toByte()
    buf[8] = (posX and 0xFF).toByte()
    buf[9] = (posY and 0xFF).toByte()
    buf[10] = 0
    buf[11] = 0
    // trailer
    buf[12] = 1
    buf[13] = 1
    buf[14] = 0
    buf[15] = 0
    return buf
}

