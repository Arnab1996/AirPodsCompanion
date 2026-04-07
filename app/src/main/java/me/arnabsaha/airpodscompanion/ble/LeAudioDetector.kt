package me.arnabsaha.airpodscompanion.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid

/**
 * Detects LE Audio capability by checking device UUIDs for
 * Published Audio Capabilities (PACS) and Audio Stream Control (ASCS) services.
 */
object LeAudioDetector {

    private val PACS_UUID = ParcelUuid.fromString("00001850-0000-1000-8000-00805f9b34fb")
    private val ASCS_UUID = ParcelUuid.fromString("0000184e-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    fun detect(device: BluetoothDevice): LeAudioCapability {
        val uuids = device.uuids ?: return LeAudioCapability(hasPacs = false, hasAscs = false)
        val hasPacs = uuids.contains(PACS_UUID)
        val hasAscs = uuids.contains(ASCS_UUID)
        return LeAudioCapability(hasPacs, hasAscs)
    }
}

data class LeAudioCapability(
    val hasPacs: Boolean,
    val hasAscs: Boolean
) {
    val isLeAudioSupported: Boolean get() = hasPacs || hasAscs
    val displayText: String get() = if (isLeAudioSupported) "LE Audio: Supported" else "Classic Audio Only"
}
