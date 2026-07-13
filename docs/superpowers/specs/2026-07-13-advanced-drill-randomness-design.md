# Fix Advanced Drill Randomness — Design

**Date:** 2026-07-13
**Branch:** `fix/advanced-drill-randomness` (off `main`, v0.2.3)

## Problem

Advanced (Dynamic) drills always play their positions in a fixed order and can
never randomize, even the 6 bundled preset drills that are marked as random.

Randomness in this app is **not** produced by app-side RNG. It is a firmware
feature: the app sets per-point flag bytes in the BLE pattern payload and the
robot firmware draws each shot from a shuffle-bag of the flagged positions
(`rand()` LCG, see `PROTOCOL.md` § Random Mode).

`RobotProtocol.encodeBasicPattern` sets those flags correctly (fixed in commit
`dbc6694`). `RobotProtocol.encodeAdvancedPattern` (RobotProtocol.kt:211) hardcodes
the three flag bytes to `0` and ignores each `BallEntry.random` field, so every
advanced drill is encoded as an in-order walk. The fix from `dbc6694` was applied
only to the basic path and never carried to the advanced path.

## Root cause (verified in code + data)

- `BallEntry` (Models.kt:33) already carries a `random: Int = 0` field.
- The bundled `advanced-trainings.json` (33 drills) marks randomness with
  `random == 1`, **not** `landType`:
  - `landType` on advanced entries takes values `0` and `3` — `3` is not in our
    `LandType` enum (STATIC=0/LOOP=1/RANDOM=2). On the advanced path `landType`
    is a vestigial original-app field and must not be interpreted with the basic
    `LandType` enum.
  - 6 entries have `random == 1`. The meaningful case is the drill
    "Half Long 2/3 FH Loop": `random=1` with 10 points → the firmware should
    shuffle among those 10 positions but currently walks them in order.
- The advanced editor UI (`AdvancedEditorScreen.BallEntryEditor`) has no control
  that sets `random`, so users cannot author a random advanced ball in-app.

## Firmware encoding (authoritative: PROTOCOL.md:160-193)

The advanced per-ball random encoding differs deliberately from the basic one:

| Byte | Basic full-random | Advanced per-ball random | Non-random |
|------|-------------------|--------------------------|------------|
| 7  (flags)      | `0x80` | `0x80` | `0` |
| 10 (randomMode) | `2`    | **`0`** | `0` |
| 11 (randomPick) | `1`    | `1`    | `0` |

`byte 11 == 1` is the operative trigger in both cases; `byte 7 == 0x80` travels
with it. The advanced path leaves `byte 10` at `0`. The firmware respects these
flags per-point, so an advanced drill may mix an in-order ball entry (e.g. a
serve) with a random-position entry in the same payload.

## Design

### Decisions

- **Trigger field:** `entry.random == 1` (matches presets + PROTOCOL.md). Do
  **not** key on `landType` for the advanced path.
- **UI control:** a single binary "Randomize position" toggle per ball card.
  Static-vs-Sequence is not a distinct encoding on the advanced path (it is only
  a function of how many points the entry has, played in order); randomness is
  the only behavioral switch, so a three-way selector would give two options
  that encode identically.
- **`landType` on advanced entries:** left untouched and preserved through
  serialization; never interpreted via the basic `LandType` enum.
- **Scope:** full — encoder + UI + tests + hardware verification.

### 1. Encoder — `RobotProtocol.encodeAdvancedPattern` (RobotProtocol.kt:211)

For each `BallEntry`, compute `isRandom = entry.random == 1` and set that entry's
points' flag bytes accordingly:

```kotlin
val isRandom = entry.random == 1
...
buf[7]  = if (isRandom) 0x80.toByte() else 0
buf[8]  = (entry.ballTime and 0xFF).toByte()
buf[9]  = 1
buf[10] = 0                              // advanced path keeps randomMode at 0
buf[11] = if (isRandom) 1 else 0
```

All other bytes, the per-entry point loop, and the trailer are unchanged. This
single change makes the 6 preset random drills randomize.

### 2. UI — `AdvancedEditorScreen.BallEntryEditor`

Add a "Randomize position" toggle (Switch) in the ball-entry editor, near the
`TableGrid` point selector. It reflects `entry.random == 1` and, on change,
persists via the existing immutable-copy path:

```kotlin
onToggle = { on -> state.updateBall(index, entry.copy(random = if (on) 1 else 0)) }
```

`addBall()` and the initial `BallEntry` keep `random = 0` (default). No other
state plumbing is required — `AdvancedEditorState.updateBall` already exists and
`toTraining()` already round-trips `ballList`.

The toggle stays enabled regardless of point count; with a single point a random
entry is harmless (shuffle-bag of one).

### 3. Tests — `RobotProtocolTest`

No `encodeAdvancedPattern` tests exist today. Add:

1. A random `BallEntry` (`random = 1`) sets `b7 = 0x80`, `b11 = 1`, and asserts
   `b10 == 0` (guards against copying the basic encoding).
2. A non-random entry keeps `b7/b10/b11 == 0`.
3. A mixed drill: an in-order serve entry followed by a random loop entry — the
   serve's points stay `0`, the loop's points carry the random flags.
4. An exact-wire-bytes assertion for the "Half Long 2/3 FH Loop" preset shape
   (10 points, `random = 1`) — locks the fix against regression.

### 4. Hardware verification

Because randomness is firmware-side, the definitive check is on the real robot:
load a per-ball random advanced drill (e.g. "Half Long 2/3 FH Loop") and confirm
the shots land on varying positions rather than a fixed cycle. Record the result.

## Out of scope

- App-side RNG (none exists; not adding any).
- Changes to basic mode or `encodeBasicPattern`.
- Reinterpreting or migrating advanced `landType`.
- Inter-ball timing in advanced drills (standing `PROTOCOL.md` TODO).

## Files touched

- `app/src/main/java/com/tablebot/ble/RobotProtocol.kt` — encoder fix
- `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` — toggle
- `app/src/test/java/com/tablebot/ble/RobotProtocolTest.kt` — new tests
