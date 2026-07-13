package com.tablebot.viewmodel

import com.tablebot.data.AdvancedTraining
import com.tablebot.data.BasicTraining
import com.tablebot.data.PlayMode

/** Firmware repeat count is a single byte. */
const val MAX_REPS = 255

fun ballsPerPatternBasic(t: BasicTraining): Int = t.points.size
fun ballsPerPatternAdvanced(t: AdvancedTraining): Int = t.ballList.sumOf { it.points.size }

/** reps to send to the firmware; timedDurationSec is non-null only for TIMED (stop the drill after it). */
data class ResolvedPlay(val reps: Int, val timedDurationSec: Int?)

private fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) 1 else (a + b - 1) / b

/**
 * Turn a drill's play mode into a firmware repeat count.
 * - REPETITIONS: the reps value.
 * - BALL_COUNT: ceil(ballCount / ballsPerPattern) — round up.
 * - TIMED: enough reps (capped) to cover durationSec at the given ballTime, plus the duration to stop after.
 */
fun resolvePlay(
    mode: PlayMode,
    reps: Int,
    ballCount: Int,
    durationSec: Int,
    ballsPerPattern: Int,
    ballTimeTenths: Int,
): ResolvedPlay {
    val bpp = ballsPerPattern.coerceAtLeast(1)
    return when (mode) {
        PlayMode.REPETITIONS -> ResolvedPlay(reps.coerceIn(1, MAX_REPS), null)
        PlayMode.BALL_COUNT -> ResolvedPlay(ceilDiv(ballCount, bpp).coerceIn(1, MAX_REPS), null)
        PlayMode.TIMED -> {
            val perBall = ballTimeTenths.coerceAtLeast(1)
            val estBalls = ceilDiv(durationSec * 10, perBall)
            ResolvedPlay(ceilDiv(estBalls, bpp).coerceIn(1, MAX_REPS), durationSec)
        }
    }
}
