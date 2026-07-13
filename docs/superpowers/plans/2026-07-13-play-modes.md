# Play Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three per-drill play modes — Repetitions (current), Ball count, Timed — persisted on the drill and honored wherever a drill is played.

**Architecture:** Persist `playMode`/`ballCount`/`durationSec` on both training models; a pure `PlayResolver` turns (mode, drill, ballsPerPattern, ballTime) into a firmware repeat count (+ optional timed duration). `RobotViewModel` resolves at play and, for Timed, runs a client-side countdown that stops the drill with the existing `0x03` stop. A shared `PlayModeSelector` composable edits the mode in the editors, QuickPlay, and saved-drill rows; `StopOverlay` shows a countdown.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Lifecycle ViewModel + coroutines, kotlinx.serialization, JUnit4.

## Global Constraints

- Tests run with: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
- New model fields MUST be defaulted (existing/bundled JSON deserializes as REPETITIONS). `Json` already uses `ignoreUnknownKeys = true`.
- Firmware repeat count is one byte: clamp reps to `1..255` (`MAX_REPS = 255`).
- Ball count rounds **up**: `reps = ceil(ballCount / ballsPerPattern)`.
- `ballTime` is tenths of a second. `ballsPerPattern` = `points.size` (basic) / `ballList.sumOf { it.points.size }` (advanced).
- Timed uses Option A (compute-and-cap): size reps to cover the duration (capped 255) and stop at T via `0x03`.
- Do not change the BLE encoding, calibration, or unrelated behavior. Follow existing Compose style.
- Commit after each task.

---

### Task 1: PlayMode enum + persisted fields

**Files:**
- Modify: `app/src/main/java/com/tablebot/data/Models.kt` (BasicTraining ~12-29, AdvancedTraining ~44-56)
- Modify: `app/src/main/java/com/tablebot/ui/screens/BasicEditorScreen.kt` (`DrillEditorState` ~28-71)
- Modify: `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` (`AdvancedEditorState` ~31-92)
- Test: `app/src/test/java/com/tablebot/data/PlayModeModelTest.kt` (create)

**Interfaces:**
- Produces: `enum class PlayMode(val value: Int) { REPETITIONS(0), BALL_COUNT(1), TIMED(2) }` with `fromValue`. `BasicTraining`/`AdvancedTraining` gain `playMode: Int`, `ballCount: Int`, `durationSec: Int` (defaults 0/30/60). `DrillEditorState`/`AdvancedEditorState` gain matching `mutableStateOf` fields, carried in `toTraining()`/`loadFrom()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tablebot/data/PlayModeModelTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.data.PlayModeModelTest"`
Expected: FAIL — `PlayMode` unresolved / `playMode` not a member.

- [ ] **Step 3: Add PlayMode enum + model fields**

In `Models.kt`, add the enum (near the other enums, e.g. after `LandType`):

```kotlin
enum class PlayMode(val value: Int) {
    REPETITIONS(0), BALL_COUNT(1), TIMED(2);
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: REPETITIONS
    }
}
```

In `BasicTraining` (add fields before the closing `)`, after `isDefault`):

```kotlin
    val playMode: Int = 0,
    val ballCount: Int = 30,
    val durationSec: Int = 60,
```

In `AdvancedTraining` (same three lines, after `isDefault`):

```kotlin
    val playMode: Int = 0,
    val ballCount: Int = 30,
    val durationSec: Int = 60,
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.data.PlayModeModelTest"`
Expected: PASS.

- [ ] **Step 5: Add matching fields to the editor-state holders**

In `BasicEditorScreen.kt` `DrillEditorState`, add after `tags`:

```kotlin
    var playMode by mutableIntStateOf(initial?.playMode ?: 0)
    var ballCount by mutableIntStateOf(initial?.ballCount ?: 30)
    var durationSec by mutableIntStateOf(initial?.durationSec ?: 60)
```

Add to `loadFrom(training)`:

```kotlin
        playMode = training.playMode
        ballCount = training.ballCount
        durationSec = training.durationSec
```

Add to the `toTraining()` `BasicTraining(...)` call:

```kotlin
        playMode = playMode, ballCount = ballCount, durationSec = durationSec,
```

In `AdvancedEditorScreen.kt` `AdvancedEditorState`, do the same: add the three `var`s (`initial?.X ?: default`), add the three assignments in `loadFrom`, and add `playMode = playMode, ballCount = ballCount, durationSec = durationSec,` to `toTraining()`'s `AdvancedTraining(...)`.

