# Advanced "Steps" Model Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild advanced (dynamic) drills around the real Joola "step" model: a drill is an ordered list of steps; a step = one ball config + 1–5 balls (duplicate positions allowed for weighting); a multi-ball step is within-random; any step can be order-random. Produce the exact original-app wire bytes, migrate existing drills/presets, and give users a step editor with weighting.

**Architecture:** New `Step` data type replaces `BallEntry` throughout; `BallEntry` is kept as a legacy deserialization-only type consumed by a migration function. The encoder emits per-point flag bytes per the verified table. The editor becomes a step editor supporting per-cell ball counts.

**Tech Stack:** Kotlin, Android, Jetpack Compose (Material 3), kotlinx.serialization, JUnit4, Gradle.

## Global Constraints

- **Wire encoding per point** (from `PROTOCOL.md`): for a step with `N = balls.size`, ball index `j`:
  `withinRandom = N > 1`; `rnd = withinRandom || orderRandom`.
  `b7 = if (rnd) 0x80 else 0`; `b9 = N.coerceIn(1,5)`; `b10 = if (withinRandom && j==0) 1 else 0`; `b11 = if (rnd) 1 else 0`.
  Bytes 0–4 = motor lookup on `(ball,spin,power,point.x)`; byte 5 = 0; byte 6 = `repeatDelay.coerceAtLeast(1)`; byte 8 = `ballTime`. Trailer = `[repeatNum & 0xFF, 1, 0, 0]`.
- **A step holds 1–5 balls**, duplicates allowed (weighting). The editor caps at 5 balls/step.
- **`orderRandom` is only independently meaningful for single-ball steps** (multi-ball forces `b11=1`). Editor shows it implied-on & disabled for multi-ball steps.
- **Encoder mirrors `encodeBasicPattern`**: public `lookup`-lambda core + `MotorConfig` overload delegating to it.
- **Never lose user drills:** migration is applied at every `AdvancedTraining` decode site; `ballList` is never written back.
- **Build needs JDK 17.** If `./gradlew` fails on Java version, set `JAVA_HOME` to a JDK 17 (e.g. `/opt/homebrew/opt/openjdk@17/…` or `$(/usr/libexec/java_home -v 17)`).
- Test command: `./gradlew :app:testDebugUnitTest` ; build check: `./gradlew :app:assembleDebug`.

---

### Task 1: Data model, migration, encoder, and consumer renames (green build + tests)

Foundational task: introduce `Step`, migrate, rewrite the encoder, and rename every `ballList`/`BallEntry`/`.points` consumer so the whole project compiles and all tests pass. **Keep the editor's existing tap-toggles-a-position behavior** (weighting UX comes in Task 2) — here it's a mechanical rename only.

**Files:**
- Modify: `app/src/main/java/com/tablebot/data/Models.kt`
- Create: `app/src/main/java/com/tablebot/data/AdvancedMigration.kt`
- Modify: `app/src/main/java/com/tablebot/data/TrainingStore.kt` (normalize at decode sites)
- Modify: `app/src/main/java/com/tablebot/ble/RobotProtocol.kt` (`encodeAdvancedPattern`)
- Modify: `app/src/main/java/com/tablebot/viewmodel/CalibrationSeed.kt`
- Modify: `app/src/main/java/com/tablebot/ui/components/TableGrid.kt` (`buildCellBallColors`/`buildCellBallNumbers` helpers)
- Modify: `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` (state + card rename only)
- Modify: `app/src/main/java/com/tablebot/ui/screens/TrainingListScreen.kt`, `TrainingLibrarySheet.kt`, `QuickPlayScreen.kt` (display/enable refs)
- Test: `app/src/test/java/com/tablebot/ble/RobotProtocolTest.kt`, `app/src/test/java/com/tablebot/data/AdvancedMigrationTest.kt` (new), `app/src/test/java/com/tablebot/data/DrillSnapshotTest.kt`, `app/src/test/java/com/tablebot/ui/screens/AdvancedEditorStateTest.kt`, `app/src/test/java/com/tablebot/viewmodel/CalibrationSeedTest.kt`

**Interfaces produced (later tasks rely on these exact names):**
- `data class Step(val ball:Int=1, val spin:Int=2, val power:Int=2, val ballTime:Int=9, val balls:List<Point> = emptyList(), val orderRandom:Boolean=false)` with `val withinRandom get() = balls.size > 1`.
- `AdvancedTraining.steps: List<Step>` (replaces `ballList`).
- `fun migrateBallEntriesToSteps(ballList: List<BallEntry>): List<Step>`
- `RobotProtocol.encodeAdvancedPattern(training, repeatNumOverride?, repeatDelayOverride?, lookup)` core + `(training, motorConfig, …)` overload.

