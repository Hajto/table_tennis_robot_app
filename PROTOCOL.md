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
| 0x89 | CONNECT | `00` (1 byte) | Handshake after GATT connection |
| 0x99 | STOP/ABORT | `00` (1 byte) | Abort a running drill. **The only hard-stop the firmware accepts while a drill is actively firing** (along with 0x25 pause and 0x03). Also serves as disconnect when idle. |
| 0x05 | STOP (idle) / VERSION_QUERY | `00` or empty | Stops a pattern **only when idle** (e.g. before starting a new one) and/or queries firmware version. **Silently ignored while a drill is executing** — do not use it as the stop button. See "Stopping a running drill". |
| 0x04 | PRE_PATTERN | `02` (1 byte) | Sent before pattern command |
| 0x01 | PATTERN | see below | Play a drill pattern |
| 0x03 | POST_PATTERN | `00` (1 byte) | Sent after pattern completes |
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
  7     1     flags           0x80 on random drills, else 0
  8     1     ballTime        Inter-ball delay (1=fast, 20=slow)
  9     1     groupSize       Points per group, 1-5 (firmware chunks points into groups). We emit 1.
 10     1     randomMode      2 on random drills (sets the firmware's random mode-flag), else 0
 11     1     randomPick      1 on random drills (draw this shot's position randomly), else 0
```

> Earlier revisions of this doc listed bytes 7/9/10/11 as "reserved / always 1". That was
> a misread from a single sequence-drill capture; firmware disassembly shows byte 9 is a
> group size and bytes 10/11 drive random mode.

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

## Advanced Patterns (Multi-Ball)

Advanced drills contain multiple `BallEntry` objects, each with their own ball/spin/power/points/ballTime. Each ball entry's points are encoded as separate 12-byte blocks in sequence within the same payload.

**TODO**: The timing between different ball types in a sequence needs further investigation. The original app likely uses a different mechanism for inter-ball timing in advanced drills.

## Random Mode

Random mode is a firmware feature, not something the app simulates. The firmware parses the
pattern payload, chunks points into groups (byte 9 = group size), and for each shot decides the
landing position based on two per-point control bytes:

- **byte 11 == 1** → the firmware draws the position from a *shuffle-bag* of the enabled groups:
  `index = rand() % count`, skipping already-used entries and marking each as used, resetting the
  bag once every position has been fired. `rand()` is a firmware LCG (`seed = seed*mult + 12345`).
- **byte 10 == 2** → sets the firmware's random mode-flag (governs bag reset between cycles).
- **byte 7 == 0x80** → observed on random drills in the original app's traffic.

With bytes 10/11 left at 0 the firmware walks the positions **in stored order**, which is why a
"random" drill built with those bytes zeroed plays as a fixed sequence. A single random point
looks like this on the wire (motor bytes vary):

```
14 14 22 11 00 01 80 09 01 02 01
             │rDly│ 80 09 01 02 01
                   b7 b8 b9 b10 b11
```

Source: HCI capture of the original app playing a random drill, cross-checked against the robot
firmware (Ghidra): dispatcher `FUN_08013…`, shuffle-bag selector, and the LCG at the
random-source function.

## Stopping a running drill

The firmware's frame parser gates *which* commands it will act on by execution state. **While a
drill is actively firing it accepts only 0x99 (abort), 0x25 (pause) and 0x03** — every other
command, **including 0x05, is silently skipped**. Only 0x99 sets the internal abort flag that
halts the drill.

Practical consequence: use **0x99** for the stop button regardless of robot type. `0x05` works to
clear a pattern only when the robot is idle (e.g. right before starting a new drill). Because 0x99
also means DISCONNECT at idle, only send it while a pattern is active.

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
- How advanced drill timing/sequencing works (inter-ball delays)
- Trailer bytes — values may differ for advanced vs basic drills
- CMD 0x98 behavior vs CMD 0x01 on different firmware versions
- Post-pattern CMD 0x03 — is it required or optional?
- Exact mapping of `adjustPosition` and `adjustSpin` flags to motor behavior
- Whether random within a multi-point group (byte 10 == 1, a second firmware code path) is used by the original app for anything
- Exact role of per-point byte 7 = 0x80 on random drills (present on the wire; firmware usage not fully traced)
