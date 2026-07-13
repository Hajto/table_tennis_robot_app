# Play modes: Repetitions, Ball count, Timed

Date: 2026-07-13
Status: Approved (design) — pending spec review

> **Assumption to confirm at review:** BALL_COUNT is a *persisted, first-class mode* — the drill
> stores a target ball count and reps are recomputed at play time (so the total auto-tracks pattern
> edits), not merely a one-time input that collapses to reps. If that's wrong, ball count collapses
> into the reps control and `ballCount`/the mode go away.

## Problem

A drill can currently only be played as a fixed number of pattern **repetitions**. Users want two
more ways to play, chosen per drill:

1. **Repetitions** — play N pattern cycles (current behavior).
2. **Ball count** — play until ~N total balls have been shot. `reps = ceil(ballCount / ballsPerPattern)`
   (e.g. a 3-ball pattern with a 30-ball target → 10 reps; 31 → 11; always round up).
3. **Timed** — play until a timer elapses.

Applies to both basic and advanced/dynamic drills, at both play surfaces (QuickPlay composed drill
and the saved-drill list). The play mode is **saved per drill**.

## Background (how play works today)

- `RobotViewModel.playBasicTraining(training, timesOverride, ballTimeOverride)` and
  `playAdvancedTraining(training, repeatNumOverride, repeatDelayOverride)` encode the pattern and
  send it; the firmware plays a fixed **repeat count** autonomously (the pattern trailer's
  `repeatCount` byte, 1–255) then reports `PATTERN_DONE` (0x8F).
- Repeat count = `times` (basic) / `repeatNum` (advanced).
- Balls fired per repetition (`ballsPerPattern`) = `points.size` (basic) /
  `ballList.sumOf { it.points.size }` (advanced).
- `ballTime` is the inter-ball interval in tenths of a second.
- There is **no firmware "play for T seconds."** Stopping mid-drill is done with POST_PATTERN
  (`0x03`) — see `PROTOCOL.md` "Stopping a running drill".
- Play is triggered from: QuickPlay bottom bar (`onPlayBasic/onPlayAdvanced`) and the saved-drill
  rows in `TrainingListScreen` (`onPlay(training, times/repeatNum, ballTime/repeatDelay)`).

## Goals / non-goals

Goals:
- Three per-drill play modes, persisted, honored at both play surfaces.
- One shared mode-picker UI component.
- Timed mode with a visible countdown, ending the drill reliably.

Non-goals:
- Changing the BLE encoding or the calibration flow.
- Per-play *overrides* that differ from the drill's saved mode (the saved mode is authoritative;
  editing the picker edits/saves the drill).
- Arbitrary-length timed drills beyond the repeat-count cap (see Timed §, Option A limitation).

## Data model (`Models.kt`)

```kotlin
enum class PlayMode(val value: Int) {
    REPETITIONS(0), BALL_COUNT(1), TIMED(2);
    companion object { fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: REPETITIONS }
}
```

Add to **both** `BasicTraining` and `AdvancedTraining` (all defaulted → existing/bundled JSON
deserializes as REPETITIONS; `Json { ignoreUnknownKeys = true }` already tolerates the additions):

- `playMode: Int = 0`
- `ballCount: Int = 30`   // target total balls for BALL_COUNT
- `durationSec: Int = 60` // timer for TIMED

Repetitions continues to use the existing `times` / `repeatNum` — no new reps field.

The QuickPlay editing-state holders (`DrillEditorState`, `AdvancedEditorState`) gain the same three
fields, and their `toTraining()` / `loadFrom()` carry them.

## Play resolution (pure, unit-tested) — `PlayResolver.kt`

```kotlin
fun ballsPerPatternBasic(t: BasicTraining): Int = t.points.size
fun ballsPerPatternAdvanced(t: AdvancedTraining): Int = t.ballList.sumOf { it.points.size }

const val MAX_REPS = 255  // firmware repeatCount is one byte

data class ResolvedPlay(val reps: Int, val timedDurationSec: Int?)  // timedDurationSec set only for TIMED

fun resolvePlay(
    mode: PlayMode, reps: Int, ballCount: Int, durationSec: Int,
    ballsPerPattern: Int, ballTimeTenths: Int,
): ResolvedPlay {
    val bpp = ballsPerPattern.coerceAtLeast(1)
    return when (mode) {
        PlayMode.REPETITIONS -> ResolvedPlay(reps.coerceIn(1, MAX_REPS), null)
        PlayMode.BALL_COUNT  -> ResolvedPlay(ceilDiv(ballCount, bpp).coerceIn(1, MAX_REPS), null)
        PlayMode.TIMED -> {
            val perBallTenths = ballTimeTenths.coerceAtLeast(1)
            val estBalls = ceilDiv(durationSec * 10, perBallTenths)   // balls that fit in T
            val r = ceilDiv(estBalls, bpp).coerceIn(1, MAX_REPS)
            ResolvedPlay(r, durationSec)
        }
    }
}
// ceilDiv(a,b) = (a + b - 1) / b
```

