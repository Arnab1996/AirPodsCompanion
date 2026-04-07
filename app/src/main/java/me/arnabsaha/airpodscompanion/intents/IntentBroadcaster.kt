package me.arnabsaha.airpodscompanion.intents

import android.content.Context
import android.content.Intent
import me.arnabsaha.airpodscompanion.service.AacpBatteryState
import me.arnabsaha.airpodscompanion.service.EarState

/**
 * Broadcasts public intents for Tasker, MacroDroid, and other automation apps.
 * All intents use the prefix `me.arnabsaha.airpodscompanion.action.`
 */
object IntentBroadcaster {

    const val ACTION_CONNECTED = "me.arnabsaha.airpodscompanion.action.AIRPODS_CONNECTED"
    const val ACTION_DISCONNECTED = "me.arnabsaha.airpodscompanion.action.AIRPODS_DISCONNECTED"
    const val ACTION_BATTERY_CHANGED = "me.arnabsaha.airpodscompanion.action.BATTERY_CHANGED"
    const val ACTION_ANC_MODE_CHANGED = "me.arnabsaha.airpodscompanion.action.ANC_MODE_CHANGED"
    const val ACTION_EAR_STATE_CHANGED = "me.arnabsaha.airpodscompanion.action.EAR_STATE_CHANGED"

    fun broadcastConnected(context: Context, deviceName: String) {
        context.sendBroadcast(Intent(ACTION_CONNECTED).apply {
            putExtra("deviceName", deviceName)
        })
    }

    fun broadcastDisconnected(context: Context) {
        context.sendBroadcast(Intent(ACTION_DISCONNECTED))
    }

    fun broadcastBattery(context: Context, state: AacpBatteryState) {
        context.sendBroadcast(Intent(ACTION_BATTERY_CHANGED).apply {
            putExtra("leftLevel", state.leftLevel)
            putExtra("rightLevel", state.rightLevel)
            putExtra("caseLevel", state.caseLevel)
            putExtra("leftCharging", state.leftCharging)
            putExtra("rightCharging", state.rightCharging)
            putExtra("caseCharging", state.caseCharging)
        })
    }

    fun broadcastAncMode(context: Context, mode: Byte) {
        val modeName = when (mode.toInt() and 0xFF) {
            0x01 -> "off"
            0x02 -> "anc"
            0x03 -> "transparency"
            0x04 -> "adaptive"
            else -> "unknown"
        }
        context.sendBroadcast(Intent(ACTION_ANC_MODE_CHANGED).apply {
            putExtra("mode", modeName)
        })
    }

    fun broadcastEarState(context: Context, state: EarState) {
        context.sendBroadcast(Intent(ACTION_EAR_STATE_CHANGED).apply {
            putExtra("leftInEar", state.leftInEar)
            putExtra("rightInEar", state.rightInEar)
        })
    }
}
