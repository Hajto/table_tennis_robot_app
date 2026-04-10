package com.tablebot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

private const val SESSION_GAP_MS = 30 * 60 * 1000L // 30 minutes
private const val BREAK_INTERVAL_MS = 30 * 60 * 1000L // suggest break every 30 minutes

class HistoryStore(private val context: Context) {

    private val file get() = File(context.filesDir, "training-history.json")
    private var lastBreakReminderAt: Long = 0L

    suspend fun loadSessions(): List<TrainingSession> = withContext(Dispatchers.IO) {
        if (file.exists()) {
            runCatching {
                json.decodeFromString<List<TrainingSession>>(file.readText())
            }.getOrDefault(emptyList())
        } else emptyList()
    }

    /**
     * Logs an exercise and returns true when a break is recommended
     * (session has been going for 30+ continuous minutes).
     */
    suspend fun logEntry(name: String, type: String, trainingId: Int): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(
            trainingName = name,
            trainingType = type,
            trainingId = trainingId,
            timestamp = now,
        )

        val sessions = loadSessions().toMutableList()
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

        file.writeText(json.encodeToString(sessions))

        val elapsed = now - sessionStart
        val shouldRemind = elapsed >= BREAK_INTERVAL_MS &&
            (now - lastBreakReminderAt) >= BREAK_INTERVAL_MS
        if (shouldRemind) lastBreakReminderAt = now
        shouldRemind
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        file.delete()
    }
}
