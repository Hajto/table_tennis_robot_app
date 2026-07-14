package com.tablebot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tablebot.data.AppPrefs

private val BAR_HEIGHT = 48.dp
private val CORNER = 24.dp

/**
 * QuickPlay's split Play button. The wide (~80%) primary segment shows the current start mode and
 * plays in it — `Start now` fires immediately via [onPlayNow]; `Delayed` opens the lead-in overlay
 * via [onPlayDelayed]. The narrow (~20%) segment is an up-arrow that opens a menu to change the
 * remembered mode ([AppPrefs.startDelayed]) without starting a drill.
 */
@Composable
fun SegmentedPlayButton(
    enabled: Boolean,
    onPlayNow: () -> Unit,
    onPlayDelayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val delayed by AppPrefs.startDelayed.collectAsState()
    val delaySec by AppPrefs.startDelaySec.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    Row(modifier.height(BAR_HEIGHT)) {
        // Primary segment (~80%) — plays in the current mode.
        Button(
            onClick = { if (delayed) onPlayDelayed() else onPlayNow() },
            enabled = enabled,
            shape = RoundedCornerShape(topStart = CORNER, bottomStart = CORNER),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(0.8f).fillMaxHeight(),
        ) {
            Icon(
                if (delayed) Icons.Default.Timer else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (delayed) "Delayed ${delaySec}s" else "Start now",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Hairline divider so the two segments read as one split control.
        Spacer(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)),
        )

        // Chooser segment (~20%) — opens the mode menu; never starts a drill.
        Box(Modifier.weight(0.2f).fillMaxHeight()) {
            Button(
                onClick = { menuOpen = true },
                enabled = enabled,
                shape = RoundedCornerShape(topEnd = CORNER, bottomEnd = CORNER),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    Icons.Default.ArrowDropUp,
                    contentDescription = "Change start mode",
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ModeItem(
                    label = "Start now",
                    icon = Icons.Default.PlayArrow,
                    selected = !delayed,
                ) { AppPrefs.setStartDelayed(false); menuOpen = false }
                ModeItem(
                    label = "Delayed",
                    icon = Icons.Default.Timer,
                    selected = delayed,
                ) { AppPrefs.setStartDelayed(true); menuOpen = false }
            }
        }
    }
}

@Composable
private fun ModeItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { if (selected) Icon(Icons.Default.Check, contentDescription = "Selected") },
    )
}