- [ ] **Step 1: Write the failing encoder + migration + serialization tests**

In `RobotProtocolTest.kt` add advanced-step tests (helpers first):
```kotlin
import com.tablebot.data.AdvancedTraining
import com.tablebot.data.Step
// ...
private fun step(vararg xs: Int, orderRandom: Boolean = false, ball: Int = 1) =
    Step(ball = ball, spin = 2, power = 2, ballTime = 9,
         balls = xs.map { Point(it, 2) }, orderRandom = orderRandom)
private fun adv(vararg steps: Step) =
    AdvancedTraining(id = 1, name = "adv", repeatNum = 10, repeatDelay = 1, steps = steps.toList())

@Test fun `advanced in-order single-ball step is all-zero flags`() {
    val buf = RobotProtocol.encodeAdvancedPattern(adv(step(8)), lookup = nullLookup)
    assertEquals(0.toByte(), buf[7]); assertEquals(1.toByte(), buf[9])
    assertEquals(0.toByte(), buf[10]); assertEquals(0.toByte(), buf[11])
}
@Test fun `advanced order-random single-ball step sets b7 b11 not b9 b10`() {
    val buf = RobotProtocol.encodeAdvancedPattern(adv(step(8, orderRandom = true)), lookup = nullLookup)
    assertEquals(0x80.toByte(), buf[7]); assertEquals(1.toByte(), buf[9])
    assertEquals(0.toByte(), buf[10]); assertEquals(1.toByte(), buf[11])
}
@Test fun `advanced within-random multi-ball step groups with b10=1 on leader only`() {
    val buf = RobotProtocol.encodeAdvancedPattern(adv(step(6, 8, 10)), lookup = nullLookup)
    for (i in 0 until 3) { val o = i*12
        assertEquals("b7 $i", 0x80.toByte(), buf[o+7]); assertEquals("b9 $i", 3.toByte(), buf[o+9])
        assertEquals("b11 $i", 1.toByte(), buf[o+11])
        assertEquals("b10 $i", (if (i==0) 1 else 0).toByte(), buf[o+10]) }
}
@Test fun `advanced weighting duplicate positions emit duplicate points`() {
    val buf = RobotProtocol.encodeAdvancedPattern(adv(step(5, 5, 11, 11, 20)), lookup = nullLookup)
    assertEquals(5.toByte(), buf[9])                 // group size 5
    assertEquals(1.toByte(), buf[10])                // leader
    assertEquals(5*12 + 4, buf.size)                 // 5 points + trailer
}
@Test fun `advanced two multi-ball steps produce two groups each leader b10=1`() {
    val buf = RobotProtocol.encodeAdvancedPattern(adv(step(6,7,8,9,10), step(1,2,3,4,5)), lookup = nullLookup)
    assertEquals(1.toByte(), buf[10])                // step1 leader
    assertEquals(1.toByte(), buf[5*12 + 10])         // step2 leader
    assertEquals(0.toByte(), buf[1*12 + 10])         // non-leader
}
```
Create `AdvancedMigrationTest.kt`:
```kotlin
package com.tablebot.data
import org.junit.Assert.*
import org.junit.Test
class AdvancedMigrationTest {
    private fun be(random: Int, vararg xs: Int) =
        BallEntry(ball = 1, spin = 2, power = 2, ballTime = 9, random = random, points = xs.map { Point(it, 2) })
    @Test fun `single-point random becomes single-ball order-random step`() {
        val s = migrateBallEntriesToSteps(listOf(be(1, 8)))
        assertEquals(1, s.size); assertEquals(1, s[0].balls.size); assertTrue(s[0].orderRandom)
    }
    @Test fun `2 to 5 point random becomes one within-random step`() {
        val s = migrateBallEntriesToSteps(listOf(be(1, 6, 8, 10)))
        assertEquals(1, s.size); assertEquals(3, s[0].balls.size); assertFalse(s[0].orderRandom)
    }
    @Test fun `over 5 point random splits into chunks of 5`() {
        val s = migrateBallEntriesToSteps(listOf(be(1, 1,2,3,4,5,6,7)))
        assertEquals(2, s.size); assertEquals(5, s[0].balls.size); assertEquals(2, s[1].balls.size)
    }
    @Test fun `non-random multi-point becomes N single-ball steps in order`() {
        val s = migrateBallEntriesToSteps(listOf(be(0, 6, 8, 10)))
        assertEquals(3, s.size); assertTrue(s.all { it.balls.size == 1 && !it.orderRandom })
    }
    @Test fun `legacy ballList json normalizes into steps`() {
        val old = """{"id":1,"name":"t","ballList":[{"ball":1,"spin":2,"power":2,"points":[{"x":8,"y":2}],"random":1}]}"""
        val t = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(AdvancedTraining.serializer(), old).migrated()
        assertEquals(1, t.steps.size); assertTrue(t.steps[0].orderRandom); assertNull(t.legacyBallList)
    }
}
```

