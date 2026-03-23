package me.arnabsaha.airpodscompanion.ble.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec
import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec.AacpPacket
import me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages the L2CAP connection to AirPods and handles the AACP protocol.
 *
 * Connection sequence (from LibrePods reverse engineering):
 * 1. Create L2CAP socket (PSM 0x1001)
 * 2. Connect socket
 * 3. Send handshake: 00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00
 * 4. Send SET_FEATURE_FLAGS: 04 00 04 00 4D 00 D7 00 00 00 00 00 00 00
 * 5. Send REQUEST_NOTIFICATIONS: 04 00 04 00 0F 00 FF FF FF FF
 * 6. Send PROXIMITY_KEYS_REQ: 04 00 04 00 30 00 05 00
 * 7. Send EQ_ENABLE: 04 00 04 00 29 00 00 FF FF FF FF FF FF FF FF
 * 8. Enter read loop — parse incoming AACP packets
 */
class AacpTransport {

    companion object {
        private const val TAG = "AacpTransport"
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val READ_BUFFER_SIZE = 2048
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        HANDSHAKING,
        CONNECTED,
        RECONNECTING
    }

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var readJob: Job? = null
    private var reconnectJob: Job? = null

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Incoming packets
    private val _incomingPackets = MutableSharedFlow<AacpPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<AacpPacket> = _incomingPackets.asSharedFlow()

    // Debug: raw bytes for packet log
    private val _rawPacketLog = MutableSharedFlow<Pair<Boolean, ByteArray>>(extraBufferCapacity = 256)
    val rawPacketLog: SharedFlow<Pair<Boolean, ByteArray>> = _rawPacketLog.asSharedFlow()

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED

    /**
     * Connect to an AirPods device and perform the AACP handshake.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "Already connecting/connected")
            return
        }

        connectedDevice = device
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                Log.d(TAG, "Creating L2CAP socket to ${device.address}...")
                val sock = L2capSocketFactory.createL2capSocket(device)
                if (sock == null) {
                    Log.e(TAG, "Failed to create socket")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                socket = sock

                Log.d(TAG, "Connecting socket (timeout ${CONNECT_TIMEOUT_MS}ms)...")
                withTimeout(CONNECT_TIMEOUT_MS) {
                    sock.connect()
                }

                inputStream = sock.inputStream
                outputStream = sock.outputStream

                Log.d(TAG, "Socket connected, starting AACP handshake...")
                _connectionState.value = ConnectionState.HANDSHAKING

                performHandshake()

                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "AACP handshake complete — fully connected")

                startReadLoop()
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}")
                closeSocket()
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    /**
     * Perform the AACP initialization handshake.
     * Sends the required packets in sequence with small delays.
     */
    private suspend fun performHandshake() {
        // Step 1: Handshake
        sendRaw(AacpConstants.HANDSHAKE)
        delay(100)

        // Step 2: Set feature flags (enables Adaptive Transparency, CA during audio)
        sendRaw(AacpConstants.SET_FEATURE_FLAGS)
        delay(100)

        // Step 3: Request all notifications
        sendRaw(AacpConstants.REQUEST_NOTIFICATIONS)
        delay(100)

        // Step 4: Request proximity keys (IRK + ENC_KEY)
        sendRaw(AacpConstants.REQUEST_PROXIMITY_KEYS)
        delay(100)

        // Step 5: Enable EQ
        sendRaw(AacpConstants.ENABLE_EQ)

        Log.d(TAG, "Handshake sequence complete (5 packets sent)")
    }

    /**
     * Send a pre-built raw byte array to the AirPods.
     */
    fun sendRaw(data: ByteArray) {
        scope.launch {
            try {
                outputStream?.write(data)
                outputStream?.flush()
                _rawPacketLog.tryEmit(Pair(true, data.clone())) // true = outgoing
                Log.d(TAG, "TX: ${AacpPacketCodec.toHex(data)}")
            } catch (e: IOException) {
                Log.e(TAG, "Send failed: ${e.message}")
                handleDisconnect()
            }
        }
    }

