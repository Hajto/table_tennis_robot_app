package com.tablebot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.tablebot.data.PlayMode

/** mm:ss for a whole-second duration (e.g. 90 -> "1:30"). */
fun formatDurationMmSs(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayModeSelector(
    playMode: Int,
    reps: Int,
    ballCount: Int,
    durationSec: Int,
    ballsPerPattern: Int,
    repsRange: IntRange,
    onPlayModeChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onBallCountChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf("Reps", "Balls", "Time")
    val trayCapacity by AppPrefs.ballTrayCapacity.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = playMode == i,
                    onClick = { onPlayModeChange(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(label) }
            }
        }
        when (PlayMode.fromValue(playMode)) {
            PlayMode.REPETITIONS -> {
                val bpp = ballsPerPattern.coerceAtLeast(1)
                // Don't offer more reps than would empty the tray (reps x balls-per-pattern).
                val maxReps = (trayCapacity / bpp).coerceIn(repsRange.first, repsRange.last)
                StepSlider("Repetitions", reps, repsRange.first..maxReps, displayValue = { "$it  (≈ ${it * bpp} balls)" }) {
                    onRepsChange(it)
                }
            }
            PlayMode.BALL_COUNT -> {
                val bpp = ballsPerPattern.coerceAtLeast(1)
                StepSlider("Ball count", ballCount, 1..trayCapacity, displayValue = { "$it  (≈ ${(it + bpp - 1) / bpp} reps)" }) {
                    onBallCountChange(it)
                }
            }
            PlayMode.TIMED -> {
                DurationWheelPicker(
                    durationSec = durationSec,
                    onDurationChange = onDurationChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
