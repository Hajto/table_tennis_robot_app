package com.tablebot.ble

import com.tablebot.data.BasicTraining
import com.tablebot.data.AdvancedTraining
import com.tablebot.data.MotorConfig
import com.tablebot.data.MotorParams
import com.tablebot.data.RobotType

object RobotProtocol {

    const val SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val CHAR_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    const val ALT_SERVICE_UUID = "0000fee7-0000-1000-8000-00805f9b34fb"
    const val ALT_CHAR_WRITE = "0000fec7-0000-1000-8000-00805f9b34fb"
    const val ALT_CHAR_NOTIFY = "0000fec8-0000-1000-8000-00805f9b34fb"

    private const val FRAME_START: Byte = 0x68
    private const val FRAME_END: Byte = 0x16
    private const val FRAME_FIXED: Byte = 0x01

    const val CMD_CONNECT: Byte = 0x89.toByte()
    const val CMD_DISCONNECT: Byte = 0x99.toByte()
    const val CMD_PATTERN: Byte = 0x01
    const val CMD_PATTERN_ADV: Byte = 0x98.toByte()  // advanced pattern / upload
    const val CMD_STOP: Byte = 0x05
    const val CMD_STOP_LEGACY: Byte = 0x99.toByte()
    const val CMD_PAUSE: Byte = 0x25.toByte()
    const val CMD_CALIBRATION: Byte = 0x03

    const val BLE_MTU = 10

    // Response command IDs
    const val RESP_PATTERN_ACK = 0x81
    const val RESP_PATTERN_DONE = 0x8F
    const val RESP_FIRMWARE = 0x85

    private fun scaleMotor(value: Int): Int = value  // pass through raw base-conf values

    // CRC-CCITT lookup table (polynomial 0x1021, init 0x0000)
    private val CRC_TABLE = intArrayOf(
        0, 4129, 8258, 12387, 16516, 20645, 24774, 28903, 33032, 37161, 41290, 45419, 49548, 53677, 57806, 61935,
        4657, 528, 12915, 8786, 21173, 17044, 29431, 25302, 37689, 33560, 45947, 41818, 54205, 50076, 62463, 58334,
        9314, 13379, 1056, 5121, 25830, 29895, 17572, 21637, 42346, 46411, 34088, 38153, 58862, 62927, 50604, 54669,
        13907, 9842, 5649, 1584, 30423, 26358, 22165, 18100, 46939, 42874, 38681, 34616, 63455, 59390, 55197, 51132,
        18628, 22757, 26758, 30887, 2112, 6241, 10242, 14371, 51660, 55789, 59790, 63919, 35144, 39273, 43274, 47403,
        23285, 19156, 31415, 27286, 6769, 2640, 14899, 10770, 56317, 52188, 64447, 60318, 39801, 35672, 47931, 43802,
        27814, 31879, 19684, 23749, 11298, 15363, 3168, 7233, 60846, 64911, 52716, 56781, 44330, 48395, 36200, 40265,
        32407, 28342, 24277, 20212, 15891, 11826, 7761, 3696, 65439, 61374, 57309, 53244, 48923, 44858, 40793, 36728,
        37256, 33193, 45514, 41451, 53516, 49453, 61774, 57711, 4224, 161, 12482, 8419, 20484, 16421, 28742, 24679,
        33721, 37784, 41979, 46042, 49981, 54044, 58239, 62302, 689, 4752, 8947, 13010, 16949, 21012, 25207, 29270,
        46570, 42443, 38312, 34185, 62830, 58703, 54572, 50445, 13538, 9411, 5280, 1153, 29798, 25671, 21540, 17413,
        42971, 47098, 34713, 38840, 59231, 63358, 50973, 55100, 9939, 14066, 1681, 5808, 26199, 30326, 17941, 22068,
        55628, 51565, 63758, 59695, 39368, 35305, 47498, 43435, 22596, 18533, 30726, 26663, 6336, 2273, 14466, 10403,
        52093, 56156, 60223, 64286, 35833, 39896, 43963, 48026, 19061, 23124, 27191, 31254, 2801, 6864, 10931, 14994,
        64814, 60687, 56684, 52557, 48554, 44427, 40424, 36297, 31782, 27655, 23652, 19525, 15522, 11395, 7392, 3265,
        61215, 65342, 53085, 57212, 44955, 49082, 36825, 40952, 28183, 32310, 20053, 24180, 11923, 16050, 3793, 7920,
    )

