package me.arnabsaha.airpodscompanion.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
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
 * Emits the latest advertisement per device address via StateFlow.
 */
class AirPodsScanner(context: Context) {

    companion object {
        private const val TAG = "AirPodsScanner"
        private const val SCAN_REPORT_DELAY_MS = 500L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

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

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
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

        // Deduplicate by model: AirPods use rotating BLE addresses (RPA),
        // so the same device shows up with different addresses.
        // Keep only the entry with the strongest RSSI per model.
        _detectedDevices.update { current ->
            val modelKey = advertisement.deviceModel.toString()
            val existing = current.values.find {
                it.deviceModel == advertisement.deviceModel && it.address != address
            }
            val updated = current.toMutableMap()
            if (existing != null && advertisement.rssi > existing.rssi) {
                // Remove the weaker duplicate
                updated.remove(existing.address)
            } else if (existing != null && advertisement.rssi <= existing.rssi) {
                // Update the existing entry's timestamp but keep its address
                updated[existing.address] = existing.copy(timestampMs = System.currentTimeMillis())
                return@update updated
            }
            updated[address] = advertisement
            updated
        }
        _nearestAirPods.value = _detectedDevices.value.values.maxByOrNull { it.rssi }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        if (bleScanner == null) {
            Log.e(TAG, "BLE scanner not available (Bluetooth disabled?)")
            return
        }

        Log.d(TAG, "Starting BLE scan for AirPods...")
        bleScanner.startScan(scanFilters, scanSettings, scanCallback)
        isScanning = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return

        Log.d(TAG, "Stopping BLE scan")
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: $e")
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
