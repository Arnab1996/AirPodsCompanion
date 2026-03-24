package me.arnabsaha.airpodscompanion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.arnabsaha.airpodscompanion.ble.scanner.AirPodsAdvertisement
import me.arnabsaha.airpodscompanion.ble.scanner.AirPodsScanner
import me.arnabsaha.airpodscompanion.ble.transport.AacpTransport
import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec
import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec.AacpPacket
import me.arnabsaha.airpodscompanion.protocol.constants.AacpOpcode
import me.arnabsaha.airpodscompanion.protocol.constants.BatteryComponent
import me.arnabsaha.airpodscompanion.protocol.constants.BatteryStatus
import me.arnabsaha.airpodscompanion.protocol.constants.ControlCommandId
import me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode
import me.arnabsaha.airpodscompanion.ui.popup.ConnectionPopup
import me.arnabsaha.airpodscompanion.utils.HeadGestureDetector
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Battery state from AACP packets (more accurate than BLE advertisement) */
data class AacpBatteryState(
    val leftLevel: Int = -1,
    val leftCharging: Boolean = false,
    val rightLevel: Int = -1,
    val rightCharging: Boolean = false,
    val caseLevel: Int = -1,
    val caseCharging: Boolean = false
)

/** Ear detection state */
data class EarState(
    val leftInEar: Boolean = false,
    val rightInEar: Boolean = false,
    val leftInCase: Boolean = false,
    val rightInCase: Boolean = false
) {
    val bothInEar: Boolean get() = leftInEar && rightInEar
    val noneInEar: Boolean get() = !leftInEar && !rightInEar
}

/** Bonded AirPods device info for the device picker */
data class BondedAirPods(
    val name: String,
    val address: String,
    val device: BluetoothDevice,
    val isCurrentlyConnected: Boolean = false
)

/**
 * Foreground service that runs the BLE scanner, maintains AirPods AACP connection,
 * and exposes StateFlows for the UI to observe.
 */
class AirPodsService : Service() {

    companion object {
        private const val TAG = "AirPodsService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "airpods_connection"
        private const val PRUNE_INTERVAL_MS = 10_000L
        private const val STALE_THRESHOLD_MS = 30_000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val ioScope = CoroutineScope(Dispatchers.Default + serviceJob) // For head tracking processing
    private lateinit var scanner: AirPodsScanner
    private val handler = Handler(Looper.getMainLooper())

    // AACP Transport — the core protocol connection
    private val _transport = AacpTransport()
    val transport: AacpTransport get() = _transport

    // Bonded device name (from Android BT settings)
    private val _bondedDeviceName = MutableStateFlow<String?>(null)
    val bondedDeviceName: StateFlow<String?> = _bondedDeviceName.asStateFlow()

    // Live battery from AACP (more accurate than BLE advertisement)
    private val _aacpBattery = MutableStateFlow<AacpBatteryState?>(null)
    val aacpBattery: StateFlow<AacpBatteryState?> = _aacpBattery.asStateFlow()

    // Ear detection state
    private val _earState = MutableStateFlow(EarState())
    val earState: StateFlow<EarState> = _earState.asStateFlow()

    // ANC mode
    private val _ancMode = MutableStateFlow<Byte>(0x01) // OFF by default
    val ancMode: StateFlow<Byte> = _ancMode.asStateFlow()

    // List of all bonded AirPods devices
    private val _bondedAirPodsList = MutableStateFlow<List<BondedAirPods>>(emptyList())
    val bondedAirPodsList: StateFlow<List<BondedAirPods>> = _bondedAirPodsList.asStateFlow()
    private var headTrackingActive = false
    private var connectedBtDevice: BluetoothDevice? = null
    private val gestureDetector = HeadGestureDetector()
    private lateinit var connectionPopup: ConnectionPopup

    /** All detected AirPods, keyed by BLE address */
    val detectedDevices: StateFlow<Map<String, AirPodsAdvertisement>>
        get() = scanner.detectedDevices

    /** Nearest AirPods by RSSI */
    val nearestAirPods: StateFlow<AirPodsAdvertisement?>
        get() = scanner.nearestAirPods

    /** AACP connection state */
    val connectionState: StateFlow<AacpTransport.ConnectionState>
        get() = transport.connectionState

    /** Incoming AACP packets */
    val incomingPackets: SharedFlow<AacpPacket>
        get() = transport.incomingPackets

    /** Raw packet log (for debug screen) */
    val rawPacketLog: SharedFlow<Pair<Boolean, ByteArray>>
        get() = transport.rawPacketLog

    override fun onBind(intent: Intent?): IBinder = binder

    // Bluetooth state receiver — handles BT toggle on/off gracefully
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.d(TAG, "Bluetooth turned OFF")
                    scanner.stopScan()
                    _transport.disconnect()
                }
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth turned ON — restarting")
                    scanner.startScan()
                    handler.postDelayed({ autoConnect() }, 2000) // Delay for BT stack to initialize
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Scanning for AirPods..."))

