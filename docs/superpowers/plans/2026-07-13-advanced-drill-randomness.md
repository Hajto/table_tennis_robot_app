# Advanced Drill Randomness Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make advanced (multi-ball) drills honor per-ball randomness so a `BallEntry` marked `random = 1` shuffles its target positions on the robot, and give users an in-app toggle to set it.

**Architecture:** Randomness is a firmware feature driven by per-point flag bytes in the BLE pattern payload; the app only sets the flags. `encodeAdvancedPattern` currently hardcodes those bytes to 0. We fix the encoder to set them per `BallEntry.random`, refactor it to a testable `lookup`-lambda core (mirroring `encodeBasicPattern`), and add a binary "Randomize position" toggle to the advanced editor.

**Tech Stack:** Kotlin, Android, Jetpack Compose (Material 3), JUnit4 unit tests, Gradle.

## Global Constraints

- Advanced-path random encoding differs from basic: random points use `byte7 = 0x80`, `byte11 = 1`, **`byte10 = 0`** (basic uses `byte10 = 2`). Non-random points keep all three at `0`. Source: `PROTOCOL.md:172-179`.
- The randomness trigger on the advanced path is `entry.random == 1` — **not** `landType`. `landType` on advanced entries is vestigial (preset values 0/3 don't map to the `LandType` enum) and must not be interpreted or changed.
- Follow the existing `encodeBasicPattern` pattern: public `lookup`-lambda core + a `MotorConfig` convenience overload that delegates to it. Keep the existing `RobotViewModel` call site (`RobotViewModel.kt:148`) working unchanged.
- Flag-byte unit tests use a null lookup (`nullLookup`) — flag encoding is independent of motor values.
- Test command: `./gradlew :app:testDebugUnitTest --tests "com.tablebot.ble.RobotProtocolTest"`

---

### Task 1: Fix `encodeAdvancedPattern` random flags (with testable overload)

**Files:**
- Modify: `app/src/main/java/com/tablebot/ble/RobotProtocol.kt:211-251`
- Test: `app/src/test/java/com/tablebot/ble/RobotProtocolTest.kt`

**Interfaces:**
- Consumes: `AdvancedTraining`, `BallEntry` (com.tablebot.data.Models), `MotorParams`, `MotorConfig`.
- Produces:
  - `fun encodeAdvancedPattern(training: AdvancedTraining, repeatNumOverride: Int? = null, repeatDelayOverride: Int? = null, lookup: (ball: Int, spin: Int, power: Int, cell: Int) -> MotorParams?): ByteArray` — testable core.
  - `fun encodeAdvancedPattern(training: AdvancedTraining, motorConfig: MotorConfig, repeatNumOverride: Int? = null, repeatDelayOverride: Int? = null): ByteArray` — convenience overload (unchanged call signature for `RobotViewModel`).

- [ ] **Step 1: Write the failing tests**

Add to `RobotProtocolTest.kt`. First add these imports near the top (after the existing `com.tablebot.data.*` imports):

```kotlin
import com.tablebot.data.AdvancedTraining
import com.tablebot.data.BallEntry
```

Then add this test block after the basic random-mode tests (after line 210-ish, inside the class):

```kotlin
    // ── Advanced random-mode pattern encoding ────────────────────────────────────

    private fun ballEntry(random: Int, points: List<Point>, ball: Int = 1, spin: Int = 2, power: Int = 2) =
        BallEntry(ball = ball, spin = spin, power = power, points = points, ballTime = 9, random = random)

    private fun advancedTraining(vararg entries: BallEntry) =
        AdvancedTraining(id = 1, name = "adv", repeatNum = 10, repeatDelay = 1, ballList = entries.toList())

    @Test
    fun `encodeAdvancedPattern random entry sets byte7 and byte11 but leaves byte10 clear`() {
        val training = advancedTraining(ballEntry(random = 1, points = listOf(Point(6, 2), Point(7, 2), Point(8, 2))))
        val buf = RobotProtocol.encodeAdvancedPattern(training, lookup = nullLookup)
        for (i in 0 until 3) {
            val off = i * 12
            assertEquals("point $i byte7", 0x80.toByte(), buf[off + 7])
            assertEquals("point $i byte9 groupSize", 1.toByte(), buf[off + 9])
            assertEquals("point $i byte10 must stay 0 on advanced path", 0.toByte(), buf[off + 10])
            assertEquals("point $i byte11 random-trigger", 1.toByte(), buf[off + 11])
        }
    }

    @Test
    fun `encodeAdvancedPattern non-random entry leaves all random flags clear`() {
        val training = advancedTraining(ballEntry(random = 0, points = listOf(Point(3, 2), Point(8, 2))))
        val buf = RobotProtocol.encodeAdvancedPattern(training, lookup = nullLookup)
        for (i in 0 until 2) {
            val off = i * 12
            assertEquals("point $i byte7", 0.toByte(), buf[off + 7])
            assertEquals("point $i byte10", 0.toByte(), buf[off + 10])
            assertEquals("point $i byte11", 0.toByte(), buf[off + 11])
        }
    }

    @Test
    fun `encodeAdvancedPattern mixes in-order serve with random loop per entry`() {
        // Entry 0: in-order serve (1 point). Entry 1: random loop (2 points).
        val training = advancedTraining(
            ballEntry(random = 0, points = listOf(Point(8, 2)), ball = 0),
            ballEntry(random = 1, points = listOf(Point(6, 2), Point(10, 2))),
        )
        val buf = RobotProtocol.encodeAdvancedPattern(training, lookup = nullLookup)
        // Point 0 (serve) — flags clear
        assertEquals("serve byte7", 0.toByte(), buf[7])
        assertEquals("serve byte11", 0.toByte(), buf[11])
        // Points 1 and 2 (random loop) — flags set, byte10 clear
        for (i in 1 until 3) {
            val off = i * 12
            assertEquals("loop point $i byte7", 0x80.toByte(), buf[off + 7])
            assertEquals("loop point $i byte10", 0.toByte(), buf[off + 10])
            assertEquals("loop point $i byte11", 1.toByte(), buf[off + 11])
        }
    }

    @Test
    fun `encodeAdvancedPattern regression - Half Long FH Loop preset shape randomizes all points`() {
        // Mirrors the bundled "Half Long 2/3 FH Loop" preset: random=1 with 10 points.
        val points = listOf(
            Point(6, 1), Point(6, 2), Point(7, 1), Point(7, 2), Point(8, 2),
            Point(8, 3), Point(9, 2), Point(9, 3), Point(10, 2), Point(10, 3),
        )
        val training = advancedTraining(ballEntry(random = 1, points = points))
        val buf = RobotProtocol.encodeAdvancedPattern(training, lookup = nullLookup)
        assertEquals("payload size", points.size * 12 + 4, buf.size)
        for (i in points.indices) {
            val off = i * 12
            assertEquals("point $i byte7", 0x80.toByte(), buf[off + 7])
            assertEquals("point $i byte11", 1.toByte(), buf[off + 11])
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tablebot.ble.RobotProtocolTest"`
Expected: FAIL — either a compile error (`encodeAdvancedPattern` has no `lookup` overload) or assertion failures on `byte7`/`byte11` (currently 0).

- [ ] **Step 3: Refactor the encoder and set the flags**

Replace the whole `encodeAdvancedPattern` function (RobotProtocol.kt:211-251) with a testable `lookup` core plus a `MotorConfig` convenience overload:

```kotlin
    fun encodeAdvancedPattern(
        training: AdvancedTraining,
        repeatNumOverride: Int? = null,
        repeatDelayOverride: Int? = null,
        lookup: (ball: Int, spin: Int, power: Int, cell: Int) -> MotorParams?,
    ): ByteArray {
        val effectiveRepeatNum = repeatNumOverride ?: training.repeatNum
        val effectiveRepeatDelay = repeatDelayOverride ?: training.repeatDelay
        val allPoints = mutableListOf<ByteArray>()
        for (entry in training.ballList) {
            // Advanced path: the per-ball randomness marker is `random`, not landType.
            // Random points get b7=0x80 and b11=1 but b10 stays 0 (basic uses b10=2). See PROTOCOL.md.
            val isRandom = entry.random == 1
            for (p in entry.points) {
                val params = lookup(entry.ball, entry.spin, entry.power, p.x)
                val buf = ByteArray(12)
                buf[0] = (params?.m1speed ?: 0).toByte()
                buf[1] = (params?.m2speed ?: 0).toByte()
                buf[2] = (params?.xaxis ?: 0).toByte()
                buf[3] = (params?.yaxis ?: 0).toByte()
                buf[4] = (params?.zaxis ?: 0).toByte()
                buf[5] = 0  // repeatDelay high
                buf[6] = (effectiveRepeatDelay.coerceAtLeast(1) and 0xFF).toByte()
                buf[7] = if (isRandom) 0x80.toByte() else 0
                buf[8] = (entry.ballTime and 0xFF).toByte()
                buf[9] = 1
                buf[10] = 0
                buf[11] = if (isRandom) 1 else 0
                allPoints.add(buf)
            }
        }

        val payload = ByteArray((allPoints.size * 12) + 4)
        for ((i, pointBuf) in allPoints.withIndex()) {
            pointBuf.copyInto(payload, i * 12)
        }
        val trailerOff = allPoints.size * 12
        payload[trailerOff + 0] = (effectiveRepeatNum and 0xFF).toByte()  // repeatNum
        payload[trailerOff + 1] = 1
        payload[trailerOff + 2] = 0
        payload[trailerOff + 3] = 0

        return payload
    }

    fun encodeAdvancedPattern(
        training: AdvancedTraining,
        motorConfig: MotorConfig,
        repeatNumOverride: Int? = null,
        repeatDelayOverride: Int? = null,
    ): ByteArray = encodeAdvancedPattern(training, repeatNumOverride, repeatDelayOverride) { ball, spin, power, cell ->
        motorConfig.lookup(ball, spin, power, cell)
    }
```

Note: the existing call site `RobotViewModel.kt:148` (`encodeAdvancedPattern(training, motorConfig, repeatNumOverride = …, repeatDelayOverride = …)`) resolves to the `MotorConfig` overload unchanged — do not touch it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tablebot.ble.RobotProtocolTest"`
Expected: PASS (all new advanced tests + all pre-existing basic tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tablebot/ble/RobotProtocol.kt \
        app/src/test/java/com/tablebot/ble/RobotProtocolTest.kt
git commit -m "fix: honor per-ball random flag in advanced drill encoder"
```

---

### Task 2: Add "Randomize position" toggle to the advanced editor

**Files:**
- Modify: `app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt` (`BallEntryEditor`, the expanded section around lines 408-427)

**Interfaces:**
- Consumes: `BallEntry.random` (Int 0/1), the existing `onUpdate: (BallEntry) -> Unit` callback and `entry.copy(...)` pattern already used throughout `BallEntryEditor`.
- Produces: no new public API — UI-only change.

- [ ] **Step 1: Add the toggle row**

In `BallEntryEditor`, inside the `if (expanded) { … }` block, add a "Randomize position" switch immediately after the `TableGrid(...)` call (after line 427, still inside the `if (expanded)` `Column`). Insert:

```kotlin
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Randomize position", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Shoot this ball to a randomly chosen one of the selected positions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = entry.random == 1,
                        onCheckedChange = { on -> onUpdate(entry.copy(random = if (on) 1 else 0)) },
                    )
                }
