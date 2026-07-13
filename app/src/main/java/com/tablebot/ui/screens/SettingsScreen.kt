package com.tablebot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.data.AppPrefs
import com.tablebot.ui.components.StepSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val showFieldNumbers by AppPrefs.showFieldNumbers.collectAsState()
    val debugMode by AppPrefs.debugMode.collectAsState()
    val inferRowCalibration by AppPrefs.inferRowCalibration.collectAsState()
    val ballTrayCapacity by AppPrefs.ballTrayCapacity.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsToggle(
                title = "Show field numbers",
                subtitle = "Display position numbers (1-15) on the table grid",
                checked = showFieldNumbers,
                onCheckedChange = { AppPrefs.setShowFieldNumbers(it) },
            )

            HorizontalDivider()

            SettingsToggle(
                title = "Enable debug mode",
                subtitle = "Show debug tools in the menu",
                checked = debugMode,
                onCheckedChange = { AppPrefs.setDebugMode(it) },
            )

            HorizontalDivider()

            SettingsToggle(
                title = "Infer calibration row from ends",
                subtitle = "Experimental: adds a button on the calibration screen to fill a row's middle cells by interpolating its two calibrated ends",
                checked = inferRowCalibration,
                onCheckedChange = { AppPrefs.setInferRowCalibration(it) },
            )

            HorizontalDivider()

            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text("Ball tray capacity", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Caps how many balls a drill will request in Reps and Ball-count modes. Lower it if you run without wings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                StepSlider(
                    label = "Capacity",
                    value = ballTrayCapacity,
                    range = 10..250,
                    displayValue = { "$it balls" },
                    onValueChange = { AppPrefs.setBallTrayCapacity(it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
