package com.tablebot.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

@SuppressLint("MissingPermission")
class RobotManager(private val context: Context) {

    companion object {
        private const val TAG = "RobotManager"
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null      // for control (connect/stop)
    private var dataWriteChar: BluetoothGattCharacteristic? = null  // for pattern data (fed5 if available)
    var deviceId: String = "0000000000000000"
        private set
    private var useAltService = false

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var drillJob: kotlinx.coroutines.Job? = null  // track active drill coroutine
    private var keepaliveJob: kotlinx.coroutines.Job? = null

    // Callback synchronization
    private var writeCompletion: CompletableDeferred<Int>? = null
    private var descriptorCompletion: CompletableDeferred<Int>? = null

    // Pattern repetition
    private var currentPatternPayload: ByteArray? = null
    private var remainingReps: Int = 0
    private var patternActive: Boolean = false  // only handle 0x8F when we're actually playing
    var onPatternDone: (() -> Unit)? = null  // called when all reps finished

    // Response reassembly
    private val rxBuffer = mutableListOf<Byte>()
    private var rxComplete = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (!name.startsWith("J-")) return

            Log.i(TAG, "Found robot: $name")
            stopScan()
            _state.value = ConnectionState.CONNECTING
            _statusMessage.value = "Connecting to $name..."
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _state.value = ConnectionState.DISCONNECTED
            _statusMessage.value = "Scan failed (error $errorCode)"
        }
    }

    fun scan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _statusMessage.value = "Bluetooth not available"
            return
        }

        _state.value = ConnectionState.SCANNING
        _statusMessage.value = "Scanning for robot..."

        val filter = ScanFilter.Builder().build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)

        // Auto-stop scan after 15s
        scope.launch {
            delay(15_000)
            if (_state.value == ConnectionState.SCANNING) {
                stopScan()
                _state.value = ConnectionState.DISCONNECTED
                _statusMessage.value = "No robot found. Make sure it's powered on."
            }
        }
    }

    private fun stopScan() {
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
    }

    private fun connectToDevice(device: BluetoothDevice) {
        _deviceName.value = device.name

        // Extract device ID from name: "J-XXXXXXXXXXXXXXXX"
        val name = device.name ?: ""
        deviceId = if (name.startsWith("J-") && name.length >= 18) {
            name.substring(2, 18)
        } else if (name.startsWith("J-")) {
            name.substring(2).padEnd(16, '0')
        } else {
            "0000000000000000"
        }
        Log.i(TAG, "Device ID extracted: $deviceId")

        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "onConnectionStateChange: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, discovering services...")
                    _statusMessage.value = "Discovering services..."
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected")
                    _state.value = ConnectionState.DISCONNECTED
                    _statusMessage.value = "Disconnected"
                    _deviceName.value = null
                    writeChar = null
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _statusMessage.value = "Service discovery failed"
                g.disconnect()
                return
            }

            // Log all discovered services
            for (svc in g.services) {
                Log.i(TAG, "  Service: ${svc.uuid}")
                for (ch in svc.characteristics) {
                    Log.i(TAG, "    Char: ${ch.uuid} props=0x${ch.properties.toString(16)}")
                }
            }

            // Try primary service first
            var service = g.getService(UUID.fromString(RobotProtocol.SERVICE_UUID))
            var wChar: BluetoothGattCharacteristic? = null
            var nChar: BluetoothGattCharacteristic? = null

            if (service != null) {
                val c = service.getCharacteristic(UUID.fromString(RobotProtocol.CHAR_UUID))
                if (c != null) {
                    // Primary service uses same char for read/write/notify
                    wChar = c
                    nChar = c
                    Log.i(TAG, "Using PRIMARY service ${RobotProtocol.SERVICE_UUID}")
                    useAltService = false
                }
            }

            // Fallback to alt service — detect write vs notify by properties
            var dChar: BluetoothGattCharacteristic? = null  // secondary write char for data
            if (wChar == null) {
                service = g.getService(UUID.fromString(RobotProtocol.ALT_SERVICE_UUID))
                if (service != null) {
                    Log.i(TAG, "Using ALT service, detecting characteristics by properties...")
                    useAltService = true

                    val writeChars = mutableListOf<BluetoothGattCharacteristic>()
                    val notifyChars = mutableListOf<BluetoothGattCharacteristic>()

                    for (ch in service.characteristics) {
                        val props = ch.properties
                        val isWritable = (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                            (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                        val isNotifiable = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                            (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)

                        Log.i(TAG, "  ${ch.uuid} props=0x${props.toString(16)} writable=$isWritable notifiable=$isNotifiable")

                        if (isWritable) writeChars.add(ch)
                        if (isNotifiable) notifyChars.add(ch)
                    }

                    // First writable = control, second writable = data (if available)
                    wChar = writeChars.getOrNull(0)
                    dChar = writeChars.getOrNull(1)
                    nChar = notifyChars.getOrNull(0)

                    Log.i(TAG, "Write chars found: ${writeChars.size}, Notify chars found: ${notifyChars.size}")
                    if (wChar != null) Log.i(TAG, "  Control write: ${wChar.uuid}")
                    if (dChar != null) Log.i(TAG, "  Data write: ${dChar.uuid}")
                    if (nChar != null) Log.i(TAG, "  Notify: ${nChar.uuid}")
                }
            }

            if (wChar == null) {
                Log.e(TAG, "No writable characteristic found")
                _statusMessage.value = "Incompatible device"
                g.disconnect()
                return
            }

            Log.i(TAG, "Write char: ${wChar.uuid} props=0x${wChar.properties.toString(16)}")
            if (nChar != null) Log.i(TAG, "Notify char: ${nChar.uuid} props=0x${nChar.properties.toString(16)}")
            writeChar = wChar
            dataWriteChar = dChar  // may be null if only one write char exists

            // Setup notifications and send handshake in coroutine to properly sequence operations
            scope.launch {
                try {
                    // Enable notifications/indications on ALL notifiable/indicatable characteristics
                    // CRITICAL: Original app enables BOTH fec8 notifications AND fed6 indications
                    if (service != null && useAltService) {
                        for (ch in service.characteristics) {
                            val props = ch.properties
                            val hasNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                            val hasIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                            if (hasNotify || hasIndicate) {
                                Log.i(TAG, "Enabling ${if (hasIndicate) "INDICATIONS" else "notifications"} on ${ch.uuid}")
                                enableNotificationsAsync(g, ch, hasIndicate)
                                delay(500)
                            }
                        }
                    } else {
                        if (nChar != null && nChar != wChar) {
                            Log.i(TAG, "Enabling notifications on notify char ${nChar.uuid}")
                            enableNotificationsAsync(g, nChar)
                            delay(500)
                        }
                    }

                    // Send handshake
                    Log.i(TAG, "Sending handshake frame...")
                    _statusMessage.value = "Sending handshake..."
                    val connectFrame = RobotProtocol.buildConnectFrame(deviceId)
                    Log.i(TAG, "Handshake frame (${connectFrame.size} bytes): ${connectFrame.toHex()}")
                    sendFrame(connectFrame)

                    Log.i(TAG, "Handshake sent, waiting for response...")
                    delay(1000) // Give robot time to respond

                    _state.value = ConnectionState.CONNECTED
                    _statusMessage.value = "Connected to ${_deviceName.value}"
                    Log.i(TAG, "Connection complete!")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup failed: ${e.message}", e)
                    _statusMessage.value = "Setup failed: ${e.message}"
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.i(TAG, "onDescriptorWrite: ${descriptor.characteristic.uuid} status=$status")
            descriptorCompletion?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite: status=$status")
            writeCompletion?.complete(status)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.i(TAG, "Notification (new API) ${value.size} bytes: ${value.toHex()}")
            handleNotification(value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value
            Log.i(TAG, "Notification (legacy API) ${value.size} bytes: ${value.toHex()}")
            handleNotification(value)
        }
    }

    private suspend fun enableNotificationsAsync(g: BluetoothGatt, char: BluetoothGattCharacteristic, useIndication: Boolean = false) {
        g.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptorCompletion = CompletableDeferred()
            descriptor.value = if (useIndication) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            g.writeDescriptor(descriptor)
            val status = withTimeoutOrNull(3000) { descriptorCompletion?.await() }
            Log.i(TAG, "Descriptor write (${if (useIndication) "INDICATE" else "NOTIFY"}) result: $status")
        } else {
            Log.w(TAG, "No CCCD descriptor found on ${char.uuid}")
        }
    }

    private fun handleNotification(data: ByteArray) {
        if (data.isEmpty()) return

        val first = data[0]
        val last = data[data.size - 1]

        if (first == 0x68.toByte()) {
            rxBuffer.clear()
            rxComplete = false
            rxBuffer.addAll(data.toList())
            if (last == 0x16.toByte()) rxComplete = true
        } else if (last == 0x16.toByte()) {
            rxBuffer.addAll(data.toList())
            rxComplete = true
        } else {
            rxBuffer.addAll(data.toList())
        }

        if (!rxComplete) return

        val frame = rxBuffer.toByteArray()
        rxBuffer.clear()
        rxComplete = false

        Log.i(TAG, "Complete response frame (${frame.size} bytes): ${frame.toHex()}")
        val response = RobotProtocol.parseFrame(frame)
        if (response != null) {
            Log.i(TAG, "Parsed response: cmd=0x${response.cmd.toString(16)} status=0x${response.status.toString(16)} payloadLen=${response.payload.size}")
            if (response.firmwareVersion != null) {
                Log.i(TAG, "Firmware: ${response.firmwareVersion}")
            }

            // Handle pattern completion
            if (response.cmd == RobotProtocol.RESP_PATTERN_DONE && patternActive) {
                // 0x8F = basic drill single rep done — resend for remaining reps
                Log.i(TAG, "Basic rep done! Remaining reps: $remainingReps")
                if (remainingReps > 0) {
                    remainingReps--
                    val payload = currentPatternPayload
                    if (payload != null) {
                        scope.launch {
                            Log.i(TAG, "Resending pattern, reps left: $remainingReps")
                            sendFrame(RobotProtocol.buildStopFrame(deviceId))
                            delay(100)
                            sendFrame(RobotProtocol.buildPrePatternFrame(deviceId))
                            delay(100)
                            val frame2 = RobotProtocol.buildFrame(deviceId, RobotProtocol.CMD_PATTERN, payload)
                            sendFrame(frame2)
                        }
                    }
                } else {
                    patternActive = false
                    currentPatternPayload = null
                    Log.i(TAG, "All basic reps complete")
                    _statusMessage.value = "Training complete"
                    onPatternDone?.invoke()
                }
            } else if (response.cmd == 0x82) {
                // 0x82 = finished (drill complete or stopped)
                Log.i(TAG, "Robot finished (0x82)")
                stopConfirmed = true
                stopJob?.cancel()
                keepaliveJob?.cancel()
                // Wait for motors to physically stop, then reconnect handshake
                scope.launch {
                    delay(2000)
                    // Re-send connect handshake since 0x99 (stop) doubles as disconnect
                    Log.i(TAG, "Re-sending connect handshake after stop")
                    sendFrame(RobotProtocol.buildConnectFrame(deviceId))
                    patternActive = false
                    currentPatternPayload = null
                    _statusMessage.value = "Connected to ${_deviceName.value}"
                    onPatternDone?.invoke()
                }
            } else if (response.cmd == RobotProtocol.RESP_PATTERN_DONE) {
                Log.i(TAG, "Got 0x8F but no pattern active, ignoring")
            }
        }
    }

    suspend fun sendFrame(frame: ByteArray) {
        val char = writeChar
        val g = gatt
        if (char == null || g == null) {
            Log.e(TAG, "sendFrame: not connected (char=$char gatt=$g)")
            return
        }

        Log.i(TAG, "sendFrame: ${frame.size} bytes, splitting into ${RobotProtocol.splitIntoChunks(frame).size} chunks")
        val chunks = RobotProtocol.splitIntoChunks(frame)
        for ((i, chunk) in chunks.withIndex()) {
            Log.d(TAG, "  chunk[$i/${chunks.size}] (${chunk.size} bytes): ${chunk.toHex()}")

            writeCompletion = CompletableDeferred()
            char.value = chunk

            // HCI snoop confirms: original app uses WRITE_CMD (no response) opcode 0x52
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val success = g.writeCharacteristic(char)
            if (!success) {
                Log.e(TAG, "  writeCharacteristic returned false for chunk $i!")
            }
            delay(5) // 5ms between chunks
        }
        Log.i(TAG, "sendFrame: all chunks sent")
    }

    private suspend fun sendFrameToChar(frame: ByteArray, char: BluetoothGattCharacteristic) {
        val g = gatt ?: return
        val chunks = RobotProtocol.splitIntoChunks(frame)
        for ((i, chunk) in chunks.withIndex()) {
            writeCompletion = CompletableDeferred()
            char.value = chunk
            val hasWriteNoResp = char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            char.writeType = if (hasWriteNoResp) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            val success = g.writeCharacteristic(char)
            Log.d(TAG, "  dataChar chunk[$i] success=$success writeType=${char.writeType}")
            if (success && char.writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                withTimeoutOrNull(2000) { writeCompletion?.await() }
            } else {
                delay(30)
            }
        }
    }

    suspend fun sendToDataChar(frame: ByteArray) {
        val dChar = dataWriteChar
        if (dChar == null) {
            Log.e(TAG, "sendToDataChar: no data char available")
            return
        }
        Log.i(TAG, "sendToDataChar: ${frame.size} bytes to ${dChar.uuid}")
        sendFrameToChar(frame, dChar)
    }

    suspend fun sendRawToDataChar(payload: ByteArray) {
        val dChar = dataWriteChar
        val g = gatt
        if (dChar == null || g == null) {
            Log.e(TAG, "sendRawToDataChar: not available")
            return
        }
        Log.i(TAG, "sendRawToDataChar: ${payload.size} raw bytes to ${dChar.uuid}: ${payload.toHex()}")
        val chunks = RobotProtocol.splitIntoChunks(payload)
        for ((i, chunk) in chunks.withIndex()) {
            writeCompletion = CompletableDeferred()
            dChar.value = chunk
            dChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val success = g.writeCharacteristic(dChar)
            Log.d(TAG, "  raw chunk[$i] success=$success")
            if (success) {
                withTimeoutOrNull(2000) { writeCompletion?.await() }
            }
            delay(30)
        }
    }

    suspend fun sendBasicDrill(payload: ByteArray, reps: Int = 1) {
        Log.i(TAG, "sendBasicDrill: payload ${payload.size} bytes, reps=$reps")
        currentPatternPayload = payload
        remainingReps = (reps - 1).coerceAtLeast(0)
        patternActive = true

        // Sequence captured from HCI snoop of original app:
        // 1. Stop any existing pattern
        Log.i(TAG, "sendBasicDrill: stop existing")
        sendFrame(RobotProtocol.buildStopFrame(deviceId))
        delay(300)

        // 2. Pre-pattern setup (cmd 0x04, payload 0x02)
        Log.i(TAG, "sendBasicDrill: pre-pattern 0x04")
        sendFrame(RobotProtocol.buildPrePatternFrame(deviceId))
        delay(200)

        // 3. Send pattern (cmd 0x01)
        val frame = RobotProtocol.buildFrame(deviceId, RobotProtocol.CMD_PATTERN, payload)
        Log.i(TAG, "sendBasicDrill: pattern 0x01, ${frame.size} bytes: ${frame.toHex()}")
        sendFrame(frame)

        // 4. Start keepalive (0x04 every 5s, like original app)
        startKeepalive()
    }

    suspend fun sendAdvancedDrill(payload: ByteArray, reps: Int = 1) {
        Log.i(TAG, "sendAdvancedDrill: payload ${payload.size} bytes, reps=$reps")
        currentPatternPayload = payload
        remainingReps = 0  // robot handles advanced drill reps internally
        patternActive = true

        // Same sequence as basic: stop → pre-pattern → pattern
        Log.i(TAG, "sendAdvancedDrill: stop existing")
        sendFrame(RobotProtocol.buildStopFrame(deviceId))
        delay(300)

        Log.i(TAG, "sendAdvancedDrill: pre-pattern 0x04")
        sendFrame(RobotProtocol.buildPrePatternFrame(deviceId))
        delay(200)

        val frame = RobotProtocol.buildFrame(deviceId, RobotProtocol.CMD_PATTERN, payload)
        Log.i(TAG, "sendAdvancedDrill: pattern 0x01, ${frame.size} bytes: ${frame.toHex()}")
        sendFrame(frame)

        // Start keepalive
        startKeepalive()
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (patternActive) {
                delay(5000)
                if (patternActive) {
                    Log.d(TAG, "Keepalive 0x04")
                    sendFrame(RobotProtocol.buildPrePatternFrame(deviceId))
                }
            }
        }
    }

    private var stopJob: kotlinx.coroutines.Job? = null
    @Volatile private var stopConfirmed = false

    suspend fun stop() {
        Log.i(TAG, "Sending STOP — repeating until 0x82 confirmation")
        keepaliveJob?.cancel()
        keepaliveJob = null
        drillJob?.cancel()
        drillJob = null
        patternActive = false
        currentPatternPayload = null
        remainingReps = 0
        stopConfirmed = false

        // Keep sending stop sequence every 500ms until robot confirms with 0x82
        stopJob?.cancel()
        stopJob = scope.launch {
            repeat(20) { // max 10 seconds
                if (stopConfirmed) return@launch
                Log.i(TAG, "Stop attempt ${it + 1}")
                sendFrame(RobotProtocol.buildFrame(deviceId, 0x04, byteArrayOf(0x02)))
                delay(50)
                sendFrame(RobotProtocol.buildFrame(deviceId, 0x99.toByte(), byteArrayOf(0)))
                delay(100)
                sendFrame(RobotProtocol.buildFrame(deviceId, 0x03, byteArrayOf(0)))
                delay(350)
            }
        }
        _statusMessage.value = "Stopping..."
    }

    fun disconnect() {
        scope.launch {
            val g = gatt ?: return@launch
            if (_state.value == ConnectionState.CONNECTED) {
                try {
                    Log.i(TAG, "Sending disconnect frame")
                    sendFrame(RobotProtocol.buildDisconnectFrame(deviceId))
                    delay(200)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send disconnect: ${e.message}")
                }
            }
            g.disconnect()
            g.close()
            gatt = null
            writeChar = null
            _state.value = ConnectionState.DISCONNECTED
            _statusMessage.value = null
            _deviceName.value = null
        }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