- [ ] **Step 2: Run the new tests — expect compile failure / red**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tablebot.ble.RobotProtocolTest" --tests "com.tablebot.data.AdvancedMigrationTest"`
Expected: FAIL to compile (`Step`, `steps`, `migrateBallEntriesToSteps`, `migrated`, `legacyBallList` undefined).

- [ ] **Step 3: Implement the model in `Models.kt`**

Add `Step`; keep `BallEntry` as-is (now legacy); change `AdvancedTraining`:
```kotlin
@Serializable
data class Step(
    val ball: Int = 1,
    val spin: Int = 2,
    val power: Int = 2,
    val ballTime: Int = 9,
    val balls: List<Point> = emptyList(),   // 1..5; duplicates allowed = weighting
    val orderRandom: Boolean = false,
) {
    val withinRandom: Boolean get() = balls.size > 1
}
```
In `AdvancedTraining`, replace `val ballList: List<BallEntry> = emptyList()` with:
```kotlin
    val steps: List<Step> = emptyList(),
    @SerialName("ballList") val legacyBallList: List<BallEntry>? = null,
```
Add below the class:
```kotlin
fun AdvancedTraining.migrated(): AdvancedTraining =
    if (legacyBallList != null && steps.isEmpty())
        copy(steps = migrateBallEntriesToSteps(legacyBallList), legacyBallList = null)
    else if (legacyBallList != null) copy(legacyBallList = null)
    else this
```

- [ ] **Step 4: Implement migration in new `AdvancedMigration.kt`**

```kotlin
package com.tablebot.data

/** Converts the legacy per-BallEntry advanced model into the step model. */
fun migrateBallEntriesToSteps(ballList: List<BallEntry>): List<Step> =
    ballList.flatMap { e ->
        val random = e.random == 1
        when {
            random && e.points.size <= 1 ->
                listOf(Step(e.ball, e.spin, e.power, e.ballTime, e.points, orderRandom = true))
            random ->
                e.points.chunked(5).map { chunk ->
                    Step(e.ball, e.spin, e.power, e.ballTime, chunk, orderRandom = false)
                }
            else ->
                e.points.map { p ->
                    Step(e.ball, e.spin, e.power, e.ballTime, listOf(p), orderRandom = false)
                }
        }
    }
```

- [ ] **Step 5: Normalize at every decode site in `TrainingStore.kt`**

After each `json.decodeFromString<List<AdvancedTraining>>(...)`, map `.map { it.migrated() }`. Sites: `loadAdvancedTrainings` (local file branch AND assets branch), the bundled-advanced load in the reconcile/merge method, the local-advanced decode in that method, `nextAdvancedId`, and the export/import decode of `DrillExportBundle` (map its `advanced`). Do not write `ballList` back (guaranteed by `migrated()` clearing `legacyBallList`).

- [ ] **Step 6: Rewrite `encodeAdvancedPattern` in `RobotProtocol.kt`**

