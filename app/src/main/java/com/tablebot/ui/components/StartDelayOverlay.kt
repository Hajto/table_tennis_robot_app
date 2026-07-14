package com.tablebot.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablebot.data.AppPrefs

/**
 * Full-screen dim overlay for the delayed-start lead-in. Two states:
 *
 *  1. **Picker** (`countdownSec == null`) — a single-column seconds wheel (default from
 *     [initialDelaySec], range [AppPrefs.MIN_START_DELAY_SEC]..[AppPrefs.MAX_START_DELAY_SEC]) plus
 *     Confirm / Cancel. Confirm reports the chosen seconds via [onConfirm].
 *  2. **Countdown** (`countdownSec != null`) — a large remaining-seconds display plus Cancel.
 *
 * Tapping the scrim or pressing back invokes [onCancel]. The dim treatment matches `StopOverlay`.
 */
@Composable
fun StartDelayOverlay(
    countdownSec: Int?,
    initialDelaySec: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        // Absorb taps on the interactive content so they don't fall through to the scrim's Cancel.
        val contentInteraction = remember { MutableInteractionSource() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .clickable(
                    interactionSource = contentInteraction,
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (countdownSec == null) {
                PickerState(initialDelaySec = initialDelaySec, onConfirm = onConfirm, onCancel = onCancel)
            } else {
                CountdownState(countdownSec = countdownSec, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun PickerState(
    initialDelaySec: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var sec by remember {
        mutableStateOf(AppPrefs.clampStartDelaySec(initialDelaySec))
    }
    Text(
        "Get in position",
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Countdown before the first ball",
        color = Color.White.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))
    SecondsWheelPicker(
        seconds = sec,
        onSecondsChange = { sec = it },
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color.White)
        }
        Button(onClick = { onConfirm(sec) }) {
            Text("Start")
        }
    }
}

@Composable
private fun CountdownState(
    countdownSec: Int,
    onCancel: () -> Unit,
) {
    Text(
        "Get in position",
        color = Color.White.copy(alpha = 0.7f),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "$countdownSec",
        color = Color.White,
        fontSize = 140.sp,
        fontWeight = FontWeight.Black,
    )
    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onCancel) {
        Text("Cancel", color = Color.White)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Tap anywhere to cancel",
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * A single-column "seconds" wheel reusing [NumberColumn]'s drag/selection styling from
 * [DurationWheelPicker]. Drag up to increase, down to decrease; clamped to the start-delay range.
 */
@Composable
fun SecondsWheelPicker(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minSec: Int = AppPrefs.MIN_START_DELAY_SEC,
    maxSec: Int = AppPrefs.MAX_START_DELAY_SEC,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        NumberColumn(
            value = seconds.coerceIn(minSec, maxSec),
            range = minSec..maxSec,
            label = "sec",
            onChange = { onSecondsChange(it.coerceIn(minSec, maxSec)) },
        )
    }
}
