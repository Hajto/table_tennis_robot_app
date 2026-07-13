# Preserve in-progress ball edits across the calibration round-trip

Date: 2026-07-12
Status: Approved (design)

## Problem

While composing a ball in the home screen's **Basic** or **Dynamic** tab, opening
**Calibration** (and returning) wipes the in-progress edit back to defaults.

Repro:
1. On the Basic tab, pick a landing point.
2. Open the menu → Calibration.
3. Return — the point (and every other edit) is reset.

Desired behavior:
- If I am working on a ball — basic or dynamic — hitting Calibration should open the
  calibration screen already showing **that ball's settings** (ball type, spin, power, position).
- When I finish calibrating, I continue exactly where I left off.

## Root cause

The "Basic tab" is not a separate screen; it lives inside `QuickPlayScreen` (the `home`
destination). All of its editing state is held in `remember { }`, which is scoped to the
composition:

- `mode` (Basic/Dynamic tab index) — `QuickPlayScreen`
- `basicState` (`DrillEditorState`) — via `rememberDrillEditorState` = `remember { DrillEditorState(...) }`
- `advancedState` (`AdvancedEditorState`) — via `rememberAdvancedEditorState`
- `loadedBasicId` / `loadedAdvancedId` — `QuickPlayScreen`
- each Dynamic ball's `expanded` toggle — `remember { mutableStateOf(false) }` inside `BallEntryEditor`

Navigation Compose disposes the departing destination's composition when navigating to
`calibration`. On return, `home` recomposes fresh and every `remember { }` re-initializes to its
default — hence the reset.

Separately, `CalibrationScreen` owns independent state (`ball=1, spin=2, power=2, selectedCell=null`)
and is never told which ball is being edited, so even with the reset fixed it would not reflect the
current ball.

## Goals / non-goals

Goals:
- Preserve the Basic and Dynamic editing state across navigation to Calibration/Settings/etc. and
  across screen rotation.
- Seed the calibration screen from the ball currently being worked on.
- Keep the single global "Calibration" menu item (no per-ball calibrate buttons).

Non-goals:
- Surviving OS process-death while backgrounded (chosen scope: navigation + rotation only).
- Any change to the calibration algorithm or the drill wire format.
- Refactoring unrelated `QuickPlayScreen` concerns (permissions, import/export, profile switcher).

## Approach

### A. Hoist the draft editing state into an activity-scoped ViewModel

Introduce `QuickPlayDraftViewModel` (an `AndroidViewModel` is unnecessary — a plain `ViewModel`,
since it holds no `Context`). It owns the state currently kept in `QuickPlayScreen`'s `remember`s:

- `mode: MutableState<Int>`
- `basicState: DrillEditorState`
- `advancedState: AdvancedEditorState`
- `loadedBasicId: MutableState<Int?>`, `loadedAdvancedId: MutableState<Int?>`
- `calibrationSeed: CalibrationSeed?` (see B)

`DrillEditorState` / `AdvancedEditorState` already use Compose `mutableStateOf` internally and hold
no `Context`, so they live safely inside a ViewModel; Compose observes them the same way.

`QuickPlayDraftViewModel` is obtained with `viewModel()` at the `setContent` level in
`MainActivity` (the same scope as `robotVm` / `trainingVm`), so it is activity-scoped and survives
both the Calibration round-trip and rotation. `QuickPlayScreen` reads its state from this VM instead
of local `remember`s.

Note on initialization: today `rememberDrillEditorState(initial = null, id = nextBasicId())` seeds
the id from `nextBasicId()`. The VM cannot call the composable-scoped `nextId` lambdas at
construction, so it creates the states with a placeholder id and `QuickPlayScreen` assigns the real
`nextId` once (guarded so it only happens on first composition / when not editing a loaded drill).
This preserves current behavior.

### B. Compute a calibration seed on tap and apply it in CalibrationScreen

```
data class CalibrationSeed(val ball: Int, val spin: Int, val power: Int, val cell: Int?)
```

When Calibration is tapped, `QuickPlayScreen` derives the seed from the active tab and stores it on
the VM (`draftVm.calibrationSeed = ...`) immediately before `navigate("calibration")`:

- **Basic tab (`mode == 0`)**: `CalibrationSeed(basicState.ball, basicState.spin, basicState.power,
  basicState.points.firstOrNull()?.x)`.
