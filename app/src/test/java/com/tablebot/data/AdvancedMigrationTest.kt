package com.tablebot.data

import org.junit.Assert.*
import org.junit.Test

class AdvancedMigrationTest {
    private fun be(random: Int, vararg xs: Int) =
        BallEntry(ball = 1, spin = 2, power = 2, ballTime = 9, random = random, points = xs.map { Point(it, 2) })

    @Test fun `single-point random becomes single-ball order-random step`() {
        val s = migrateBallEntriesToSteps(listOf(be(1, 8)))
        assertEquals(1, s.size); assertEquals(1, s[0].balls.size); assertTrue(s[0].orderRandom)
    }

    @Test fun `2 to 5 point random becomes one within-random step`() {
        val s = migrateBallEntriesToSteps(listOf(be(1, 6, 8, 10)))
        assertEquals(1, s.size); assertEquals(3, s[0].balls.size); assertFalse(s[0].orderRandom)
    }

    @Test fun `over 5 point random splits into chunks of 5`() {
        val s = migrateBallEntriesToSteps(listOf(be(1, 1,2,3,4,5,6,7)))
        assertEquals(2, s.size); assertEquals(5, s[0].balls.size); assertEquals(2, s[1].balls.size)
    }

    @Test fun `non-random multi-point becomes N single-ball steps in order`() {
        val s = migrateBallEntriesToSteps(listOf(be(0, 6, 8, 10)))
        assertEquals(3, s.size); assertTrue(s.all { it.balls.size == 1 && !it.orderRandom })
    }

    @Test fun `legacy ballList json normalizes into steps`() {
        val old = """{"id":1,"name":"t","ballList":[{"ball":1,"spin":2,"power":2,"points":[{"x":8,"y":2}],"random":1}]}"""
        val t = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(AdvancedTraining.serializer(), old).migrated()
        assertEquals(1, t.steps.size); assertTrue(t.steps[0].orderRandom); assertNull(t.legacyBallList)
    }
}
