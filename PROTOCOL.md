# Joola Infinity Robot BLE Protocol

Reverse-engineered from the original Joola Infinity Android app (v2.1.1) via smali disassembly and HCI snoop capture.

## BLE Service & Characteristics

The robot advertises as `J-{16 hex chars}` (e.g. `J-34EAE7F6CDD60001`).

**Primary service** (not present on all robots):
- Service: `0000ffe0-0000-1000-8000-00805f9b34fb`
- Characteristic: `0000ffe1-0000-1000-8000-00805f9b34fb` (read/write/notify)

**Alt service** (used when primary is absent):
- Service: `0000fee7-0000-1000-8000-00805f9b34fb`
- `0000fec7` — WRITE_WITHOUT_RESPONSE (props=0x04) — **command writes**
- `0000fec8` — READ | NOTIFY (props=0x12) — **responses via notification**
- `0000fed5` — WRITE (props=0x08) — purpose unknown
- `0000fed6` — INDICATE (props=0x20) — **responses via indication** (mirrors fec8)

### Connection Setup

1. Connect GATT
2. Discover services
3. Enable **notifications** on `fec8` (write `01 00` to its CCCD descriptor `0x2902`)
4. Enable **indications** on `fed6` (write `01 00` to its CCCD descriptor `0x2902`)
5. Send handshake frame (CMD_CONNECT)

### Write Method

All command frames are written to `fec7` using **ATT Write Command (opcode 0x52)** — write WITHOUT response. NOT Write Request (0x12).

Frames are split into 20-byte MTU chunks. 5ms delay between chunks.

## Frame Format

```
[0]    0x68         Frame start
[1]    0x01         Fixed
[2-9]  Device ID    8 bytes, hex-decoded from device name (J-XXXXXXXXXXXXXXXX)
[10]   0x68         Second start marker
[11]   CMD          Command byte
[12]   LEN_H        Payload length high byte (big-endian)
[13]   LEN_L        Payload length low byte
[14..] PAYLOAD      Variable length
[-3]   CRC_H        CRC-16-CCITT high byte
[-2]   CRC_L        CRC-16-CCITT low byte
[-1]   0x16         Frame end
```

Total frame size: 14 + payload_length + 3 = payload_length + 17

### CRC-16-CCITT

- Polynomial: 0x1021
- Initial value: 0x0000
- Computed over bytes [0] through [13 + payload_length] (everything before CRC)
- Lookup table extracted from original APK (see `RobotProtocol.kt`)

### Device ID Extraction

From BLE device name `J-XXXXXXXXXXXXXXXX`:
- Take characters 2-18 (16 hex chars)
- Hex-decode to 8 bytes
- Pad with `0` if shorter than 16 chars

## Commands

| CMD | Name | Payload | Description |
|-----|------|---------|-------------|
| 0x89 | CONNECT | `00` (1 byte) | Handshake after GATT connection. Do not send right after a stop — it can re-arm the pattern. |
| 0x99 | DISCONNECT / abort-flag | `00` (1 byte) | Sets an internal abort flag and serves as disconnect when idle. **Does not stop a firing drill** — the shot loop never reads the flag (verified on hardware). Use 0x03 to stop. |
| 0x05 | STOP (idle) / VERSION_QUERY | `00` or empty | Stops a pattern **only when idle** (e.g. before starting a new one) and/or queries firmware version. **Silently ignored while a drill is executing** — do not use it as the stop button. See "Stopping a running drill". |
| 0x03 | POST_PATTERN / STOP | `00` (1 byte) | Ends the current pattern: parser returns `3`, driving the shot loop to its done state. **This is the command that stops a running drill.** |
| 0x04 | PRE_PATTERN | `02` (1 byte) | Sent before pattern command |
| 0x01 | PATTERN | see below | Play a drill pattern |
| 0x98 | PATTERN_ALT | same as 0x01 | Alternative pattern command (used when robot version != "average") |

### Command Sequence for Playing a Drill

```
1. CMD 0x05 (STOP)          — stop any existing pattern
2. wait 300ms
3. CMD 0x04 payload=0x02    — pre-pattern setup
4. wait 200ms
5. CMD 0x01 payload=pattern — play the pattern
6. (robot plays, sends 0x8F when done)
7. CMD 0x03 payload=0x00    — post-pattern cleanup (optional?)
```

## Response Commands

Responses arrive on both `fec8` (notification) and `fed6` (indication) simultaneously.

| CMD | Name | Payload | Description |
|-----|------|---------|-------------|
| 0x81 | PATTERN_ACK | empty | Pattern command received |
| 0x83 | POST_ACK | 1 byte (`01`) | Post-pattern acknowledged |
| 0x84 | PRE_ACK | 1 byte | Pre-pattern acknowledged |
| 0x85 | FIRMWARE | 4 bytes | Firmware version (e.g. `00 02 00 02` = v02.02) |
| 0x8F | PATTERN_DONE | 1 byte (`10`) | Pattern execution complete |

## Pattern Payload Encoding

### Per-Point Layout (12 bytes)