Replace the function with a `lookup`-core + `MotorConfig` overload (mirror `encodeBasicPattern`). Core:
```kotlin
fun encodeAdvancedPattern(
    training: AdvancedTraining,
    repeatNumOverride: Int? = null,
    repeatDelayOverride: Int? = null,
    lookup: (ball: Int, spin: Int, power: Int, cell: Int) -> MotorParams?,
): ByteArray {
    val effectiveRepeatNum = repeatNumOverride ?: training.repeatNum
    val effectiveRepeatDelay = repeatDelayOverride ?: training.repeatDelay
    val points = mutableListOf<ByteArray>()
    for (step in training.steps) {
        val n = step.balls.size
        val within = n > 1
        val rnd = within || step.orderRandom
        step.balls.forEachIndexed { j, p ->
            val params = lookup(step.ball, step.spin, step.power, p.x)
            val b = ByteArray(12)
            b[0] = (params?.m1speed ?: 0).toByte(); b[1] = (params?.m2speed ?: 0).toByte()
            b[2] = (params?.xaxis ?: 0).toByte(); b[3] = (params?.yaxis ?: 0).toByte()
            b[4] = (params?.zaxis ?: 0).toByte()
            b[5] = 0; b[6] = (effectiveRepeatDelay.coerceAtLeast(1) and 0xFF).toByte()
            b[7] = if (rnd) 0x80.toByte() else 0
            b[8] = (step.ballTime and 0xFF).toByte()
            b[9] = n.coerceIn(1, 5).toByte()
            b[10] = if (within && j == 0) 1 else 0
            b[11] = if (rnd) 1 else 0
            points.add(b)
        }
    }
    val payload = ByteArray(points.size * 12 + 4)
    points.forEachIndexed { i, pb -> pb.copyInto(payload, i * 12) }
    val t = points.size * 12
    payload[t] = (effectiveRepeatNum and 0xFF).toByte(); payload[t+1] = 1; payload[t+2] = 0; payload[t+3] = 0
    return payload
}
fun encodeAdvancedPattern(
    training: AdvancedTraining, motorConfig: MotorConfig,
    repeatNumOverride: Int? = null, repeatDelayOverride: Int? = null,
): ByteArray = encodeAdvancedPattern(training, repeatNumOverride, repeatDelayOverride) { ball, spin, power, cell ->
    motorConfig.lookup(ball, spin, power, cell)
}
```

- [ ] **Step 7: Rename all remaining consumers to the step model (behavior unchanged)**

Mechanical renames — keep existing UI behavior; only switch types/fields:
- `TableGrid.kt`: `buildCellBallColors(ballEntries: List<BallEntry>)` → `(steps: List<Step>)` reading `it.balls`; likewise any `buildCellBallNumbers` helper doc/type. Update callers.
- `CalibrationSeed.kt`: `advanced.ballList` → `advanced.steps`; `entry.points`/config reads → `step.balls`/step config.
- `AdvancedEditorScreen.kt` (`AdvancedEditorState` + `BallEntryEditor`): `ballList` → `steps`; `BallEntry` → `Step`; `.points` → `.balls`; rename `addBall/updateBall/removeBall/moveBall` → `addStep/updateStep/removeStep/moveStep` and the composable `BallEntryEditor` → `StepEditor` (param `entry: Step`). Keep the current tap-toggles-position `TableGrid` behavior for now (Task 2 replaces it). `initial?.ballList` → `initial?.steps`.
- `TrainingListScreen.kt`, `TrainingLibrarySheet.kt`, `QuickPlayScreen.kt`: `training.ballList`/`state.ballList`/`advancedState.ballList` → `steps`; `entry.points` → `step.balls`; `${training.ballList.size} balls` → `${training.steps.size} balls`.
- Tests `DrillSnapshotTest.kt`, `AdvancedEditorStateTest.kt`, `CalibrationSeedTest.kt`: replace `ballList = listOf(BallEntry(points = …))` with `steps = listOf(Step(balls = …))` and update field names accordingly.

- [ ] **Step 8: Build + run the full unit test suite; expect green**

Run: `./gradlew :app:assembleDebug` then `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL and all tests PASS (new advanced/migration tests + all pre-existing).

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: replace advanced BallEntry model with steps + migration + encoder"
```

---

### Task 2: Step editor with weighting

**Files:** Modify `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` (the `StepEditor` composable and, if needed, the sequence-overview count). Optional: a small pure helper + unit test for the "add ball capped at 5 / remove one" list logic.

**Interfaces:** Consumes `Step` (from Task 1) and its `onUpdate(Step)` callback with `step.copy(...)`.

- [ ] **Step 1: Add a pure list-edit helper + test (TDD)**

Create `app/src/test/java/com/tablebot/ui/screens/StepBallsEditTest.kt` and a helper in `AdvancedEditorScreen.kt`:
```kotlin
// in AdvancedEditorScreen.kt (file scope)
/** Add one ball at [cell] if under the 5-ball cap; returns unchanged list if full. */
fun addBallAt(balls: List<Point>, cell: Int, cap: Int = 5): List<Point> =
    if (balls.size >= cap) balls else balls + Point(cell, 2)
/** Remove one ball at [cell] (a single occurrence), if present. */
fun removeBallAt(balls: List<Point>, cell: Int): List<Point> {
    val i = balls.indexOfFirst { it.x == cell }
    return if (i < 0) balls else balls.toMutableList().also { it.removeAt(i) }
}
```
Test both: adding beyond 5 is a no-op; removing removes exactly one occurrence (weighting preserved).

