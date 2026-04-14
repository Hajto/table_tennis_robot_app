package com.tablebot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StepSlider(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    displayValue: ((Int) -> String)? = null,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            if (displayValue != null) "$label: ${displayValue(value)}" else "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalIconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first,
                modifier = Modifier.size(36.dp),
            ) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            FilledTonalIconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last,
                modifier = Modifier.size(36.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
