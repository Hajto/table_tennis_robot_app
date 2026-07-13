package com.tablebot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

private const val SESSION_GAP_MS = 30 * 60 * 1000L // 30 minutes
private const val BREAK_INTERVAL_MS = 30 * 60 * 1000L // suggest break every 30 minutes

class HistoryStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, "training-history.json"))

    private var lastBreakReminderAt: Long = 0L

    // NOT reentrant: never call a withLock method from inside another. Public
    // methods take the lock once and delegate to *Locked helpers below.
    private val mutex = Mutex()

    suspend fun loadSessions(): List<TrainingSession> =
        mutex.withLock { loadSessionsLocked() }

    /**
     * Decodes the on-disk history. Assumes [mutex] is already held.
     *
     * A missing file is a plain empty result. A file that exists but fails to
     * decode is quarantined to a sibling `<name>.corrupt.json` (overwriting any
     * previous backup) before returning empty — this prevents the next write
     * from silently overwriting and destroying recoverable history.
     */
    private suspend fun loadSessionsLocked(): List<TrainingSession> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<TrainingSession>>(file.readText())
        }.getOrElse {
            val backup = File(file.parentFile, file.nameWithoutExtension + ".corrupt.json")
            file.copyTo(backup, overwrite = true)
            file.delete()
            emptyList()
        }
    }

    /**
     * Logs an exercise and returns true when a break is recommended
     * (session has been going for 30+ continuous minutes).
     *
     * All time logic derives from entry.timestamp (callers stamp entries
     * with the current time), which keeps session grouping and reminder
     * behavior deterministic under test.
     */
    suspend fun logEntry(entry: HistoryEntry): Boolean = mutex.withLock {
        val now = entry.timestamp

        val sessions = loadSessionsLocked().toMutableList()
        val last = sessions.lastOrNull()

        val sessionStart: Long
        if (last != null && (now - last.entries.last().timestamp) < SESSION_GAP_MS) {
            sessions[sessions.lastIndex] = last.copy(
                entries = last.entries + entry,
            )
            sessionStart = last.startedAt
        } else {
            sessions.add(
                TrainingSession(
                    id = UUID.randomUUID().toString(),
                    startedAt = now,
                    entries = listOf(entry),
                )
            )
            sessionStart = now
        }

        withContext(Dispatchers.IO) { file.writeText(json.encodeToString(sessions)) }

        val elapsed = now - sessionStart
        val shouldRemind = elapsed >= BREAK_INTERVAL_MS &&
            (now - lastBreakReminderAt) >= BREAK_INTERVAL_MS
        if (shouldRemind) lastBreakReminderAt = now
        shouldRemind
    }

    suspend fun clearHistory() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
    }
}
