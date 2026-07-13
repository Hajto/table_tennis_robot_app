package com.tablebot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tablebot.ble.ConnectionState

@Composable
fun ConnectionBar(
    state: ConnectionState,
    deviceName: String?,
    statusMessage: String?,
    firmwareVersion: String?,
    isPlaying: Boolean,
    currentTrainingName: String?,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        when (state) {
            ConnectionState.CONNECTED -> if (isPlaying) Color(0xFF1B5E20) else Color(0xFF2E7D32)
            ConnectionState.SCANNING, ConnectionState.CONNECTING -> Color(0xFFE65100)
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "barColor",
    )

    val contentColor = when (state) {
        ConnectionState.CONNECTED, ConnectionState.SCANNING, ConnectionState.CONNECTING -> Color.White
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (state) {
                ConnectionState.CONNECTED -> if (isPlaying) Icons.Default.PlayArrow else Icons.Default.BluetoothConnected
                ConnectionState.SCANNING -> Icons.Default.BluetoothSearching
                ConnectionState.CONNECTING -> Icons.Default.BluetoothSearching
                ConnectionState.DISCONNECTED -> Icons.Default.BluetoothDisabled
            },
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    isPlaying && currentTrainingName != null -> "Playing: $currentTrainingName"
                    state == ConnectionState.CONNECTED -> "Connected: ${deviceName ?: "Robot"}"
                    else -> statusMessage ?: "Tap to connect"
                },
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state == ConnectionState.CONNECTED && isPlaying) {
                Text(
                    text = deviceName ?: "",
                    color = contentColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (state == ConnectionState.CONNECTED && firmwareVersion != null) {
                Text(
                    text = "Firmware $firmwareVersion",
                    color = contentColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        when {
            state == ConnectionState.CONNECTED -> {
                IconButton(onClick = onDisconnectClick) {
                    Icon(Icons.Default.LinkOff, "Disconnect", tint = contentColor)
                }
            }
            state == ConnectionState.DISCONNECTED -> {
                TextButton(onClick = onScanClick) {
                    Text("Connect", color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