- [ ] **Step 6: Verify full build + tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tablebot/data/Models.kt \
  app/src/main/java/com/tablebot/ui/screens/BasicEditorScreen.kt \
  app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt \
  app/src/test/java/com/tablebot/data/PlayModeModelTest.kt
git commit -m "feat: add PlayMode enum and persisted play-mode fields"
```

---

### Task 2: PlayResolver (pure, unit-tested)

**Files:**
- Create: `app/src/main/java/com/tablebot/viewmodel/PlayResolver.kt`
- Test: `app/src/test/java/com/tablebot/viewmodel/PlayResolverTest.kt`

**Interfaces:**
- Consumes: `PlayMode`, `BasicTraining`, `AdvancedTraining` (Task 1).
- Produces: `MAX_REPS = 255`; `fun ballsPerPatternBasic(t: BasicTraining): Int`; `fun ballsPerPatternAdvanced(t: AdvancedTraining): Int`; `data class ResolvedPlay(val reps: Int, val timedDurationSec: Int?)`; `fun resolvePlay(mode: PlayMode, reps: Int, ballCount: Int, durationSec: Int, ballsPerPattern: Int, ballTimeTenths: Int): ResolvedPlay`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tablebot/viewmodel/PlayResolverTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.viewmodel.PlayResolverTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/tablebot/viewmodel/PlayResolver.kt`:

```kotlin
package com.tablebot.viewmodel

import com.tablebot.data.AdvancedTraining
import com.tablebot.data.BasicTraining
import com.tablebot.data.PlayMode

/** Firmware repeat count is a single byte. */
const val MAX_REPS = 255

fun ballsPerPatternBasic(t: BasicTraining): Int = t.points.size
fun ballsPerPatternAdvanced(t: AdvancedTraining): Int = t.ballList.sumOf { it.points.size }

/** reps to send to the firmware; timedDurationSec is non-null only for TIMED (stop the drill after it). */
data class ResolvedPlay(val reps: Int, val timedDurationSec: Int?)

private fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) 1 else (a + b - 1) / b

/**
 * Turn a drill's play mode into a firmware repeat count.
 * - REPETITIONS: the reps value.
 * - BALL_COUNT: ceil(ballCount / ballsPerPattern) — round up.
 * - TIMED: enough reps (capped) to cover durationSec at the given ballTime, plus the duration to stop after.
 */
fun resolvePlay(
    mode: PlayMode,
    reps: Int,
    ballCount: Int,
    durationSec: Int,
    ballsPerPattern: Int,
    ballTimeTenths: Int,
): ResolvedPlay {
    val bpp = ballsPerPattern.coerceAtLeast(1)
    return when (mode) {
        PlayMode.REPETITIONS -> ResolvedPlay(reps.coerceIn(1, MAX_REPS), null)
        PlayMode.BALL_COUNT -> ResolvedPlay(ceilDiv(ballCount, bpp).coerceIn(1, MAX_REPS), null)
        PlayMode.TIMED -> {
            val perBall = ballTimeTenths.coerceAtLeast(1)
            val estBalls = ceilDiv(durationSec * 10, perBall)
            ResolvedPlay(ceilDiv(estBalls, bpp).coerceIn(1, MAX_REPS), durationSec)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.viewmodel.PlayResolverTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tablebot/viewmodel/PlayResolver.kt \
  app/src/test/java/com/tablebot/viewmodel/PlayResolverTest.kt
git commit -m "feat: add PlayResolver (ballsPerPattern + resolvePlay)"
```

---

### Task 3: RobotViewModel — resolve at play + timed countdown

**Files:**
- Modify: `app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt` (imports; state ~45-49; `playBasicTraining`/`playAdvancedTraining` ~84-118; `stop()` ~206; `onPatternDone` ~57-59)

**Interfaces:**
- Consumes: `resolvePlay`, `ballsPerPatternBasic/Advanced`, `ResolvedPlay` (Task 2); `PlayMode` (Task 1).
- Produces: `playCountdownSec: StateFlow<Int?>` (null unless a timed drill is counting down). `playBasicTraining(training, ballTimeOverride: Int? = null)` and `playAdvancedTraining(training, repeatDelayOverride: Int? = null)` now derive reps from `training.playMode` internally (the `timesOverride`/`repeatNumOverride` params are removed).

- [ ] **Step 1: Add imports and countdown state**

Add imports:

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
```

After the `_currentTrainingName` block (~line 49) add:

```kotlin
    private val _playCountdownSec = MutableStateFlow<Int?>(null)
    val playCountdownSec: StateFlow<Int?> = _playCountdownSec
    private var countdownJob: Job? = null