    private fun crcCCITT(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = (CRC_TABLE[((crc shr 8) xor (data[i].toInt() and 0xFF)) and 0xFF] xor (crc shl 8)) and 0xFFFF
        }
        return crc
    }

    private fun hexToBytes(hex: String): ByteArray {
        val padded = hex.padEnd(16, '0').take(16)
        return ByteArray(padded.length / 2) { i ->
            padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun buildFrame(deviceId: String, cmd: Byte, payload: ByteArray): ByteArray {
        val devBytes = hexToBytes(deviceId)
        val len = payload.size
        val frame = ByteArray(14 + len + 3)
        var i = 0

        frame[i++] = FRAME_START
        frame[i++] = FRAME_FIXED
        devBytes.copyInto(frame, i); i += 8
        frame[i++] = FRAME_START
        frame[i++] = cmd
        frame[i++] = ((len shr 8) and 0xFF).toByte()
        frame[i++] = (len and 0xFF).toByte()
        payload.copyInto(frame, i); i += len

        val crc = crcCCITT(frame, 0, i)
        frame[i++] = ((crc shr 8) and 0xFF).toByte()
        frame[i++] = (crc and 0xFF).toByte()
        frame[i++] = FRAME_END

        return frame
    }

    fun buildConnectFrame(deviceId: String): ByteArray =
        buildFrame(deviceId, CMD_CONNECT, byteArrayOf(0))

    fun buildVersionQueryFrame(deviceId: String): ByteArray =
        buildFrame(deviceId, CMD_STOP, byteArrayOf())  // 0x05 — may trigger 0x85 firmware response

    fun buildDisconnectFrame(deviceId: String): ByteArray =
        buildFrame(deviceId, CMD_DISCONNECT, byteArrayOf(0))

    fun buildStopFrame(deviceId: String, robotType: RobotType = RobotType.JOOLA_V2): ByteArray {
        val cmd = if (robotType == RobotType.JOOLA_V1) CMD_STOP_LEGACY else CMD_STOP
        return buildFrame(deviceId, cmd, byteArrayOf(0))
    }

    fun buildPauseFrame(deviceId: String): ByteArray =
        buildFrame(deviceId, CMD_PAUSE, byteArrayOf(0))

    // Pre-pattern setup: cmd 0x04 with payload 0x02
    fun buildPrePatternFrame(deviceId: String): ByteArray =
        buildFrame(deviceId, 0x04, byteArrayOf(0x02))

    // Post-pattern: cmd 0x03 with payload 0x00
    fun buildPostPatternFrame(deviceId: String): ByteArray =
        buildFrame(deviceId, 0x03, byteArrayOf(0x00))

    fun encodeSingleBall(params: MotorParams, ballTime: Int = 15): ByteArray {
        val buf = ByteArray(16) // 12 per point + 4 trailer
        buf[0] = params.m1speed.toByte()
        buf[1] = params.m2speed.toByte()
        buf[2] = params.xaxis.toByte()
        buf[3] = params.yaxis.toByte()
        buf[4] = params.zaxis.toByte()
        buf[5] = 0  // repeatDelay high
        buf[6] = 1  // repeatDelay low
        buf[7] = 0
        buf[8] = (ballTime and 0xFF).toByte()
        buf[9] = 1
        buf[10] = 0
        buf[11] = 0
        // trailer: 1 rep
        buf[12] = 1
        buf[13] = 1
        buf[14] = 0
        buf[15] = 0
        return buf
    }

    fun encodeBasicPattern(
        drill: BasicTraining,
        motorConfig: MotorConfig,
        timesOverride: Int? = null,
        ballTimeOverride: Int? = null,
    ): ByteArray {
        // Encoding from HCI capture of working Joola app (private pinfinity server)
        // Per-point layout (12 bytes):
        //   [0] m1speed    [1] m2speed    [2] xaxis    [3] yaxis    [4] zaxis
        //   [5-6] repeatDelay (big-endian, 1 = 0.2s)
        //   [7] 0
        //   [8] ballTime
        //   [9] 1
        //   [10] 0    [11] 0
        // Trailer: [repeatNum] [1] [0] [0]
        val effectiveBallTime = ballTimeOverride ?: drill.ballTime
        val effectiveTimes = timesOverride ?: drill.times
        val points = drill.points
        val buf = ByteArray((points.size * 12) + 4)

        for ((idx, p) in points.withIndex()) {
            val off = idx * 12
            val params = motorConfig.lookup(drill.ball, drill.spin, drill.power, p.x)

            buf[off + 0] = (params?.m1speed ?: 0).toByte()
            buf[off + 1] = (params?.m2speed ?: 0).toByte()
            buf[off + 2] = (params?.xaxis ?: 0).toByte()
            buf[off + 3] = (params?.yaxis ?: 0).toByte()
            buf[off + 4] = (params?.zaxis ?: 0).toByte()
            buf[off + 5] = 0  // repeatDelay high byte
            buf[off + 6] = 1  // repeatDelay low byte (1 = 0.2s default)
            buf[off + 7] = 0
            buf[off + 8] = (effectiveBallTime and 0xFF).toByte()
            buf[off + 9] = 1
            buf[off + 10] = 0
            buf[off + 11] = 0
        }

        val trailerOff = points.size * 12
        buf[trailerOff + 0] = (effectiveTimes.coerceIn(1, 255) and 0xFF).toByte()  // repeatNum
        buf[trailerOff + 1] = 1
        buf[trailerOff + 2] = 0
        buf[trailerOff + 3] = 0

        return buf
    }

    fun encodeAdvancedPattern(
        training: AdvancedTraining,
        motorConfig: MotorConfig,
        repeatNumOverride: Int? = null,
        repeatDelayOverride: Int? = null,
    ): ByteArray {
        val effectiveRepeatNum = repeatNumOverride ?: training.repeatNum
        val effectiveRepeatDelay = repeatDelayOverride ?: training.repeatDelay
        val allPoints = mutableListOf<ByteArray>()
        for (entry in training.ballList) {
            for (p in entry.points) {
                val params = motorConfig.lookup(entry.ball, entry.spin, entry.power, p.x)
                val buf = ByteArray(12)
                buf[0] = (params?.m1speed ?: 0).toByte()
                buf[1] = (params?.m2speed ?: 0).toByte()
                buf[2] = (params?.xaxis ?: 0).toByte()
                buf[3] = (params?.yaxis ?: 0).toByte()
                buf[4] = (params?.zaxis ?: 0).toByte()
                buf[5] = 0  // repeatDelay high
                buf[6] = (effectiveRepeatDelay.coerceAtLeast(1) and 0xFF).toByte()
                buf[7] = 0
                buf[8] = (entry.ballTime and 0xFF).toByte()
                buf[9] = 1
                buf[10] = 0
                buf[11] = 0
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

    fun splitIntoChunks(frame: ByteArray, mtu: Int = BLE_MTU): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < frame.size) {
            val end = minOf(offset + mtu, frame.size)
            chunks.add(frame.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    // Response parsing
    data class RobotResponse(
        val cmd: Int,
        val status: Int,
        val deviceId: String,
        val payload: ByteArray,
        val firmwareVersion: String? = null,
    )

    fun parseFrame(frame: ByteArray): RobotResponse? {
        if (frame.size < 17) return null

        val payloadLen = ((frame[12].toInt() and 0xFF) shl 8) or (frame[13].toInt() and 0xFF)
        if (frame.size != 14 + payloadLen + 3) return null

        val deviceId = frame.sliceArray(2..9).joinToString("") { "%02x".format(it) }
        val cmd = frame[11].toInt() and 0xFF
        val status = frame[10].toInt() and 0xFF
        val payload = frame.sliceArray(14 until 14 + payloadLen)

        var firmwareVersion: String? = null
        if (cmd == 0x85 && payloadLen == 4) {
            firmwareVersion = "${payload[0]}${payload[1]}.${payload[2]}${payload[3]}"
        }

        return RobotResponse(cmd, status, deviceId, payload, firmwareVersion)
    }
}