        connectionPopup = ConnectionPopup(this)

        // Register BT state receiver
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        scanner = AirPodsScanner(this)
        scanner.startScan()
        startPruneLoop()
        observeConnectionState()
        startPacketDispatcher()

        // Auto-connect to bonded AirPods on service start
        autoConnect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        // Only restart scanning if not currently connected
        if (!scanner.isScanning() && !_transport.isConnected) {
            scanner.startScan()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        handler.removeCallbacksAndMessages(null)
        serviceJob.cancel()
        connectionPopup.dismiss()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (_: Exception) {}
        scanner.stopScan()
        _transport.disconnect()
        super.onDestroy()
    }

    // ═══════════════════════════════════════
    // Public API for UI to trigger actions
    // ═══════════════════════════════════════

    /**
     * Auto-connect: find all bonded AirPods, connect to the first available one.
     * Called on service start — no user interaction needed.
     */
    @SuppressLint("MissingPermission")
    fun autoConnect() {
        if (_transport.isConnected) return

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        val aapUuid = android.os.ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

        // Find ALL bonded AirPods
        val airPodsList = mutableListOf<BondedAirPods>()
        for (device in adapter.bondedDevices) {
            val hasAapUuid = device.uuids?.contains(aapUuid) == true
            val isAirPods = device.name?.contains("AirPods", ignoreCase = true) == true
            if (hasAapUuid || isAirPods) {
                airPodsList.add(BondedAirPods(
                    name = device.name ?: "AirPods",
                    address = device.address,
                    device = device
                ))
                Log.d(TAG, "Found bonded AirPods: ${device.name} (${device.address})")
            }
        }

        _bondedAirPodsList.value = airPodsList

        if (airPodsList.isEmpty()) {
            Log.d(TAG, "No bonded AirPods found")
            updateNotification("Pair AirPods in Bluetooth settings")
            return
        }

        // Connect to the first one (or the last-used one from prefs)
        val prefs = getSharedPreferences("airbridge_settings", MODE_PRIVATE)
        val lastAddress = prefs.getString("last_connected_address", null)
        val target = airPodsList.find { it.address == lastAddress } ?: airPodsList.first()

        connectToSpecificDevice(target.device)
    }

    /** Connect to a specific bonded AirPods device */
    @SuppressLint("MissingPermission")
    fun connectToSpecificDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to: ${device.name} (${device.address})")
        _bondedDeviceName.value = device.name ?: "AirPods"
        connectedBtDevice = device
        updateNotification("Connecting to ${device.name}...")

        // Save as last connected
        getSharedPreferences("airbridge_settings", MODE_PRIVATE)
            .edit().putString("last_connected_address", device.address).apply()

        // Update the bonded list with connection status
        _bondedAirPodsList.value = _bondedAirPodsList.value.map {
            it.copy(isCurrentlyConnected = it.address == device.address)
        }

