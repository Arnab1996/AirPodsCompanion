package me.arnabsaha.airpodscompanion.wear

/**
 * Shared constants for Wearable Data Layer communication between phone and watch.
 */
object DataPaths {
    const val STATE_PATH = "/airbridge/state"
    const val COMMAND_PATH = "/airbridge/command"

    // State keys (DataItem)
    const val KEY_CONNECTED = "connected"
    const val KEY_DEVICE_NAME = "device_name"
    const val KEY_LEFT_BATTERY = "left_battery"
    const val KEY_RIGHT_BATTERY = "right_battery"
    const val KEY_CASE_BATTERY = "case_battery"
    const val KEY_LEFT_CHARGING = "left_charging"
    const val KEY_RIGHT_CHARGING = "right_charging"
    const val KEY_CASE_CHARGING = "case_charging"
    const val KEY_ANC_MODE = "anc_mode"
    const val KEY_LEFT_IN_EAR = "left_in_ear"
    const val KEY_RIGHT_IN_EAR = "right_in_ear"

    // Command values (MessageClient)
    const val CMD_ANC_OFF = "anc_off"
    const val CMD_ANC_ON = "anc_on"
    const val CMD_ANC_TRANSPARENCY = "anc_transparency"
    const val CMD_ANC_ADAPTIVE = "anc_adaptive"
}
