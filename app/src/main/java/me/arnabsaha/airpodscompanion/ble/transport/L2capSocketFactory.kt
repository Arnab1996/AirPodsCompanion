package me.arnabsaha.airpodscompanion.ble.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid
import android.util.Log
import me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Creates L2CAP sockets for connecting to AirPods.
 *
 * AirPods use L2CAP PSM 0x1001 for the AACP control channel.
 * Uses HiddenApiBypass.newInstance() to create BluetoothSocket with type=3 (L2CAP).
 *
 * Multiple constructor signatures are tried because different Android versions
 * and OEM builds have different BluetoothSocket constructors.
 *
 * Based on LibrePods' proven approach.
 */
object L2capSocketFactory {

    private const val TAG = "L2capSocket"
    private const val SOCKET_TYPE_L2CAP = 3

    /**
     * Create an L2CAP socket to the given bonded device at PSM 0x1001.
     *
     * IMPORTANT: The device must be bonded (paired) via Android Bluetooth settings.
     * The BLE advertising address is NOT the same as the bonded device address.
     */
    @SuppressLint("MissingPermission")
    fun createL2capSocket(device: BluetoothDevice): BluetoothSocket? {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")

        val uuid = ParcelUuid(AacpConstants.L2CAP_UUID)
        val psm = AacpConstants.L2CAP_PSM

        // Multiple constructor signatures to try — different Android/OEM builds
        // have different BluetoothSocket constructors
        val constructorSpecs = listOf(
            // Signature 1: (device, type, auth, encrypt, psm, uuid)
            arrayOf<Any>(device, SOCKET_TYPE_L2CAP, true, true, psm, uuid),
            // Signature 2: (device, type, fd, auth, encrypt, psm, uuid)
            arrayOf<Any>(device, SOCKET_TYPE_L2CAP, 1, true, true, psm, uuid),
            // Signature 3: (type, fd, auth, encrypt, device, psm, uuid)
            arrayOf<Any>(SOCKET_TYPE_L2CAP, 1, true, true, device, psm, uuid),
            // Signature 4: (type, auth, encrypt, device, psm, uuid)
            arrayOf<Any>(SOCKET_TYPE_L2CAP, true, true, device, psm, uuid),
        )

        // Log available constructors for debugging
        val constructors = BluetoothSocket::class.java.declaredConstructors
        Log.d(TAG, "BluetoothSocket has ${constructors.size} constructors:")
        constructors.forEachIndexed { index, ctor ->
            val params = ctor.parameterTypes.joinToString(", ") { it.simpleName }
            Log.d(TAG, "  Constructor $index: ($params)")
        }

        var lastException: Exception? = null

        for ((index, params) in constructorSpecs.withIndex()) {
            try {
                Log.d(TAG, "Trying constructor signature #${index + 1}")
                val socket = HiddenApiBypass.newInstance(
                    BluetoothSocket::class.java, *params
                ) as BluetoothSocket
                Log.d(TAG, "Constructor #${index + 1} succeeded")
                return socket
            } catch (e: Exception) {
                Log.w(TAG, "Constructor #${index + 1} failed: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All ${constructorSpecs.size} constructor signatures failed", lastException)
        return null
    }
}