`RobotViewModel.playBasic/Advanced` compute `ballsPerPattern` and `ballTime` from the drill, call
`resolvePlay`, and pass `resolved.reps` as the existing `timesOverride`/`repeatNumOverride`.
Everything downstream (encode, BLE) is unchanged. When `resolved.timedDurationSec != null`, they
also start the timed engine.

## Timed engine (Option A — compute-and-cap)

The reps from `resolvePlay` are sized to cover T (capped at 255). After sending the drill,
`RobotViewModel` starts a countdown coroutine:

- Expose `playCountdownSec: StateFlow<Int?>` (null unless a timed drill is active); tick down each
  second.
- At 0, call `stop()` (which sends `0x03`, the confirmed mid-drill stop).
- Cancel the countdown if the user stops manually, the drill is replaced, or playback ends.

`StopOverlay` shows `mm:ss` when `playCountdownSec != null`.

**Known limitation (accepted for v1):** if a drill's balls-per-pattern is tiny and `ballTime` large,
255 reps may cover less than T, so the drill could finish slightly before the timer. Realistic
timers are well within the cap (a 3-ball 0.9s drill covers ~11 min). A future "resend on
PATTERN_DONE" engine (Option B) would remove the cap.

## UI

A shared `PlayModeSelector` composable: a segmented control (Reps / Balls / Time) bound to
`playMode`, plus the value control for the active mode:
- **Reps** → the existing `StepSlider` (`times` 1–100 / `repeatNum` 1–50).
- **Balls** → a stepper for `ballCount` (e.g. 3–300), with a live "≈ N reps · M balls" readout.
- **Time** → an `mm:ss` picker for `durationSec` (e.g. 15 s – 30 min).

Placed in:
- `BasicEditorScreen` / `AdvancedEditorScreen` — replaces the current "Repetitions" / "Repeat Count"
  slider (that slider becomes the Reps option).
- `QuickPlayScreen` composed-drill controls (new; binds to `DrillEditorState`/`AdvancedEditorState`).
- `TrainingListScreen` saved-drill play rows — the existing per-row reps control becomes the picker;
  editing it updates the drill (persisted via the existing save path) before Play.

`StopOverlay` gains the countdown display for timed drills.

## Edge cases

- No points selected → Play already disabled; `resolvePlay` still guards `reps ≥ 1` and `bpp ≥ 1`.
- Ball count not an exact multiple → rounds up (33 balls for a 30 target on a 3-ball pattern).
- Manual stop during a timed drill → countdown cancelled, `playCountdownSec` cleared.
- Backward compatibility → older/bundled drills default to REPETITIONS with today's behavior.
- Advanced random / basic random → `ballsPerPattern` (points count) is unaffected by random mode, so
  ball-count math holds.

## Testing

- Unit-test `resolvePlay` (all three modes; ceil rounding; timed cap at 255; `reps ≥ 1`) and
  `ballsPerPattern` (basic vs advanced).
- Focused test of the countdown/stop wiring where feasible without an Android device.
- Manual on-device: each mode on a basic and an advanced drill; timed countdown reaches 0 and stops;
  ball-count fires ~target balls.

## Files touched

- `data/Models.kt` — `PlayMode` enum + three fields on both training types.
- New `viewmodel/PlayResolver.kt` — `ballsPerPattern*`, `resolvePlay`, `ResolvedPlay` (+ unit test).
- `viewmodel/RobotViewModel.kt` — resolve at play; `playCountdownSec` timed engine.
- New `ui/components/PlayModeSelector.kt` — shared picker.
- `ui/screens/BasicEditorScreen.kt`, `AdvancedEditorScreen.kt` — use the picker; editor-state fields.
- `ui/screens/QuickPlayScreen.kt` — picker in composed-drill controls.
- `ui/screens/TrainingListScreen.kt` — picker in play rows; `onPlay` carries the resolved play.
- `ui/components/StopOverlay.kt` — timed countdown.
- Tests under `app/src/test/...`.