        _transport.connect(device)
    }

    /**
     * Legacy method — kept for backward compatibility but now just calls autoConnect.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(bleAddress: String) {
        autoConnect()
    }

    /** Set noise control mode: OFF, ANC, TRANSPARENCY, ADAPTIVE */
    fun setNoiseControlMode(mode: Byte) {
        transport.sendControlCommand(ControlCommandId.LISTENING_MODE, mode)
    }

    /** Toggle conversational awareness */
    fun setConversationalAwareness(enabled: Boolean) {
        transport.sendControlCommand(
            ControlCommandId.CONVERSATION_AWARENESS,
            if (enabled) 0x01 else 0x02
        )
    }

    /** Toggle adaptive volume */
    fun setAdaptiveVolume(enabled: Boolean) {
        transport.sendControlCommand(
            ControlCommandId.ADAPTIVE_VOLUME,
            if (enabled) 0x01 else 0x02
        )
    }

    /** Toggle ear detection */
    fun setEarDetection(enabled: Boolean) {
        transport.sendControlCommand(
            ControlCommandId.EAR_DETECTION,
            if (enabled) 0x01 else 0x02
        )
    }

    /** Set chime volume (0-100) */
    fun setChimeVolume(volume: Int) {
        transport.sendControlCommand(
            ControlCommandId.CHIME_VOLUME,
            volume.coerceIn(0, 100).toByte()
        )
    }

    /** Rename AirPods (opcode 0x1E). Note: re-pairing needed for Android BT settings to reflect the new name. */
    fun renameAirPods(newName: String) {
        val nameBytes = newName.toByteArray(Charsets.UTF_8)
        val payload = byteArrayOf(0x01, 0x00, nameBytes.size.toByte()) + nameBytes
        _transport.sendRaw(
            me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants.HEADER +
            byteArrayOf(0x1E, 0x00) + payload
        )
        _bondedDeviceName.value = newName
        Log.d(TAG, "Renamed AirPods to: $newName")
    }

    /** Start head tracking — sends full initialization sequence matching LibrePods */
    fun startHeadTracking() {
        // LibrePods sends handshake + feature flags before head tracking start
        _transport.sendRaw(me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants.HANDSHAKE)
        _transport.sendRaw(me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants.SET_FEATURE_FLAGS)
        _transport.sendRaw(me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants.REQUEST_NOTIFICATIONS)

        val startPacket = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
            0x10, 0x00, 0x10, 0x00, 0x08, 0xA1.toByte(), 0x02, 0x42,
            0x0B, 0x08, 0x0E, 0x10, 0x02, 0x1A, 0x05, 0x01,
            0x40, 0x9C.toByte(), 0x00, 0x00
        )
        _transport.sendRaw(startPacket)
        Log.d(TAG, "Head tracking started (full init sequence)")
    }

    /** Stop head tracking */
    fun stopHeadTracking() {
        val stopPacket = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
            0x10, 0x00, 0x11, 0x00, 0x08, 0x7E, 0x10, 0x02,
            0x42, 0x0B, 0x08, 0x4E, 0x10, 0x02, 0x1A, 0x05,
            0x01, 0x00, 0x00, 0x00, 0x00
        )
        _transport.sendRaw(stopPacket)
        Log.d(TAG, "Head tracking stopped")
    }

    /** Get case battery from BLE advertisement as fallback when AACP reports disconnected */
    fun getCaseBatteryFromAd(): Int {
        val nearest = scanner.nearestAirPods.value
        return nearest?.caseBattery ?: -1
    }

    /** Toggle head tracking with gesture detection */
    fun toggleHeadTracking(): Boolean {
        Log.d(TAG, "toggleHeadTracking called, current=$headTrackingActive")
        if (headTrackingActive) {
            stopHeadTracking()
            gestureDetector.reset()
            headTrackingActive = false
        } else {
            startHeadTracking()
            headTrackingActive = true
        }
        Log.d(TAG, "Head tracking now: $headTrackingActive")
        return headTrackingActive
    }

    // ═══════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════

    private var hasConnectedOnce = false

    private fun observeConnectionState() {
        serviceScope.launch {
            _transport.connectionState.collect { state ->
                when (state) {
                    AacpTransport.ConnectionState.CONNECTED -> {
                        scanner.stopScan()
                        Log.d(TAG, "Stopped BLE scan (connected)")

                        // Only show popup on first connection, not reconnects
                        if (!hasConnectedOnce) {
                            hasConnectedOnce = true
                            val ancName = when (_ancMode.value) {
                                NoiseControlMode.OFF -> "Off"
                                NoiseControlMode.NOISE_CANCELLATION -> "Noise Cancellation"
                                NoiseControlMode.TRANSPARENCY -> "Transparency"
                                NoiseControlMode.ADAPTIVE -> "Adaptive"
                                else -> "ANC"
                            }
                            connectionPopup.show(
                                _bondedDeviceName.value ?: "AirPods",
                                _aacpBattery.value,
                                ancName
                            )
                        }
                    }
                    AacpTransport.ConnectionState.DISCONNECTED -> {
                        // Only restart scanner if not in a reconnection cycle
                        // The transport handles reconnection internally
                    }
                    AacpTransport.ConnectionState.FAILED -> {
                        // Connection truly failed — restart scanner for discovery
                        if (!scanner.isScanning()) {
                            scanner.startScan()
                            Log.d(TAG, "Restarted BLE scan (connection failed)")
                        }
                        hasConnectedOnce = false
                    }
                    else -> {}
                }

                val battery = _aacpBattery.value
                val text = when (state) {
                    AacpTransport.ConnectionState.DISCONNECTED -> "Scanning for AirPods..."
                    AacpTransport.ConnectionState.CONNECTING -> "Connecting..."
                    AacpTransport.ConnectionState.HANDSHAKING -> "Handshaking..."
                    AacpTransport.ConnectionState.CONNECTED -> {
                        if (battery != null) {
                            "L: ${battery.leftLevel}%  R: ${battery.rightLevel}%  Case: ${if (battery.caseLevel >= 0) "${battery.caseLevel}%" else "--"}"
                        } else "Connected to ${_bondedDeviceName.value ?: "AirPods"}"
                    }
                    AacpTransport.ConnectionState.RECONNECTING -> "Reconnecting..."
                    AacpTransport.ConnectionState.FAILED -> "Connection failed"
                }
                updateNotification(text)
            }
        }
    }

    /**
     * Phase 4: Packet dispatcher — routes incoming AACP packets to handlers.
     * This is the core event loop that makes battery, ANC, ear detection, etc. work.
     */
    private fun startPacketDispatcher() {
        ioScope.launch {
            _transport.incomingPackets.collect { packet ->
                try {
                    dispatchPacket(packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching packet opcode=0x${"%02X".format(packet.opcode)}: $e", e)
                }
            }
        }
    }

    private fun dispatchPacket(packet: AacpPacket) {
        when (packet.opcode) {
            AacpOpcode.BATTERY_INFO -> handleBatteryPacket(packet)
            AacpOpcode.EAR_DETECTION -> handleEarDetection(packet)
            AacpOpcode.CONTROL_COMMAND -> handleControlCommand(packet)
            AacpOpcode.CONVERSATION_AWARENESS -> handleConversationalAwareness(packet)
            AacpOpcode.STEM_PRESS -> handleStemPress(packet)
            AacpOpcode.HEAD_TRACKING -> handleHeadTrackingData(packet)
            AacpOpcode.DEVICE_INFO -> handleDeviceInfo(packet)
            AacpOpcode.PROXIMITY_KEYS_RSP -> handleProximityKeys(packet)
            else -> Log.d(TAG, "Unhandled opcode: 0x${"%02X".format(packet.opcode)}")
        }
    }

    // ═══ Battery Handler (opcode 0x04) ═══
    private fun handleBatteryPacket(packet: AacpPacket) {
        val raw = packet.rawBytes
        Log.d(TAG, "Battery packet size: ${raw.size}, hex: ${raw.take(30).joinToString(" ") { "%02X".format(it) }}")

        if (raw.size < 12) {
            Log.w(TAG, "Battery packet too short: ${raw.size}")
            return
        }

        // Format: 04 00 04 00 04 00 [count] [comp] 01 [level] [status] 01 ...
        // Parse all components dynamically based on count
        val count = raw[6].toInt() and 0xFF
        var state = AacpBatteryState()
        var offset = 7

        for (i in 0 until count) {
            if (offset + 4 > raw.size) break
            val comp = raw[offset].toInt() and 0xFF
            val level = raw[offset + 2].toInt() and 0xFF
            val status = raw[offset + 3].toInt() and 0xFF
            val charging = status == BatteryStatus.CHARGING
            val disconnected = status == BatteryStatus.DISCONNECTED
            val battLevel = if (disconnected) -1 else level

            state = when (comp) {
                BatteryComponent.LEFT -> state.copy(leftLevel = battLevel, leftCharging = charging)
                BatteryComponent.RIGHT -> state.copy(rightLevel = battLevel, rightCharging = charging)
                BatteryComponent.CASE -> state.copy(caseLevel = battLevel, caseCharging = charging)
                else -> { Log.d(TAG, "Unknown battery component: $comp"); state }
            }
            // Each component block is 5 bytes: comp, 0x01, level, status, 0x01
            offset += 5
        }

        _aacpBattery.value = state
        Log.d(TAG, "Battery: L=${state.leftLevel}% R=${state.rightLevel}% C=${state.caseLevel}%")

        // Update system Bluetooth metadata (best-effort)
        // Update notification with battery
        updateNotification("L: ${state.leftLevel}%  R: ${state.rightLevel}%  Case: ${if (state.caseLevel >= 0) "${state.caseLevel}%" else "--"}")
    }

    // ═══ Ear Detection Handler (opcode 0x06) ═══
    private fun handleEarDetection(packet: AacpPacket) {
        val raw = packet.rawBytes
        Log.d(TAG, "Ear detection packet size: ${raw.size}, hex: ${raw.joinToString(" ") { "%02X".format(it) }}")

        if (raw.size < 8) {
            Log.w(TAG, "Ear detection packet too short: ${raw.size}")
            return
        }

        // primary = left bud status, secondary = right bud status
        // 0x00 = In Ear, 0x01 = Out of Ear, 0x02 = In Case
        val primaryStatus = raw[6].toInt() and 0xFF
        val secondaryStatus = raw[7].toInt() and 0xFF

        val prevState = _earState.value
        val newState = EarState(
            leftInEar = primaryStatus == 0x00,
            rightInEar = secondaryStatus == 0x00,
            leftInCase = primaryStatus == 0x02,
            rightInCase = secondaryStatus == 0x02
        )

        _earState.value = newState
        Log.d(TAG, "Ear: L=${if (newState.leftInEar) "IN" else "OUT"} R=${if (newState.rightInEar) "IN" else "OUT"}")

        // Auto play/pause logic:
        // PAUSE: if ANY ear was in and is now removed (either one or both)
        // PLAY: only when BOTH ears are back in after being out
        val wasAnyInEar = prevState.leftInEar || prevState.rightInEar
        val isAnyInEar = newState.leftInEar || newState.rightInEar

        if (wasAnyInEar && !isAnyInEar) {
            // All ears removed → PAUSE
            Log.d(TAG, "All ears removed → PAUSE")
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else if (prevState.bothInEar && !newState.bothInEar && isAnyInEar) {
            // One ear removed (but other still in) → PAUSE
            Log.d(TAG, "One ear removed → PAUSE")
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else if (newState.bothInEar && !prevState.bothInEar) {
            // Both ears back in → PLAY
            Log.d(TAG, "Both ears in → PLAY")
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    // ═══ Control Command Handler (opcode 0x09) ═══
    private fun handleControlCommand(packet: AacpPacket) {
        val raw = packet.rawBytes
        if (raw.size < 8) return

        val commandId = raw[6]
        when (commandId) {
            ControlCommandId.LISTENING_MODE -> {
                val mode = raw[7]
                _ancMode.value = mode
                val modeName = when (mode) {
                    NoiseControlMode.OFF -> "Off"
                    NoiseControlMode.NOISE_CANCELLATION -> "ANC"
                    NoiseControlMode.TRANSPARENCY -> "Transparency"
                    NoiseControlMode.ADAPTIVE -> "Adaptive"
                    else -> "Unknown"
                }
                Log.d(TAG, "ANC mode: $modeName")
            }
            else -> Log.d(TAG, "Control cmd: 0x${"%02X".format(commandId)}")
        }
    }

    // ═══ Conversational Awareness (opcode 0x4B) ═══
    private fun handleConversationalAwareness(packet: AacpPacket) {
        val raw = packet.rawBytes
        if (raw.size < 10) return
        val level = raw[9].toInt() and 0xFF
        Log.d(TAG, "Conversational awareness level: $level")
    }

    // ═══ Stem Press (opcode 0x19) ═══
    private fun handleStemPress(packet: AacpPacket) {
        val raw = packet.rawBytes
        if (raw.size < 8) return
        val type = raw[6].toInt() and 0xFF
        val bud = raw[7].toInt() and 0xFF
        val typeName = when (type) {
            0x05 -> "Single"
            0x06 -> "Double"
            0x07 -> "Triple"
            0x08 -> "Long"
            else -> "Unknown($type)"
        }
        val budName = if (bud == 0x01) "Left" else "Right"
        Log.d(TAG, "Stem press: $typeName on $budName")

        // Default actions
        when (type) {
            0x06 -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) // Double = skip
            0x07 -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) // Triple = previous
        }
    }

    // ═══ Device Info (opcode 0x1D) ═══
    private fun handleDeviceInfo(packet: AacpPacket) {
        Log.d(TAG, "Device info packet received (${packet.rawBytes.size} bytes)")
    }

    // ═══ Proximity Keys Response (opcode 0x31) ═══
    private fun handleProximityKeys(packet: AacpPacket) {
        Log.d(TAG, "Proximity keys response received (${packet.rawBytes.size} bytes)")
        // TODO: Parse and store IRK/ENC_KEY for RPA verification
    }

    // ═══ Head Tracking Data (opcode 0x17) ═══
    private fun handleHeadTrackingData(packet: AacpPacket) {
        if (!headTrackingActive) return

        val raw = packet.rawBytes

        // Log ALL tracking packets when active for diagnostics
        if (raw.size > 10) {
            Log.d(TAG, "HT: ${raw.size}B byte[10]=0x${"%02X".format(raw[10])}")
        }

        // Skip small control packets and large descriptor packets
        if (raw.size < 60 || raw.size > 200) return

        // Check for the orientation data marker (0x44 or 0x45 at byte 10)
        if (raw[10] == 0x44.toByte() || raw[10] == 0x45.toByte()) {
            Log.d(TAG, "HEAD TRACKING ORIENTATION: ${raw.size}B")
            gestureDetector.processPacket(raw)

            val gesture = gestureDetector.lastGesture.value
            if (gesture != HeadGestureDetector.Gesture.NONE) {
                when (gesture) {
                    HeadGestureDetector.Gesture.NOD_YES -> {
                        Log.d(TAG, "HEAD GESTURE: NOD (YES)")
                        sendMediaKey(KeyEvent.KEYCODE_CALL)
                    }
                    HeadGestureDetector.Gesture.SHAKE_NO -> {
                        Log.d(TAG, "HEAD GESTURE: SHAKE (NO)")
                        sendMediaKey(KeyEvent.KEYCODE_ENDCALL)
                    }
                    else -> {}
                }
            }
        }
    }

    // ═══ Media Control Helper ═══
    private fun sendMediaKey(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startPruneLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                scanner.pruneStaleDevices(STALE_THRESHOLD_MS)
                handler.postDelayed(this, PRUNE_INTERVAL_MS)
            }
        }, PRUNE_INTERVAL_MS)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AirPods Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains connection to AirPods"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val battery = _aacpBattery.value
        val title = if (battery != null && _transport.isConnected) {
            "🎧 ${_bondedDeviceName.value ?: "AirPods"}"
        } else "AirBridge"

        val content = if (battery != null && _transport.isConnected) {
            "L: ${battery.leftLevel}%  R: ${battery.rightLevel}%  Case: ${if (battery.caseLevel >= 0) "${battery.caseLevel}%" else "—"}"
        } else text

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }
}
