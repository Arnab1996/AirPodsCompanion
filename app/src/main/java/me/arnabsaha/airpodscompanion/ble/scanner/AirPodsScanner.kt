package me.arnabsaha.airpodscompanion.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.arnabsaha.airpodscompanion.protocol.constants.AppleBleConstants

/**
 * BLE scanner that detects AirPods via Apple manufacturer data (0x004C).
 *
 * Uses ScanFilter to match only Apple "Nearby" advertisements (type=0x07, length=0x19),
 * then parses them via ManufacturerDataParser to extract battery, model, and state.
 *
 * Battery optimization: starts with BALANCED mode for fast discovery, then
 * drops to LOW_POWER after 30 seconds to reduce battery drain.
 *
 * Emits the latest advertisement per device address via StateFlow.
 */
class AirPodsScanner(context: Context) {

    companion object {
        private const val TAG = "AirPodsScanner"
        private const val SCAN_REPORT_DELAY_MS = 500L
        private const val DOWNGRADE_DELAY_MS = 30_000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val handler = Handler(Looper.getMainLooper())
    private var downgradeRunnable: Runnable? = null

    private val parser = ManufacturerDataParser()

    // All detected AirPods, keyed by BLE address
    private val _detectedDevices = MutableStateFlow<Map<String, AirPodsAdvertisement>>(emptyMap())
    val detectedDevices: StateFlow<Map<String, AirPodsAdvertisement>> = _detectedDevices.asStateFlow()

    // The "best" nearby AirPods (strongest RSSI)
    private val _nearestAirPods = MutableStateFlow<AirPodsAdvertisement?>(null)
    val nearestAirPods: StateFlow<AirPodsAdvertisement?> = _nearestAirPods.asStateFlow()

    @Volatile
    private var isScanning = false

    /**
     * ScanFilter: match manufacturer data starting with type=0x07, length=0x19
     * for company ID 0x004C (Apple).
     */
    private val scanFilters = listOf(
        ScanFilter.Builder()
            .setManufacturerData(
                AppleBleConstants.APPLE_COMPANY_ID,
                byteArrayOf(0x07, 0x19),               // type=Nearby, length=25
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // exact match on both bytes
            )
            .build()
    )

    private fun buildScanSettings(mode: Int): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(mode)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(SCAN_REPORT_DELAY_MS)
            .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val address = result.device.address
        val rssi = result.rssi

        // Get Apple manufacturer data directly from ScanRecord API
        val appleData = scanRecord.getManufacturerSpecificData(AppleBleConstants.APPLE_COMPANY_ID)
            ?: return

        val advertisement = parser.parse(appleData, address, rssi) ?: return

        Log.d(TAG, "Detected: $advertisement")

        // Simple update — keep all addresses but let the UI sort by RSSI.
        // The user's AirPods will have the strongest signal.
        _detectedDevices.update { current -> current + (address to advertisement) }
        _nearestAirPods.value = _detectedDevices.value.values.maxByOrNull { it.rssi }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        // Check BT adapter is enabled before trying to scan
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not enabled, cannot start scan")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return
        }

        // Start with BALANCED for fast initial discovery
        Log.d(TAG, "Starting BLE scan (BALANCED mode)...")
        try {
            scanner.startScan(scanFilters, buildScanSettings(ScanSettings.SCAN_MODE_BALANCED), scanCallback)
            isScanning = true

            // After 30 seconds, downgrade to LOW_POWER to save battery
            downgradeRunnable = Runnable { downgradeToLowPower() }
            handler.postDelayed(downgradeRunnable!!, DOWNGRADE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan: ${e.message}")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun downgradeToLowPower() {
        if (!isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        Log.d(TAG, "Downgrading to LOW_POWER scan mode")
        try {
            scanner.stopScan(scanCallback)
            scanner.startScan(scanFilters, buildScanSettings(ScanSettings.SCAN_MODE_LOW_POWER), scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error downgrading scan: ${e.message}")
            isScanning = false
        }
        downgradeRunnable = null
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        Log.d(TAG, "Stopping BLE scan")
        // Cancel pending downgrade
        downgradeRunnable?.let { handler.removeCallbacks(it) }
        downgradeRunnable = null
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
        isScanning = false
    }

    fun isScanning(): Boolean = isScanning

    /**
     * Clear stale entries older than the given threshold.
     */
    fun pruneStaleDevices(maxAgeMs: Long = 30_000) {
        val now = System.currentTimeMillis()
        val current = _detectedDevices.value.toMutableMap()
        val stale = current.entries.filter { now - it.value.timestampMs > maxAgeMs }
        stale.forEach { current.remove(it.key) }
        if (stale.isNotEmpty()) {
            _detectedDevices.value = current
            _nearestAirPods.value = current.values.maxByOrNull { it.rssi }
        }
    }
}
