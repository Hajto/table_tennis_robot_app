package com.tablebot.viewmodel

import com.tablebot.data.BallEntry
import com.tablebot.data.BasicTraining
import com.tablebot.data.AdvancedTraining
import com.tablebot.data.PlayMode
import com.tablebot.data.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayResolverTest {
    @Test fun `ballsPerPattern basic is point count`() {
        val t = BasicTraining(id = 1, name = "x", points = listOf(Point(1, 2), Point(2, 2), Point(3, 2)))
        assertEquals(3, ballsPerPatternBasic(t))
    }

    @Test fun `ballsPerPattern advanced sums points across entries`() {
        val t = AdvancedTraining(id = 1, name = "x", ballList = listOf(
            BallEntry(points = listOf(Point(1, 2), Point(2, 2))),
            BallEntry(points = listOf(Point(3, 2))),
        ))
        assertEquals(3, ballsPerPatternAdvanced(t))
    }

    @Test fun `repetitions passes reps through, clamped`() {
        assertEquals(ResolvedPlay(10, null), resolvePlay(PlayMode.REPETITIONS, 10, 30, 60, 3, 9))
        assertEquals(ResolvedPlay(255, null), resolvePlay(PlayMode.REPETITIONS, 9999, 30, 60, 3, 9))
        assertEquals(ResolvedPlay(1, null), resolvePlay(PlayMode.REPETITIONS, 0, 30, 60, 3, 9))
    }

    @Test fun `ball count rounds up`() {
        assertEquals(ResolvedPlay(10, null), resolvePlay(PlayMode.BALL_COUNT, 5, 30, 60, 3, 9)) // 30/3
        assertEquals(ResolvedPlay(11, null), resolvePlay(PlayMode.BALL_COUNT, 5, 31, 60, 3, 9)) // ceil(31/3)
        assertEquals(ResolvedPlay(1, null), resolvePlay(PlayMode.BALL_COUNT, 5, 1, 60, 3, 9))
    }

    @Test fun `timed sizes reps to cover duration and reports duration`() {
        // 60s, ballTime 10 tenths = 1s/ball -> 60 balls; 3 per pattern -> 20 reps
        assertEquals(ResolvedPlay(20, 60), resolvePlay(PlayMode.TIMED, 5, 30, 60, 3, 10))
    }

    @Test fun `timed caps reps at 255`() {
        // 3600s at 1 ball/s = 3600 balls / 1 per pattern -> capped at 255
        val r = resolvePlay(PlayMode.TIMED, 5, 30, 3600, 1, 10)
        assertEquals(255, r.reps)
        assertEquals(3600, r.timedDurationSec)
    }
}
