package com.tablebot.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.data.AppPrefs

/**
 * QuickPlay's two-segment Play control: `Start now` / `Delayed`, highlighting the remembered mode
 * from [AppPrefs.startDelayed]. Pressing a segment persists that mode and runs it — `Start now`
 * fires immediately via [onPlayNow]; `Delayed` opens the lead-in overlay via [onPlayDelayed].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedPlayButton(
    enabled: Boolean,
    onPlayNow: () -> Unit,
    onPlayDelayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val delayed by AppPrefs.startDelayed.collectAsState()
    SingleChoiceSegmentedButtonRow(modifier) {
        SegmentedButton(
            selected = !delayed,
            enabled = enabled,
            onClick = {
                AppPrefs.setStartDelayed(false)
                onPlayNow()
            },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = {},
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start now")
        }
        SegmentedButton(
            selected = delayed,
            enabled = enabled,
            onClick = {
                AppPrefs.setStartDelayed(true)
                onPlayDelayed()
            },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = {},
        ) {
            Icon(Icons.Default.Timer, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Delayed")
        }
    }
}