Confirmed via HCI snoop capture of the working original app **and** Ghidra analysis of the
robot firmware (see "Random Mode" below):

```
Offset  Size  Field           Source
  0     1     m1speed         Motor 1 speed index (from base-conf lookup, 0-40)
  1     1     m2speed         Motor 2 speed index (from base-conf lookup, 0-40)
  2     1     xaxis           X servo position index (from base-conf lookup, 0-40)
  3     1     yaxis           Y servo position index (from base-conf lookup, 0-32)
  4     1     zaxis           Z servo position index (from base-conf lookup, 0-20)
  5     2     repeatDelay     Big-endian uint16 (1 = 0.2s between set repeats)
  7     1     flags           0x80 on any point that takes part in randomness, else 0 (travels with b11)
  8     1     ballTime        Inter-ball delay (1=fast, 20=slow)
  9     1     groupSize       Size of the group this point belongs to, 1-5 (see "Random Mode")
 10     1     groupMode       0 normally; 1 on the FIRST point of a within-step random group
                              (advanced); 2 for a basic whole-drill random. See "Random Mode".
 11     1     randomDraw      1 if this point takes part in a random draw, else 0
```

> Earlier revisions of this doc listed bytes 7/9/10/11 as "reserved / always 1", then later as
> a single "random mode" flag. Both were misreads from partial captures. The current model
> (byte 9 = group size, byte 10 = group leader / random-mode selector, byte 11 = random-draw
> enable) is confirmed by a battery of controlled HCI captures — see "Random Mode".

### Trailer (4 bytes, after all points)

```
Offset  Size  Field                   Values
  0     1     repeatCount             1-255 (how many times to cycle the pattern)
  1     1     interSetDelayMult       Typically 1
  2     1     reserved                0
  3     1     reserved                0
```

### Total Payload

`(num_points * 12) + 4` bytes

### Motor Config Lookup

Motor values come from the **base-conf** table, indexed by:
- `ball` (0=serve, 1=normal, 2=lob)
- `spin` (0=max topspin, 1=topspin, 2=float, 3=backspin, 4=max backspin)
- `power` (0=extreme, 1=strong, 2=medium, 3=light)
- `landarea` (1-15, grid position)

Returns: `m1speed`, `m2speed`, `xaxis`, `yaxis`, `zaxis`

The original app may apply device-specific adjustments from `ballSettings` / `minorAdjustments` on top of base-conf values. Without device calibration, base-conf values work correctly.

## Advanced Patterns (Multi-Ball) — the "step" model

An advanced (dynamic) drill is an **ordered list of steps**. Each step is one ball configuration
(ball / spin / power / ballTime) plus **1 to 5 target positions**. The original app's editor caps a
step at **5 position markers**; to cover more positions you add more steps.

Steps map onto the payload as **contiguous groups of 12-byte points** (byte 9 = group size). A
step's config is baked into every one of its points (bytes 0-4), so distinct steps just sit
back-to-back in the payload with their own motor bytes — there is no separate step delimiter or
inter-step timing field beyond the per-point `ballTime`/`repeatDelay`.

There are two kinds of step:

- **Single-ball step** (1 position) → a group of size 1 (`b9=1`).
- **Multi-ball step** (2-5 positions) → one group of that size (`b9=N`), and it is **always
  within-step random** ("fire a random one of its balls"); the app has no fixed-order multi-ball
  step. See "Random Mode".

## Random Mode

Randomness is a firmware feature — the app only sets flag bytes, and the robot's LCG
(`seed = seed*mult + 12345`) does the drawing. The firmware chunks the points into **groups**
(byte 9 = group size) and, for each point whose **byte 11 == 1**, draws from a *shuffle-bag*:
`index = rand() % count`, skipping already-used entries and marking each used, resetting the bag
once every entry has fired. `byte 7 == 0x80` travels with every randomised point.

Two independent axes of randomness compose through that one mechanism:

### Axis 1 — step-order random (shuffle the order of steps)

Each participating step stays as its own point(s) and gets `b7=0x80, b11=1` with `b10=0`. The
firmware's bag then draws over these groups, i.e. it **shuffles the order** in which the steps
fire. (Everything still fires; only the order varies.)

### Axis 2 — within-step random (a multi-ball step fires a random one of its balls)

A multi-ball step's N points form **one group**: every point carries `b9=N, b7=0x80, b11=1`, and
the group's **first point additionally carries `b10=1`** — that leader bit is how the firmware
delimits the group. The bag then draws **within** the group.

`b10=1` on the leader is the "random within a multi-point group" firmware code path. It is
distinct from **basic** whole-drill random, which uses `b10=2` on every point.

**"Double random"** = a within-step group (`b9=N, b10=1`) whose points are also `b11=1`, so the
group takes part in the order shuffle-bag alongside its sibling steps. On the wire this is
identical to a lone within-step group; the ordering effect only manifests when sibling groups
exist to shuffle against.

### Probability weighting via duplicate positions

A step is an ordered **list** of up to 5 balls, not a set — the same position may appear more than
once, which adds it to the shuffle-bag again and raises its draw odds. E.g. a step
`{5, 5, 11, 11, 20}` draws position 5 and position 11 with probability 2/5 each and position 20
with 1/5.

