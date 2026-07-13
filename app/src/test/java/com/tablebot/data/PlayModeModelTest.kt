package com.tablebot.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `PlayMode fromValue maps and defaults`() {
        assertEquals(PlayMode.REPETITIONS, PlayMode.fromValue(0))
        assertEquals(PlayMode.BALL_COUNT, PlayMode.fromValue(1))
        assertEquals(PlayMode.TIMED, PlayMode.fromValue(2))
        assertEquals(PlayMode.REPETITIONS, PlayMode.fromValue(99))
    }

    @Test fun `legacy BasicTraining JSON without new fields defaults to repetitions`() {
        val legacy = """{"id":1,"name":"x","points":[{"x":8,"y":2}]}"""
        val t = json.decodeFromString<BasicTraining>(legacy)
        assertEquals(0, t.playMode)
        assertEquals(30, t.ballCount)
        assertEquals(60, t.durationSec)
    }

    @Test fun `legacy AdvancedTraining JSON without new fields defaults to repetitions`() {
        val legacy = """{"id":1,"name":"x"}"""
        val t = json.decodeFromString<AdvancedTraining>(legacy)
        assertEquals(0, t.playMode)
        assertEquals(30, t.ballCount)
        assertEquals(60, t.durationSec)
    }
}
