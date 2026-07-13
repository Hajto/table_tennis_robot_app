package com.tablebot.ui.screens

import com.tablebot.data.Point
import org.junit.Assert.*
import org.junit.Test

class StepBallsEditTest {

    @Test fun `addBallAt adds a ball at the cell`() {
        val result = addBallAt(emptyList(), cell = 8)
        assertEquals(listOf(Point(8, 2)), result)
    }

    @Test fun `addBallAt allows duplicates for weighting`() {
        val result = addBallAt(listOf(Point(8, 2)), cell = 8)
        assertEquals(listOf(Point(8, 2), Point(8, 2)), result)
    }

    @Test fun `addBallAt is a no-op beyond the cap`() {
        val full = List(5) { Point(3, 2) }
        val result = addBallAt(full, cell = 3)
        assertEquals(full, result)
        assertEquals(5, result.size)
    }

    @Test fun `addBallAt caps at the given cap`() {
        val two = listOf(Point(1, 2), Point(2, 2))
        val result = addBallAt(two, cell = 3, cap = 2)
        assertEquals(two, result)
    }

    @Test fun `removeBallAt removes exactly one occurrence preserving weighting`() {
        val balls = listOf(Point(8, 2), Point(8, 2), Point(3, 2))
        val result = removeBallAt(balls, cell = 8)
        assertEquals(listOf(Point(8, 2), Point(3, 2)), result)
    }

    @Test fun `removeBallAt is a no-op when the cell is absent`() {
        val balls = listOf(Point(8, 2), Point(3, 2))
        val result = removeBallAt(balls, cell = 5)
        assertEquals(balls, result)
    }

    @Test fun `removeBallAt on a single occurrence empties that cell`() {
        val balls = listOf(Point(8, 2), Point(3, 2))
        val result = removeBallAt(balls, cell = 8)
        assertEquals(listOf(Point(3, 2)), result)
    }
}
