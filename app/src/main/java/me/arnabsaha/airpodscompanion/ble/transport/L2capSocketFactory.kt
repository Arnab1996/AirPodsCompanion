package me.arnabsaha.airpodscompanion.ble.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Creates L2CAP sockets for connecting to AirPods.
 *
 * Strategy (ordered by preference):
 * 1. Public API: BluetoothDevice.createL2capChannel() (API 29+, for BLE CoC)
 *    - May not work for classic BT L2CAP, but try first for Play Store compliance
 * 2. RFCOMM fallback with AAP UUID
 *    - Works on some devices where the UUID maps to the L2CAP PSM
 * 3. HiddenApiBypass reflection (last resort)
 *    - Multiple constructor signatures for different Android/OEM builds
 */
object L2capSocketFactory {

    private const val TAG = "L2capSocket"
    private const val SOCKET_TYPE_L2CAP = 3
    private var cachedSignatureIndex = -1 // Cache working constructor to avoid re-probing

    @SuppressLint("MissingPermission")
    fun createL2capSocket(device: BluetoothDevice): BluetoothSocket? {
        // Strategy 1: Public API (Play Store safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val socket = device.createL2capChannel(AacpConstants.L2CAP_PSM)
                Log.d(TAG, "Created socket via public createL2capChannel()")
                return socket
            } catch (e: Exception) {
                Log.d(TAG, "Public API failed: ${e.message}, trying RFCOMM fallback")
            }
        }

        // Strategy 2: RFCOMM with AAP UUID
        try {
            val socket = device.createRfcommSocketToServiceRecord(AacpConstants.L2CAP_UUID)
            Log.d(TAG, "Created socket via RFCOMM UUID fallback")
            return socket
        } catch (e: Exception) {
            Log.d(TAG, "RFCOMM fallback failed: ${e.message}, trying reflection")
        }

        // Strategy 3: HiddenApiBypass reflection (for OEMs where above fail)
        return createViaReflection(device)
    }

    private fun createViaReflection(device: BluetoothDevice): BluetoothSocket? {
        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        } catch (e: Exception) {
            Log.w(TAG, "HiddenApiBypass not available: ${e.message}")
            return null
        }

        val uuid = ParcelUuid(AacpConstants.L2CAP_UUID)
        val psm = AacpConstants.L2CAP_PSM

        val constructorSpecs = listOf(
            arrayOf<Any>(device, SOCKET_TYPE_L2CAP, true, true, psm, uuid),
            arrayOf<Any>(device, SOCKET_TYPE_L2CAP, 1, true, true, psm, uuid),
            arrayOf<Any>(SOCKET_TYPE_L2CAP, 1, true, true, device, psm, uuid),
            arrayOf<Any>(SOCKET_TYPE_L2CAP, true, true, device, psm, uuid),
        )

        // Use cached signature if we've found one before
        if (cachedSignatureIndex >= 0 && cachedSignatureIndex < constructorSpecs.size) {
            try {
                val socket = HiddenApiBypass.newInstance(
                    BluetoothSocket::class.java, *constructorSpecs[cachedSignatureIndex]
                ) as BluetoothSocket
                Log.d(TAG, "Created socket via cached signature #${cachedSignatureIndex + 1}")
                return socket
            } catch (_: Exception) {
                cachedSignatureIndex = -1 // Cache invalidated
            }
        }

        var lastException: Exception? = null
        for ((index, params) in constructorSpecs.withIndex()) {
            try {
                val socket = HiddenApiBypass.newInstance(
                    BluetoothSocket::class.java, *params
                ) as BluetoothSocket
                cachedSignatureIndex = index // Cache for next time
                Log.d(TAG, "Reflection signature #${index + 1} succeeded")
                return socket
            } catch (e: Exception) {
                Log.w(TAG, "Reflection #${index + 1} failed: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All socket creation strategies failed", lastException)
        return null
    }
}
