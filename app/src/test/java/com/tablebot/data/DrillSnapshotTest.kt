package com.tablebot.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DrillSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun basicTraining() = BasicTraining(
        id = 7, name = "Forehand Loop", ball = 1, spin = 1, power = 2,
        landType = 2, ballTime = 9, times = 20,
        points = listOf(Point(8), Point(12)),
    )

    private fun advancedTraining() = AdvancedTraining(
        id = 3, name = "Two-Ball Combo", repeatNum = 10, repeatDelay = 1,
        ballList = listOf(BallEntry(points = listOf(Point(6))), BallEntry(spin = 3)),
    )

    @Test
    fun `basic snapshot round-trips with type discriminator`() {
        val entry = HistoryEntry(
            trainingName = "Forehand Loop", trainingType = "basic", trainingId = 7,
            timestamp = 1_700_000_000_000L,
            snapshot = DrillSnapshot.Basic(basicTraining(), timesOverride = 5, ballTimeOverride = 12),
            profileName = "Infinity", robotType = RobotType.JOOLA_V2,
        )
        val encoded = json.encodeToString(entry)
        assertTrue(encoded.contains("\"type\":\"basic\""))
        assertEquals(entry, json.decodeFromString<HistoryEntry>(encoded))
    }

    @Test
    fun `advanced snapshot round-trips with type discriminator`() {
        val entry = HistoryEntry(
            trainingName = "Two-Ball Combo", trainingType = "advanced", trainingId = 3,
            timestamp = 1_700_000_000_000L,
            snapshot = DrillSnapshot.Advanced(advancedTraining(), repeatNumOverride = 4, repeatDelayOverride = 2),
            profileName = "Garage table", robotType = RobotType.JOOLA_V1,
        )
        val encoded = json.encodeToString(entry)
        assertTrue(encoded.contains("\"type\":\"advanced\""))
        assertEquals(entry, json.decodeFromString<HistoryEntry>(encoded))
    }

    @Test
    fun `legacy entry without new fields decodes to nulls`() {
        val legacy = """
            {"trainingName":"Old Drill","trainingType":"basic","trainingId":1,"timestamp":1744000000000}
        """.trimIndent()
        val entry = json.decodeFromString<HistoryEntry>(legacy)
        assertNull(entry.snapshot)
        assertNull(entry.profileName)
        assertNull(entry.robotType)
        assertEquals("Old Drill", entry.trainingName)
    }
}
