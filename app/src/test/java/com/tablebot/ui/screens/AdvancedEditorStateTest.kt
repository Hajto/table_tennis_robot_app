package com.tablebot.ui.screens

import com.tablebot.data.AdvancedTraining
import com.tablebot.data.BallEntry
import com.tablebot.data.Point
import org.junit.Assert.*
import org.junit.Test

class AdvancedEditorStateTest {
    private fun state(n: Int): AdvancedEditorState {
        val s = AdvancedEditorState(initial = null, id = 1)
        s.ballList = (0 until n).map { BallEntry(ball = 1, spin = 2, power = 2, points = listOf(Point(it + 1, 2)), ballTime = 9) }
        return s
    }

    @Test fun `no ball expanded by default`() {
        val s = state(3)
        assertNull(s.lastExpandedIndex())
        assertFalse(s.isExpanded(0))
    }

    @Test fun `toggle expands and lastExpandedIndex returns highest expanded`() {
        val s = state(3)
        s.toggleExpanded(0)
        s.toggleExpanded(2)
        assertTrue(s.isExpanded(0))
        assertTrue(s.isExpanded(2))
        assertEquals(2, s.lastExpandedIndex())
    }

    @Test fun `toggle twice collapses`() {
        val s = state(2)
        s.toggleExpanded(1)
        s.toggleExpanded(1)
        assertFalse(s.isExpanded(1))
        assertNull(s.lastExpandedIndex())
    }

    @Test fun `removeBall keeps the correct ball expanded`() {
        val s = state(3)          // balls 0,1,2
        s.toggleExpanded(2)       // ball 2 expanded
        s.removeBall(0)           // now old ball 2 is at index 1
        assertFalse(s.isExpanded(0))
        assertTrue(s.isExpanded(1))
        assertEquals(1, s.lastExpandedIndex())
    }

    @Test fun `removeBall drops the expanded flag of the removed ball`() {
        val s = state(3)
        s.toggleExpanded(1)
        s.removeBall(1)
        assertNull(s.lastExpandedIndex())
    }

    @Test fun `moveBall follows the expanded ball to its new index`() {
        val s = state(3)
        s.toggleExpanded(0)       // ball 0 expanded
        s.moveBall(0, 2)          // swap 0<->2; expanded ball now at index 2
        assertTrue(s.isExpanded(2))
        assertFalse(s.isExpanded(0))
    }

    @Test fun `loadFrom clears expanded indices`() {
        val s = state(3)
        s.toggleExpanded(2)
        assertEquals(2, s.lastExpandedIndex())

        val training = AdvancedTraining(
            id = 2,
            name = "Fresh Drill",
            ballList = listOf(
                BallEntry(ball = 1, spin = 2, power = 2, points = listOf(Point(1, 2)), ballTime = 9)
            ),
        )
        s.loadFrom(training)

        assertNull(s.lastExpandedIndex())
        assertFalse(s.isExpanded(2))
    }
}
