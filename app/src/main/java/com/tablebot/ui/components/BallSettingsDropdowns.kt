package com.tablebot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.data.BallType
import com.tablebot.data.PowerType
import com.tablebot.data.SpinType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LabeledDropdown(
    label: String,
    entries: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(labelOf(entry)) },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun BallSettingsDropdowns(
    ball: Int,
    spin: Int,
    power: Int,
    onBallChange: (Int) -> Unit,
    onSpinChange: (Int) -> Unit,
    onPowerChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledDropdown(
                label = "Ball Type",
                entries = BallType.entries.toList(),
                selected = BallType.fromValue(ball),
                labelOf = { it.label },
                onSelect = { onBallChange(it.value) },
                modifier = Modifier.weight(1f),
            )
            LabeledDropdown(
                label = "Power",
                entries = PowerType.entries.toList(),
                selected = PowerType.fromValue(power),
                labelOf = { it.label },
                onSelect = { onPowerChange(it.value) },
                modifier = Modifier.weight(1f),
            )
        }
        LabeledDropdown(
            label = "Spin",
            entries = SpinType.entries.toList(),
            selected = SpinType.fromValue(spin),
            labelOf = { it.label },
            onSelect = { onSpinChange(it.value) },
        )
    }
}
