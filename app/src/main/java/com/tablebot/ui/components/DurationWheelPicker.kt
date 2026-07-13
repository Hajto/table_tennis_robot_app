package com.tablebot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A flip-clock-style duration picker. Two reels (minutes, seconds) that you drag up/down to set;
 * dragging up increases the value. The combined mm:ss is clamped to [minSec, maxSec] and reported
 * as whole seconds via [onDurationChange].
 */
@Composable
fun DurationWheelPicker(
    durationSec: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minSec: Int = 15,
    maxSec: Int = 30 * 60,
) {
    val mm = durationSec / 60
    val ss = durationSec % 60
    Column(modifier) {
        Text("Duration", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DigitReel(
                value = mm,
                range = 0..(maxSec / 60),
                label = "min",
                onChange = { newMm -> onDurationChange((newMm * 60 + ss).coerceIn(minSec, maxSec)) },
            )
            Text(
                ":",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            DigitReel(
                value = ss,
                range = 0..59,
                label = "sec",
                onChange = { newSs -> onDurationChange((mm * 60 + newSs).coerceIn(minSec, maxSec)) },
            )
        }
    }
}

private const val REEL_ITEM_HEIGHT_DP = 56

@Composable
private fun DigitReel(
    value: Int,
    range: IntRange,
    label: String,
    onChange: (Int) -> Unit,
) {
    val stepPx = with(LocalDensity.current) { REEL_ITEM_HEIGHT_DP.dp.toPx() }
    // Read the latest value/range inside the long-lived drag gesture without restarting it.
    val current by rememberUpdatedState(value)
    val rng by rememberUpdatedState(range)
    var offset by remember { mutableStateOf(0f) }

    val bg = MaterialTheme.colorScheme.inverseSurface
    val fg = MaterialTheme.colorScheme.inverseOnSurface

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .height((REEL_ITEM_HEIGHT_DP * 3).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { offset = 0f },
                        onDragCancel = { offset = 0f },
                    ) { change, dy ->
                        change.consume()
                        offset += dy
                        // Drag up (negative dy) increases the value.
                        while (offset <= -stepPx) {
                            offset += stepPx
                            if (current + 1 <= rng.last) onChange(current + 1) else offset = 0f
                        }
                        while (offset >= stepPx) {
                            offset -= stepPx
                            if (current - 1 >= rng.first) onChange(current - 1) else offset = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Five stacked rows (centre ± 2) translated by the live drag offset, clipped to 3 rows.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset { IntOffset(0, offset.roundToInt()) },
            ) {
                for (rel in -2..2) {
                    val n = value + rel
                    val inRange = n in rng
                    val (size, alpha) = when (kotlin.math.abs(rel)) {
                        0 -> 34.sp to 1f
                        1 -> 22.sp to 0.45f
                        else -> 15.sp to 0.2f
                    }
                    Box(
                        modifier = Modifier.height(REEL_ITEM_HEIGHT_DP.dp).width(84.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (inRange) "%02d".format(n) else "",
                            color = fg.copy(alpha = alpha),
                            fontSize = size,
                            fontWeight = if (rel == 0) FontWeight.Black else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            // Flip-clock seam across the middle of the current digit.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.25f)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