- **Dynamic tab (`mode == 1`)**: choose the **last expanded** ball entry; if none is expanded, the
  **last** entry in `ballList`. Seed from that entry's `ball/spin/power` and
  `points.firstOrNull()?.x`. If `ballList` is empty (should not occur; min size is 1), fall back to
  the today's default seed.

`MainActivity`'s `calibration` composable reads `draftVm.calibrationSeed` and passes it to
`CalibrationScreen(seed = ...)` as an optional parameter (keeping `CalibrationScreen` decoupled from
the VM). `CalibrationScreen` gains `seed: CalibrationSeed? = null` and, in a `LaunchedEffect(Unit)`,
initializes `ball/spin/power/selectedCell` from it when non-null (then triggers its existing
`loadParams()`); when null it keeps today's defaults. The seed is consumed once
(`draftVm.calibrationSeed = null` after read) so re-entering calibration later without editor
context starts clean.

Because `CalibrationScreen` saves calibrated values into `motorConfig`, and the editors read
`motorConfig` reactively, returning shows the preserved drill with the just-calibrated cell updated.

### C. Hoist the per-ball `expanded` flag

Move `expanded` out of `BallEntryEditor`'s local `remember` into `AdvancedEditorState` so that (1)
expansion survives the round-trip like the rest of the edit, and (2) the seed logic can find the
"last expanded" ball. Represent it as a set of expanded indices on `AdvancedEditorState`:

```
val expandedIndices: MutableSet<Int>   // backed by a snapshot state set
fun isExpanded(i: Int): Boolean
fun toggleExpanded(i: Int)
```

`moveBall` / `removeBall` update the set so indices stay consistent after reorder/delete.
`BallEntryEditor` takes `expanded` + `onToggleExpanded` as parameters (state hoisting) rather than
owning it.

## Components & data flow

```
MainActivity (setContent)
  ├─ quickPlayVm = viewModel()            // activity-scoped, survives nav + rotation
  ├─ NavHost
  │   ├─ "home"        -> QuickPlayScreen(draft = quickPlayVm, ...)
  │   │                     • reads mode/basicState/advancedState from quickPlayVm
  │   │                     • onCalibrate: quickPlayVm.calibrationSeed = seedForActiveTab(); navigate("calibration")
  │   └─ "calibration" -> CalibrationScreen(seed = quickPlayVm.calibrationSeed, ...)
  │                          • LaunchedEffect(Unit): apply seed once, then quickPlayVm.calibrationSeed = null
```

Seed selection (pure function, unit-tested):

```
fun calibrationSeed(
    mode: Int,
    basic: DrillEditorState,
    advanced: AdvancedEditorState,
): CalibrationSeed
```

## Edge cases

- Ball has no points selected → `cell = null`; calibration opens with ball/spin/power set and no
  cell highlighted.
- Dynamic tab with nothing expanded → seed from the last ball.
- Dynamic `ballList` empty → default seed (defensive; min size is 1 today).
- Calibration reached with no editor context (seed null) → today's defaults, unchanged.
- Rotation mid-edit → VM survives, state intact.
- Reorder/remove of ball entries → `expandedIndices` remapped so the correct ball stays expanded.

## Testing

- Unit test `calibrationSeed(...)` (pure logic, no Android): Basic seeds from `basicState`; Dynamic
  picks the last-expanded ball; Dynamic with none expanded picks the last ball; empty points →
  `cell == null`.
- Unit test `AdvancedEditorState` expanded-set behavior across `moveBall` / `removeBall`.
- Manual verification on device: Basic pick-point → Calibration → back preserves the point and opens
  calibration on that cell; Dynamic expand ball #2 → Calibration opens on ball #2; rotation mid-edit
  keeps state.

## Files touched

- New `app/src/main/java/com/tablebot/viewmodel/QuickPlayDraftViewModel.kt`
- New `CalibrationSeed` + `calibrationSeed(...)` (co-located with the VM or a small
  `EditorSeed.kt`), unit-tested.
- `app/src/main/java/com/tablebot/ui/screens/QuickPlayScreen.kt` — consume the VM; build seed on
  calibrate.
- `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` — hoist `expanded` into
  `AdvancedEditorState`; `BallEntryEditor` takes it as a parameter.
- `app/src/main/java/com/tablebot/ui/screens/CalibrationScreen.kt` — accept + apply optional seed.
- `app/src/main/java/com/tablebot/MainActivity.kt` — provide the VM; wire seed into the calibration
  route.
- New test(s) under `app/src/test/java/com/tablebot/`.