    /**
     * Send a control command (opcode 0x09).
     * Format: [HEADER] [0x09] [0x00] [commandId] [values...]
     */
    fun sendControlCommand(commandId: Byte, vararg values: Byte) {
        sendRaw(AacpPacketCodec.encodeControlCommand(commandId, *values))
    }

    /**
     * Send a generic AACP packet with the given opcode and payload.
     */
    fun sendPacket(opcode: Byte, payload: ByteArray = ByteArray(0)) {
        sendRaw(AacpPacketCodec.encodePacket(opcode, payload))
    }

    /**
     * Start the read loop that continuously reads from the socket
     * and emits decoded packets.
     */
    private fun startReadLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            Log.d(TAG, "Read loop started")

            while (isActive && socket?.isConnected == true) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead <= 0) {
                        Log.d(TAG, "Socket closed (read returned $bytesRead)")
                        break
                    }

                    val data = buffer.copyOf(bytesRead)
                    _rawPacketLog.tryEmit(Pair(false, data.clone())) // false = incoming
                    Log.d(TAG, "RX (${bytesRead}B): ${AacpPacketCodec.toHex(data)}")

                    // Decode and emit
                    val packet = AacpPacketCodec.decode(data)
                    if (packet != null) {
                        _incomingPackets.tryEmit(packet)
                    } else {
                        // May be a multi-packet read or non-AACP data
                        // Try to find AACP packets within the buffer
                        parseMultiplePackets(data)
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        Log.e(TAG, "Read error: ${e.message}")
                    }
                    break
                }
            }

            Log.d(TAG, "Read loop ended")
            handleDisconnect()
        }
    }

    /**
     * Handle cases where multiple AACP packets arrive in a single read.
     * Scans the buffer for AACP headers and decodes each packet.
     */
    private fun parseMultiplePackets(data: ByteArray) {
        var offset = 0
        while (offset < data.size - 5) {
            // Look for AACP header: 04 00 04 00
            if (data[offset] == 0x04.toByte() && data[offset + 1] == 0x00.toByte() &&
                data[offset + 2] == 0x04.toByte() && data[offset + 3] == 0x00.toByte()) {

                // Try to decode from this offset
                val remaining = data.copyOfRange(offset, data.size)
                val packet = AacpPacketCodec.decode(remaining)
                if (packet != null) {
                    _incomingPackets.tryEmit(packet)
                }
            }
            offset++
        }
    }

    /**
     * Disconnect from the AirPods.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        reconnectJob?.cancel()
        readJob?.cancel()
        closeSocket()
        connectedDevice = null
        _connectionState.value = ConnectionState.DISCONNECTED
        supervisorJob.cancel()
        // Create fresh scope for potential reconnect
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    }

    private fun handleDisconnect() {
        closeSocket()
        if (_connectionState.value == ConnectionState.CONNECTED) {
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun closeSocket() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing socket: ${e.message}")
        }
        inputStream = null
        outputStream = null
        socket = null
    }

    /**
     * Schedule reconnection with exponential backoff.
     */
    private fun scheduleReconnect() {
        val device = connectedDevice ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                val delayMs = RECONNECT_BASE_DELAY_MS * (1L shl (attempt - 1))
                Log.d(TAG, "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")
                _connectionState.value = ConnectionState.RECONNECTING

                try {
                    delay(delayMs)
                } catch (e: CancellationException) {
                    return@launch
                }

                if (_connectionState.value == ConnectionState.CONNECTED) return@launch

                try {
                    val sock = L2capSocketFactory.createL2capSocket(device) ?: continue
                    socket = sock
                    sock.connect()
                    inputStream = sock.inputStream
                    outputStream = sock.outputStream

                    _connectionState.value = ConnectionState.HANDSHAKING
                    performHandshake()
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.d(TAG, "Reconnected on attempt $attempt")
                    startReadLoop()
                    return@launch
                } catch (e: IOException) {
                    Log.w(TAG, "Reconnect attempt $attempt failed: ${e.message}")
                    closeSocket()
                }
            }

            Log.e(TAG, "All reconnect attempts exhausted")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
}
