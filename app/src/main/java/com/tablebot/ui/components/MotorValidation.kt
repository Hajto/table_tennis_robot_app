package com.tablebot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.tablebot.data.MotorConfig
import com.tablebot.data.Point

data class MotorConstraints(
    val validSpins: Set<Int>?,
    val validPowers: Set<Int>?,
    val enabledCells: Set<Int>?,
)

/**
 * Computes motor placement constraints and auto-corrects invalid selections.
 * Reusable across basic and advanced editors.
 */
@Composable
fun rememberMotorConstraints(
    ball: Int,
    spin: Int,
    power: Int,
    points: List<Point>,
    motorConfig: MotorConfig?,
    onSpinChange: (Int) -> Unit,
    onPowerChange: (Int) -> Unit,
    onPointsChange: (List<Point>) -> Unit,
): MotorConstraints {
    val validSpins = remember(ball) { motorConfig?.validSpins(ball) }
    val validPowers = remember(ball, spin) { motorConfig?.validPowers(ball, spin) }
    val enabledCells = remember(ball, spin, power) { motorConfig?.validLandareas(ball, spin, power) }

    LaunchedEffect(ball) {
        val allowed = validSpins ?: return@LaunchedEffect
        if (spin !in allowed) onSpinChange(allowed.minOrNull() ?: 2)
    }
    LaunchedEffect(ball, spin) {
        val allowed = validPowers ?: return@LaunchedEffect
        if (power !in allowed) onPowerChange(allowed.minOrNull() ?: 2)
    }
    LaunchedEffect(enabledCells) {
        val allowed = enabledCells ?: return@LaunchedEffect
        if (allowed.isEmpty()) return@LaunchedEffect
        val filtered = points.filter { it.x in allowed }
        if (filtered.size != points.size) onPointsChange(filtered)
    }

    return MotorConstraints(validSpins, validPowers, enabledCells)
}
