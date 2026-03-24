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

    private val _headTracking = MutableStateFlow(prefs.getBoolean("head_tracking", false))
    /** Head Tracking / Spatial Audio toggle state. */
    val headTracking: StateFlow<Boolean> = _headTracking.asStateFlow()

    private val _chimeVolume = MutableStateFlow(prefs.getFloat("chime_volume", 50f))
    /** Chime volume (0-100). */
    val chimeVolume: StateFlow<Float> = _chimeVolume.asStateFlow()

    private val _stemAction = MutableStateFlow(prefs.getString("stem_action", "Noise Control") ?: "Noise Control")
    /** Stem long-press action label. */
    val stemAction: StateFlow<String> = _stemAction.asStateFlow()

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

    /** Rename the AirPods. Sends the rename packet and updates the bonded device name. */
    fun renameAirPods(newName: String) {
        withService("renameAirPods") { it.renameAirPods(newName) }
    }

    /** Toggle head tracking / spatial audio. Returns the new active state. */
    fun toggleHeadTracking() {
        withService("toggleHeadTracking") { service ->
            val active = service.toggleHeadTracking()
            _headTracking.value = active
            saveBool("head_tracking", active)
        }
    }

    /** Connect to a specific bonded AirPods device. */
    fun connectToDevice(device: BluetoothDevice) {
        withService("connectToDevice") { it.connectToSpecificDevice(device) }
    }

    /** Auto-connect to the most recently used or first available bonded AirPods. */
    fun autoConnect() {
        withService("autoConnect") { it.autoConnect() }
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

    // ── Internal ─────────────────────────────────────────────────

    /**
     * Collect all StateFlows from the service and forward them to our own StateFlows.
     * Called once when the service is bound.
     */
    private fun startCollectingServiceFlows(service: AirPodsService) {
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
                // Surface reconnection failures as transient errors
                if (state == AacpTransport.ConnectionState.DISCONNECTED &&
                    _battery.value != null) {
                    // Was previously connected and lost connection
                    _connectionError.value = "Connection to AirPods lost"
                }
            }
        }
        viewModelScope.launch {
            service.bondedAirPodsList.collect { _bondedAirPodsList.value = it }
        }
    }

    /**
     * Apply all persisted settings to the service on first connection.
     * This replaces the LaunchedEffect(Unit) block that was in DashboardScreen.
     */
    private var settingsApplied = false

    private fun applySavedSettings(service: AirPodsService) {
        viewModelScope.launch {
            service.connectionState.collect { state ->
                if (state == AacpTransport.ConnectionState.CONNECTED && !settingsApplied) {
                    settingsApplied = true
                    // Stagger commands to avoid flooding the L2CAP channel
                    kotlinx.coroutines.delay(500)
                    service.setConversationalAwareness(_caEnabled.value)
                    kotlinx.coroutines.delay(100)
                    service.setAdaptiveVolume(_avEnabled.value)
                    kotlinx.coroutines.delay(100)
                    service.setEarDetection(_edEnabled.value)
                    kotlinx.coroutines.delay(100)
                    service.setChimeVolume(_chimeVolume.value.toInt())
                    Log.d(TAG, "Saved settings applied (staggered)")
                }
                if (state == AacpTransport.ConnectionState.FAILED ||
                    state == AacpTransport.ConnectionState.DISCONNECTED) {
                    settingsApplied = false
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
