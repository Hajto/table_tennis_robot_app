package com.tablebot.viewmodel

import com.tablebot.data.MotorParams
import kotlin.math.roundToInt

/**
 * PoC: infer a grid row's middle cells (columns 2–4) by linearly interpolating between the
 * two hand-calibrated ends — leftmost (column 1) and rightmost (column 5).
 *
 * Basis (from analyzing the exported motor config): within a row the wheel speeds, Y and Z
 * axes are ~constant and the X axis (horizontal aim) is ~linear across the 5 columns, centered
 * at column 3. So calibrating just the two ends and interpolating reproduces the factory grid
 * to within ~1–2 raw units. This is experimental — verify on the robot before relying on it.
 *
 * The 3×5 grid landareas (1..15):
 * ```
 *   1  2  3  4  5   (row 0, near net)
 *   6  7  8  9 10   (row 1, middle)
 *  11 12 13 14 15   (row 2, near player)
 * ```
 */

/** Grid row (0 near net .. 2 near player) for a 1..15 landarea. */
fun rowOf(cell: Int): Int = (cell - 1) / 5

/** Leftmost (column 1) landarea of the row containing [cell]. */
fun rowLeftCell(cell: Int): Int = rowOf(cell) * 5 + 1

/** Rightmost (column 5) landarea of the row containing [cell]. */
fun rowRightCell(cell: Int): Int = rowOf(cell) * 5 + 5

/** The three middle landareas (columns 2, 3, 4) of the row containing [cell]. */
fun rowMiddleCells(cell: Int): List<Int> = rowOf(cell).let { r -> listOf(r * 5 + 2, r * 5 + 3, r * 5 + 4) }

/** 0.0 at column 1 .. 1.0 at column 5, for a 1..15 landarea. */
fun columnFraction(cell: Int): Double = ((cell - 1) % 5) / 4.0

private fun lerp(a: Int, b: Int, t: Double): Int = (a + (b - a) * t).roundToInt()

/**
 * Interpolate motor values for [target]'s column between the calibrated row ends [left]
 * (column 1) and [right] (column 5). Preserves [target]'s id/ball/spin/power/landarea so the
 * result can be saved directly over the existing entry.
 */
fun interpolateCell(left: MotorParams, right: MotorParams, target: MotorParams): MotorParams {
    val t = columnFraction(target.landarea)
    return target.copy(
        m1speed = lerp(left.m1speed, right.m1speed, t),
        m2speed = lerp(left.m2speed, right.m2speed, t),
        xaxis = lerp(left.xaxis, right.xaxis, t),
        yaxis = lerp(left.yaxis, right.yaxis, t),
        zaxis = lerp(left.zaxis, right.zaxis, t),
    )
}
