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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A drag-to-set duration picker: two columns (minutes, seconds) of numbers with the selected
 * value in the centre. **Drag up to increase, down to decrease.** The combined mm:ss is clamped
 * to [minSec, maxSec] and reported as whole seconds via [onDurationChange].
 */
@Composable
fun DurationWheelPicker(
    durationSec: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minSec: Int = 1,
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
            NumberColumn(
                value = mm,
                range = 0..(maxSec / 60),
                label = "min",
                onChange = { newMm -> onDurationChange((newMm * 60 + ss).coerceIn(minSec, maxSec)) },
            )
            Text(
                ":",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            NumberColumn(
                value = ss,
                range = 0..59,
                label = "sec",
                onChange = { newSs -> onDurationChange((mm * 60 + newSs).coerceIn(minSec, maxSec)) },
            )
        }
    }
}

internal const val ITEM_HEIGHT_DP = 44

@Composable
internal fun NumberColumn(
    value: Int,
    range: IntRange,
    label: String,
    onChange: (Int) -> Unit,
) {
    // Pixels of vertical drag per one unit of change.
    val stepPx = with(LocalDensity.current) { 40.dp.toPx() }
    // Read the latest value/range/callback inside the long-lived drag gesture without restarting
    // it. onChange closes over the *other* unit (minutes captures seconds and vice-versa), so a
    // stale copy would clobber that unit — e.g. scrolling seconds would reset minutes.
    val current by rememberUpdatedState(value)
    val rng by rememberUpdatedState(range)
    val latestOnChange by rememberUpdatedState(onChange)
    // Derive the target from the total drag since the gesture started, so a fast drag advances
    // by many units in one frame instead of getting stuck on a stale mid-gesture value.
    var startValue by remember { mutableStateOf(value) }
    var dragTotal by remember { mutableStateOf(0f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(88.dp)
                .height((ITEM_HEIGHT_DP * 3).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { startValue = current; dragTotal = 0f },
                    ) { change, dy ->
                        change.consume()
                        dragTotal += dy // drag up (negative dy) increases
                        val target = (startValue - (dragTotal / stepPx).toInt())
                            .coerceIn(rng.first, rng.last)
                        if (target != current) latestOnChange(target)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Selection band behind the centre number.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ITEM_HEIGHT_DP.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (rel in -1..1) {
                    val n = value + rel
                    val selected = rel == 0
                    Box(
                        modifier = Modifier.height(ITEM_HEIGHT_DP.dp).width(88.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (n in rng) "%02d".format(n) else "",
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = if (selected) 34.sp else 18.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
