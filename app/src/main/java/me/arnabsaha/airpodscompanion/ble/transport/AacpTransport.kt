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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec
import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec.AacpPacket
import me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages the L2CAP connection to AirPods and handles the AACP protocol.
 * Thread-safe: all socket mutations are protected by a Mutex.
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
        DISCONNECTED, CONNECTING, HANDSHAKING, CONNECTED, RECONNECTING, FAILED
    }

    // Thread safety: protects socket, streams, and state transitions
    private val socketMutex = Mutex()

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var readJob: Job? = null
    private var reconnectJob: Job? = null

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<AacpPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<AacpPacket> = _incomingPackets.asSharedFlow()

    private val _rawPacketLog = MutableSharedFlow<Pair<Boolean, ByteArray>>(extraBufferCapacity = 256)
    val rawPacketLog: SharedFlow<Pair<Boolean, ByteArray>> = _rawPacketLog.asSharedFlow()

    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "Already connecting/connected")
            return
        }

        connectedDevice = device
        _connectionState.value = ConnectionState.CONNECTING
        _connectionError.value = null

        scope.launch {
            try {
                socketMutex.withLock {
                    Log.d(TAG, "Creating L2CAP socket to ${device.address}...")
                    val sock = L2capSocketFactory.createL2capSocket(device)
                    if (sock == null) {
                        Log.e(TAG, "Failed to create socket")
                        _connectionError.value = "Failed to create Bluetooth socket"
                        _connectionState.value = ConnectionState.FAILED
                        return@withLock
                    }

                    Log.d(TAG, "Connecting socket (timeout ${CONNECT_TIMEOUT_MS}ms)...")
                    try {
                        withTimeout(CONNECT_TIMEOUT_MS) { sock.connect() }
                    } catch (e: Exception) {
                        Log.e(TAG, "Socket connect timeout/failed: ${e.message}")
                        sock.close()
                        _connectionError.value = "Connection timed out. Make sure AirPods are nearby."
                        _connectionState.value = ConnectionState.FAILED
                        return@withLock
                    }

                    socket = sock
                    inputStream = sock.inputStream
                    outputStream = sock.outputStream
                }

                if (_connectionState.value == ConnectionState.FAILED) {
                    scheduleReconnect()
                    return@launch
                }

                Log.d(TAG, "Socket connected, starting AACP handshake...")
                _connectionState.value = ConnectionState.HANDSHAKING
                performHandshake()
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "AACP handshake complete — fully connected")
                startReadLoop()
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionError.value = "Connection failed: ${e.message}"
                closeSocketInternal()
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                _connectionError.value = "Unexpected error: ${e.message}"
                closeSocketInternal()
                _connectionState.value = ConnectionState.FAILED
            }
        }
    }

    private suspend fun performHandshake() {
        sendRawDirect(AacpConstants.HANDSHAKE)
        delay(100)
        sendRawDirect(AacpConstants.SET_FEATURE_FLAGS)
        delay(100)
        sendRawDirect(AacpConstants.REQUEST_NOTIFICATIONS)
        delay(100)
        sendRawDirect(AacpConstants.REQUEST_PROXIMITY_KEYS)
        delay(100)
        sendRawDirect(AacpConstants.ENABLE_EQ)
        Log.d(TAG, "Handshake sequence complete (5 packets sent)")
    }

    /** Thread-safe send */
    fun sendRaw(data: ByteArray) {
        scope.launch {
            try {
                sendRawDirect(data)
            } catch (e: IOException) {
                Log.e(TAG, "Send failed: ${e.message}")
                handleDisconnect()
            }
        }
    }

    /** Direct send without launching a new coroutine — used during handshake */
    private suspend fun sendRawDirect(data: ByteArray) {
        val os = outputStream ?: throw IOException("Output stream is null")
        os.write(data)
        os.flush()
        _rawPacketLog.tryEmit(Pair(true, data.clone()))
        Log.d(TAG, "TX: ${AacpPacketCodec.toHex(data)}")
    }

    fun sendControlCommand(commandId: Byte, vararg values: Byte) {
        sendRaw(AacpPacketCodec.encodeControlCommand(commandId, *values))
    }

    fun sendPacket(opcode: Byte, payload: ByteArray = ByteArray(0)) {
        sendRaw(AacpPacketCodec.encodePacket(opcode, payload))
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            Log.d(TAG, "Read loop started")

            while (isActive) {
                try {
                    val stream = inputStream ?: break
                    val bytesRead = stream.read(buffer)
                    if (bytesRead <= 0) {
                        Log.d(TAG, "Socket closed (read returned $bytesRead)")
                        break
                    }

                    val data = buffer.copyOf(bytesRead)
                    _rawPacketLog.tryEmit(Pair(false, data.clone()))

                    // Parse all packets from the buffer
                    parsePacketsFromBuffer(data)
                } catch (e: IOException) {
                    if (isActive) Log.e(TAG, "Read error: ${e.message}")
                    break
                }
            }

            Log.d(TAG, "Read loop ended")
            handleDisconnect()
        }
    }

    /** Parse one or more AACP packets from a read buffer */
    private fun parsePacketsFromBuffer(data: ByteArray) {
        var offset = 0
        while (offset < data.size - 4) {
            // Look for AACP header: 04 00 04 00 or 00 00 04 00
            val isDataHeader = data[offset] == 0x04.toByte() && data[offset + 1] == 0x00.toByte() &&
                data[offset + 2] == 0x04.toByte() && data[offset + 3] == 0x00.toByte()
            val isHandshakeHeader = data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() &&
                data[offset + 2] == 0x04.toByte() && data[offset + 3] == 0x00.toByte()

            if (isDataHeader || isHandshakeHeader) {
                val remaining = data.copyOfRange(offset, data.size)
                val packet = AacpPacketCodec.decode(remaining)
                if (packet != null) {
                    _incomingPackets.tryEmit(packet)
                    // Advance past this packet (header 4 + opcode 1 + secondary 1 + payload)
                    offset += packet.rawBytes.size
                    continue
                }
            }
            offset++
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        reconnectJob?.cancel()
        readJob?.cancel()
        scope.launch {
            socketMutex.withLock { closeSocketInternal() }
        }
        connectedDevice = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionError.value = null
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    }

    private fun handleDisconnect() {
        scope.launch {
            socketMutex.withLock { closeSocketInternal() }
        }
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.HANDSHAKING) {
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    /** Must be called inside socketMutex or from disconnect() */
    private fun closeSocketInternal() {
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

    private fun scheduleReconnect() {
        val device = connectedDevice ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                val delayMs = RECONNECT_BASE_DELAY_MS * (1L shl (attempt - 1))
                Log.d(TAG, "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")
                _connectionState.value = ConnectionState.RECONNECTING

                try { delay(delayMs) } catch (e: CancellationException) { return@launch }
                if (_connectionState.value == ConnectionState.CONNECTED) return@launch

                try {
                    socketMutex.withLock {
                        val sock = L2capSocketFactory.createL2capSocket(device) ?: return@withLock
                        withTimeout(CONNECT_TIMEOUT_MS) { sock.connect() }
                        socket = sock
                        inputStream = sock.inputStream
                        outputStream = sock.outputStream
                    }

                    if (socket?.isConnected != true) continue

                    _connectionState.value = ConnectionState.HANDSHAKING
                    performHandshake()
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.d(TAG, "Reconnected on attempt $attempt")
                    startReadLoop()
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt $attempt failed: ${e.message}")
                    socketMutex.withLock { closeSocketInternal() }
                }
            }

            Log.e(TAG, "All reconnect attempts exhausted")
            _connectionError.value = "Could not reconnect after $MAX_RECONNECT_ATTEMPTS attempts"
            _connectionState.value = ConnectionState.FAILED
        }
    }
}
