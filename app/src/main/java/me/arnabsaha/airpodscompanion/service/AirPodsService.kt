package me.arnabsaha.airpodscompanion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
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

/** Device info parsed from AACP opcode 0x1D */
data class DeviceInfo(
    val name: String = "",
    val modelNumber: String = "",
    val serialNumber: String = "",
    val firmwareVersion: String = ""
)

/**
 * Foreground service that runs the BLE scanner, maintains AirPods AACP connection,
 * and exposes StateFlows for the UI to observe.
 */
class AirPodsService : Service() {

    companion object {
        private const val TAG = "AirPodsService"
        private const val NOTIFICATION_ID = 1          // Foreground service (minimal, ongoing)
        private const val INFO_NOTIFICATION_ID = 2     // Battery/ANC info (dismissable)
        private const val CHANNEL_ID = "airpods_service"          // IMPORTANCE_MIN for fg service
        private const val INFO_CHANNEL_ID = "airpods_info"        // IMPORTANCE_LOW for battery info
        private const val PRUNE_INTERVAL_MS = 10_000L
        private const val STALE_THRESHOLD_MS = 30_000L
        const val ACTION_SHOW_POPUP = "me.arnabsaha.airpodscompanion.SHOW_POPUP"
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

    // Device info parsed from opcode 0x1D
    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    // Ear detection state
    private val _earState = MutableStateFlow(EarState())
    val earState: StateFlow<EarState> = _earState.asStateFlow()

    // ANC mode
    private val _ancMode = MutableStateFlow<Byte>(0x01) // OFF by default
    val ancMode: StateFlow<Byte> = _ancMode.asStateFlow()

    // List of all bonded AirPods devices
    private val _bondedAirPodsList = MutableStateFlow<List<BondedAirPods>>(emptyList())
    val bondedAirPodsList: StateFlow<List<BondedAirPods>> = _bondedAirPodsList.asStateFlow()
    private var caInitialVolume: Int = -1  // Saved volume before CA lowers it
    private var caPreVoiceAncMode: Byte = NoiseControlMode.NOISE_CANCELLATION // ANC mode before CA switched to transparency
    private var headTrackingActive = false
    private var connectedBtDevice: BluetoothDevice? = null
    private val gestureDetector = HeadGestureDetector()
    private lateinit var connectionPopup: ConnectionPopup
    private var pruneRunnable: Runnable? = null

    // Notification debounce — avoid waking system on every battery packet
    private var pendingNotificationText: String? = null
    private var notificationUpdateRunnable: Runnable? = null
    private var lastNotificationUpdateMs = 0L
    private val NOTIFICATION_DEBOUNCE_MS = 10_000L

    // A2DP profile connection state — tracks whether Android BT system has the AirPods connected
    private val _isBluetoothProfileConnected = MutableStateFlow(false)
    val isBluetoothProfileConnected: StateFlow<Boolean> = _isBluetoothProfileConnected.asStateFlow()
    private var a2dpProxy: BluetoothA2dp? = null
    private var a2dpUnlatchRunnable: Runnable? = null

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
                    Log.d(TAG, "Bluetooth turned OFF — entering idle mode")
                    scanner.stopScan()
                    stopPruneLoop()
                    _transport.disconnect()
                    _isBluetoothProfileConnected.value = false
                    a2dpUnlatchRunnable?.let { handler.removeCallbacks(it) }
                    a2dpUnlatchRunnable = null
                    // Clear stale state
                    _aacpBattery.value = null
                    _earState.value = EarState()
                    _ancMode.value = 0x01 // OFF
                    hasConnectedOnce = false
                    headTrackingActive = false
                    connectedBtDevice = null
                    connectionPopup.dismiss()
                    updateNotificationImmediate("Bluetooth is off")
                }
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth turned ON — restarting")
                    scanner.startScan()
                    startPruneLoop()
                    handler.postDelayed({ autoConnect() }, 2000) // Delay for BT stack to initialize
                    // Re-acquire A2DP proxy
                    setupA2dpMonitor()
                }
            }
        }
    }

    // A2DP connection state receiver — tracks system-level audio profile connection
    // Uses a "latch" approach: once A2DP is seen connected, stays true to avoid
    // dashboard flapping when A2DP briefly disconnects/reconnects (common on AirPods).
    // Un-latches after sustained disconnect (10s debounce) — handles case lid closed.
    private val a2dpStateReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            val targetAddress = connectedBtDevice?.address

            Log.d(TAG, "A2DP state changed: ${device.name} (${device.address}) → $state")

            if (device.address == targetAddress) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    // Cancel any pending un-latch
                    a2dpUnlatchRunnable?.let { handler.removeCallbacks(it) }
                    a2dpUnlatchRunnable = null

                    _isBluetoothProfileConnected.value = true
                    Log.d(TAG, "A2DP profile CONNECTED for target AirPods (latched)")

                    // If AACP is already connected but we haven't received battery data yet,
                    // re-send notification request + ear detection enable to trigger the data flow.
                    if (_transport.isConnected && _aacpBattery.value == null) {
                        Log.d(TAG, "A2DP up + AACP up but no battery yet — re-requesting notifications")
                        requestNotificationsAndEarDetection()
                    }
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    // Debounced un-latch: if A2DP stays disconnected for 10s, hide dashboard.
                    // Brief flaps during reconnect (0 → 1 → 2) won't trigger this.
                    a2dpUnlatchRunnable?.let { handler.removeCallbacks(it) }
                    val runnable = Runnable {
                        Log.d(TAG, "A2DP sustained disconnect — un-latching")
                        _isBluetoothProfileConnected.value = false
                        _aacpBattery.value = null // Clear stale battery data
                    }
                    a2dpUnlatchRunnable = runnable
                    handler.postDelayed(runnable, 10_000)
                }
            }
        }
    }

    // A2DP profile service listener — for initial state check
    private val a2dpProfileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.A2DP) return
            a2dpProxy = proxy as BluetoothA2dp
            checkA2dpConnectionState()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildServiceNotification())

        connectionPopup = ConnectionPopup(this)

        // Register BT state receiver
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        // Register A2DP state receiver
        registerReceiver(a2dpStateReceiver, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))

        // Setup A2DP profile proxy for initial state check
        setupA2dpMonitor()

        scanner = AirPodsScanner(this)
        scanner.startScan()
        startPruneLoop()
        observeConnectionState()
        startPacketDispatcher()
        setupWearSync()

        // Auto-connect to bonded AirPods on service start
        autoConnect()

        // Show initial info notification
        updateNotificationImmediate("Scanning for AirPods...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Handle Quick View action from notification
        if (intent?.action == ACTION_SHOW_POPUP) {
            showPopupIfConnected()
            return START_STICKY
        }

        // Only restart scanning if not currently connected
        if (!scanner.isScanning() && !_transport.isConnected) {
            scanner.startScan()
        }
        return START_STICKY
    }

    private fun showPopupIfConnected() {
        if (!_transport.isConnected) return
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
            ancName,
            _earState.value,
            forceByCooldown = true
        )
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        handler.removeCallbacksAndMessages(null)
        stopPruneLoop()
        serviceJob.cancel()
        connectionPopup.dismiss()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(a2dpStateReceiver) } catch (_: Exception) {}
        releaseA2dpProxy()
        wearCommandCleanup?.invoke()
        scanner.stopScan()
        _transport.disconnect()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed — service continues running")
        // Safety net: schedule restart in case OEM battery optimization kills us
        val restartIntent = Intent(applicationContext, AirPodsService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 0, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
        super.onTaskRemoved(rootIntent)
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
            updateNotificationImmediate("Pair AirPods in Bluetooth settings")
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
        updateNotificationImmediate("Connecting to ${device.name}...")

        // Save as last connected
        getSharedPreferences("airbridge_settings", MODE_PRIVATE)
            .edit().putString("last_connected_address", device.address).apply()

        // Update the bonded list with connection status
        _bondedAirPodsList.value = _bondedAirPodsList.value.map {
            it.copy(isCurrentlyConnected = it.address == device.address)
        }

        _transport.connect(device)

        // Check if this device already has A2DP connected
        checkA2dpConnectionState()
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

    /** Start head tracking — just send the start packet, NO takeover needed */
    fun startHeadTracking() {
        headTrackingActive = true
        // Standard start packet (matching Python prototype + LibrePods)
        val startPacket = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
            0x10, 0x00, 0x10, 0x00, 0x08, 0xA1.toByte(), 0x02, 0x42,
            0x0B, 0x08, 0x0E, 0x10, 0x02, 0x1A, 0x05, 0x01,
            0x40, 0x9C.toByte(), 0x00, 0x00
        )
        _transport.sendRaw(startPacket)
        Log.d(TAG, "Head tracking: standard start packet sent")

        // Also try alternate start packet (for different firmware versions)
        ioScope.launch {
            kotlinx.coroutines.delay(300)
            val altPacket = byteArrayOf(
                0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
                0x10, 0x00, 0x0F, 0x00, 0x08, 0x73, 0x42, 0x0B,
                0x08, 0x10, 0x10, 0x02, 0x1A, 0x05, 0x01,
                0x40, 0x9C.toByte(), 0x00, 0x00
            )
            _transport.sendRaw(altPacket)
            Log.d(TAG, "Head tracking: alternate start packet sent")
        }
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

    /**
     * Re-send notification request + ear detection enable to trigger battery/ear data flow.
     * Called when A2DP connects after AACP is already up — the AirPods firmware only sends
     * battery/ear packets in the initial burst when A2DP is established.
     */
    fun requestNotificationsAndEarDetection() {
        serviceScope.launch {
            kotlinx.coroutines.delay(500) // Brief delay for A2DP to stabilize
            if (!_transport.isConnected) return@launch

            // Re-send notification enable (same as handshake step 3)
            _transport.sendRaw(
                me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants.REQUEST_NOTIFICATIONS
            )
            Log.d(TAG, "Re-sent notification request")

            kotlinx.coroutines.delay(200)
            if (!_transport.isConnected) return@launch

            // Re-send ear detection enable
            transport.sendControlCommand(ControlCommandId.EAR_DETECTION, 0x01)
            Log.d(TAG, "Re-sent ear detection enable")
        }
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

    private fun setupA2dpMonitor() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        adapter.getProfileProxy(this, a2dpProfileListener, BluetoothProfile.A2DP)
    }

    @SuppressLint("MissingPermission")
    private fun checkA2dpConnectionState() {
        val proxy = a2dpProxy ?: return
        val targetAddress = connectedBtDevice?.address ?: return
        val connected = proxy.connectedDevices.any { it.address == targetAddress }
        if (connected) {
            _isBluetoothProfileConnected.value = true
        }
        Log.d(TAG, "A2DP check: ${if (connected) "CONNECTED" else "NOT CONNECTED"} for $targetAddress")
    }

    private fun releaseA2dpProxy() {
        val proxy = a2dpProxy ?: return
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
        a2dpProxy = null
    }

    private fun observeConnectionState() {
        serviceScope.launch {
            _transport.connectionState.collect { state ->
                when (state) {
                    AacpTransport.ConnectionState.CONNECTED -> {
                        scanner.stopScan()
                        stopPruneLoop()
                        Log.d(TAG, "Stopped BLE scan + prune loop (connected)")

                        // Safety net: if no battery data arrives within 8 seconds of
                        // CONNECTED, re-send notification request to trigger data flow.
                        // This handles the race where AACP connects before A2DP.
                        serviceScope.launch {
                            kotlinx.coroutines.delay(8000)
                            if (_transport.isConnected && _aacpBattery.value == null) {
                                Log.d(TAG, "No battery data after 8s — re-requesting")
                                requestNotificationsAndEarDetection()
                            }
                        }

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
                                ancName,
                                _earState.value
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
                            startPruneLoop()
                            Log.d(TAG, "Restarted BLE scan + prune loop (connection failed)")
                        }
                        hasConnectedOnce = false
                        _isBluetoothProfileConnected.value = false // Reset A2DP latch
                        resetInfoNotificationDismissed() // Allow notification to show again on next connection
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
                updateNotificationImmediate(text)
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
        syncToWatch()

        // Update system Bluetooth metadata (best-effort)
        // Update notification with battery
        updateNotification("L: ${state.leftLevel}%  R: ${state.rightLevel}%  Case: ${if (state.caseLevel >= 0) "${state.caseLevel}%" else "--"}")

        // Update battery widget
        me.arnabsaha.airpodscompanion.widgets.BatteryWidget.sendUpdate(
            this, state.leftLevel, state.rightLevel, state.caseLevel
        )

        // Update popup if showing
        connectionPopup.updateContent(state, _ancMode.value, _earState.value)
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
        syncToWatch()
        connectionPopup.updateContent(_aacpBattery.value, _ancMode.value, newState)

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
        val value = raw[7]
        when (commandId) {
            ControlCommandId.LISTENING_MODE -> {
                _ancMode.value = value
                val modeName = when (value) {
                    NoiseControlMode.OFF -> "Off"
                    NoiseControlMode.NOISE_CANCELLATION -> "ANC"
                    NoiseControlMode.TRANSPARENCY -> "Transparency"
                    NoiseControlMode.ADAPTIVE -> "Adaptive"
                    else -> "Unknown"
                }
                Log.d(TAG, "ANC mode: $modeName")
                syncToWatch()
                connectionPopup.updateContent(_aacpBattery.value, value, _earState.value)
            }
            ControlCommandId.CONVERSATION_AWARENESS -> {
                val state = if (value == 0x01.toByte()) "ON" else "OFF"
                Log.d(TAG, "CA confirmed: $state (0x${"%02X".format(value)})")
            }
            ControlCommandId.ADAPTIVE_VOLUME -> {
                val state = if (value == 0x01.toByte()) "ON" else "OFF"
                Log.d(TAG, "Adaptive Volume confirmed: $state (0x${"%02X".format(value)})")
            }
            ControlCommandId.EAR_DETECTION -> {
                val state = if (value == 0x01.toByte()) "ON" else "OFF"
                Log.d(TAG, "Ear Detection confirmed: $state (0x${"%02X".format(value)})")
            }
            else -> Log.d(TAG, "Control cmd: 0x${"%02X".format(commandId)} val=0x${"%02X".format(value)}")
        }
    }

    // ═══ Conversational Awareness (opcode 0x4B) ═══
    // The AirPods send voice detection events via this opcode.
    // The HOST app must control the system volume — AirPods don't do it over A2DP.
    // flag 0x01 = voice detected → lower volume
    // flag 0x08 = CA disabled
    // flag 0x09 = CA enabled
    // other    = voice ended → restore volume
    private fun handleConversationalAwareness(packet: AacpPacket) {
        val raw = packet.rawBytes
        if (raw.size < 10) return
        val flag = raw[9].toInt() and 0xFF

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when (flag) {
            0x01 -> {
                // Voice detected — save volume + ANC mode, switch to transparency, lower volume
                Log.d(TAG, "CA: voice detected — switching to transparency + lowering volume")
                if (caInitialVolume == -1) {
                    caInitialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    caPreVoiceAncMode = _ancMode.value
                    Log.d(TAG, "CA: saved volume=$caInitialVolume, ancMode=0x${"%02X".format(caPreVoiceAncMode)}")
                }
                // Switch to transparency so user can hear conversation
                setNoiseControlMode(NoiseControlMode.TRANSPARENCY)
                // Lower volume to 20%
                val target = (caInitialVolume * 0.20).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                Log.d(TAG, "CA: volume lowered to $target")
            }
            0x08 -> {
                Log.d(TAG, "CA: disabled")
                restoreCaState(audioManager)
            }
            0x09 -> {
                Log.d(TAG, "CA: enabled")
            }
            else -> {
                // Voice ended or other state — restore volume + ANC mode
                if (caInitialVolume != -1) {
                    Log.d(TAG, "CA: voice ended — restoring volume=$caInitialVolume, ancMode=0x${"%02X".format(caPreVoiceAncMode)}")
                    restoreCaState(audioManager)
                } else {
                    Log.d(TAG, "CA level: $flag")
                }
            }
        }
    }

    private fun restoreCaState(audioManager: AudioManager) {
        if (caInitialVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, caInitialVolume, 0)
            setNoiseControlMode(caPreVoiceAncMode)
            Log.d(TAG, "CA: restored volume=$caInitialVolume, ancMode=0x${"%02X".format(caPreVoiceAncMode)}")
            caInitialVolume = -1
        }
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
        val raw = packet.rawBytes
        Log.d(TAG, "Device info packet received (${raw.size} bytes)")

        // The device info packet contains null-terminated strings:
        // name\0model\0manufacturer\0serial\0version\0...
        try {
            val payload = raw.copyOfRange(5, raw.size) // Skip header + opcode
            val text = String(payload, Charsets.UTF_8)
            val parts = text.split("\u0000").filter { it.isNotBlank() }

            if (parts.size >= 4) {
                val info = DeviceInfo(
                    name = parts.getOrNull(0) ?: "",
                    modelNumber = parts.getOrNull(1) ?: "",
                    serialNumber = parts.getOrNull(3) ?: "",
                    firmwareVersion = parts.getOrNull(4) ?: ""
                )
                _deviceInfo.value = info
                Log.d(TAG, "Device: name=${info.name} model=${info.modelNumber} serial=${info.serialNumber} fw=${info.firmwareVersion}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse device info: ${e.message}")
        }
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
                        Log.d(TAG, "HEAD GESTURE: NOD (YES) — answering call")
                        answerCall()
                    }
                    HeadGestureDetector.Gesture.SHAKE_NO -> {
                        Log.d(TAG, "HEAD GESTURE: SHAKE (NO) — declining call")
                        declineCall()
                    }
                    else -> {}
                }
                // Clear peaks but keep calibration — prevents re-trigger on noise
                gestureDetector.clearAfterDetection()
            }
        }
    }

    // ═══ Media Control Helper ═══
    /** Get real Bluetooth MAC address (Android hides it since API 23) */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getRealBluetoothMac(): String {
        // Method 1: Try Settings.Secure
        try {
            val mac = android.provider.Settings.Secure.getString(
                contentResolver, "bluetooth_address"
            )
            if (mac != null && mac != "02:00:00:00:00:00") {
                Log.d(TAG, "Got BT MAC from Settings.Secure: $mac")
                return mac
            }
        } catch (_: Exception) {}

        // Method 2: Try reading from system file
        try {
            val file = java.io.File("/sys/class/bluetooth/hci0/address")
            if (file.exists()) {
                val mac = file.readText().trim()
                if (mac.isNotEmpty() && mac != "02:00:00:00:00:00") {
                    Log.d(TAG, "Got BT MAC from /sys: $mac")
                    return mac
                }
            }
        } catch (_: Exception) {}

        // Method 3: Try BluetoothAdapter directly
        try {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val mac = adapter?.address
            if (mac != null && mac != "02:00:00:00:00:00") {
                Log.d(TAG, "Got BT MAC from adapter: $mac")
                return mac
            }
        } catch (_: Exception) {}

        // Fallback: use the connected device's address reversed as a proxy
        // This isn't our real MAC but at least it's a valid BT address
        val fallback = connectedBtDevice?.address ?: "02:00:00:00:00:00"
        Log.w(TAG, "Could not get real BT MAC, using fallback: $fallback")
        return fallback
    }

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    @SuppressLint("MissingPermission")
    private fun answerCall() {
        try {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.acceptRingingCall()
            Log.d(TAG, "Call answered via TelecomManager")
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager.acceptRingingCall failed: ${e.message}, trying media key fallback")
            sendMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
        }
    }

    @SuppressLint("MissingPermission")
    private fun declineCall() {
        try {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            telecom.endCall()
            Log.d(TAG, "Call declined via TelecomManager")
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager.endCall failed: ${e.message}")
        }
    }

    // ═══ Wear OS Data Sync ═══

    private var wearCommandCleanup: (() -> Unit)? = null

    private fun setupWearSync() {
        // Listen for commands from the watch (ANC toggles)
        wearCommandCleanup = me.arnabsaha.airpodscompanion.wear.WearDataSender.listenForCommands(this) { cmd ->
            Log.d(TAG, "Watch command: $cmd")
            when (cmd) {
                "anc_off" -> setNoiseControlMode(me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode.OFF)
                "anc_on" -> setNoiseControlMode(me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode.NOISE_CANCELLATION)
                "anc_transparency" -> setNoiseControlMode(me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode.TRANSPARENCY)
                "anc_adaptive" -> setNoiseControlMode(me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode.ADAPTIVE)
            }
        }
    }

    private fun syncToWatch() {
        me.arnabsaha.airpodscompanion.wear.WearDataSender.syncState(
            this,
            connected = _transport.isConnected,
            deviceName = _bondedDeviceName.value,
            battery = _aacpBattery.value,
            ancMode = _ancMode.value,
            earState = _earState.value
        )
    }

    /** Debounced notification update — throttled to max once per 10 seconds for battery updates */
    private fun updateNotification(text: String) {
        pendingNotificationText = text
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs >= NOTIFICATION_DEBOUNCE_MS) {
            flushNotification()
        } else if (notificationUpdateRunnable == null) {
            val runnable = Runnable { flushNotification() }
            notificationUpdateRunnable = runnable
            val delay = NOTIFICATION_DEBOUNCE_MS - (now - lastNotificationUpdateMs)
            handler.postDelayed(runnable, delay)
        }
    }

    /** Immediate notification update — bypasses debounce for state transitions */
    private fun updateNotificationImmediate(text: String) {
        pendingNotificationText = text
        flushNotification()
    }

    private fun flushNotification() {
        notificationUpdateRunnable?.let { handler.removeCallbacks(it) }
        notificationUpdateRunnable = null
        val text = pendingNotificationText ?: return
        lastNotificationUpdateMs = System.currentTimeMillis()
        val manager = getSystemService(NotificationManager::class.java)
        if (!isInfoNotificationDismissed()) {
            manager.notify(INFO_NOTIFICATION_ID, buildInfoNotification(text))
        }
    }

    private fun startPruneLoop() {
        if (pruneRunnable != null) return // Already running
        val runnable = object : Runnable {
            override fun run() {
                scanner.pruneStaleDevices(STALE_THRESHOLD_MS)
                handler.postDelayed(this, PRUNE_INTERVAL_MS)
            }
        }
        pruneRunnable = runnable
        handler.postDelayed(runnable, PRUNE_INTERVAL_MS)
    }

    private fun stopPruneLoop() {
        pruneRunnable?.let { handler.removeCallbacks(it) }
        pruneRunnable = null
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Channel 1: Bare minimum for foreground service (barely visible)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "AirBridge Service", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Required for background operation"
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)

        // Channel 2: Informational battery/status (can be swiped away)
        val infoChannel = NotificationChannel(
            INFO_CHANNEL_ID, "AirPods Status", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Battery and connection status"
            setShowBadge(false)
        }
        manager.createNotificationChannel(infoChannel)

        // Clean up old channel if exists
        manager.deleteNotificationChannel("airpods_connection")
    }

    private fun buildServiceNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, me.arnabsaha.airpodscompanion.MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AirBridge")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildInfoNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, me.arnabsaha.airpodscompanion.MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent().setAction(me.arnabsaha.airpodscompanion.receivers.NotificationDismissReceiver.ACTION_DISMISSED)
                .setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        val battery = _aacpBattery.value
        val title = if (battery != null && _transport.isConnected) {
            "\uD83C\uDFA7 ${_bondedDeviceName.value ?: "AirPods"}"
        } else "AirBridge"

        val content = if (battery != null && _transport.isConnected) {
            "L: ${battery.leftLevel}%  R: ${battery.rightLevel}%  Case: ${if (battery.caseLevel >= 0) "${battery.caseLevel}%" else "—"}"
        } else text

        val builder = Notification.Builder(this, INFO_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(tapIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(false)

        // Add Quick View action when connected
        if (_transport.isConnected) {
            val popupIntent = PendingIntent.getService(
                this, 1,
                Intent(this, AirPodsService::class.java).setAction(ACTION_SHOW_POPUP),
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(Notification.Action.Builder(null, "Quick View", popupIntent).build())
        }

        return builder.build()
    }

    private fun isInfoNotificationDismissed(): Boolean {
        return getSharedPreferences("airbridge_settings", MODE_PRIVATE)
            .getBoolean("info_notification_dismissed", false)
    }

    private fun resetInfoNotificationDismissed() {
        getSharedPreferences("airbridge_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("info_notification_dismissed", false)
            .apply()
    }

    /** Called when the app UI opens — resets notification dismissed state */
    fun onAppOpened() {
        if (isInfoNotificationDismissed()) {
            resetInfoNotificationDismissed()
            if (_transport.isConnected && _aacpBattery.value != null) {
                val battery = _aacpBattery.value!!
                updateNotification("L: ${battery.leftLevel}%  R: ${battery.rightLevel}%  Case: ${if (battery.caseLevel >= 0) "${battery.caseLevel}%" else "--"}")
            }
        }
    }
}
