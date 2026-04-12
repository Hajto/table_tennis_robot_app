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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var drillJob: kotlinx.coroutines.Job? = null  // track active drill coroutine
    private var keepaliveJob: kotlinx.coroutines.Job? = null

    // Sequential response processing
    private val frameChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var responseJob: kotlinx.coroutines.Job? = null

    init {
        // Start sequential response processor
        responseJob = scope.launch {
            for (frame in frameChannel) {
                processFrame(frame)
            }
        }
    }

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

    // Sequential response processing
    private var writeCompletion: CompletableDeferred<Int>? = null
    private var descriptorCompletion: CompletableDeferred<Int>? = null

    // Pattern repetition
    private var currentPatternPayload: ByteArray? = null
    private var remainingReps: Int = 0
    private var totalReps: Int = 0
    private var patternActive: Boolean = false
    var onPatternDone: (() -> Unit)? = null

    // Response reassembly
    private val rxBuffer = mutableListOf<Byte>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val name = scanRecord?.deviceName ?: device.name ?: return

            if (!name.startsWith("J-")) return

            Log.i(TAG, "Found robot: $name (${device.address})")
            stopScan()
            _state.value = ConnectionState.CONNECTING
            _statusMessage.value = "Connecting to $name..."
            connectToDevice(device, name)
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

    private fun connectToDevice(device: BluetoothDevice, name: String) {
        _deviceName.value = name

        // Extract device ID from name: "J-XXXXXXXXXXXXXXXX"
        // Name should be "J-" followed by up to 16 hex chars.
        val nameSuffix = if (name.startsWith("J-")) {
            name.substring(2).padEnd(16, '0').take(16)
        } else {
            ""
        }

        deviceId = if (nameSuffix.length == 16 && nameSuffix.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            nameSuffix
        } else {
            device.address.replace(":", "").padEnd(16, '0').take(16)
        }
        Log.i(TAG, "Initial Device ID guess: $deviceId")

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

            // Fallback to alt service
            var dChar: BluetoothGattCharacteristic? = null  // secondary write char for data
            if (wChar == null) {
                service = g.getService(UUID.fromString(RobotProtocol.ALT_SERVICE_UUID))
                if (service != null) {
                    Log.i(TAG, "Using ALT service, detecting characteristics...")
                    useAltService = true

                    val writeChars = mutableListOf<BluetoothGattCharacteristic>()
                    val notifyChars = mutableListOf<BluetoothGattCharacteristic>()

                    for (ch in service.characteristics) {
                        val uuidStr = ch.uuid.toString().lowercase()
                        val props = ch.properties
                        val isWritable = (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                        val isNotifiable = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                                (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)

                        Log.i(TAG, "  ${ch.uuid} props=0x${props.toString(16)} writable=$isWritable notifiable=$isNotifiable")

                        // Prefer specific UUIDs if they match
                        if (uuidStr == RobotProtocol.ALT_CHAR_WRITE.lowercase()) {
                            wChar = ch
                        } else if (uuidStr == RobotProtocol.ALT_CHAR_NOTIFY.lowercase()) {
                            nChar = ch
                        }

                        if (isWritable) writeChars.add(ch)
                        if (isNotifiable) notifyChars.add(ch)
                    }

                    // Fallback to first available if specific UUIDs not found
                    if (wChar == null) wChar = writeChars.getOrNull(0)
                    if (dChar == null) dChar = writeChars.getOrNull(1)
                    if (nChar == null) nChar = notifyChars.getOrNull(0)

                    Log.i(TAG, "Write chars found: ${writeChars.size}, Notify chars found: ${notifyChars.size}")
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
            dataWriteChar = dChar

            // Setup notifications and send handshake
            scope.launch {
                try {
                    if (service != null && useAltService) {
                        // Enable notifications/indications on ALL notifiable/indicatable characteristics in alt service
                        for (ch in service.characteristics) {
                            val props = ch.properties
                            val hasNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                            val hasIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                            if (hasNotify || hasIndicate) {
                                Log.i(TAG, "Enabling ${if (hasIndicate) "INDICATIONS" else "notifications"} on ${ch.uuid}")
                                enableNotificationsAsync(g, ch, hasIndicate)
                                delay(300)
                            }
                        }
                    } else if (nChar != null) {
                        // Primary service - enable notifications even if same as write char
                        Log.i(TAG, "Enabling notifications on char ${nChar.uuid}")
                        enableNotificationsAsync(g, nChar)
                        delay(300)
                    }

                    // Send handshake
                    Log.i(TAG, "Sending handshake frame for ID: $deviceId")
                    _statusMessage.value = "Sending handshake..."
                    val connectFrame = try {
                        RobotProtocol.buildConnectFrame(deviceId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to build connect frame: ${e.message}")
                        // Fallback to zeros if ID is invalid
                        RobotProtocol.buildConnectFrame("0000000000000000")
                    }

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
        synchronized(rxBuffer) {
            rxBuffer.addAll(data.toList())
            
            // Extract all complete frames
            while (rxBuffer.size >= 17) {
                val startIdx = rxBuffer.indexOf(0x68.toByte())
                if (startIdx == -1) { rxBuffer.clear(); break }
                if (startIdx > 0) { repeat(startIdx) { rxBuffer.removeAt(0) } }
                if (rxBuffer.size < 14) break
                
                // Verify the second marker at index 10
                if (rxBuffer.size >= 11 && rxBuffer[10] != 0x68.toByte()) {
                    rxBuffer.removeAt(0) // Not the right 0x68
                    continue
                }

                val payloadLen = ((rxBuffer[12].toInt() and 0xFF) shl 8) or (rxBuffer[13].toInt() and 0xFF)
                val totalSize = 14 + payloadLen + 3
                if (rxBuffer.size < totalSize) break
                
                if (rxBuffer[totalSize - 1] == 0x16.toByte()) {
                    val frame = rxBuffer.take(totalSize).toByteArray()
                    repeat(totalSize) { rxBuffer.removeAt(0) }
                    // Queue for sequential processing
                    frameChannel.trySend(frame)
                } else {
                    rxBuffer.removeAt(0)
                }
            }
        }
    }

    private suspend fun processFrame(frame: ByteArray) {
        Log.i(TAG, "Complete response frame (${frame.size} bytes): ${frame.toHex()}")
        val response = RobotProtocol.parseFrame(frame) ?: return
        
        // DYNAMIC ID UPDATE: Update if robot responds with a specific ID
        if (response.deviceId != "0000000000000000" && response.deviceId != deviceId) {
            Log.i(TAG, "Updating deviceId from robot response: ${response.deviceId}")
            deviceId = response.deviceId
        }

        Log.d(TAG, "Response: cmd=0x${response.cmd.toString(16)} status=0x${response.status.toString(16)} payload=${response.payload.toHex()}")
        
        when (response.cmd) {
            0x81 -> { // PATTERN_ACK
                if (_statusMessage.value?.startsWith("Playing") != true) {
                    _statusMessage.value = "Pattern accepted"
                }
            }
            0x84 -> { // PRE_ACK
                if (_statusMessage.value?.startsWith("Playing") != true) {
                    _statusMessage.value = "Preparing robot..."
                }
            }
            0x85 -> { // FIRMWARE
                Log.i(TAG, "Robot Firmware: ${response.firmwareVersion}")
            }
            0x83 -> { // POST_ACK
                Log.d(TAG, "Post-pattern acknowledged")
            }
            RobotProtocol.RESP_PATTERN_DONE, 0x82 -> { // 0x8F or 0x82
                if (!patternActive) {
                    if (response.cmd == 0x82) {
                        Log.i(TAG, "Robot reported 0x82 (stopped)")
                        finishDrill()
                    }
                    return
                }

                Log.i(TAG, "Robot reported drill complete (0x${response.cmd.toString(16)})")
                finishDrill()
            }
        }
    }

    private suspend fun finishDrill() {
        if (!patternActive && _statusMessage.value == "Connected to ${_deviceName.value}") return
        
        patternActive = false
        currentPatternPayload = null
        keepaliveJob?.cancel()
        
        _statusMessage.value = "Cleaning up..."
        sendFrame(RobotProtocol.buildPostPatternFrame(deviceId))
        delay(1000)
        
        // Re-handshake to ensure clean state
        sendFrame(RobotProtocol.buildConnectFrame(deviceId))
        _statusMessage.value = "Connected to ${_deviceName.value}"
        onPatternDone?.invoke()
    }

    suspend fun sendFrame(frame: ByteArray) {
        val char = writeChar
        val g = gatt
        if (char == null || g == null) {
            Log.e(TAG, "sendFrame: not connected")
            return
        }

        val chunks = RobotProtocol.splitIntoChunks(frame)
        for ((i, chunk) in chunks.withIndex()) {
            writeCompletion = CompletableDeferred()
            char.value = chunk
            
            // Protocol says: Use ATT Write Command (Write Without Response)
            val hasWriteNoResp = char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            char.writeType = if (hasWriteNoResp) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            
            val success = g.writeCharacteristic(char)
            if (success && char.writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                withTimeoutOrNull(1000) { writeCompletion?.await() }
            }
            // Essential delay for robot processing
            delay(30)
        }
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
        totalReps = reps
        remainingReps = 0 // Hardware handles repetitions
        patternActive = true

        // Sequence for firmware compatibility:
        Log.i(TAG, "sendBasicDrill: stop existing")
        sendFrame(RobotProtocol.buildStopFrame(deviceId))
        delay(500)

        Log.i(TAG, "sendBasicDrill: pre-pattern 0x04")
        sendFrame(RobotProtocol.buildPrePatternFrame(deviceId))
        delay(500)

        _statusMessage.value = "Playing... (Total $reps reps)"
        
        // Use CMD_PATTERN (0x01) for basic drills
        val frame = RobotProtocol.buildFrame(deviceId, RobotProtocol.CMD_PATTERN, payload)
        Log.i(TAG, "sendBasicDrill: pattern 0x01, ${frame.size} bytes: ${frame.toHex()}")
        sendFrame(frame)

        startKeepalive()
    }

    suspend fun sendAdvancedDrill(payload: ByteArray, reps: Int = 1) {
        Log.i(TAG, "sendAdvancedDrill: payload ${payload.size} bytes, reps=$reps")
        
        currentPatternPayload = payload
        totalReps = reps
        remainingReps = 0 // Hardware handles repetitions
        patternActive = true

        Log.i(TAG, "sendAdvancedDrill: stop existing")
        sendFrame(RobotProtocol.buildStopFrame(deviceId))
        delay(500)

        Log.i(TAG, "sendAdvancedDrill: pre-pattern 0x04")
        sendFrame(RobotProtocol.buildPrePatternFrame(deviceId))
        delay(500)

        _statusMessage.value = "Playing... (Total $reps reps)"

        // Use CMD_PATTERN (0x01) for advanced drills as well on this firmware
        val frame = RobotProtocol.buildFrame(deviceId, RobotProtocol.CMD_PATTERN, payload)
        Log.i(TAG, "sendAdvancedDrill: pattern 0x01, ${frame.size} bytes: ${frame.toHex()}")
        sendFrame(frame)

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

    suspend fun stop() {
        Log.i(TAG, "Sending UNIVERSAL STOP sequence to ID: $deviceId")
        
        // Immediate UI/State update
        patternActive = false
        currentPatternPayload = null
        drillJob?.cancel()
        drillJob = null
        keepaliveJob?.cancel()
        
        scope.launch {
            // 1. Send Pause (0x25)
            sendFrame(RobotProtocol.buildPauseFrame(deviceId))
            delay(100)
            
            // 2. Send Stop (0x99)
            sendFrame(RobotProtocol.buildStopFrame(deviceId))
            delay(100)
            
            // 3. Perform standard finish cleanup
            finishDrill()
        }
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
