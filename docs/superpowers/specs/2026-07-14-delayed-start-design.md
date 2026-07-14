# Delayed start (get-in-position countdown)

Date: 2026-07-14
Status: Approved (design) — pending spec review

## Problem

When you tap Play, the robot fires immediately. There is no time to walk to the table and get into
position. Users want an optional lead-in countdown before the first ball.

## Solution overview

An optional **start delay**: after starting a drill in "countdown" mode, the screen shows a dim
overlay with a running countdown; the robot fires only when it reaches zero. The last 5 seconds
beep, and a distinct "go" tone plays at zero.

The delay is a **global, remembered** setting (not per-drill): a mode (immediate vs. delayed) and a
duration (default **0:10**). Both are shared across every play surface, so choosing "countdown"
anywhere applies everywhere until changed.

Two surfaces expose the choice with **different, surface-appropriate controls**, both backed by the
same global state:

1. **QuickPlay** (bottom-bar Play) → a **segmented Play button**: `Start now` / `Delayed`. The
   segment picks the mode; the highlighted mode runs when pressed. (Chosen because QuickPlay has
   room and the segmented control is the most discoverable.)
2. **Drill library** (saved-drill rows in `TrainingListScreen`) → the existing **single Play
   button** with a **long-press chooser** (`Play now` / `Countdown`). (Chosen because the rows are
   compact and can't fit a segmented control.)

This is a pure UI + ViewModel lead-in phase. **No BLE/firmware change** — the delay only gates
*when* the existing `playBasicTraining` / `playAdvancedTraining` call happens.

## Background (how play works today)

- Play is triggered from the QuickPlay bottom bar (`onPlayBasic` / `onPlayAdvanced`) and from
  saved-drill rows in `TrainingListScreen`, both routed to
  `RobotViewModel.playBasicTraining(training)` / `playAdvancedTraining(training)`.
- Those functions resolve reps (`resolvePlay`), encode the pattern, and send it over BLE
  immediately. Everything from resolve → encode → BLE is unchanged by this feature.
- There is already an *in-play* countdown for the **Timed** play mode:
  `RobotViewModel.playCountdownSec: StateFlow<Int?>` counts the running drill *down* and
  `StopOverlay` renders it. **This feature does NOT reuse that machinery** — the timed countdown is
  an in-play timer; the start delay is a distinct *pre-start* phase. Conflating them would muddy
  both. The lead-in gets its own state and overlay.
- Global app settings live in `AppPrefs` (an `object` over `SharedPreferences`, each value mirrored
  as a `StateFlow`). New global settings belong here.
- A reusable `DurationWheelPicker` (mm:ss wheel) already exists and is used by the Timed play mode;
  the delay picker reuses it.
- The in-app manual is a `helpArticles: List<HelpArticle>` in `ManualScreen.kt`; a `HelpArticle`
  has a title and a list of `HelpSection` (`Heading`, `Paragraph`, `BulletList`, `Illustration`).

## Goals / non-goals

Goals:
- Optional lead-in countdown before a drill starts, with a visible timer.
- Beep on each of the last 5 seconds; distinct "go" tone at zero.
- Global, remembered mode + duration (default 0:10), consistent across all play surfaces.
- Segmented Play button in QuickPlay; long-press chooser on the compact library rows.
- Cancelable during the countdown — the robot is never touched until fire time.
- A dedicated manual entry.

Non-goals:
- Any change to BLE encoding, reps resolution, or the calibration flow.
- Per-drill delay (delay is global).
- Delaying the **Test** (single-ball) button — Test stays immediate.
- Scheduling / very long delays as a first-class concept (the wheel can express minutes, but the
  feature is framed as a get-in-position lead-in).

## Data model / persistence — `AppPrefs.kt`

Two new global, persisted values, following the existing pattern (private `MutableStateFlow` +
public `StateFlow` + setter that writes SharedPreferences):

- `startDelayed: Boolean` (default `false`) — the current mode (false = immediate, true = countdown).
  Keys: `KEY_START_DELAYED`. Setter: `setStartDelayed(Boolean)`.
- `startDelaySec: Int` (default `10`) — the countdown duration in seconds. Key: `KEY_START_DELAY_SEC`.
  Setter: `setStartDelaySec(Int)`. `const val DEFAULT_START_DELAY_SEC = 10`.

No changes to `BasicTraining` / `AdvancedTraining`.

## ViewModel — `RobotViewModel.kt`

New state for the lead-in, kept separate from `playCountdownSec`:

- `startCountdownSec: StateFlow<Int?>` — remaining lead-in seconds; `null` when no lead-in is active.
- `beginDelayedStart(delaySec: Int, onFire: () -> Unit)`:
  - Cancels any existing lead-in job.
  - Ticks down once per second from `delaySec`, publishing to `startCountdownSec`.
  - Plays a short beep on each of the last 5 seconds (`remaining in 1..5`) and a distinct "go" tone
    at 0, via a small `StartCue` sound helper wrapping Android `ToneGenerator`
    (`STREAM_MUSIC`; e.g. `TONE_PROP_BEEP` for ticks, `TONE_PROP_BEEP2` / a longer tone for go).
  - On reaching 0: clears `startCountdownSec`, then invokes `onFire`.
  - Uses its own `Job` (`startCountdownJob`), cancelable.
- `cancelStartCountdown()` — cancels the job, clears `startCountdownSec`, releases the tone
  generator. Never touches the robot (nothing was sent yet).

`onFire` is `{ playBasicTraining(training) }` / `{ playAdvancedTraining(training) }`. Because the
existing play functions are called unchanged at fire time, resolve/encode/BLE and the *Timed*
in-play countdown all continue to work exactly as before.

Lifecycle: release the `ToneGenerator` in `onCleared()` and after each countdown/cancel.

## UI

### New `StartDelayOverlay.kt`
A full-screen dim scrim (same dim treatment as `StopOverlay`) with two states:

1. **Picker** — a `DurationWheelPicker` bound to `startDelaySec` (mm:ss, minutes start at 0, default
   0:10, remembered), a **Confirm** button, and a **Cancel** affordance (tap-scrim / back).
   Confirm persists the chosen duration (`AppPrefs.setStartDelaySec`) and transitions to countdown.
2. **Countdown** — large remaining-seconds display driven by `startCountdownSec`, plus **Cancel**
   (tap/back → `cancelStartCountdown`, overlay dismisses, drill never starts).

The overlay is hosted in `MainActivity` alongside `StopOverlay`, shown whenever a delayed start is
in progress (picker phase or `startCountdownSec != null`). Confirm wires to
`robotVm.beginDelayedStart(delaySec) { <the pending play call> }`.

### QuickPlay — segmented Play button (`SegmentedPlayButton.kt`, new)
Replaces the plain Play button in the QuickPlay bottom bar with a two-segment control
`Start now` / `Delayed`, driven by `AppPrefs.startDelayed`:
- Tapping a segment selects it and persists the mode (`setStartDelayed`).
- Pressing runs the highlighted mode: `Start now` → immediate play (today's path); `Delayed` →
  open `StartDelayOverlay` (picker → countdown → play).
- The `Test` button and its gating are untouched.

### Drill library — single Play button + long-press chooser (`TrainingListScreen.kt`)
The existing per-row Play button keeps its single-button footprint:
- **Tap** → run the remembered global mode (`AppPrefs.startDelayed`): immediate, or open the
  overlay for that drill.
- **Long-press** → a small chooser (`Play now` / `Countdown`). Each item both acts and sets the
  remembered mode: `Play now` → play immediately + `setStartDelayed(false)`; `Countdown` → open the
  overlay + `setStartDelayed(true)`.
- The button reflects the current mode so tap is predictable — `PlayArrow` when immediate, a
  timer/hourglass icon (or short duration hint) when delayed.

Both surfaces read/write the same `AppPrefs.startDelayed` + `startDelaySec`, so the mode and
duration stay in sync everywhere.

## Manual — new dedicated entry (`ManualScreen.kt`)

Add a new `HelpArticle` (title e.g. **"Delayed start"**) to `helpArticles`, documenting it as a
separate entry:
- What it is: an optional get-in-position countdown before the robot fires; last 5 seconds beep, a
  "go" tone at zero.
- QuickPlay: the segmented `Start now` / `Delayed` Play button; the delay picker (default 0:10,
  remembered).
- Drill library: **long-press** a drill's Play button to choose `Play now` / `Countdown`; tap
  repeats the last choice.
- Canceling the countdown before zero (the robot never starts).

## Edge cases

- **Duration 0:00** → treat as immediate (no overlay/countdown); guard `delaySec >= 1` before
  starting a lead-in.
- **Cancel during countdown** → job canceled, `startCountdownSec` cleared, tone generator released,
  robot untouched.
- **Play/Test pressed during a lead-in, or connection lost mid-lead-in** → cancel the pending lead-in
  cleanly before any new action; since nothing was sent, there's nothing to stop on the robot.
- **`ToneGenerator` unavailable / silent mode** → beeps are best-effort; a failure to obtain the
  tone generator must not block the countdown or the drill (wrap in try/catch, log, continue).
- **Backward compatibility** → new prefs default to immediate + 10s; existing behavior is unchanged
  until a user opts into countdown.
- **Test button** → always immediate, never delayed.

## Testing

- Unit-test `AppPrefs` start-delay get/set round-trips and defaults (immediate, 10s).
- Unit/logic test the lead-in tick sequence where feasible without a device: from N seconds it emits
  N…1 then fires exactly once; a 0/negative duration fires immediately with no ticks; cancel stops
  emissions and never fires.
- Verify the tone helper is invoked for `remaining in 1..5` and once at 0 (via an injectable
  cue interface so tests don't need real audio).
- Manual on-device: countdown reaches zero and the drill starts; last-5s beeps + go tone audible;
  cancel aborts without starting; QuickPlay segmented control and library long-press both drive the
  same global mode/duration; Test remains immediate.

## Files touched

- `data/AppPrefs.kt` — `startDelayed`, `startDelaySec` (+ StateFlows, setters, default const).
- `viewmodel/RobotViewModel.kt` — `startCountdownSec`, `beginDelayedStart`, `cancelStartCountdown`,
  tone-generator lifecycle.
- New `ui/components/StartCue.kt` (or inline helper) — `ToneGenerator` wrapper for tick/go tones.
- New `ui/components/StartDelayOverlay.kt` — picker + countdown overlay.
- New `ui/components/SegmentedPlayButton.kt` — QuickPlay segmented control.
- `ui/screens/QuickPlayScreen.kt` — use the segmented Play button.
- `ui/screens/TrainingListScreen.kt` — long-press chooser + mode-aware Play on rows.
- `MainActivity.kt` — host `StartDelayOverlay`; wire Confirm → `beginDelayedStart`.
- `ui/screens/ManualScreen.kt` — new "Delayed start" `HelpArticle`.
- Tests under `app/src/test/...`.
