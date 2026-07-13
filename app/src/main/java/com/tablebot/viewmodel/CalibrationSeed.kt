package com.tablebot.viewmodel

import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState

/** Ball context handed to the calibration screen so it opens on the ball being edited. */
data class CalibrationSeed(val ball: Int, val spin: Int, val power: Int, val cell: Int?)

/**
 * Derives the calibration seed from the active editor tab.
 * Basic (mode 0): the single basic ball. Dynamic (mode 1): the last expanded step,
 * else the last step in the list. Empty step list falls back to the basic ball.
 */
fun calibrationSeed(mode: Int, basic: DrillEditorState, advanced: AdvancedEditorState): CalibrationSeed {
    if (mode == 0) {
        return CalibrationSeed(basic.ball, basic.spin, basic.power, basic.points.firstOrNull()?.x)
    }
    val list = advanced.steps
    if (list.isEmpty()) {
        return CalibrationSeed(basic.ball, basic.spin, basic.power, basic.points.firstOrNull()?.x)
    }
    val index = advanced.lastExpandedIndex()?.takeIf { it in list.indices } ?: list.lastIndex
    val step = list[index]
    return CalibrationSeed(step.ball, step.spin, step.power, step.balls.firstOrNull()?.x)
}
