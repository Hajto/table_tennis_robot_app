# Training History: Exact Drill Snapshots — Design

**Date:** 2026-07-13
**Branch:** `feat/training-history` (extends PR #9)
**Status:** Approved by user

## Problem

History entries currently store only a *reference* to the drill played: `trainingName`, `trainingType` ("basic"/"advanced"), `trainingId`, `timestamp`. The referenced drill is mutable and deletable, so history cannot answer "what exactly did I train?" — and any future feature needing that data (details view, replay, stats) would face a migration where old entries are unrecoverable. The user wants the complete data captured **from day one** of the history feature so no such regression can occur.

## Decision summary

- **Store-only**: entries embed the full drill data; HistoryScreen is unchanged. Future UI finds complete data waiting.
- **Snapshot contents**: the full training object, the play-time overrides actually used, the active profile's name, and its robot type (embedded, not inferred — profiles are mutable/deletable too). Firmware version deliberately excluded.
- **Shape**: polymorphic `@Serializable sealed class DrillSnapshot` (user chose over flat nullable fields). Overrides live inside each variant, so there are no cross-field invariants.
- **Backwards compatible**: all new `HistoryEntry` fields default to `null`; history files written by the current build decode unchanged. No migration, ever.

## Data model (`app/src/main/java/com/tablebot/data/Models.kt`)

```kotlin
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
```

`HistoryEntry` gains three fields, appended after `timestamp`, all defaulting to `null`:

```kotlin
val snapshot: DrillSnapshot? = null,
val profileName: String? = null,
val robotType: RobotType? = null,
```

The legacy fields (`trainingName`, `trainingType`, `trainingId`) are kept: HistoryScreen renders `trainingName`, old files contain them, and they remain the cheap display path. `BasicTraining`, `AdvancedTraining`, `BallEntry`, and `RobotType` are already `@Serializable` (export/import depends on this) — no changes to them.

Closed sealed hierarchies need no `SerializersModule`; kotlinx-serialization (1.7.1) auto-registers subclasses and writes a `"type"` discriminator using the `@SerialName` values, e.g.:

```json
"snapshot": { "type": "basic", "training": { ... }, "timesOverride": 3 }
```

## Store API (`app/src/main/java/com/tablebot/data/HistoryStore.kt`)

1. **Constructor takes the file** (testability — plain-JVM tests get a temp file; `Context` remains the production path):

```kotlin
class HistoryStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "training-history.json"))
```

2. **`logEntry` accepts a complete entry** built by the caller; the store keeps owning session grouping and break-reminder logic:

```kotlin
suspend fun logEntry(entry: HistoryEntry): Boolean
```

3. **Time comes from `entry.timestamp`**, not an internal `System.currentTimeMillis()`. Session grouping (30-min gap), session `startedAt`, and the break-reminder elapsed check all use `entry.timestamp` as "now". This makes every time-dependent behavior testable with crafted timestamps and changes nothing in production (the caller stamps entries with the current time). `lastBreakReminderAt` stays in-memory as today.

`loadSessions()` and `clearHistory()` are unchanged. `SESSION_GAP_MS` and `BREAK_INTERVAL_MS` are unchanged (30 min each).

## Wiring (`app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt`)

`playBasicTraining` builds and logs:

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

`playAdvancedTraining` mirrors this with `DrillSnapshot.Advanced(training, repeatNumOverride, repeatDelayOverride)`. No other call sites exist.

## Testing (`app/src/test/java/com/tablebot/data/HistoryStoreTest.kt`, new — plain JVM JUnit 4)

Using a temp file (`kotlin.io.path.createTempFile` / JUnit `TemporaryFolder`) and `kotlinx-coroutines-test`:

1. **Basic round-trip**: log an entry with a `DrillSnapshot.Basic` (with overrides) → `loadSessions()` returns it structurally equal, snapshot intact.
2. **Advanced round-trip**: same for `DrillSnapshot.Advanced`.
3. **Legacy decode** (the regression guard): write a verbatim fixture of the *current* on-disk format (session + entry without `snapshot`/`profileName`/`robotType`) into the file → `loadSessions()` parses it; new fields are `null`; logging a new entry into that file keeps the old entries readable.
4. **Session grouping**: two entries 29 min apart share a session; 31 min apart split sessions.
5. **Break reminder**: entries spanning ≥30 min of one session make `logEntry` return `true` exactly once per 30-min interval; a fresh session returns `false`.
6. Existing suite stays green (`RobotViewModel` call-site change compiles against the new signature; no behavior change).

## Out of scope

- HistoryScreen changes (details view, replay) — the data will be waiting when wanted.
- Firmware version capture.
- Persisting `lastBreakReminderAt` across process restarts (pre-existing behavior).
- Any migration tooling — by construction none is needed.
