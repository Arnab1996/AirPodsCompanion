package me.arnabsaha.airpodscompanion.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.arnabsaha.airpodscompanion.ble.transport.AacpTransport
import me.arnabsaha.airpodscompanion.service.AacpBatteryState
import me.arnabsaha.airpodscompanion.service.AirPodsService
import me.arnabsaha.airpodscompanion.service.BondedAirPods
import me.arnabsaha.airpodscompanion.service.EarState

/**
 * ViewModel that acts as a clean intermediary between the UI and [AirPodsService].
 *
 * Responsibilities:
 * - Binds to [AirPodsService] via a [ServiceConnection] and exposes its StateFlows
 * - Provides action methods that delegate to the service
 * - Manages persisted user settings via SharedPreferences
 * - Exposes a [connectionError] flow for transient error display
 *
 * The UI should only interact with this ViewModel, never with the service directly.
 */
class AirPodsViewModel(private val application: Application) : ViewModel() {

    companion object {
        private const val TAG = "AirPodsViewModel"
        private const val PREFS_NAME = "airbridge_settings"
    }

    // ── Service binding ──────────────────────────────────────────

    private var airPodsService: AirPodsService? = null
    private var serviceBound = false

    /** Suppresses the transient "connection lost" error when the user disconnects on purpose. */
    private var suppressDisconnectError = false

