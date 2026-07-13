# Advanced "Steps" Model Redesign — Design

**Date:** 2026-07-13
**Branch:** `feat/advanced-steps-model` (off `main`; carries the PROTOCOL.md rewrite)
**Supersedes:** PR #21 (interim per-`BallEntry` randomness fix — wrong multi-position semantics)

## Background

HCI captures of the original Joola app (decoded in this session, documented in `PROTOCOL.md`
§ "Advanced Patterns" / "Random Mode") revealed the real advanced-drill model. Our current
`BallEntry`-based model and encoder cannot represent it. This redesign rebuilds the advanced
drill around **steps**.

### The verified model (from `PROTOCOL.md`)
- An advanced drill is an **ordered list of steps**.
- A **step** = one ball config (ball/spin/power/ballTime) + **1–5 balls**, each aimed at a grid
  position. Duplicate positions are allowed and **weight** the random draw.
- A **multi-ball step (2–5 balls) is always within-random**: the firmware fires a random one of
  its balls. There is no fixed-order multi-ball step.
- Any step can additionally be **order-random**: it joins the firmware shuffle-bag that randomizes
  the order steps fire in.
- Wire encoding per point (byte layout in `PROTOCOL.md`):

| step kind | b7 | b9 (groupSize) | b10 | b11 |
|---|---|---|---|---|
| single-ball, not order-random | 0 | 1 | 0 | 0 |
| single-ball, order-random | 0x80 | 1 | 0 | 1 |
| multi-ball, group leader (`j==0`) | 0x80 | N | **1** | 1 |
| multi-ball, other balls | 0x80 | N | 0 | 1 |

General rule: `b7 = b11 = (N>1 || orderRandom)`, `b9 = N`, `b10 = (N>1 && j==0)`.

## Decisions (locked in brainstorm)
1. **Random model:** within-random is derived from `balls.size > 1`; `orderRandom` is a per-step toggle.
2. **Migration:** auto-migrate user drills by rule (below) AND re-author the 33 bundled presets natively.
3. **Weighting:** the editor supports duplicate positions per step (up to 5 balls total).
4. **Scope:** one plan — model + migration + encoder + editor + presets + tests, one branch.

## Design

### 1. Data model (`data/Models.kt`)
Introduce `Step`, replace `AdvancedTraining.ballList: List<BallEntry>` with `steps: List<Step>`:
```kotlin
@Serializable
data class Step(
    val ball: Int = 1,
    val spin: Int = 2,
    val power: Int = 2,
    val ballTime: Int = 9,
    val balls: List<Point> = emptyList(),  // 1..5, duplicates allowed (weighting)
    val orderRandom: Boolean = false,
)
```
- `withinRandom` is a derived property: `balls.size > 1`.
- `BallEntry` is **retained as a legacy, deserialization-only type** used solely by migration.
- `AdvancedTraining` keeps `id/name/repeatNum/repeatDelay/isFavourite/skillLevel/tags/isDefault`,
  swaps `ballList` → `steps`.

### 2. Migration (`BallEntry` → `Step`), applied on load and to presets
A dedicated pure function `migrateBallEntriesToSteps(ballList): List<Step>`:
- `random == 1`, 1 point → `Step(config, balls=[pt], orderRandom=true)`.
- `random == 1`, 2–5 points → one `Step(config, balls=points, orderRandom=false)` (within-random).
- `random == 1`, >5 points → chunk points into groups of ≤5, each a within-random `Step`
  (`orderRandom=false`).
- `random == 0`, N points → **N single-ball `Step`s** in order (`orderRandom=false`) — preserves
  fixed-order playback (no fixed multi-ball step exists).

