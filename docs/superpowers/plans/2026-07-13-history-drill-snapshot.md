# History Drill Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** History entries embed the exact drill played (full training object + play-time overrides + profile name + robot type) so history survives drill edits/deletions with no future migration.

**Architecture:** A polymorphic `@Serializable sealed class DrillSnapshot` (variants `Basic`/`Advanced`, overrides inside each variant) hangs off `HistoryEntry` as a nullable field, alongside nullable `profileName`/`robotType` — all defaulting to `null` so existing history files decode unchanged. `HistoryStore` is refactored to take its `File` directly and to accept a caller-built `HistoryEntry`, deriving all time logic from `entry.timestamp` — making session grouping and break reminders JVM-testable. `RobotViewModel` builds the complete entry at play time.

**Tech Stack:** Kotlin, kotlinx-serialization 1.7.1 (closed sealed hierarchy — no `SerializersModule` needed), JUnit 4 + kotlinx-coroutines-test (plain JVM).

**Spec:** `docs/superpowers/specs/2026-07-13-history-drill-snapshot-design.md`

## Global Constraints

- Work on the CURRENT branch `feat/training-history` in the current worktree (`/Users/thenvoi/Projects/joola/tablebot/.claude/worktrees/ble-location-scan-gate`) — do NOT create a new branch or worktree. This extends open PR #9.
- Gradle needs JDK 17: prefix every `./gradlew` call with `JAVA_HOME=/opt/homebrew/opt/openjdk@17`.
- Unit tests are plain JVM JUnit 4 in `app/src/test` — no Robolectric, no new dependencies (`junit:4.13.2` and `kotlinx-coroutines-test:1.8.1` are already declared).
- Backwards compatibility is a hard requirement: every new `HistoryEntry` field defaults to `null`; a verbatim fixture of the CURRENT on-disk format must decode with the new code.
- HistoryScreen must NOT change (store-only scope).
- Match existing test style: backtick test names, `import org.junit.Assert.*`.
- Commit messages end with: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

---

### Task 1: `DrillSnapshot` model + `HistoryEntry` fields

**Files:**
- Modify: `app/src/main/java/com/tablebot/data/Models.kt` (imports at top; history section at bottom, currently lines 157–173)
- Test: `app/src/test/java/com/tablebot/data/DrillSnapshotTest.kt` (create)

**Interfaces:**
- Consumes: existing `@Serializable` types `BasicTraining`, `AdvancedTraining`, `RobotType` (all already in `Models.kt`).
- Produces (Tasks 2–3 rely on these exact shapes):
  - `DrillSnapshot.Basic(training: BasicTraining, timesOverride: Int? = null, ballTimeOverride: Int? = null)`
  - `DrillSnapshot.Advanced(training: AdvancedTraining, repeatNumOverride: Int? = null, repeatDelayOverride: Int? = null)`
  - `HistoryEntry(trainingName: String, trainingType: String, trainingId: Int, timestamp: Long, snapshot: DrillSnapshot? = null, profileName: String? = null, robotType: RobotType? = null)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tablebot/data/DrillSnapshotTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "com.tablebot.data.DrillSnapshotTest"`
Expected: FAIL — compilation error `Unresolved reference: DrillSnapshot` (and no `snapshot` parameter on `HistoryEntry`).

- [ ] **Step 3: Write minimal implementation**

In `app/src/main/java/com/tablebot/data/Models.kt`, change the imports at the top (currently line 3) to:

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
```

Then replace the Training History section at the bottom of the file (currently lines 157–173) with:

```kotlin
// ── Training History ──────────────────────────────────────────────────

/**
 * Immutable capture of exactly what was played, embedded in each history
 * entry so history survives later edits/deletions of the drill or profile.
 * Closed sealed hierarchy: kotlinx-serialization writes a "type"
 * discriminator from the @SerialName values — no SerializersModule needed.
 */
@Serializable
sealed class DrillSnapshot {
    @Serializable
    @SerialName("basic")
    data class Basic(
        val training: BasicTraining,
        val timesOverride: Int? = null,
        val ballTimeOverride: Int? = null,
    ) : DrillSnapshot()

    @Serializable
    @SerialName("advanced")
    data class Advanced(
        val training: AdvancedTraining,
        val repeatNumOverride: Int? = null,
        val repeatDelayOverride: Int? = null,
    ) : DrillSnapshot()
}

@Serializable
data class HistoryEntry(
    val trainingName: String,
    val trainingType: String,       // "basic" or "advanced"
    val trainingId: Int,
    val timestamp: Long,            // epoch millis
    // Nullable with null defaults: entries written before these fields
    // existed must keep decoding (no migration, ever).
    val snapshot: DrillSnapshot? = null,
    val profileName: String? = null,
    val robotType: RobotType? = null,
)

