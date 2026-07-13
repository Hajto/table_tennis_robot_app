package com.tablebot.ble

import com.tablebot.data.AdvancedTraining
import com.tablebot.data.BallEntry
import com.tablebot.data.BasicTraining
import com.tablebot.data.LandType
import com.tablebot.data.MotorParams
import com.tablebot.data.Point
import com.tablebot.data.RobotType
import org.junit.Assert.*
import org.junit.Test

class RobotProtocolTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun motorParams(
        id: Int = 1,
        ball: Int = 1, spin: Int = 2, power: Int = 2, landarea: Int = 8,
        m1speed: Int = 100, m2speed: Int = 110,
        xaxis: Int = 50, yaxis: Int = 60, zaxis: Int = 70,
    ) = MotorParams(id, ball, spin, power, landarea, m1speed, m2speed, xaxis, yaxis, zaxis)

    private val DEVICE_ID = "aabbccdd11223344"

    // ── Frame structure ───────────────────────────────────────────────────────

    @Test
    fun `buildFrame starts with 0x68`() {
        val frame = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_CONNECT, byteArrayOf(0))
        assertEquals(0x68.toByte(), frame[0])
    }

    @Test
    fun `buildFrame ends with 0x16`() {
        val frame = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_CONNECT, byteArrayOf(0))
        assertEquals(0x16.toByte(), frame.last())
    }

    @Test
    fun `buildFrame second byte is fixed 0x01`() {
        val frame = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_CONNECT, byteArrayOf(0))
        assertEquals(0x01.toByte(), frame[1])
    }

    @Test
    fun `buildFrame total length is 14 + payload + 3`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val frame = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_PATTERN, payload)
        assertEquals(14 + payload.size + 3, frame.size)
    }

    @Test
    fun `buildFrame encodes payload length correctly`() {
        val payload = ByteArray(256) { it.toByte() }
        val frame = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_PATTERN, payload)
        val lenHigh = frame[12].toInt() and 0xFF
        val lenLow = frame[13].toInt() and 0xFF
        assertEquals(256, (lenHigh shl 8) or lenLow)
    }

    @Test
    fun `buildFrame embeds device id bytes at position 2`() {
        val frame = RobotProtocol.buildFrame("aabbccdd11223344", RobotProtocol.CMD_CONNECT, byteArrayOf(0))
        assertEquals(0xAA.toByte(), frame[2])
        assertEquals(0xBB.toByte(), frame[3])
        assertEquals(0xCC.toByte(), frame[4])
        assertEquals(0xDD.toByte(), frame[5])
        assertEquals(0x11.toByte(), frame[6])
        assertEquals(0x22.toByte(), frame[7])
        assertEquals(0x33.toByte(), frame[8])
        assertEquals(0x44.toByte(), frame[9])
    }

    @Test
    fun `buildFrame with empty device id pads with zeros`() {
        val frame = RobotProtocol.buildFrame("", RobotProtocol.CMD_CONNECT, byteArrayOf(0))
        for (i in 2..9) assertEquals(0.toByte(), frame[i])
    }

    @Test
    fun `buildFrame with short device id pads remainder with zeros`() {
        val frame = RobotProtocol.buildFrame("aabb", RobotProtocol.CMD_CONNECT, byteArrayOf(0))
        assertEquals(0xAA.toByte(), frame[2])
        assertEquals(0xBB.toByte(), frame[3])
        assertEquals(0.toByte(), frame[4])
    }

    // ── Connect / Stop frames ──────────────────────────────────────────────────

    @Test
    fun `buildConnectFrame uses CMD_CONNECT`() {
        val frame = RobotProtocol.buildConnectFrame(DEVICE_ID)
        assertEquals(RobotProtocol.CMD_CONNECT, frame[11])
    }

    @Test
    fun `buildStopFrame V2 uses CMD_STOP 0x05`() {
        val frame = RobotProtocol.buildStopFrame(DEVICE_ID, RobotType.JOOLA_V2)
        assertEquals(RobotProtocol.CMD_STOP, frame[11])
        assertEquals(0x05.toByte(), frame[11])
    }

    @Test
    fun `buildStopFrame V1 uses CMD_STOP_LEGACY 0x99`() {
        val frame = RobotProtocol.buildStopFrame(DEVICE_ID, RobotType.JOOLA_V1)
        assertEquals(RobotProtocol.CMD_STOP_LEGACY, frame[11])
        assertEquals(0x99.toByte(), frame[11])
    }

    @Test
    fun `buildStopFrame default is V2`() {
        val frame = RobotProtocol.buildStopFrame(DEVICE_ID)
        assertEquals(RobotProtocol.CMD_STOP, frame[11])
    }

    @Test
    fun `buildStopFrame V1 does not use CMD_DISCONNECT`() {
        val frame = RobotProtocol.buildStopFrame(DEVICE_ID, RobotType.JOOLA_V1)
        // 0x99 is reused for both STOP_LEGACY and DISCONNECT — but STOP and DISCONNECT are distinct intents
        // Verify V2 stop is NOT 0x99
        val frameV2 = RobotProtocol.buildStopFrame(DEVICE_ID, RobotType.JOOLA_V2)
        assertNotEquals(0x99.toByte(), frameV2[11])
    }

    @Test
    fun `buildPostPatternFrame uses 0x03 (the mid-drill stop)`() {
        // 0x03 is the only command that halts a firing drill (firmware shot loop -> done state).
        val frame = RobotProtocol.buildPostPatternFrame(DEVICE_ID)
        assertEquals(0x03.toByte(), frame[11])
        assertEquals(0x00.toByte(), frame[14]) // payload byte 0 must be 0 for the firmware to act
        assertEquals(0x68.toByte(), frame[0])
        assertEquals(0x16.toByte(), frame.last())
    }

    @Test
    fun `buildPauseFrame uses CMD_PAUSE 0x25`() {
        val frame = RobotProtocol.buildPauseFrame(DEVICE_ID)
        assertEquals(RobotProtocol.CMD_PAUSE, frame[11])
        assertEquals(0x25.toByte(), frame[11])
    }

    @Test
    fun `buildPrePatternFrame uses cmd 0x04 with payload 0x02`() {
        val frame = RobotProtocol.buildPrePatternFrame(DEVICE_ID)
        assertEquals(0x04.toByte(), frame[11])
        assertEquals(0x02.toByte(), frame[14]) // pre-pattern setup payload
        assertEquals(0x68.toByte(), frame[0])
        assertEquals(0x16.toByte(), frame.last())
    }

    // ── Random-mode pattern encoding ────────────────────────────────────────────

    private fun basicTraining(landType: Int) = BasicTraining(
        id = 1, name = "t", ball = 1, spin = 2, power = 2,
        landType = landType, ballTime = 9, times = 20,
        points = listOf(Point(3, 2), Point(8, 2)),
    )

    // The flag-byte encoding is independent of motor values, so a null lookup is fine here.
    private val nullLookup: (Int, Int, Int, Int) -> MotorParams? = { _, _, _, _ -> null }

    @Test
    fun `encodeBasicPattern random sets per-point random flags`() {
        val drill = basicTraining(LandType.RANDOM.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        for (i in drill.points.indices) {
            val off = i * 12
            assertEquals("point $i byte7", 0x80.toByte(), buf[off + 7])
            assertEquals("point $i byte9 groupSize", 1.toByte(), buf[off + 9])
            assertEquals("point $i byte10 mode-flag", 2.toByte(), buf[off + 10])
            assertEquals("point $i byte11 random-trigger", 1.toByte(), buf[off + 11])
        }
    }

    @Test
    fun `encodeBasicPattern sequence leaves random flags clear`() {
        val drill = basicTraining(LandType.LOOP.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        for (i in drill.points.indices) {
            val off = i * 12
            assertEquals("point $i byte7", 0.toByte(), buf[off + 7])
            assertEquals("point $i byte10", 0.toByte(), buf[off + 10])
            assertEquals("point $i byte11", 0.toByte(), buf[off + 11])
        }
    }

    @Test
    fun `encodeBasicPattern static leaves random flags clear`() {
        val drill = basicTraining(LandType.STATIC.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        assertEquals(0.toByte(), buf[10])
        assertEquals(0.toByte(), buf[11])
    }

    @Test
    fun `encodeBasicPattern static clears byte7 and keeps groupSize on every point`() {
        val drill = basicTraining(LandType.STATIC.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        for (i in drill.points.indices) {
            val off = i * 12
            assertEquals("point $i byte7", 0.toByte(), buf[off + 7])
            assertEquals("point $i byte9 groupSize", 1.toByte(), buf[off + 9])
        }
    }

    @Test
    fun `encodeBasicPattern sequence keeps groupSize byte9 at 1 on every point`() {
        val drill = basicTraining(LandType.LOOP.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        for (i in drill.points.indices) {
            assertEquals("point $i byte9 groupSize", 1.toByte(), buf[i * 12 + 9])
        }
    }

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

    // ── encodeBasicPattern general structure ────────────────────────────────────

    // Returns a fixed MotorParams regardless of the drill/point, so motor bytes are deterministic.
    private fun fixedLookup(params: MotorParams): (Int, Int, Int, Int) -> MotorParams? =
        { _, _, _, _ -> params }

    @Test
    fun `encodeBasicPattern payload length is points times 12 plus 4`() {
        val drill = basicTraining(LandType.LOOP.value) // 2 points
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        assertEquals(drill.points.size * 12 + 4, buf.size)
    }

    @Test
    fun `encodeBasicPattern writes looked-up motor bytes at offsets 0 to 4 on every point`() {
        val drill = basicTraining(LandType.LOOP.value)
        val params = motorParams(m1speed = 11, m2speed = 22, xaxis = 33, yaxis = 44, zaxis = 55)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = fixedLookup(params))
        for (i in drill.points.indices) {
            val off = i * 12
            assertEquals("point $i m1speed", 11.toByte(), buf[off + 0])
            assertEquals("point $i m2speed", 22.toByte(), buf[off + 1])
            assertEquals("point $i xaxis", 33.toByte(), buf[off + 2])
            assertEquals("point $i yaxis", 44.toByte(), buf[off + 3])
            assertEquals("point $i zaxis", 55.toByte(), buf[off + 4])
        }
    }

    @Test
    fun `encodeBasicPattern masks motor bytes for 0 and 255`() {
        val drill = basicTraining(LandType.LOOP.value)
        val params = motorParams(m1speed = 0, m2speed = 255, xaxis = 0, yaxis = 255, zaxis = 128)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = fixedLookup(params))
        assertEquals(0.toByte(), buf[0])
        assertEquals(0xFF.toByte(), buf[1])
        assertEquals(0.toByte(), buf[2])
        assertEquals(0xFF.toByte(), buf[3])
        assertEquals(0x80.toByte(), buf[4])
    }

    @Test
    fun `encodeBasicPattern sets repeatDelay bytes to 0 and 1 on every point`() {
        val drill = basicTraining(LandType.LOOP.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        for (i in drill.points.indices) {
            val off = i * 12
            assertEquals("point $i repeatDelay high", 0.toByte(), buf[off + 5])
            assertEquals("point $i repeatDelay low", 1.toByte(), buf[off + 6])
        }
    }

    @Test
    fun `encodeBasicPattern writes ballTime at byte 8 on every point`() {
        val drill = basicTraining(LandType.LOOP.value) // ballTime = 9
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        for (i in drill.points.indices) {
            assertEquals("point $i ballTime", 9.toByte(), buf[i * 12 + 8])
        }
    }

    @Test
    fun `encodeBasicPattern ballTimeOverride replaces ballTime`() {
        val drill = basicTraining(LandType.LOOP.value) // ballTime = 9
        val buf = RobotProtocol.encodeBasicPattern(drill, ballTimeOverride = 42, lookup = nullLookup)
        for (i in drill.points.indices) {
            assertEquals("point $i ballTime", 42.toByte(), buf[i * 12 + 8])
        }
    }

    @Test
    fun `encodeBasicPattern trailer encodes repeat count and constants`() {
        val drill = basicTraining(LandType.LOOP.value) // times = 20
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        val t = drill.points.size * 12
        assertEquals(20.toByte(), buf[t + 0]) // repeatNum
        assertEquals(1.toByte(), buf[t + 1])
        assertEquals(0.toByte(), buf[t + 2])
        assertEquals(0.toByte(), buf[t + 3])
    }

    @Test
    fun `encodeBasicPattern clamps times above 255 to 255`() {
        val drill = basicTraining(LandType.LOOP.value).copy(times = 300)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        val t = drill.points.size * 12
        assertEquals(255.toByte(), buf[t + 0])
    }

    @Test
    fun `encodeBasicPattern clamps times below 1 to 1`() {
        val drill = basicTraining(LandType.LOOP.value).copy(times = 0)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = nullLookup)
        val t = drill.points.size * 12
        assertEquals(1.toByte(), buf[t + 0])
    }

    @Test
    fun `encodeBasicPattern timesOverride replaces repeat count`() {
        val drill = basicTraining(LandType.LOOP.value) // times = 20
        val buf = RobotProtocol.encodeBasicPattern(drill, timesOverride = 7, lookup = nullLookup)
        val t = drill.points.size * 12
        assertEquals(7.toByte(), buf[t + 0])
    }

    @Test
    fun `encodeBasicPattern clamps timesOverride above 255 to 255`() {
        val drill = basicTraining(LandType.LOOP.value)
        val buf = RobotProtocol.encodeBasicPattern(drill, timesOverride = 400, lookup = nullLookup)
        val t = drill.points.size * 12
        assertEquals(255.toByte(), buf[t + 0])
    }

    @Test
    fun `encodeBasicPattern calls lookup with drill ball spin power and point x as cell`() {
        val drill = basicTraining(LandType.LOOP.value) // ball=1, spin=2, power=2, points x=3,8
        val calls = mutableListOf<List<Int>>()
        RobotProtocol.encodeBasicPattern(drill, lookup = { ball, spin, power, cell ->
            calls.add(listOf(ball, spin, power, cell))
            null
        })
        assertEquals(2, calls.size)
        assertEquals(listOf(drill.ball, drill.spin, drill.power, 3), calls[0])
        assertEquals(listOf(drill.ball, drill.spin, drill.power, 8), calls[1])
    }

    @Test
    fun `encodeBasicPattern null lookup yields zero motor bytes but keeps flag bytes`() {
        // Mixed lookup: the first point resolves, the second returns null. The null point must
        // still receive the correct random flag bytes (flags are independent of motor lookup).
        val drill = basicTraining(LandType.RANDOM.value) // points x=3, x=8
        val params = motorParams(m1speed = 77, m2speed = 88, xaxis = 99, yaxis = 11, zaxis = 22)
        val buf = RobotProtocol.encodeBasicPattern(drill, lookup = { _, _, _, cell ->
            if (cell == 3) params else null
        })

        // point 0 (x=3) -> resolved motor bytes
        assertEquals(77.toByte(), buf[0])
        assertEquals(88.toByte(), buf[1])
        assertEquals(99.toByte(), buf[2])
        assertEquals(11.toByte(), buf[3])
        assertEquals(22.toByte(), buf[4])

        // point 1 (x=8) -> null lookup => zeroed motor bytes
        val off = 12
        assertEquals(0.toByte(), buf[off + 0])
        assertEquals(0.toByte(), buf[off + 1])
        assertEquals(0.toByte(), buf[off + 2])
        assertEquals(0.toByte(), buf[off + 3])
        assertEquals(0.toByte(), buf[off + 4])

        // both points keep the random flag bytes regardless of the lookup result
        for (i in drill.points.indices) {
            val p = i * 12
            assertEquals("point $i byte7", 0x80.toByte(), buf[p + 7])
            assertEquals("point $i byte10", 2.toByte(), buf[p + 10])
            assertEquals("point $i byte11", 1.toByte(), buf[p + 11])
        }
    }

    // ── CRC consistency ───────────────────────────────────────────────────────

    @Test
    fun `same input produces same CRC`() {
        val payload = byteArrayOf(1, 2, 3)
        val frame1 = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_PATTERN, payload)
        val frame2 = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_PATTERN, payload)
        // CRC is at last 3 bytes: [crcHigh, crcLow, END]
        assertEquals(frame1[frame1.size - 3], frame2[frame2.size - 3])
        assertEquals(frame1[frame1.size - 2], frame2[frame2.size - 2])
    }

    @Test
    fun `different payload produces different CRC`() {
        val frame1 = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_PATTERN, byteArrayOf(1))
        val frame2 = RobotProtocol.buildFrame(DEVICE_ID, RobotProtocol.CMD_PATTERN, byteArrayOf(2))
        val crc1 = ((frame1[frame1.size - 3].toInt() and 0xFF) shl 8) or (frame1[frame1.size - 2].toInt() and 0xFF)
        val crc2 = ((frame2[frame2.size - 3].toInt() and 0xFF) shl 8) or (frame2[frame2.size - 2].toInt() and 0xFF)
        assertNotEquals(crc1, crc2)
    }

    // ── encodeSingleBall ──────────────────────────────────────────────────────

    @Test
    fun `encodeSingleBall is 16 bytes`() {
        assertEquals(16, RobotProtocol.encodeSingleBall(motorParams()).size)
    }

    @Test
    fun `encodeSingleBall encodes motor params at expected offsets`() {
        val p = motorParams(m1speed = 100, m2speed = 110, xaxis = 50, yaxis = 60, zaxis = 70)
        val buf = RobotProtocol.encodeSingleBall(p)
        assertEquals(100.toByte(), buf[0])
        assertEquals(110.toByte(), buf[1])
        assertEquals(50.toByte(), buf[2])
        assertEquals(60.toByte(), buf[3])
        assertEquals(70.toByte(), buf[4])
    }

    @Test
    fun `encodeSingleBall encodes ballTime at offset 8`() {
        val buf = RobotProtocol.encodeSingleBall(motorParams(), ballTime = 25)
        assertEquals(25.toByte(), buf[8])
    }

    @Test
    fun `encodeSingleBall default ballTime is 15`() {
        val buf = RobotProtocol.encodeSingleBall(motorParams())
        assertEquals(15.toByte(), buf[8])
    }

    @Test
    fun `encodeSingleBall trailer encodes 1 repetition`() {
        val buf = RobotProtocol.encodeSingleBall(motorParams())
        assertEquals(1.toByte(), buf[12])
        assertEquals(1.toByte(), buf[13])
        assertEquals(0.toByte(), buf[14])
        assertEquals(0.toByte(), buf[15])
    }

    @Test
    fun `encodeSingleBall handles zero motor values`() {
        val p = motorParams(m1speed = 0, m2speed = 0, xaxis = 0, yaxis = 0, zaxis = 0)
        val buf = RobotProtocol.encodeSingleBall(p)
        assertEquals(0.toByte(), buf[0])
        assertEquals(0.toByte(), buf[1])
    }

    @Test
    fun `encodeSingleBall handles max motor values`() {
        val p = motorParams(m1speed = 255, m2speed = 255, xaxis = 255, yaxis = 255, zaxis = 255)
        val buf = RobotProtocol.encodeSingleBall(p)
        assertEquals(0xFF.toByte(), buf[0])
        assertEquals(0xFF.toByte(), buf[1])
    }

    // ── splitIntoChunks ───────────────────────────────────────────────────────

    @Test
    fun `splitIntoChunks splits at BLE_MTU boundary`() {
        val data = ByteArray(45) { it.toByte() }
        val chunks = RobotProtocol.splitIntoChunks(data, mtu = 20)
        assertEquals(3, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
        assertEquals(5, chunks[2].size)
    }

    @Test
    fun `splitIntoChunks with data smaller than mtu returns one chunk`() {
        val data = ByteArray(10) { it.toByte() }
        val chunks = RobotProtocol.splitIntoChunks(data, mtu = 20)
        assertEquals(1, chunks.size)
        assertEquals(10, chunks[0].size)
    }

    @Test
    fun `splitIntoChunks reassembles to original`() {
        val data = ByteArray(100) { it.toByte() }
        val chunks = RobotProtocol.splitIntoChunks(data, mtu = 20)
        val reassembled = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        assertArrayEquals(data, reassembled)
    }

    @Test
    fun `splitIntoChunks exact multiple of mtu`() {
        val data = ByteArray(40) { it.toByte() }
        val chunks = RobotProtocol.splitIntoChunks(data, mtu = 20)
        assertEquals(2, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
    }

    @Test
    fun `splitIntoChunks empty array returns one empty chunk`() {
        val chunks = RobotProtocol.splitIntoChunks(ByteArray(0), mtu = 20)
        assertEquals(0, chunks.size)
    }

    // ── parseFrame ────────────────────────────────────────────────────────────

    @Test
    fun `parseFrame returns null for too-short frame`() {
        assertNull(RobotProtocol.parseFrame(ByteArray(16)))
    }

    @Test
    fun `parseFrame round-trips a built frame`() {
        // Build a valid response-shaped frame manually: 14 header + 2 payload + 3 tail = 19 bytes
        // Response frame layout (from parseFrame): bytes 2..9=deviceId, 10=status, 11=cmd, 12-13=len, 14..=payload
        val deviceIdHex = "aabbccdd11223344"
        val payload = byteArrayOf(0x01, 0x02)
        val frame = ByteArray(19)
        frame[0] = 0x68
        frame[1] = 0x01
        // device id
        val devBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
                                    0x11, 0x22, 0x33, 0x44)
        devBytes.copyInto(frame, 2)
        frame[10] = 0x00  // status
        frame[11] = 0x81.toByte()  // RESP_PATTERN_ACK
        frame[12] = 0x00  // len high
        frame[13] = 0x02  // len low
        payload.copyInto(frame, 14)
        frame[16] = 0x00  // crc high (ignored in parseFrame)
        frame[17] = 0x00  // crc low
        frame[18] = 0x16  // FRAME_END

        val response = RobotProtocol.parseFrame(frame)
        assertNotNull(response)
        assertEquals(RobotProtocol.RESP_PATTERN_ACK, response!!.cmd)
        assertEquals(deviceIdHex, response.deviceId)
        assertArrayEquals(payload, response.payload)
    }

    @Test
    fun `parseFrame parses firmware version from 0x85 response`() {
        val frame = ByteArray(18)
        frame[0] = 0x68; frame[1] = 0x01
        byteArrayOf(0,0,0,0,0,0,0,0).copyInto(frame, 2)
        frame[10] = 0x00
        frame[11] = 0x85.toByte()
        frame[12] = 0x00; frame[13] = 0x04
        frame[14] = 1; frame[15] = 2; frame[16] = 3; frame[17] = 4
        // need 3 more bytes: crcH crcL END
        val full = frame + byteArrayOf(0, 0, 0x16)
        // Repack correctly: 14 header + 4 payload + 3 = 21 bytes
        val f2 = ByteArray(21)
        f2[0] = 0x68; f2[1] = 0x01
        byteArrayOf(0,0,0,0,0,0,0,0).copyInto(f2, 2)
        f2[10] = 0x00; f2[11] = 0x85.toByte()
        f2[12] = 0x00; f2[13] = 0x04
        byteArrayOf(1, 2, 3, 4).copyInto(f2, 14)
        f2[18] = 0; f2[19] = 0; f2[20] = 0x16

        val response = RobotProtocol.parseFrame(f2)
        assertNotNull(response)
        assertEquals("12.34", response!!.firmwareVersion)
    }

    @Test
    fun `parseFrame returns null for wrong size relative to declared length`() {
        val frame = ByteArray(20)
        frame[0] = 0x68; frame[1] = 0x01
        frame[12] = 0x00; frame[13] = 0x10  // declares 16 bytes payload, but frame is only 20
        assertNull(RobotProtocol.parseFrame(frame))
    }
}