**Deserialization compatibility:** stored `AdvancedTraining` JSON has a `ballList` key and no
`steps`. Use a serialization approach where an old file populates `steps` via migration. Concrete
approach: give `AdvancedTraining` an optional legacy `ballList: List<BallEntry>? = null`; after
decode, if `steps` is empty and `ballList` non-null, run migration to fill `steps`; never write
`ballList` back. (The store's load path performs this normalization before use.)

### 3. Encoder (`ble/RobotProtocol.kt`)
Rewrite `encodeAdvancedPattern` to walk `training.steps`, emitting the flag bytes per the table
above. Keep the `encodeBasicPattern` structural pattern: a public `lookup`-lambda core plus a
`MotorConfig` convenience overload that delegates. Trailer `repeatCount = repeatNum`.

```kotlin
for (step in training.steps) {
    val n = step.balls.size
    val within = n > 1
    val rnd = within || step.orderRandom
    step.balls.forEachIndexed { j, p ->
        val params = lookup(step.ball, step.spin, step.power, p.x)
        // bytes 0..6 as today (motor + repeatDelay)
        buf[7]  = if (rnd) 0x80.toByte() else 0
        buf[8]  = (step.ballTime and 0xFF).toByte()
        buf[9]  = n.coerceIn(1, 5).toByte()
        buf[10] = if (within && j == 0) 1 else 0
        buf[11] = if (rnd) 1 else 0
    }
}
```

### 4. Editor UI (`ui/screens/AdvancedEditorScreen.kt`)
- Rename ball cards to **step cards**; state `ballList` → `steps` (`Step`), update
  add/remove/move/update helpers.
- Each step card: ball/spin/power dropdowns, ball interval, a **position grid where each tap adds
  a ball** at that cell (tap again → +1; per-cell **count badge**), capped at **5 balls total per
  step**; a **remove-one** affordance per cell (e.g. long-press or a stepper). A **"Random order"**
  toggle. A hint reading "random target" when `balls.size > 1`.
- For multi-ball steps, show `orderRandom` as **implied-on and disabled** (within-random forces
  `b11=1`, so order-random isn't independently controllable — see PROTOCOL.md nuance).
- Keep step reorder (up/down/drag) and the sequence overview grid; the overview counts duplicates.

### 5. Presets (`assets/advanced-trainings.json`)
Re-author all 33 drills into the `steps` schema. Generate a first pass by running
`migrateBallEntriesToSteps` over the existing JSON, then hand-fix the grouped/weighted drills
(notably "Half Long 2/3 FH Loop" → two 5-ball within-random steps with the weighting duplicates)
so their encoded bytes match the original app.

### 6. Playback (`viewmodel/RobotViewModel.kt`)
`playAdvancedTraining` continues to call `encodeAdvancedPattern(training, motorConfig, …)`; update
only for the model rename. History snapshot logging (if it references `ballList`) updated to `steps`.

### 7. Tests
- **Encoder** (`RobotProtocolTest`): one test per row of the flag table; a weighting test (duplicate
  positions produce duplicate points); the two-step FH-loop shape (two `b9=5` groups, `b10=1` on
  each leader); `b9` capped at 5.
- **Migration** (new test): each rule (1-pt random, ≤5 random, >5 random split, non-random split).
- **Serialization round-trip:** an old `ballList` JSON decodes and normalizes into the expected
  `steps`; a `steps` JSON round-trips.

## Out of scope
- Basic mode / `encodeBasicPattern` (unchanged).
- Inter-step timing beyond per-step `ballTime`/`repeatDelay` (none exists on the wire).

## Known nuance (verify on hardware)
`b11=1` is forced for multi-ball steps, so a multi-ball step is inherently in the order shuffle-bag;
the `orderRandom` toggle is only independently meaningful for single-ball steps. Encoding uses
`b11 = (N>1 || orderRandom)`, matching every capture. Confirm on the robot that re-authored presets
and migrated drills play correctly (varying positions; correct step ordering).

## Files touched
- `app/src/main/java/com/tablebot/data/Models.kt` — `Step`, `AdvancedTraining.steps`, legacy `BallEntry`
- `app/src/main/java/com/tablebot/data/` — migration function (+ its home, e.g. a `Migration.kt` or in the store)
- `app/src/main/java/com/tablebot/ble/RobotProtocol.kt` — `encodeAdvancedPattern`
- `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` — step editor + weighting
- `app/src/main/java/com/tablebot/viewmodel/RobotViewModel.kt` — model rename in play/history path
- `app/src/main/assets/advanced-trainings.json` — re-authored presets
- `app/src/test/java/com/tablebot/ble/RobotProtocolTest.kt` + new migration/serialization tests