@Serializable
data class TrainingSession(
    val id: String,                 // UUID
    val startedAt: Long,            // epoch millis of first entry
    val entries: List<HistoryEntry> = emptyList(),
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "com.tablebot.data.DrillSnapshotTest"`
Expected: PASS, 3 tests. (The full module will not compile-break: `HistoryStore`/`RobotViewModel` still use only the four legacy constructor args, which keep their positions.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tablebot/data/Models.kt app/src/test/java/com/tablebot/data/DrillSnapshotTest.kt
git commit -m "feat: add DrillSnapshot and snapshot fields to HistoryEntry

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: `HistoryStore` refactor + `RobotViewModel` wiring + store tests

**Files:**
- Modify: `app/src/main/java/com/tablebot/data/HistoryStore.kt` (whole class — full replacement below)
- Modify: `app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt` (the two `logEntry` call sites, currently lines 100–102 and 121–123)
- Test: `app/src/test/java/com/tablebot/data/HistoryStoreTest.kt` (create)

**Interfaces:**
- Consumes: `DrillSnapshot.Basic/Advanced` and the new `HistoryEntry` shape from Task 1; existing `activeProfile: StateFlow<Profile?>` in `RobotViewModel` (`Profile` has `name: String` and `robotType: RobotType`).
- Produces: `HistoryStore(file: File)` primary constructor, `HistoryStore(context: Context)` secondary, `suspend fun logEntry(entry: HistoryEntry): Boolean`. `loadSessions()`/`clearHistory()` signatures unchanged.

The store signature change and the ViewModel call-site change MUST land in the same commit — the old `logEntry(name, type, id)` disappears, so a split would leave a non-compiling commit.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/tablebot/data/HistoryStoreTest.kt`:

```kotlin
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
                    "startedAt": 1744000000000,
                    "entries": [
                        {
                            "trainingName": "Old Drill",
                            "trainingType": "basic",
                            "trainingId": 9,
                            "timestamp": 1744000000000
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "com.tablebot.data.HistoryStoreTest"`
Expected: FAIL — compilation errors: `HistoryStore` has no `File` constructor and `logEntry` does not accept a `HistoryEntry`.

- [ ] **Step 3: Replace `HistoryStore` implementation**

Replace the class in `app/src/main/java/com/tablebot/data/HistoryStore.kt` (keep the file's existing package line, imports block shown below, `json` val, and the two constants — the diff is: `File` import stays, constructor change, `logEntry` signature/time change):

```kotlin
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

class HistoryStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, "training-history.json"))

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
     *
     * All time logic derives from entry.timestamp (callers stamp entries
     * with the current time), which keeps session grouping and reminder
     * behavior deterministic under test.
     */
    suspend fun logEntry(entry: HistoryEntry): Boolean = withContext(Dispatchers.IO) {
        val now = entry.timestamp

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
```

- [ ] **Step 4: Update the two call sites in `RobotViewModel`**

In `app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt`, inside `playBasicTraining` replace:

```kotlin
            if (historyStore.logEntry(training.name, "basic", training.id)) {
                _breakReminder.tryEmit(Unit)
            }
```

with:

```kotlin
            val profile = activeProfile.value
            if (historyStore.logEntry(HistoryEntry(
                    trainingName = training.name,
                    trainingType = "basic",
                    trainingId = training.id,
                    timestamp = System.currentTimeMillis(),
                    snapshot = DrillSnapshot.Basic(training, timesOverride, ballTimeOverride),
                    profileName = profile?.name,
                    robotType = profile?.robotType,
                ))) {
                _breakReminder.tryEmit(Unit)
            }
```

Inside `playAdvancedTraining` replace:

```kotlin
            if (historyStore.logEntry(training.name, "advanced", training.id)) {
                _breakReminder.tryEmit(Unit)
            }
```

with:

```kotlin
            val profile = activeProfile.value
            if (historyStore.logEntry(HistoryEntry(
                    trainingName = training.name,
                    trainingType = "advanced",
                    trainingId = training.id,
                    timestamp = System.currentTimeMillis(),
                    snapshot = DrillSnapshot.Advanced(training, repeatNumOverride, repeatDelayOverride),
                    profileName = profile?.name,
                    robotType = profile?.robotType,
                ))) {
                _breakReminder.tryEmit(Unit)
            }
```

`RobotViewModel.kt` already has `import com.tablebot.data.*` (line 8), which covers `HistoryEntry` and `DrillSnapshot` — no import changes. (There is also a redundant explicit `import com.tablebot.data.HistoryStore` on line 9; leave it.)

- [ ] **Step 5: Run the new tests, then the full suite**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "com.tablebot.data.HistoryStoreTest"`
Expected: PASS, 5 tests.

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — full suite green (existing tests + DrillSnapshotTest + HistoryStoreTest).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tablebot/data/HistoryStore.kt app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt app/src/test/java/com/tablebot/data/HistoryStoreTest.kt
git commit -m "feat: capture exact drill snapshot in training history

History entries now embed the full training object, play-time overrides,
profile name and robot type, so history survives drill edits/deletions.
HistoryStore takes its File directly and derives time from entry.timestamp,
making session grouping and break reminders unit-testable.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Verify on device and update PR #9

**Files:** none (verification + push only).

**Interfaces:** consumes the committed work of Tasks 1–2.

- [ ] **Step 1: Build and install on the connected Pixel**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:installDebug
```

Expected: `Installed on 1 device.` (Skip gracefully if no device is attached — note it in the report instead.)

- [ ] **Step 2: Verify a real log entry carries the snapshot**

The robot does not need to be on: `sendTestBall`/drill start writes history only when a drill is *played*, so this check needs the robot — if it is off, verify instead that the app launches and Training History still renders (legacy compatibility on-device):

```bash
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB shell am start -n com.tablebot/.MainActivity
# after playing a drill (robot on), pull the history file:
$ADB exec-out run-as com.tablebot cat files/training-history.json | tail -40
```

Expected (with robot): the newest entry contains `"snapshot": {"type": ...}`, `"profileName"`, `"robotType"`. Without robot: app launches, History screen opens without crash on the pre-existing file.

- [ ] **Step 3: Push to update PR #9**

```bash
git push origin feat/training-history
```

Expected: fast-forward push (no force). PR #9 picks up the spec + two implementation commits.

---

## Out of scope (deliberately)

- HistoryScreen changes (details view, replay) — data-only per spec.
- Firmware capture; persisting `lastBreakReminderAt` across restarts (pre-existing).
- Migration tooling — unnecessary by construction.