```

- [ ] **Step 2: Replace the play functions**

Replace `playBasicTraining` and `playAdvancedTraining` with mode-aware versions:

```kotlin
    fun playBasicTraining(training: BasicTraining, ballTimeOverride: Int? = null) {
        robotManager.drillJob?.cancel()
        val ballTime = ballTimeOverride ?: training.ballTime
        val resolved = resolvePlay(
            PlayMode.fromValue(training.playMode),
            training.times, training.ballCount, training.durationSec,
            ballsPerPatternBasic(training), ballTime,
        )
        robotManager.drillJob = viewModelScope.launch {
            _isPlaying.value = true
            _currentTrainingName.value = training.name
            val payload = RobotProtocol.encodeBasicPattern(
                training, motorConfig, timesOverride = resolved.reps, ballTimeOverride = ballTimeOverride,
            )
            robotManager.sendBasicDrill(payload, reps = resolved.reps)
            resolved.timedDurationSec?.let { startTimedCountdown(it) }
        }
    }

    fun playAdvancedTraining(training: AdvancedTraining, repeatDelayOverride: Int? = null) {
        robotManager.drillJob?.cancel()
        val resolved = resolvePlay(
            PlayMode.fromValue(training.playMode),
            training.repeatNum, training.ballCount, training.durationSec,
            ballsPerPatternAdvanced(training), /* ballTime for timed est */ firstBallTime(training),
        )
        robotManager.drillJob = viewModelScope.launch {
            _isPlaying.value = true
            _currentTrainingName.value = training.name
            val payload = RobotProtocol.encodeAdvancedPattern(
                training, motorConfig, repeatNumOverride = resolved.reps, repeatDelayOverride = repeatDelayOverride,
            )
            robotManager.sendAdvancedDrill(payload, reps = resolved.reps)
            resolved.timedDurationSec?.let { startTimedCountdown(it) }
        }
    }

    private fun firstBallTime(t: AdvancedTraining): Int = t.ballList.firstOrNull()?.ballTime ?: 9
```

- [ ] **Step 3: Add the countdown engine**

Add these private helpers (e.g. just below the play functions):

```kotlin
    private fun startTimedCountdown(durationSec: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = durationSec
            _playCountdownSec.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                _playCountdownSec.value = remaining
            }
            stop()
        }
    }

    private fun clearCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _playCountdownSec.value = null
    }
```

- [ ] **Step 4: Clear the countdown on stop and on pattern-done**

In `stop()`, at the start of the `viewModelScope.launch { ... }` body (before `robotManager.stop()`), add `clearCountdown()`.

In the `robotManager.onPatternDone = { ... }` block (~57), add `clearCountdown()` alongside the existing `_isPlaying.value = false`.

- [ ] **Step 5: Verify build + existing tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. NOTE: callers still passing `timesOverride=`/`repeatNumOverride=` will fail to compile — those callers are updated in Tasks 5–7. If the only compile errors are unresolved named args at `HomeScreen.kt`/`MainActivity.kt` play call sites, proceed to the UI tasks and re-run there. (The controller should sequence Task 3 with the caller updates.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt
git commit -m "feat: resolve play mode in RobotViewModel + timed countdown engine"
```

---

### Task 4: PlayModeSelector composable

**Files:**
- Create: `app/src/main/java/com/tablebot/ui/components/PlayModeSelector.kt`

**Interfaces:**
- Consumes: `PlayMode` (Task 1), existing `StepSlider(label, value, range, displayValue?, onValueChange)`.
- Produces:
  ```kotlin
  @Composable fun PlayModeSelector(
      playMode: Int, reps: Int, ballCount: Int, durationSec: Int,
      ballsPerPattern: Int, repsRange: IntRange,
      onPlayModeChange: (Int) -> Unit, onRepsChange: (Int) -> Unit,
      onBallCountChange: (Int) -> Unit, onDurationChange: (Int) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: Write the component**

Create `app/src/main/java/com/tablebot/ui/components/PlayModeSelector.kt`:

```kotlin
package com.tablebot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.data.PlayMode