- [ ] **Step 2: Run the helper test — red, then implement (above) — green**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tablebot.ui.screens.StepBallsEditTest"`

- [ ] **Step 3: Rework the `StepEditor` position UI for weighting**

In the expanded section of `StepEditor`, replace the toggle-a-cell `TableGrid.onCellClick` behavior with **add-a-ball**: `onCellClick = { cell -> onUpdate(entry.copy(balls = addBallAt(entry.balls, cell))) }`. Provide a **remove-one** affordance (e.g. `onCellLongClick` if `TableGrid` supports it, else a small per-cell stepper row below the grid). Show a **per-cell count** — reuse the existing cell-ball-number/badge mechanism so a cell with 2 balls reads "2". Show total `${entry.balls.size}/5` and disable adding at 5. Add a caption "Randomises target (weighted by repeats)" shown when `entry.balls.size > 1`.

- [ ] **Step 4: Add the "Random order" toggle with the multi-ball rule**

Below the grid: a Switch bound to `entry.orderRandom`, `onCheckedChange = { onUpdate(entry.copy(orderRandom = it)) }`. When `entry.withinRandom` (`balls.size > 1`), render it **checked and `enabled = false`** with a caption "Multi-ball steps are always randomised." (Encoding already forces this.)

- [ ] **Step 5: Sequence overview counts duplicates**

Confirm the overview grid (`state.steps.flatMap { it.balls }` + `buildCellBall*`) reflects duplicates as counts. Adjust the map builders only if they de-dupe.

- [ ] **Step 6: Build + commit**

Run: `./gradlew :app:assembleDebug` (expect SUCCESSFUL), then:
```bash
git add -A && git commit -m "feat: step editor with per-cell ball weighting and order-random toggle"
```

---

### Task 3: Re-author bundled presets + preset-encoding validation

**Files:** Modify `app/src/main/assets/advanced-trainings.json`. Test: add a preset-shape assertion to `RobotProtocolTest.kt`.

- [ ] **Step 1: Convert presets to the `steps` schema**

Regenerate `advanced-trainings.json` so each drill uses `steps` (not `ballList`). Baseline conversion follows the migration rules (Task 1), but **hand-fix the grouped/weighted drills** so their encoded bytes match the original app — notably **"Half Long 2/3 FH Loop"**: two within-random steps of 5 balls each, with the weighting duplicates (a `balls` list like `[5,5,11,11,20]` per step, exact positions from `PROTOCOL.md`'s worked example). Any drill whose intent is "random out of these positions" becomes a single within-random step (≤5) or split steps (>5). Keep `id/name/repeatNum/repeatDelay/tags/skillLevel/isFavourite/isDefault`.

- [ ] **Step 2: Add a preset-shape encoder test**

In `RobotProtocolTest.kt`, construct the FH-loop-style training (two 5-ball within-random steps with duplicate positions) and assert: payload size `= (10*12)+4`, `b9==5` on all points, `b10==1` at offsets `0` and `5*12`, `b10==0` elsewhere, `b11==1` on all. (This mirrors the decoded frame in `PROTOCOL.md`.)

- [ ] **Step 3: Verify presets load + run tests**

Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS. (Load-time migration also makes any not-hand-fixed drill valid.)

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: re-author advanced presets into the steps schema"
```

---

## Manual / hardware verification (after all tasks)

1. Install debug APK (`./gradlew :app:installDebug`).
2. Play **"Half Long 2/3 FH Loop"** — confirm varied landing positions (weighted) and that it doesn't crash / plays smoothly.
3. Build a new drill: one 3-ball step (with a duplicated cell) + one single-ball step marked "random order"; play and sanity-check behavior.
4. Open a pre-existing user advanced drill (created before this change) — confirm it migrated and still plays.

## Self-Review
- **Spec coverage:** model → T1.S3; migration → T1.S4–5; encoder → T1.S6; consumer renames → T1.S7; editor weighting → T2; order-random rule → T2.S4; presets → T3; tests across all. Covered.
- **Placeholders:** hard/subtle parts have complete code; mechanical renames are enumerated file-by-file with exact symbol mappings.
- **Type consistency:** `Step(ball,spin,power,ballTime,balls,orderRandom)` + `withinRandom`; `AdvancedTraining.steps` + `legacyBallList`; `migrateBallEntriesToSteps`; `encodeAdvancedPattern` core+overload — used consistently across tasks.