```

- [ ] **Step 2: Add the `Switch` import if missing**

Ensure the file imports `androidx.compose.material3.Switch`. Check the existing imports block; if `Switch` is not already imported, add:

```kotlin
import androidx.compose.material3.Switch
```

(`Row`, `Column`, `Text`, `Modifier`, `Alignment`, `MaterialTheme`, `fillMaxWidth`, `weight` are already imported and used in this file.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tablebot/ui/screens/AdvancedEditorScreen.kt
git commit -m "feat: add per-ball randomize toggle to advanced editor"
```

---

## Hardware Verification (manual, after both tasks)

Not a subagent task — requires the physical robot.

1. Build & install the debug APK on the phone.
2. Open a per-ball random advanced drill (e.g. the bundled **"Half Long 2/3 FH Loop"**, or a new drill with one ball card, several target positions, and the "Randomize position" toggle ON).
3. Play it and confirm the shots land on **varying** positions rather than a fixed repeating cycle.
4. Also confirm a non-random advanced drill still plays its positions in order (no regression).
5. Record PASS/FAIL.

---

## Self-Review

- **Spec coverage:** Encoder fix → Task 1. Testable overload → Task 1 Step 3. Tests (random / non-random / mixed / preset regression) → Task 1 Step 1. UI toggle bound to `entry.random` → Task 2. Hardware verification → manual section. `landType` left untouched → no task modifies it. All spec items covered.
- **Placeholders:** none — every code step shows complete code.
- **Type consistency:** `encodeAdvancedPattern` lookup-core + `MotorConfig` overload signatures match `encodeBasicPattern`'s pattern and the existing `RobotViewModel.kt:148` call; `entry.random` is `Int` (0/1) consistent with `BallEntry` and preset data; `nullLookup` reused from existing tests.