@Composable
fun PlayModeSelector(
    playMode: Int,
    reps: Int,
    ballCount: Int,
    durationSec: Int,
    ballsPerPattern: Int,
    repsRange: IntRange,
    onPlayModeChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onBallCountChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf("Reps", "Balls", "Time")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = playMode == i,
                    onClick = { onPlayModeChange(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(label) }
            }
        }
        when (PlayMode.fromValue(playMode)) {
            PlayMode.REPETITIONS -> {
                val bpp = ballsPerPattern.coerceAtLeast(1)
                StepSlider("Repetitions", reps, repsRange, displayValue = { "$it  (≈ ${it * bpp} balls)" }) {
                    onRepsChange(it)
                }
            }
            PlayMode.BALL_COUNT -> {
                val bpp = ballsPerPattern.coerceAtLeast(1)
                StepSlider("Ball count", ballCount, 1..300, displayValue = { "$it  (≈ ${(it + bpp - 1) / bpp} reps)" }) {
                    onBallCountChange(it)
                }
            }
            PlayMode.TIMED -> {
                StepSlider("Duration (seconds)", durationSec, 15..1800, displayValue = { "%d:%02d".format(it / 60, it % 60) }) {
                    onDurationChange(it)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (If `SingleChoiceSegmentedButtonRow`/`SegmentedButton` are unavailable in the project's Material3 version, replace the segmented row with a `Row` of `FilterChip`s — same `selected`/`onClick` semantics.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/components/PlayModeSelector.kt
git commit -m "feat: add shared PlayModeSelector composable"
```

---

### Task 5: Use PlayModeSelector in the editors

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/BasicEditorScreen.kt` (the `StepSlider("Repetitions", state.times, 1..100) { state.times = it }` line ~290)
- Modify: `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` (the `StepSlider("Repeat Count", state.repeatNum, 1..50) { state.repeatNum = it }` line ~256)

**Interfaces:**
- Consumes: `PlayModeSelector` (Task 4); `DrillEditorState`/`AdvancedEditorState` fields (Task 1).

- [ ] **Step 1: Replace the reps slider in BasicEditorScreen**

Replace the `StepSlider("Repetitions", ...)` line with:

```kotlin
        com.tablebot.ui.components.PlayModeSelector(
            playMode = state.playMode,
            reps = state.times,
            ballCount = state.ballCount,
            durationSec = state.durationSec,
            ballsPerPattern = state.points.size,
            repsRange = 1..100,
            onPlayModeChange = { state.playMode = it },
            onRepsChange = { state.times = it },
            onBallCountChange = { state.ballCount = it },
            onDurationChange = { state.durationSec = it },
        )
```

- [ ] **Step 2: Replace the reps slider in AdvancedEditorScreen**

Replace the `StepSlider("Repeat Count", ...)` line with the same call, using `reps = state.repeatNum`, `onRepsChange = { state.repeatNum = it }`, `ballsPerPattern = state.ballList.sumOf { it.points.size }`, `repsRange = 1..50`.

- [ ] **Step 3: Verify build**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/BasicEditorScreen.kt \
  app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt
git commit -m "feat: play-mode selector in basic and advanced editors"
```

---

### Task 6: Play-mode selector in QuickPlay

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/QuickPlayScreen.kt` (add the selector to the composed-drill controls for both tabs; the Play `onClick` at ~485 needs no change — it already passes `basicState.toTraining()` / `advancedState.toTraining()`, which now carry the mode).

**Interfaces:**
- Consumes: `PlayModeSelector` (Task 4); `basicState`/`advancedState` (`DrillEditorState`/`AdvancedEditorState`).

- [ ] **Step 1: Add the selector to the Basic tab controls**

In the Basic-tab content (`mode == 0` branch of the tab body, near the other basic controls), add:

```kotlin
                        com.tablebot.ui.components.PlayModeSelector(
                            playMode = basicState.playMode,
                            reps = basicState.times,
                            ballCount = basicState.ballCount,
                            durationSec = basicState.durationSec,
                            ballsPerPattern = basicState.points.size,
                            repsRange = 1..100,
                            onPlayModeChange = { basicState.playMode = it },
                            onRepsChange = { basicState.times = it },
                            onBallCountChange = { basicState.ballCount = it },
                            onDurationChange = { basicState.durationSec = it },
                        )
```

- [ ] **Step 2: Add the selector to the Dynamic tab (AdvancedEditorContent call site)**

In the `mode == 1` branch, add the same selector bound to `advancedState` (`reps = advancedState.repeatNum`, `onRepsChange = { advancedState.repeatNum = it }`, `ballsPerPattern = advancedState.ballList.sumOf { it.points.size }`, `repsRange = 1..50`).

- [ ] **Step 3: Verify build**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/QuickPlayScreen.kt
git commit -m "feat: play-mode selector in QuickPlay"
```

---

### Task 7: Saved-drill rows honor the play mode

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/TrainingListScreen.kt` (basic row ~108-170, advanced row ~244-300; `onPlay` signatures ~27, ~68)
- Modify: `app/src/main/java/com/tablebot/ui/screens/HomeScreen.kt` (play callbacks ~193, ~212) and/or `app/src/main/java/com/tablebot/MainActivity.kt` play wiring.

**Interfaces:**
- Consumes: `RobotViewModel.playBasicTraining(training, ballTimeOverride)` / `playAdvancedTraining(training, repeatDelayOverride)` (Task 3).
- Produces: `onPlay` on the rows becomes `onPlay: (BasicTraining) -> Unit` / `(AdvancedTraining) -> Unit` — the row plays the (possibly edited) training whose `playMode`/value it shows.

- [ ] **Step 1: Change the row `onPlay` to carry the training**

In `TrainingListScreen.kt`, change the basic row param from `onPlay: (times: Int, ballTime: Int) -> Unit` to `onPlay: (BasicTraining) -> Unit`, and the advanced row similarly to `onPlay: (AdvancedTraining) -> Unit`. Change the outer list params (`onPlay: (BasicTraining, Int, Int) -> Unit` → `onPlay: (BasicTraining) -> Unit`, advanced likewise).

In each row, replace the local `var times`/`var ballTime` play state with editing of a local training copy, and render `PlayModeSelector` (Task 4) in the expanded section bound to that copy; the Play `IconButton` calls `onPlay(editedTraining)`. Keep the existing `ballTime`/`repeatDelay` control if present (pass through unchanged — it is separate from play mode). Minimal version: keep a `var editT by remember { mutableStateOf(training) }`, drive the selector via `editT = editT.copy(playMode = ..., times = ..., ballCount = ..., durationSec = ...)`, and `onPlay(editT)`.

- [ ] **Step 2: Update HomeScreen / MainActivity play wiring**

Update the play callbacks so `onPlay = { t -> robotVm.playBasicTraining(t) }` and `onPlay = { t -> robotVm.playAdvancedTraining(t) }` (drop the old `times`/`repeatNum` args). In `MainActivity`, `onPlayBasic = { robotVm.playBasicTraining(it) }` / `onPlayAdvanced = { robotVm.playAdvancedTraining(it) }` already match — no change there.

- [ ] **Step 3: Verify build + tests (all caller updates now in place)**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (Task 3's caller-compile note is resolved by this task).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/TrainingListScreen.kt \
  app/src/main/java/com/tablebot/ui/screens/HomeScreen.kt \
  app/src/main/java/com/tablebot/MainActivity.kt
git commit -m "feat: saved-drill rows play with the drill's play mode"
```

---

### Task 8: Timed countdown in StopOverlay

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/components/StopOverlay.kt`
- Modify: `app/src/main/java/com/tablebot/MainActivity.kt` (the `StopOverlay(...)` call ~304)

**Interfaces:**
- Consumes: `RobotViewModel.playCountdownSec` (Task 3).
- Produces: `StopOverlay(trainingName, countdownSec: Int? = null, onStop)`.

- [ ] **Step 1: Add a countdown to StopOverlay**

Add `countdownSec: Int? = null` param (after `trainingName`). Below the "STOP" text block, add:

```kotlin
            countdownSec?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    "%d:%02d".format(it / 60, it % 60),
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
```

- [ ] **Step 2: Wire it in MainActivity**

At the `StopOverlay(...)` call, pass the countdown:

```kotlin
                        StopOverlay(
                            trainingName = robotVm.currentTrainingName.collectAsState().value,
                            countdownSec = robotVm.playCountdownSec.collectAsState().value,
                            onStop = { robotVm.stop() },
                        )
```

- [ ] **Step 3: Verify build + full tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/components/StopOverlay.kt \
  app/src/main/java/com/tablebot/MainActivity.kt
git commit -m "feat: show timed-mode countdown in the stop overlay"
```

---

## Notes for the executor

- Tasks 3 and 7 are compile-coupled: Task 3 changes the `playBasic/AdvancedTraining` signatures (drops `timesOverride`/`repeatNumOverride`); the `HomeScreen`/`TrainingListScreen` callers are fixed in Task 7. Run Task 3's build check tolerant of caller-only errors, and treat the suite as green after Task 7.
- After all tasks: manual on-device check — each mode on a basic and an advanced drill; ball-count fires ~target balls; timed shows a countdown and stops at 0.
- The timed cap (255 reps) is intentional (spec Option A); do not add resend-on-done.
