package me.arnabsaha.airpodscompanion.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import me.arnabsaha.airpodscompanion.service.AacpBatteryState
import me.arnabsaha.airpodscompanion.service.EarState

/**
 * Sends AirPods state to the Wear OS companion via the Wearable Data Layer.
 * Call [syncState] whenever battery, ANC, ear, or connection state changes.
 */
object WearDataSender {

    private const val TAG = "WearDataSender"
    private const val STATE_PATH = "/airbridge/state"
    private const val COMMAND_PATH = "/airbridge/command"

    fun syncState(
        context: Context,
        connected: Boolean,
        deviceName: String?,
        battery: AacpBatteryState?,
        ancMode: Byte,
        earState: EarState
    ) {
        try {
            val req = PutDataMapRequest.create(STATE_PATH).apply {
                dataMap.putBoolean("connected", connected)
                dataMap.putString("device_name", deviceName ?: "AirPods Pro")
                dataMap.putInt("left_battery", battery?.leftLevel ?: -1)
                dataMap.putInt("right_battery", battery?.rightLevel ?: -1)
                dataMap.putInt("case_battery", battery?.caseLevel ?: -1)
                dataMap.putBoolean("left_charging", battery?.leftCharging == true)
                dataMap.putBoolean("right_charging", battery?.rightCharging == true)
                dataMap.putBoolean("case_charging", battery?.caseCharging == true)
                dataMap.putInt("anc_mode", ancMode.toInt() and 0xFF)
                dataMap.putBoolean("left_in_ear", earState.leftInEar)
                dataMap.putBoolean("right_in_ear", earState.rightInEar)
                // Force unique — DataClient deduplicates identical data items
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val putReq = req.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(putReq)
            Log.d(TAG, "Synced to watch: connected=$connected L=${battery?.leftLevel} R=${battery?.rightLevel} ANC=$ancMode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync to watch: ${e.message}")
        }
    }

    /**
     * Register a message listener to receive commands from the watch.
     * Returns a lambda to call for cleanup.
     */
    fun listenForCommands(context: Context, onCommand: (String) -> Unit): () -> Unit {
        val listener = com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener { event ->
            if (event.path == COMMAND_PATH) {
                val cmd = String(event.data)
                Log.d(TAG, "Command from watch: $cmd")
                onCommand(cmd)
            }
        }
        Wearable.getMessageClient(context).addListener(listener)
        return { Wearable.getMessageClient(context).removeListener(listener) }
    }
}
