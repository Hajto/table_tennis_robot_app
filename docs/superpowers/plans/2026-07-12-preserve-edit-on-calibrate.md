# Preserve ball edits across calibration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Editing a ball on the home screen's Basic/Dynamic tab no longer resets when you open Calibration and return, and Calibration opens seeded from the ball you're working on.

**Architecture:** Hoist `QuickPlayScreen`'s editing state into an activity-scoped `QuickPlayDraftViewModel` (survives navigation + rotation). On Calibrate, compute a `CalibrationSeed` from the active tab and apply it in `CalibrationScreen`. Hoist each Dynamic ball's `expanded` flag into `AdvancedEditorState` so it both survives and drives the "last expanded" seed.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Navigation Compose, AndroidX Lifecycle ViewModel, JUnit4.

## Global Constraints

- Tests run with: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
- Persistence scope is navigation + rotation only (ViewModel). Do NOT add SavedStateHandle/disk persistence.
- Keep the single global "Calibration" menu item; no per-ball calibrate buttons.
- Do not change the drill wire format, the calibration algorithm, or unrelated `QuickPlayScreen` concerns (permissions, import/export, profile switcher).
- Basic tab = `mode == 0`, Dynamic tab = `mode == 1`.
- Dynamic seed rule: the **last expanded** ball entry; if none expanded, the **last** entry in `ballList`.
- `cell` for a seed is the ball's `points.firstOrNull()?.x` (nullable).
- Commit after each task. Follow existing code style (Compose `mutableStateOf`, no Hungarian notation).

---

### Task 1: Hoist `expanded` into AdvancedEditorState

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` (state class `AdvancedEditorState` lines 31-92; `BallEntryEditor` signature line 287-296 + `expanded` usage line 297,350; call site lines 202-217)
- Test: `app/src/test/java/com/tablebot/ui/screens/AdvancedEditorStateTest.kt` (create)

**Interfaces:**
- Produces: `AdvancedEditorState.expandedIndices: Set<Int>` (read-only view), `fun isExpanded(index: Int): Boolean`, `fun toggleExpanded(index: Int)`, `fun lastExpandedIndex(): Int?`. `moveBall`/`removeBall` remap `expandedIndices` so the same ball stays expanded after reorder/delete. `BallEntryEditor(..., expanded: Boolean, onToggleExpanded: () -> Unit, ...)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tablebot/ui/screens/AdvancedEditorStateTest.kt`:

```kotlin
package com.tablebot.ui.screens

import com.tablebot.data.BallEntry
import com.tablebot.data.Point
import org.junit.Assert.*
import org.junit.Test

class AdvancedEditorStateTest {
    private fun state(n: Int): AdvancedEditorState {
        val s = AdvancedEditorState(initial = null, id = 1)
        s.ballList = (0 until n).map { BallEntry(ball = 1, spin = 2, power = 2, points = listOf(Point(it + 1, 2)), ballTime = 9) }
        return s
    }

    @Test fun `no ball expanded by default`() {
        val s = state(3)
        assertNull(s.lastExpandedIndex())
        assertFalse(s.isExpanded(0))
    }

    @Test fun `toggle expands and lastExpandedIndex returns highest expanded`() {
        val s = state(3)
        s.toggleExpanded(0)
        s.toggleExpanded(2)
        assertTrue(s.isExpanded(0))
        assertTrue(s.isExpanded(2))
        assertEquals(2, s.lastExpandedIndex())
    }

    @Test fun `toggle twice collapses`() {
        val s = state(2)
        s.toggleExpanded(1)
        s.toggleExpanded(1)
        assertFalse(s.isExpanded(1))
        assertNull(s.lastExpandedIndex())
    }

    @Test fun `removeBall keeps the correct ball expanded`() {
        val s = state(3)          // balls 0,1,2
        s.toggleExpanded(2)       // ball 2 expanded
        s.removeBall(0)           // now old ball 2 is at index 1
        assertFalse(s.isExpanded(0))
        assertTrue(s.isExpanded(1))
        assertEquals(1, s.lastExpandedIndex())
    }

    @Test fun `removeBall drops the expanded flag of the removed ball`() {
        val s = state(3)
        s.toggleExpanded(1)
        s.removeBall(1)
        assertNull(s.lastExpandedIndex())
    }

