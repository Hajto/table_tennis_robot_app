package com.tablebot.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val T0 = 1_700_000_000_000L
    private val MIN = 60_000L

    private fun file() = File(tmp.root, "training-history.json")

    private fun entry(
        ts: Long,
        name: String = "Drill",
        snapshot: DrillSnapshot? = null,
    ) = HistoryEntry(
        trainingName = name, trainingType = "basic", trainingId = 1,
        timestamp = ts, snapshot = snapshot,
        profileName = "Infinity", robotType = RobotType.JOOLA_V2,
    )

    @Test
    fun `snapshot round-trips through the file`() = runTest {
        val store = HistoryStore(file())
        val snap = DrillSnapshot.Basic(
            BasicTraining(id = 7, name = "Forehand Loop", points = listOf(Point(8))),
            timesOverride = 5,
        )
        store.logEntry(entry(T0, name = "Forehand Loop", snapshot = snap))

        val sessions = HistoryStore(file()).loadSessions()
        assertEquals(1, sessions.size)
        assertEquals(snap, sessions[0].entries[0].snapshot)
        assertEquals("Infinity", sessions[0].entries[0].profileName)
        assertEquals(RobotType.JOOLA_V2, sessions[0].entries[0].robotType)
    }

    @Test
    fun `legacy file decodes and accepts new entries`() = runTest {
        // Verbatim shape of the pre-snapshot on-disk format.
        file().writeText(
            """
            [
                {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "startedAt": 1644000000000,
                    "entries": [
                        {
                            "trainingName": "Old Drill",
                            "trainingType": "basic",
                            "trainingId": 9,
                            "timestamp": 1644000000000
                        }
                    ]
                }
            ]
            """.trimIndent()
        )
        val store = HistoryStore(file())

        val sessions = store.loadSessions()
        assertEquals(1, sessions.size)
        assertNull(sessions[0].entries[0].snapshot)
        assertEquals("Old Drill", sessions[0].entries[0].trainingName)

        // Logging a new snapshot entry must keep legacy entries readable.
        store.logEntry(entry(T0, snapshot = DrillSnapshot.Basic(BasicTraining(id = 1, name = "New"))))
        val after = store.loadSessions()
        assertEquals(2, after.size)
        assertEquals("Old Drill", after[0].entries[0].trainingName)
        assertNotNull(after[1].entries[0].snapshot)
    }

    @Test
    fun `entries within 30 minutes share a session`() = runTest {
        val store = HistoryStore(file())
        store.logEntry(entry(T0))
        store.logEntry(entry(T0 + 29 * MIN))
        val sessions = store.loadSessions()
        assertEquals(1, sessions.size)
        assertEquals(2, sessions[0].entries.size)
        assertEquals(T0, sessions[0].startedAt)
    }

    @Test
    fun `entries 30+ minutes apart start a new session`() = runTest {
        val store = HistoryStore(file())
        store.logEntry(entry(T0))
        store.logEntry(entry(T0 + 30 * MIN))
        val sessions = store.loadSessions()
        assertEquals(2, sessions.size)
        assertEquals(T0 + 30 * MIN, sessions[1].startedAt)
    }

    @Test
    fun `break reminder fires once per 30 continuous minutes`() = runTest {
        val store = HistoryStore(file())
        assertFalse(store.logEntry(entry(T0)))                 // session start
        assertFalse(store.logEntry(entry(T0 + 20 * MIN)))      // elapsed 20m
        assertTrue(store.logEntry(entry(T0 + 40 * MIN)))       // elapsed 40m -> remind
        assertFalse(store.logEntry(entry(T0 + 50 * MIN)))      // 10m since reminder
        assertTrue(store.logEntry(entry(T0 + 70 * MIN)))       // 30m since reminder
    }
}
