package com.tablebot.viewmodel

import com.tablebot.data.BallEntry
import com.tablebot.data.Point
import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationSeedTest {
    private fun basic(ball: Int, spin: Int, power: Int, cell: Int?): DrillEditorState {
        val s = DrillEditorState(initial = null, id = 1)
        s.ball = ball; s.spin = spin; s.power = power
        s.points = if (cell == null) emptyList() else listOf(Point(cell, 2))
        return s
    }
    private fun advanced(vararg balls: BallEntry): AdvancedEditorState {
        val s = AdvancedEditorState(initial = null, id = 1)
        s.ballList = balls.toList()
        return s
    }
    private fun ball(ball: Int, spin: Int, power: Int, cell: Int) =
        BallEntry(ball = ball, spin = spin, power = power, points = listOf(Point(cell, 2)), ballTime = 9)

    @Test fun `basic tab seeds from basic state`() {
        val seed = calibrationSeed(0, basic(0, 3, 1, 12), advanced(ball(1, 2, 2, 8)))
        assertEquals(CalibrationSeed(0, 3, 1, 12), seed)
    }

    @Test fun `basic tab with no points yields null cell`() {
        val seed = calibrationSeed(0, basic(1, 2, 2, null), advanced(ball(1, 2, 2, 8)))
        assertEquals(CalibrationSeed(1, 2, 2, null), seed)
    }

    @Test fun `dynamic tab seeds from last expanded ball`() {
        val adv = advanced(ball(0, 0, 0, 5), ball(1, 3, 1, 9), ball(2, 4, 2, 14))
        adv.toggleExpanded(1)
        val seed = calibrationSeed(1, basic(1, 2, 2, 8), adv)
        assertEquals(CalibrationSeed(1, 3, 1, 9), seed)
    }

    @Test fun `dynamic tab with nothing expanded seeds from last ball`() {
        val adv = advanced(ball(0, 0, 0, 5), ball(1, 3, 1, 9), ball(2, 4, 2, 14))
        val seed = calibrationSeed(1, basic(1, 2, 2, 8), adv)
        assertEquals(CalibrationSeed(2, 4, 2, 14), seed)
    }
}
