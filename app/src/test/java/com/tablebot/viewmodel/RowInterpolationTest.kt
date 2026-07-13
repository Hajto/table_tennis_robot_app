package com.tablebot.viewmodel

import com.tablebot.data.MotorParams
import org.junit.Assert.assertEquals
import org.junit.Test

class RowInterpolationTest {
    private fun mp(cell: Int, m1: Int = 0, m2: Int = 0, x: Int = 0, y: Int = 0, z: Int = 0) =
        MotorParams(id = cell, ball = 1, spin = 3, power = 3, landarea = cell,
            m1speed = m1, m2speed = m2, xaxis = x, yaxis = y, zaxis = z)

    @Test
    fun `row helpers map cells to ends and middles`() {
        assertEquals(2, rowOf(13))
        assertEquals(11, rowLeftCell(13))
        assertEquals(15, rowRightCell(13))
        assertEquals(listOf(12, 13, 14), rowMiddleCells(13))
        assertEquals(0, rowOf(3)); assertEquals(1, rowLeftCell(3)); assertEquals(5, rowRightCell(3))
    }

    @Test
    fun `column fraction is 0 at left, 0_5 at center, 1 at right`() {
        assertEquals(0.0, columnFraction(11), 1e-9)
        assertEquals(0.5, columnFraction(13), 1e-9)
        assertEquals(1.0, columnFraction(15), 1e-9)
    }

    @Test
    fun `interpolates xaxis linearly and holds constant fields`() {
        // backspin-normal last row ends: x 7..33; m1=9,m2=21,y=15,z=10 on both ends
        val left = mp(11, m1 = 9, m2 = 21, x = 7, y = 15, z = 10)
        val right = mp(15, m1 = 9, m2 = 21, x = 33, y = 15, z = 10)

        val c13 = interpolateCell(left, right, mp(13))
        assertEquals(20, c13.xaxis)   // center column
        assertEquals(9, c13.m1speed)
        assertEquals(21, c13.m2speed)
        assertEquals(15, c13.yaxis)
        assertEquals(10, c13.zaxis)
        assertEquals(13, c13.landarea) // identity preserved
        assertEquals(1, c13.ball)

        // col 2 (t=0.25): 7 + 26*0.25 = 13.5 -> 14 ; col 4 (t=0.75): 7 + 26*0.75 = 26.5 -> 27
        assertEquals(14, interpolateCell(left, right, mp(12)).xaxis)
        assertEquals(27, interpolateCell(left, right, mp(14)).xaxis)
    }

    @Test
    fun `interpolates differing wheel speeds across the row`() {
        // hypothetical: speeds ramp 10 -> 18 across the row
        val left = mp(6, m1 = 10, m2 = 12, x = 5)
        val right = mp(10, m1 = 18, m2 = 20, x = 35)
        val c8 = interpolateCell(left, right, mp(8))   // center
        assertEquals(14, c8.m1speed)  // (10+18)/2
        assertEquals(16, c8.m2speed)  // (12+20)/2
        assertEquals(20, c8.xaxis)    // (5+35)/2
    }
}
