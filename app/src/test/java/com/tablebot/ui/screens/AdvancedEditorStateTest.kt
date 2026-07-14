package com.tablebot.ui.screens

import com.tablebot.data.AdvancedTraining
import com.tablebot.data.Point
import com.tablebot.data.Step
import org.junit.Assert.*
import org.junit.Test

class AdvancedEditorStateTest {
    private fun state(n: Int): AdvancedEditorState {
        val s = AdvancedEditorState(initial = null, id = 1)
        s.steps = (0 until n).map { Step(ball = 1, spin = 2, power = 2, balls = listOf(Point(it + 1, 2)), ballTime = 9) }
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

    @Test fun `removeStep keeps the correct ball expanded`() {
        val s = state(3)          // balls 0,1,2
        s.toggleExpanded(2)       // ball 2 expanded
        s.removeStep(0)           // now old ball 2 is at index 1
        assertFalse(s.isExpanded(0))
        assertTrue(s.isExpanded(1))
        assertEquals(1, s.lastExpandedIndex())
    }

    @Test fun `removeStep drops the expanded flag of the removed ball`() {
        val s = state(3)
        s.toggleExpanded(1)
        s.removeStep(1)
        assertNull(s.lastExpandedIndex())
    }

    @Test fun `moveStep follows the expanded ball to its new index`() {
        val s = state(3)
        s.toggleExpanded(0)       // ball 0 expanded
        s.moveStep(0, 2)          // swap 0<->2; expanded ball now at index 2
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
            steps = listOf(
                Step(ball = 1, spin = 2, power = 2, balls = listOf(Point(1, 2)), ballTime = 9)
            ),
        )
        s.loadFrom(training)

        assertNull(s.lastExpandedIndex())
        assertFalse(s.isExpanded(2))
    }

    @Test fun `duplicateStep inserts a copy right after the index`() {
        val s = state(3)                                  // positions x = [1,2,3]
        s.duplicateStep(1)                                // duplicate the middle step (x=2)
        assertEquals(4, s.steps.size)
        assertEquals(listOf(1, 2, 2, 3), s.steps.map { it.balls[0].x })
        assertEquals(s.steps[1], s.steps[2])              // the copy equals its source
    }

    @Test fun `duplicateStep shifts expanded flags after the insertion point`() {
        val s = state(3)
        s.toggleExpanded(2)                               // last step expanded
        s.duplicateStep(0)                                // copy inserted at index 1
        assertFalse(s.isExpanded(1))                      // the fresh copy is collapsed
        assertTrue(s.isExpanded(3))                       // old step 2 shifted 2 -> 3
        assertEquals(3, s.lastExpandedIndex())
    }

    @Test fun `duplicateStep keeps the duplicated step's own expanded flag in place`() {
        val s = state(3)
        s.toggleExpanded(0)
        s.duplicateStep(0)                                // index 0 not > 0, so unshifted
        assertTrue(s.isExpanded(0))
        assertFalse(s.isExpanded(1))                      // copy collapsed
    }

    @Test fun `duplicateStep out of range is a no-op`() {
        val s = state(2)
        s.duplicateStep(5)
        assertEquals(2, s.steps.size)
    }
}