    @Test fun `moveBall follows the expanded ball to its new index`() {
        val s = state(3)
        s.toggleExpanded(0)       // ball 0 expanded
        s.moveBall(0, 2)          // swap 0<->2; expanded ball now at index 2
        assertTrue(s.isExpanded(2))
        assertFalse(s.isExpanded(0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.ui.screens.AdvancedEditorStateTest"`
Expected: FAIL — `isExpanded`/`toggleExpanded`/`lastExpandedIndex` unresolved.

- [ ] **Step 3: Add the expanded set to AdvancedEditorState**

In `AdvancedEditorScreen.kt`, add the import near the top (with the other compose.runtime imports):

```kotlin
import androidx.compose.runtime.mutableStateListOf
```

Inside `class AdvancedEditorState` (after `tags` on line 45), add:

```kotlin
    // Indices of ball entries whose settings panel is expanded (hoisted from BallEntryEditor
    // so expansion survives navigation and drives the calibration seed).
    private val _expandedIndices = mutableStateListOf<Int>()
    val expandedIndices: List<Int> get() = _expandedIndices

    fun isExpanded(index: Int): Boolean = index in _expandedIndices
    fun toggleExpanded(index: Int) {
        if (!_expandedIndices.remove(index)) _expandedIndices.add(index)
    }
    fun lastExpandedIndex(): Int? = _expandedIndices.maxOrNull()
```

Update `removeBall` (currently lines 80-84) to remap:

```kotlin
    fun removeBall(index: Int) {
        if (ballList.size > 1) {
            ballList = ballList.toMutableList().apply { removeAt(index) }
            // Remove the deleted ball's flag and shift higher indices down by one.
            val shifted = _expandedIndices.filter { it != index }.map { if (it > index) it - 1 else it }
            _expandedIndices.clear(); _expandedIndices.addAll(shifted)
        }
    }
```

Update `moveBall` (currently lines 86-91) to follow the swap:

```kotlin
    fun moveBall(from: Int, to: Int) {
        if (from !in ballList.indices || to !in ballList.indices || from == to) return
        ballList = ballList.toMutableList().apply {
            java.util.Collections.swap(this, from, to)
        }
        val fromExp = from in _expandedIndices
        val toExp = to in _expandedIndices
        _expandedIndices.remove(from); _expandedIndices.remove(to)
        if (toExp) _expandedIndices.add(from)
        if (fromExp) _expandedIndices.add(to)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.ui.screens.AdvancedEditorStateTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Hoist `expanded` in the composable**

In `BallEntryEditor` (line 287), replace the local state. Change the signature to add two parameters after `motorConfig` and delete the `var expanded by remember...` line (297):

```kotlin
private fun BallEntryEditor(
    index: Int,
    entry: BallEntry,
    ballNumber: Int,
    onUpdate: (BallEntry) -> Unit,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    motorConfig: MotorConfig? = null,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
```

Delete line 297 (`var expanded by remember { mutableStateOf(false) }`). Change the toggle `IconButton` (line 350) from `onClick = { expanded = !expanded }` to `onClick = onToggleExpanded`.

At the call site in `AdvancedEditorContent` (the `BallEntryEditor(...)` call at lines 202-217), add the two arguments:

```kotlin
                BallEntryEditor(
                    index = index,
                    entry = entry,
                    ballNumber = index + 1,
                    onUpdate = { state.updateBall(index, it) },
                    onRemove = if (state.ballList.size > 1) { { state.removeBall(index) } } else null,
                    onMoveUp = if (index > 0) { { state.moveBall(index, index - 1) } } else null,
                    onMoveDown = if (index < state.ballList.lastIndex) { { state.moveBall(index, index + 1) } } else null,
                    motorConfig = motorConfig,
                    expanded = state.isExpanded(index),
                    onToggleExpanded = { state.toggleExpanded(index) },
                )
```

- [ ] **Step 6: Verify the whole module still compiles + tests pass**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt app/src/test/java/com/tablebot/ui/screens/AdvancedEditorStateTest.kt
git commit -m "feat: hoist ball-entry expanded state into AdvancedEditorState"
```

---

### Task 2: CalibrationSeed + calibrationSeed() helper

**Files:**
- Create: `app/src/main/java/com/tablebot/viewmodel/CalibrationSeed.kt`
- Test: `app/src/test/java/com/tablebot/viewmodel/CalibrationSeedTest.kt`

**Interfaces:**
- Consumes: `DrillEditorState` (from `com.tablebot.ui.screens`, has `ball,spin,power: Int` and `points: List<Point>`), `AdvancedEditorState` (has `ballList: List<BallEntry>`, `lastExpandedIndex(): Int?` from Task 1). `BallEntry` has `ball,spin,power: Int` and `points: List<Point>`; `Point` has `x: Int`.
- Produces: `data class CalibrationSeed(val ball: Int, val spin: Int, val power: Int, val cell: Int?)` and `fun calibrationSeed(mode: Int, basic: DrillEditorState, advanced: AdvancedEditorState): CalibrationSeed`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tablebot/viewmodel/CalibrationSeedTest.kt`:

```kotlin
package com.tablebot.viewmodel

import com.tablebot.data.BallEntry
import com.tablebot.data.Point
import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationSeedTest {
    private fun basic(ball: Int, spin: Int, power: Int, cell: Int?): DrillEditorState {
        val s = DrillEditorState(initial = null, id = 1)
        s.ball = ball; s.spin = spin; s.power = power
        s.points = if (cell == null) emptyList() else listOf(Point(cell, 2))
        return s
    }
    private fun advanced(vararg balls: BallEntry): AdvancedEditorState {
        val s = AdvancedEditorState(initial = null, id = 1)
        s.ballList = balls.toList()
        return s
    }
    private fun ball(ball: Int, spin: Int, power: Int, cell: Int) =
        BallEntry(ball = ball, spin = spin, power = power, points = listOf(Point(cell, 2)), ballTime = 9)

    @Test fun `basic tab seeds from basic state`() {
        val seed = calibrationSeed(0, basic(0, 3, 1, 12), advanced(ball(1, 2, 2, 8)))
        assertEquals(CalibrationSeed(0, 3, 1, 12), seed)
    }

    @Test fun `basic tab with no points yields null cell`() {
        val seed = calibrationSeed(0, basic(1, 2, 2, null), advanced(ball(1, 2, 2, 8)))
        assertEquals(CalibrationSeed(1, 2, 2, null), seed)
    }

    @Test fun `dynamic tab seeds from last expanded ball`() {
        val adv = advanced(ball(0, 0, 0, 5), ball(1, 3, 1, 9), ball(2, 4, 2, 14))
        adv.toggleExpanded(1)
        val seed = calibrationSeed(1, basic(1, 2, 2, 8), adv)
        assertEquals(CalibrationSeed(1, 3, 1, 9), seed)
    }

    @Test fun `dynamic tab with nothing expanded seeds from last ball`() {
        val adv = advanced(ball(0, 0, 0, 5), ball(1, 3, 1, 9), ball(2, 4, 2, 14))
        val seed = calibrationSeed(1, basic(1, 2, 2, 8), adv)
        assertEquals(CalibrationSeed(2, 4, 2, 14), seed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.viewmodel.CalibrationSeedTest"`
Expected: FAIL — `CalibrationSeed`/`calibrationSeed` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/tablebot/viewmodel/CalibrationSeed.kt`:

```kotlin
package com.tablebot.viewmodel

import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState

/** Ball context handed to the calibration screen so it opens on the ball being edited. */
data class CalibrationSeed(val ball: Int, val spin: Int, val power: Int, val cell: Int?)

/**
 * Derives the calibration seed from the active editor tab.
 * Basic (mode 0): the single basic ball. Dynamic (mode 1): the last expanded ball entry,
 * else the last entry in the list. Empty ball list falls back to the basic ball.
 */
fun calibrationSeed(mode: Int, basic: DrillEditorState, advanced: AdvancedEditorState): CalibrationSeed {
    if (mode == 0) {
        return CalibrationSeed(basic.ball, basic.spin, basic.power, basic.points.firstOrNull()?.x)
    }
    val list = advanced.ballList
    if (list.isEmpty()) {
        return CalibrationSeed(basic.ball, basic.spin, basic.power, basic.points.firstOrNull()?.x)
    }
    val index = advanced.lastExpandedIndex() ?: list.lastIndex
    val entry = list[index]
    return CalibrationSeed(entry.ball, entry.spin, entry.power, entry.points.firstOrNull()?.x)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.tablebot.viewmodel.CalibrationSeedTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tablebot/viewmodel/CalibrationSeed.kt app/src/test/java/com/tablebot/viewmodel/CalibrationSeedTest.kt
git commit -m "feat: add CalibrationSeed and calibrationSeed selector"
```

---

### Task 3: QuickPlayDraftViewModel

**Files:**
- Create: `app/src/main/java/com/tablebot/viewmodel/QuickPlayDraftViewModel.kt`

**Interfaces:**
- Consumes: `DrillEditorState`, `AdvancedEditorState` (constructors `(initial: X?, id: Int)`), `CalibrationSeed` (Task 2).
- Produces: `class QuickPlayDraftViewModel : ViewModel()` exposing:
  - `var mode: Int` (Compose state)
  - `val basicState: DrillEditorState`
  - `val advancedState: AdvancedEditorState`
  - `var loadedBasicId: Int?`, `var loadedAdvancedId: Int?` (Compose state)
  - `var calibrationSeed: CalibrationSeed?` (Compose state)
  - `var idsInitialized: Boolean` (plain flag, guards one-time id assignment)

- [ ] **Step 1: Write the implementation**

Create `app/src/main/java/com/tablebot/viewmodel/QuickPlayDraftViewModel.kt`:

```kotlin
package com.tablebot.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState

/**
 * Holds the home screen's in-progress Basic/Dynamic editing state. Activity-scoped, so the draft
 * survives navigating to Calibration/Settings and screen rotation (the previous `remember`-scoped
 * state was disposed and reset on navigation).
 */
class QuickPlayDraftViewModel : ViewModel() {
    var mode by mutableIntStateOf(0)

    // ids get their real nextId assigned once by QuickPlayScreen (VM can't call composable lambdas).
    val basicState = DrillEditorState(initial = null, id = 0)
    val advancedState = AdvancedEditorState(initial = null, id = 0)
    var idsInitialized = false

    var loadedBasicId by mutableStateOf<Int?>(null)
    var loadedAdvancedId by mutableStateOf<Int?>(null)

    var calibrationSeed by mutableStateOf<CalibrationSeed?>(null)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (no new tests; existing tests pass).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tablebot/viewmodel/QuickPlayDraftViewModel.kt
git commit -m "feat: add QuickPlayDraftViewModel to hold editing draft"
```

---

### Task 4: Wire QuickPlayScreen + MainActivity to the ViewModel

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/QuickPlayScreen.kt` (signature ~line 30-77; state decls lines 78-90; calibrate menu onClick line 328)
- Modify: `app/src/main/java/com/tablebot/MainActivity.kt` (VM creation ~line 44-45; home composable QuickPlayScreen call ~line 129-183; calibration composable ~line 190-196)

**Interfaces:**
- Consumes: `QuickPlayDraftViewModel` (Task 3), `calibrationSeed(...)` (Task 2), `CalibrationScreen(seed = ...)` (Task 5).
- Produces: `QuickPlayScreen(draft: QuickPlayDraftViewModel, ...)` — the `mode`/`basicState`/`advancedState`/`loadedBasicId`/`loadedAdvancedId` local `remember`s are replaced by `draft.*`.

- [ ] **Step 1: Add the ViewModel to QuickPlayScreen's parameters**

In `QuickPlayScreen(...)` add a first parameter (before the existing ones):

```kotlin
    draft: com.tablebot.viewmodel.QuickPlayDraftViewModel,
```

- [ ] **Step 2: Replace the `remember`ed state with the VM**

Delete lines 78-87 (the `var mode by remember...`, `val basicState = rememberDrillEditorState(...)`, `var loadedBasicId...`, `val advancedState = rememberAdvancedEditorState(...)`, `var loadedAdvancedId...`) and replace with:

```kotlin
    // Editing state lives in the activity-scoped draft VM so it survives navigation + rotation.
    var mode by draft::mode
    val basicState = draft.basicState
    val advancedState = draft.advancedState
    var loadedBasicId by draft::loadedBasicId
    var loadedAdvancedId by draft::loadedAdvancedId

    // Assign real next ids once (VM constructed the states with a placeholder id).
    LaunchedEffect(Unit) {
        if (!draft.idsInitialized) {
            if (loadedBasicId == null) basicState.id = nextBasicId()
            if (loadedAdvancedId == null) advancedState.id = nextAdvancedId()
            draft.idsInitialized = true
        }
    }
```

Note: `var mode by draft::mode` uses Kotlin property delegation to the VM's `var`. Ensure `import androidx.compose.runtime.LaunchedEffect` is present (it is used elsewhere in the file; add if missing).

- [ ] **Step 3: Seed calibration on tap**

Change the Calibration menu item `onClick` (currently line 327-328 `onClick = { menuExpanded = false; onCalibrate() }`) to compute + store the seed first:

```kotlin
                                onClick = {
                                    menuExpanded = false
                                    draft.calibrationSeed = com.tablebot.viewmodel.calibrationSeed(mode, basicState, advancedState)
                                    onCalibrate()
                                },
```

- [ ] **Step 4: Provide the VM and pass the seed in MainActivity**

In `MainActivity.kt`, add the VM next to the others (after line 45 `val trainingVm...`):

```kotlin
                val quickPlayVm: com.tablebot.viewmodel.QuickPlayDraftViewModel = viewModel()
```

Pass it into the `QuickPlayScreen(` call in the `home` composable (add as the first argument, ~line 129):

```kotlin
                            QuickPlayScreen(
                                draft = quickPlayVm,
                                reopenProfileSwitcher = reopenSwitcher,
```

Update the `calibration` composable (lines 190-196) to hand the seed to `CalibrationScreen`:

```kotlin
                        composable("calibration") {
                            CalibrationScreen(
                                robotVm = robotVm,
                                onBack = { navController.popBackStack() },
                                activeProfileName = activeProfile?.name,
                                seed = quickPlayVm.calibrationSeed,
                            )
                        }
```

- [ ] **Step 5: Verify build + existing tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (Task 5 must be applied for `seed` to exist on CalibrationScreen — if doing tasks strictly in order, temporarily add the param in Task 5 before this compiles; the reviewer runs after Task 5. If build fails only on the missing `seed` param, proceed to Task 5 then re-run.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/QuickPlayScreen.kt app/src/main/java/com/tablebot/MainActivity.kt
git commit -m "feat: back QuickPlayScreen editing state with QuickPlayDraftViewModel"
```

---

### Task 5: CalibrationScreen applies the seed

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/CalibrationScreen.kt` (signature lines 39-43; selection state lines 75-78; add a one-shot seed effect after line 107)

**Interfaces:**
- Consumes: `CalibrationSeed` (Task 2).
- Produces: `CalibrationScreen(robotVm, onBack, activeProfileName, seed: CalibrationSeed? = null)`.

- [ ] **Step 1: Add the seed parameter**

Change the signature (lines 39-43):

```kotlin
fun CalibrationScreen(
    robotVm: RobotViewModel,
    onBack: () -> Unit,
    activeProfileName: String? = null,
    seed: com.tablebot.viewmodel.CalibrationSeed? = null,
) {
```

- [ ] **Step 2: Initialize selection from the seed (once)**

The selection state stays as-is (lines 75-78 keep their current defaults `1/2/2/null`). After the existing `LaunchedEffect(ball, spin, power, selectedCell) { loadParams() }` (line 107), add a one-shot effect that applies the seed on first entry:

```kotlin
    // Seed the selection from the ball being edited (only when navigated in with context).
    LaunchedEffect(Unit) {
        seed?.let {
            ball = it.ball
            spin = it.spin
            power = it.power
            selectedCell = it.cell
        }
    }
```

(The existing `LaunchedEffect(ball, spin, power, selectedCell)` then runs `loadParams()` for the seeded values.)

- [ ] **Step 3: Verify build + all tests**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/CalibrationScreen.kt
git commit -m "feat: seed CalibrationScreen from the ball being edited"
```

---

## Notes for the executor

- Tasks 4 and 5 are mutually dependent at compile time (Task 4 passes `seed=` which Task 5 defines). If a task's build fails only because of the not-yet-applied sibling, complete both before running the reviewer for Task 4/5.
- After all tasks: manual device check — Basic pick-point → Calibration → back preserves the point and opens on that cell; Dynamic expand ball #2 → Calibration opens on ball #2; rotate mid-edit keeps state.
- Do not add process-death persistence; ViewModel scope is intentional.
