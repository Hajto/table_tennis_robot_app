package com.tablebot.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RobotTypeTest {

    // ── Labels ────────────────────────────────────────────────────────────────

    @Test
    fun `JOOLA_V1 label is Infinity V1`() {
        assertEquals("Infinity V1", RobotType.JOOLA_V1.label)
    }

    @Test
    fun `JOOLA_V2 label is Infinity V2`() {
        assertEquals("Infinity V2", RobotType.JOOLA_V2.label)
    }

    // ── Profile defaults ──────────────────────────────────────────────────────

    @Test
    fun `Profile default robotType is JOOLA_V2`() {
        val profile = Profile(
            id = "test-id",
            name = "Test",
            motorConfigFileName = "motor-config-test.json",
        )
        assertEquals(RobotType.JOOLA_V2, profile.robotType)
    }

    @Test
    fun `Profile can be created with JOOLA_V1`() {
        val profile = Profile(
            id = "test-id",
            name = "Test",
            motorConfigFileName = "motor-config-test.json",
            robotType = RobotType.JOOLA_V1,
        )
        assertEquals(RobotType.JOOLA_V1, profile.robotType)
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Profile serializes and deserializes robotType`() {
        val profile = Profile(
            id = "abc",
            name = "My Robot",
            motorConfigFileName = "motor-config-abc.json",
            robotType = RobotType.JOOLA_V1,
        )
        val encoded = json.encodeToString(profile)
        val decoded = json.decodeFromString<Profile>(encoded)
        assertEquals(RobotType.JOOLA_V1, decoded.robotType)
    }

    @Test
    fun `Profile without robotType field deserializes to V2 default`() {
        // Simulates existing profiles on disk that predate the robotType field
        val legacyJson = """
            {
              "id": "old-id",
              "name": "Old Profile",
              "motorConfigFileName": "motor-config-old.json"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<Profile>(legacyJson)
        assertEquals(RobotType.JOOLA_V2, decoded.robotType)
    }

    @Test
    fun `RobotType round-trips through JSON`() {
        RobotType.entries.forEach { type ->
            val encoded = json.encodeToString(type)
            val decoded = json.decodeFromString<RobotType>(encoded)
            assertEquals(type, decoded)
        }
    }
}