### Summary of the flag combinations

```
Step kind                         b7    b9   b10  b11
in-order single-ball step         0x00   1    0    0
order-random single-ball step     0x80   1    0    1
within-random multi-ball step:
   - group leader (first point)   0x80   N    1    1
   - other points in the group    0x80   N    0    1
basic whole-drill random (each)   0x80   1    2    1
```

### Worked example — bundled "Half Long 2/3 FH Loop"

Two within-random steps of 5 balls each, both flagged for order-random between them, repeated 15×.
Note the `b10=1` leader on each group and the duplicated positions (weighting):

```
      m1 m2  x  y   b9 b10 b11
grp1   7 22  5 15    5   1   1   ← step 1 leader
       7 22  5 15    5   0   1   ← dup of position 5 (weight 2/5)
       8 23 11 15    5   0   1
       8 23 11 15    5   0   1   ← dup of position 11 (weight 2/5)
       7 22 20 15    5   0   1   ← position 20 (weight 1/5)
grp2   7 22  5 15    5   1   1   ← step 2 leader
       ... (same five) ...
trailer: 0f 00 00 00             ← repeatCount = 15
```

Source: a battery of controlled HCI captures of the original app (in-order, step-order random,
within-step random, and the FH-loop preset), cross-checked against the robot firmware (Ghidra):
dispatcher, shuffle-bag selector, and the LCG at the random-source function.

## Stopping a running drill

The firmware runs the shot loop until a state byte becomes `3`, and its command parser sets that
state to the value it returns for each command. **Only `0x03` (POST_PATTERN, payload `00`) makes
the parser return `3`**, so `0x03` is the command that actually halts a firing drill — confirmed
on the wire (the original app stops with a single `0x03`, which the robot acks with `0x83`) and in
the firmware (dispatcher `case 3` → `return 3`; the shot loop breaks on `state == 3`).

The parser also gates *which* commands it acts on while a drill is firing — it accepts only `0x99`,
`0x25` and `0x03` and skips everything else (including `0x05`) — **but that gate is a red herring
for stopping**:

- `0x05` — silently ignored mid-drill. (This is why the old V2 stop, which sent `0x05`, was flaky.)
- `0x99` — sets an internal abort flag, **but the shot loop never reads it**, and the parser
  returns `0` (state unchanged), so the drill keeps firing. `0x99` does **not** stop a running
  drill (verified on hardware). It doubles as DISCONNECT when idle.
- `0x25` — pause; clears some counters, returns `0`, does not drive the loop to `state == 3`.
- `0x03` — returns `3` → shot loop exits. **Use this for the stop button.**

Practical consequence: to stop a running drill send **`0x03`** (POST_PATTERN, payload `00`) and do
**not** immediately re-handshake with `0x89` (CONNECT), which can re-arm the pattern.

## Grid Layout

15 positions arranged as 3x5:
```
        Net
  1   2   3   4   5     (Row 0 - close to net)
  6   7   8   9  10     (Row 1 - middle)
 11  12  13  14  15     (Row 2 - close to player)
       Player
```

`landarea` in the base-conf maps directly to these grid positions (1-15). The `y` parameter (1=short, 2=medium, 3=long) represents ball depth/trajectory.

## Robot Firmware

Tested on firmware version **02.02**. The protocol may differ on other versions. The robot version string (from device-list API) determines whether CMD 0x01 or 0x98 is used:
- Version == "average": use CMD 0x01
- Otherwise: use CMD 0x98

In practice, CMD 0x01 worked on the tested robot (firmware 02.02).

## Data Sources

- **base-conf.json**: 465 motor configuration entries (from pinfinity server)
- **basic-trainings.json**: ~100 preset basic drills
- **advanced-trainings.json**: ~100 preset advanced drills
- **device-list**: Per-device calibration (ballSettings, minorAdjustments)

## Open Questions

- Exact role of `fed5` (WRITE) characteristic
- CMD 0x98 behavior vs CMD 0x01 on different firmware versions
- Post-pattern CMD 0x03 — is it required or optional?
- Exact mapping of `adjustPosition` and `adjustSpin` flags to motor behavior
- Firmware playback of a within-step group: does `b10=1` fire all N balls once each in random
  order per cycle, or one random ball per cycle? (Flag encoding is confirmed; the exact draw
  cadence is not yet observed by ball count.)
- **Device ID field mismatch:** in the captured original-app frames the 8-byte device-ID field
  (`68 01 <devid> 68 …`) did not match the name-derived ID (`J-34EAE7F6CDD60001`); the CRC still
  validated. Our app sends the name-derived ID and the robot accepts it, so the field may be
  ignored or derived differently — not yet traced.

**Resolved** (previously open): byte 9 is a per-point group size (1-5); byte 10 selects the random
code path (`1` = within-group leader, `2` = basic whole-drill); byte 7 = 0x80 marks a randomised
point. Advanced multi-ball timing/sequencing is just contiguous per-step groups (no separate
inter-step field). See "Random Mode" and "Advanced Patterns".
