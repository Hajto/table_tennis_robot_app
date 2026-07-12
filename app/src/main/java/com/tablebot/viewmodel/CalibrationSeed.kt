package com.tablebot.viewmodel

import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState

/** Ball context handed to the calibration screen so it opens on the ball being edited. */
data class CalibrationSeed(val ball: Int, val spin: Int, val power: Int, val cell: Int?)

/**
 * Derives the calibration seed from the active editor tab.
 * Basic (mode 0): the single basic ball. Dynamic (mode 1): the last expanded ball entry,
 * else the last entry in the list. Empty ball list falls back to the basic ball.
 */
fun calibrationSeed(mode: Int, basic: DrillEditorState, advanced: AdvancedEditorState): CalibrationSeed {
    if (mode == 0) {
        return CalibrationSeed(basic.ball, basic.spin, basic.power, basic.points.firstOrNull()?.x)
    }
    val list = advanced.ballList
    if (list.isEmpty()) {
        return CalibrationSeed(basic.ball, basic.spin, basic.power, basic.points.firstOrNull()?.x)
    }
    val index = advanced.lastExpandedIndex() ?: list.lastIndex
    val entry = list[index]
    return CalibrationSeed(entry.ball, entry.spin, entry.power, entry.points.firstOrNull()?.x)
}