    private val _serviceBound = MutableStateFlow(false)
    /** True once the service is bound and ready for commands. */
    val isServiceBound: StateFlow<Boolean> = _serviceBound.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as AirPodsService.LocalBinder).getService()
            airPodsService = service
            serviceBound = true
            _serviceBound.value = true
            Log.d(TAG, "Service bound")
            startCollectingServiceFlows(service)
            applySavedSettings(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            airPodsService = null
            serviceBound = false
            _serviceBound.value = false
            Log.d(TAG, "Service unbound")
        }
    }

    // ── UI State (forwarded from service) ────────────────────────

    private val _battery = MutableStateFlow<AacpBatteryState?>(null)
    /** Live AACP battery state for left, right, and case. */
    val battery: StateFlow<AacpBatteryState?> = _battery.asStateFlow()

    private val _earState = MutableStateFlow(EarState())
    /** Ear detection state for both buds. */
    val earState: StateFlow<EarState> = _earState.asStateFlow()

    private val _ancMode = MutableStateFlow<Byte>(0x01)
    /** Current noise control mode byte (OFF / ANC / Transparency / Adaptive). */
    val ancMode: StateFlow<Byte> = _ancMode.asStateFlow()

    private val _bondedDeviceName = MutableStateFlow<String?>(null)
    /** Name of the currently targeted bonded AirPods device. */
    val bondedDeviceName: StateFlow<String?> = _bondedDeviceName.asStateFlow()

    private val _connectionState = MutableStateFlow(AacpTransport.ConnectionState.DISCONNECTED)
    /** AACP transport connection state. */
    val connectionState: StateFlow<AacpTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _bondedAirPodsList = MutableStateFlow<List<BondedAirPods>>(emptyList())
    /** All bonded AirPods devices found on the system. */
    val bondedAirPodsList: StateFlow<List<BondedAirPods>> = _bondedAirPodsList.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    /** Transient error message for the UI to display (null = no error). */
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _isBluetoothProfileConnected = MutableStateFlow(false)
    /** True when the AirPods have a system-level BT profile (A2DP) connected. */
    val isBluetoothProfileConnected: StateFlow<Boolean> = _isBluetoothProfileConnected.asStateFlow()

    private val _leAudioCapability = MutableStateFlow<me.arnabsaha.airpodscompanion.ble.LeAudioCapability?>(null)
    /** LE Audio capability detected on connect. */
    val leAudioCapability: StateFlow<me.arnabsaha.airpodscompanion.ble.LeAudioCapability?> = _leAudioCapability.asStateFlow()

    private val _nearestAirPods = MutableStateFlow<me.arnabsaha.airpodscompanion.ble.scanner.AirPodsAdvertisement?>(null)
    /** Nearest AirPods by RSSI (for Find My feature). */
    val nearestAirPods: StateFlow<me.arnabsaha.airpodscompanion.ble.scanner.AirPodsAdvertisement?> = _nearestAirPods.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<me.arnabsaha.airpodscompanion.ble.scanner.AirPodsAdvertisement>>(emptyList())
    /** All AirPods/Beats currently seen over BLE (passive — no connection needed), strongest signal first. */
    val nearbyDevices: StateFlow<List<me.arnabsaha.airpodscompanion.ble.scanner.AirPodsAdvertisement>> = _nearbyDevices.asStateFlow()

    private val _deviceInfo = MutableStateFlow<me.arnabsaha.airpodscompanion.service.DeviceInfo?>(null)
    /** Real device info (model / serial / firmware) parsed from AACP opcode 0x1D. */
    val deviceInfo: StateFlow<me.arnabsaha.airpodscompanion.service.DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _headGesture = MutableStateFlow<Pair<Long, String>?>(null)
    /** Latest detected head gesture: (sequence, "nod"|"shake"). Sequence bumps each time. */
    val headGesture: StateFlow<Pair<Long, String>?> = _headGesture.asStateFlow()

    private val _connectionActivity = MutableStateFlow(0)
    /** Advertisement activity: 0=disconnected, 4=idle, 5=music, 6=call. */
    val connectionActivity: StateFlow<Int> = _connectionActivity.asStateFlow()

    // ── Persisted settings (exposed as StateFlows for the UI) ────

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _caEnabled = MutableStateFlow(prefs.getBoolean("ca_enabled", false))
    /** Conversational Awareness toggle state. */
    val caEnabled: StateFlow<Boolean> = _caEnabled.asStateFlow()

    private val _avEnabled = MutableStateFlow(prefs.getBoolean("av_enabled", false))
    /** Adaptive Volume toggle state. */
    val avEnabled: StateFlow<Boolean> = _avEnabled.asStateFlow()

    private val _edEnabled = MutableStateFlow(prefs.getBoolean("ed_enabled", true))
    /** Ear Detection toggle state. */
    val edEnabled: StateFlow<Boolean> = _edEnabled.asStateFlow()

    private val _oneBudAnc = MutableStateFlow(prefs.getBoolean("one_bud_anc", true))
    /** One-Bud ANC toggle state. */
    val oneBudAnc: StateFlow<Boolean> = _oneBudAnc.asStateFlow()

    private val _volumeSwipe = MutableStateFlow(prefs.getBoolean("volume_swipe", true))
    /** Volume Swipe toggle state. */
    val volumeSwipe: StateFlow<Boolean> = _volumeSwipe.asStateFlow()

    private val _sleepDetection = MutableStateFlow(prefs.getBoolean("sleep_detection", false))
    /** Sleep Detection toggle state. */
    val sleepDetection: StateFlow<Boolean> = _sleepDetection.asStateFlow()

    private val _inCaseTone = MutableStateFlow(prefs.getBoolean("in_case_tone", true))
    /** In-Case Tone toggle state. */
    val inCaseTone: StateFlow<Boolean> = _inCaseTone.asStateFlow()

    private val _allowOff = MutableStateFlow(prefs.getBoolean("allow_off", true))
    /** Off Listening Mode toggle state (ALLOW_OFF_OPTION). */
    val allowOff: StateFlow<Boolean> = _allowOff.asStateFlow()

    private val _headTracking = MutableStateFlow(prefs.getBoolean("head_tracking", false))
    /** Head Tracking / Spatial Audio toggle state. */
    val headTracking: StateFlow<Boolean> = _headTracking.asStateFlow()

    private val _headTrackingLoading = MutableStateFlow(false)
    /** True while head tracking is in the 30-second warm-up delay. */
    val headTrackingLoading: StateFlow<Boolean> = _headTrackingLoading.asStateFlow()

    private val _chimeVolume = MutableStateFlow(prefs.getFloat("chime_volume", 50f))
    /** Chime volume (0-100). */
    val chimeVolume: StateFlow<Float> = _chimeVolume.asStateFlow()

    private val _stemAction = MutableStateFlow(prefs.getString("stem_action", "Noise Control") ?: "Noise Control")
    /** Stem long-press action label. */
    val stemAction: StateFlow<String> = _stemAction.asStateFlow()

    private val _batteryAlertThreshold = MutableStateFlow(prefs.getInt("battery_alert_threshold", 20))
    /** Battery alert threshold percentage. */
    val batteryAlertThreshold: StateFlow<Int> = _batteryAlertThreshold.asStateFlow()

    private val _autoResume = MutableStateFlow(prefs.getBoolean("auto_resume", false))
    /** Resume media playback automatically when the AirPods connect. */
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()

    private val _backgroundScan = MutableStateFlow(prefs.getBoolean("background_scan", true))
    /** Keep a low-power BLE scan while connected (passive case battery + case-open popup). */
    val backgroundScan: StateFlow<Boolean> = _backgroundScan.asStateFlow()

    private val _runInBackground = MutableStateFlow(prefs.getBoolean("run_in_background", true))
    /** Keep the service alive after the app is swiped away (background features vs. Active-apps entry). */
    val runInBackground: StateFlow<Boolean> = _runInBackground.asStateFlow()

    // ── Lifecycle ────────────────────────────────────────────────

    /**
     * Bind to [AirPodsService]. Call this once permissions are granted.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun bindService() {
        if (serviceBound) return
        val intent = Intent(application, AirPodsService::class.java)
        application.startForegroundService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "Binding to AirPodsService")
    }

    /**
     * Unbind from [AirPodsService]. The service continues running as a foreground service.
     */
    fun unbindService() {
        if (!serviceBound) return
        try {
            application.unbindService(serviceConnection)
        } catch (_: Exception) { }
        serviceBound = false
        _serviceBound.value = false
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
        Log.d(TAG, "ViewModel cleared")
    }

    // ── Action methods (UI calls these) ──────────────────────────

    /** Set noise control mode: OFF, ANC, TRANSPARENCY, ADAPTIVE. */
    fun setNoiseControlMode(mode: Byte) {
        withService("setNoiseControlMode") { it.setNoiseControlMode(mode) }
    }

    /** Toggle conversational awareness on/off. */
    fun setConversationalAwareness(enabled: Boolean) {
        _caEnabled.value = enabled
        saveBool("ca_enabled", enabled)
        withService("setConversationalAwareness") { it.setConversationalAwareness(enabled) }
    }

    /** Toggle adaptive volume on/off. */
    fun setAdaptiveVolume(enabled: Boolean) {
        _avEnabled.value = enabled
        saveBool("av_enabled", enabled)
        withService("setAdaptiveVolume") { it.setAdaptiveVolume(enabled) }
    }

    /** Toggle ear detection auto play/pause on/off. */
    fun setEarDetection(enabled: Boolean) {
        _edEnabled.value = enabled
        saveBool("ed_enabled", enabled)
        withService("setEarDetection") { it.setEarDetection(enabled) }
    }

    /** Set chime volume (0-100). Updates the persisted value. */
    fun setChimeVolume(volume: Float) {
        _chimeVolume.value = volume
        prefs.edit().putFloat("chime_volume", volume).apply()
        withService("setChimeVolume") { it.setChimeVolume(volume.toInt()) }
    }

    /** Update chime volume slider position without sending to device (use during drag). */
    fun updateChimeVolumeSlider(volume: Float) {
        _chimeVolume.value = volume
    }

    /** Play a short chime at the current chime volume so the user can hear the level. */
    fun previewChime() {
        withService("previewChime") { it.playChimePreview() }
    }

    /** Rename the AirPods. Sends the rename packet and updates the bonded device name. */
    fun renameAirPods(newName: String) {
        withService("renameAirPods") { it.renameAirPods(newName) }
    }

    /** Toggle head tracking / spatial audio. Returns the new active state. */
    fun toggleHeadTracking() {
        // Cancel any pending delayed start
        _headTrackingLoading.value = false
        withService("toggleHeadTracking") { service ->
            val active = service.toggleHeadTracking()
            _headTracking.value = active
            saveBool("head_tracking", active)
        }
    }

    /** Connect to a specific bonded AirPods device. */
    fun connectToDevice(device: BluetoothDevice) {
        suppressDisconnectError = false
        withService("connectToDevice") { it.connectToSpecificDevice(device) }
    }

    /** Auto-connect to the most recently used or first available bonded AirPods. */
    fun autoConnect() {
        suppressDisconnectError = false
        withService("autoConnect") { it.autoConnect(userInitiated = true) }
    }

    /** User-initiated disconnect. Suppresses the transient "connection lost" error. */
    fun disconnect() {
        suppressDisconnectError = true
        withService("disconnect") { it.disconnectManually() }
    }

    /** Forget (unpair) the AirPods entirely. */
    fun forgetDevice() {
        suppressDisconnectError = true
        withService("forgetDevice") { it.forgetDevice() }
    }

    /** Toggle auto-resume of media playback when the AirPods connect. */
    fun setAutoResume(enabled: Boolean) {
        _autoResume.value = enabled
        saveBool("auto_resume", enabled)
    }

    /** Toggle the low-power background scan kept alive while connected. Applies immediately. */
    fun setBackgroundScan(enabled: Boolean) {
        _backgroundScan.value = enabled
        saveBool("background_scan", enabled)
        withService("setBackgroundScan") { it.applyBackgroundScan() }
    }

    /**
     * Toggle whether AirBridge keeps running after the app is swiped away. When off, the service
     * fully stops on swipe (leaves "Active apps") but background features pause until reopened.
     * Read by the service in onTaskRemoved — no live call needed.
     */
    fun setRunInBackground(enabled: Boolean) {
        _runInBackground.value = enabled
        saveBool("run_in_background", enabled)
    }

    /** Toggle one-bud ANC. */
    fun setOneBudAnc(enabled: Boolean) {
        _oneBudAnc.value = enabled
        saveBool("one_bud_anc", enabled)
        withService("setOneBudAnc") {
            it.transport.sendControlCommand(0x1B, if (enabled) 0x01 else 0x02)
        }
    }

    /** Toggle volume swipe on stem. */
    fun setVolumeSwipe(enabled: Boolean) {
        _volumeSwipe.value = enabled
        saveBool("volume_swipe", enabled)
        withService("setVolumeSwipe") {
            it.transport.sendControlCommand(0x25, if (enabled) 0x01 else 0x02)
        }
    }

    /** Toggle sleep detection. */
    fun setSleepDetection(enabled: Boolean) {
        _sleepDetection.value = enabled
        saveBool("sleep_detection", enabled)
        withService("setSleepDetection") {
            it.transport.sendControlCommand(0x35, if (enabled) 0x01 else 0x02)
        }
    }

    /** Toggle in-case tone. */
    fun setInCaseTone(enabled: Boolean) {
        _inCaseTone.value = enabled
        saveBool("in_case_tone", enabled)
        withService("setInCaseTone") {
            it.transport.sendControlCommand(0x31, if (enabled) 0x01 else 0x02)
        }
    }

    /** Toggle Off Listening Mode (ALLOW_OFF_OPTION 0x34). */
    fun setAllowOff(enabled: Boolean) {
        _allowOff.value = enabled
        saveBool("allow_off", enabled)
        withService("setAllowOff") {
            it.transport.sendControlCommand(0x34, if (enabled) 0x01 else 0x02)
        }
    }

    /** Set stem long-press action. */
    fun setStemAction(action: String) {
        _stemAction.value = action
        prefs.edit().putString("stem_action", action).apply()
        withService("setStemAction") {
            it.transport.sendControlCommand(
                0x16,
                if (action == "Noise Control") 0x01 else 0x00,
                if (action == "Noise Control") 0x01 else 0x00
            )
        }
    }

    /** Clear the current connection error. Call from UI after displaying. */
    fun clearConnectionError() {
        _connectionError.value = null
    }

    /** Set battery alert threshold percentage. */
    fun setBatteryAlertThreshold(value: Int) {
        _batteryAlertThreshold.value = value
        prefs.edit().putInt("battery_alert_threshold", value).apply()
    }

    /** Start BLE scan for Find My AirPods. */
    fun startFindMyScan() {
        withService("startFindMyScan") { it.startFindMyScan() }
    }

    /** Stop BLE scan for Find My AirPods. */
    fun stopFindMyScan() {
        withService("stopFindMyScan") { it.stopFindMyScan() }
    }

    // ── Internal ─────────────────────────────────────────────────

    /**
     * Collect all StateFlows from the service and forward them to our own StateFlows.
     * Called once when the service is bound.
     */
    private fun startCollectingServiceFlows(service: AirPodsService) {
        service.onAppOpened()
        viewModelScope.launch {
            service.aacpBattery.collect { _battery.value = it }
        }
        viewModelScope.launch {
            service.earState.collect { _earState.value = it }
        }
        viewModelScope.launch {
            service.ancMode.collect { _ancMode.value = it }
        }
        viewModelScope.launch {
            service.bondedDeviceName.collect { _bondedDeviceName.value = it }
        }
        viewModelScope.launch {
            service.connectionState.collect { state ->
                _connectionState.value = state
                if (state == AacpTransport.ConnectionState.CONNECTED) suppressDisconnectError = false
                // Surface reconnection failures as transient errors (but not on a manual disconnect)
                if (state == AacpTransport.ConnectionState.DISCONNECTED &&
                    _battery.value != null && !suppressDisconnectError) {
                    // Was previously connected and lost connection
                    _connectionError.value = "Connection to AirPods lost"
                }
            }
        }
        viewModelScope.launch {
            service.bondedAirPodsList.collect { _bondedAirPodsList.value = it }
        }
        viewModelScope.launch {
            service.isBluetoothProfileConnected.collect { _isBluetoothProfileConnected.value = it }
        }
        viewModelScope.launch {
            service.leAudioCapability.collect { _leAudioCapability.value = it }
        }
        viewModelScope.launch {
            service.nearestAirPods.collect { _nearestAirPods.value = it }
        }
        viewModelScope.launch {
            service.detectedDevices.collect { map ->
                _nearbyDevices.value = map.values.sortedByDescending { it.rssi }
            }
        }
        viewModelScope.launch {
            service.deviceInfo.collect { _deviceInfo.value = it }
        }
        viewModelScope.launch {
            service.headGesture.collect { _headGesture.value = it }
        }
        viewModelScope.launch {
            service.connectionActivity.collect { _connectionActivity.value = it }
        }
    }

    /**
     * Apply all persisted settings to the service on each connection.
     * Uses a Job with 3-second debounce: if the connection drops within 3 seconds
     * (common during reconnect cycles), the settings send is cancelled — no flooding.
     * Each new CONNECTED state cancels any pending job and starts fresh.
     */
    private var applySettingsJob: Job? = null

    private fun applySavedSettings(service: AirPodsService) {
        viewModelScope.launch {
            service.connectionState.collect { state ->
                if (state == AacpTransport.ConnectionState.CONNECTED) {
                    applySettingsJob?.cancel()
                    applySettingsJob = viewModelScope.launch {
                        // Wait for connection to stabilize before sending
                        kotlinx.coroutines.delay(3000)
                        if (service.connectionState.value != AacpTransport.ConnectionState.CONNECTED) return@launch
                        service.setConversationalAwareness(_caEnabled.value)
                        kotlinx.coroutines.delay(200)
                        if (service.connectionState.value != AacpTransport.ConnectionState.CONNECTED) return@launch
                        service.setAdaptiveVolume(_avEnabled.value)
                        kotlinx.coroutines.delay(200)
                        if (service.connectionState.value != AacpTransport.ConnectionState.CONNECTED) return@launch
                        service.setEarDetection(_edEnabled.value)
                        kotlinx.coroutines.delay(200)
                        if (service.connectionState.value != AacpTransport.ConnectionState.CONNECTED) return@launch
                        service.setChimeVolume(_chimeVolume.value.toInt())
                        kotlinx.coroutines.delay(200)
                        if (service.connectionState.value != AacpTransport.ConnectionState.CONNECTED) return@launch
                        service.transport.sendControlCommand(0x34, if (_allowOff.value) 0x01 else 0x02)
                        Log.d(TAG, "Saved settings applied (staggered)")

                        // Head tracking: delayed start after 30s to let the L2CAP
                        // channel stabilize with battery/ear data first.
                        if (_headTracking.value) {
                            _headTrackingLoading.value = true
                            Log.d(TAG, "Head tracking: waiting 30s for stable connection...")
                            kotlinx.coroutines.delay(30_000)
                            if (service.connectionState.value != AacpTransport.ConnectionState.CONNECTED) {
                                _headTrackingLoading.value = false
                                return@launch
                            }
                            service.startHeadTracking()
                            _headTrackingLoading.value = false
                            Log.d(TAG, "Head tracking: started after 30s delay")
                        }
                    }
                }
            }
        }
    }

    /**
     * Execute an action on the service if it is bound, otherwise post a connection error.
     */
    private inline fun withService(actionName: String, crossinline action: (AirPodsService) -> Unit) {
        val service = airPodsService
        if (service != null) {
            action(service)
        } else {
            Log.w(TAG, "$actionName failed: service not bound")
            _connectionError.value = "Service not connected. Please wait..."
        }
    }

    private fun saveBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
