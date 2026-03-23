package me.arnabsaha.airpodscompanion.protocol.constants

import java.util.UUID

/**
 * AACP (Apple Audio Control Protocol) constants.
 * Derived from LibrePods reverse engineering of the AirPods Pro 2 protocol.
 *
 * All AACP packets share a 4-byte header: 04 00 04 00
 * Byte [4] is the opcode.
 */
object AacpConstants {
    // AACP fixed header for all data packets
    val HEADER = byteArrayOf(0x04, 0x00, 0x04, 0x00)

    // Handshake packet (sent first after L2CAP connection)
    val HANDSHAKE = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    // Enable advanced features (Adaptive Transparency, CA during audio)
    val SET_FEATURE_FLAGS = HEADER + byteArrayOf(
        0x4D, 0x00, 0xD7.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    // Request all notifications
    val REQUEST_NOTIFICATIONS = HEADER + byteArrayOf(
        0x0F, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    // Request proximity keys (IRK + ENC_KEY)
    val REQUEST_PROXIMITY_KEYS = HEADER + byteArrayOf(0x30, 0x00, 0x05, 0x00)

    // Enable EQ setting
    val ENABLE_EQ = HEADER + byteArrayOf(
        0x29, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    // L2CAP connection details
    const val L2CAP_PSM = 0x1001
    val L2CAP_UUID: UUID = UUID.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

    // ATT connection UUID
    val ATT_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    // ATT handles
    const val ATT_HANDLE_TRANSPARENCY: Short = 0x18
    const val ATT_HANDLE_TRANSPARENCY_CCCD: Short = 0x19
    const val ATT_HANDLE_LOUD_SOUND_REDUCTION: Short = 0x1B
    const val ATT_HANDLE_LOUD_SOUND_REDUCTION_CCCD: Short = 0x1C
    const val ATT_HANDLE_HEARING_AID: Short = 0x2A
    const val ATT_HANDLE_HEARING_AID_CCCD: Short = 0x2B
}

/** AACP opcodes — byte [4] in every AACP packet */
object AacpOpcode {
    const val BATTERY_INFO: Byte = 0x04
    const val EAR_DETECTION: Byte = 0x06
    const val CONTROL_COMMAND: Byte = 0x09
    const val AUDIO_SOURCE: Byte = 0x0E
    const val REQUEST_NOTIFICATIONS: Byte = 0x0F
    const val SMART_ROUTING: Byte = 0x10
    const val SEND_CONNECTED_MAC: Byte = 0x14
    const val HEAD_TRACKING: Byte = 0x17
    const val STEM_PRESS: Byte = 0x19
    const val DEVICE_INFO: Byte = 0x1D
    const val RENAME: Byte = 0x1E
    const val CONNECTED_DEVICES: Byte = 0x2E
    const val PROXIMITY_KEYS_REQ: Byte = 0x30
    const val PROXIMITY_KEYS_RSP: Byte = 0x31
    const val CONVERSATION_AWARENESS: Byte = 0x4B
    const val SET_FEATURE_FLAGS: Byte = 0x4D
    const val EQ_DATA: Byte = 0x53
}

/** Control command identifiers — second payload byte when opcode = 0x09 */
object ControlCommandId {
    const val MIC_MODE: Byte = 0x01
    const val OWNS_CONNECTION: Byte = 0x06
    const val EAR_DETECTION: Byte = 0x0A
    const val LISTENING_MODE: Byte = 0x0D
    const val VOICE_TRIGGER: Byte = 0x12
    const val SINGLE_CLICK: Byte = 0x14
    const val DOUBLE_CLICK: Byte = 0x15
    const val CLICK_HOLD: Byte = 0x16
    const val DOUBLE_CLICK_INTERVAL: Byte = 0x17
    const val CLICK_HOLD_INTERVAL: Byte = 0x18
    const val LISTENING_MODE_CONFIG: Byte = 0x1A
    const val ONE_BUD_ANC: Byte = 0x1B
    const val CROWN_ROTATION_DIR: Byte = 0x1C
    const val CHIME_VOLUME: Byte = 0x1F
    const val AUTO_CONNECT: Byte = 0x20
    const val VOLUME_SWIPE_INTERVAL: Byte = 0x23
    const val VOLUME_SWIPE_MODE: Byte = 0x25
    const val ADAPTIVE_VOLUME: Byte = 0x26
    const val CONVERSATION_AWARENESS: Byte = 0x28
    const val HEARING_AID: Byte = 0x2C
    const val AUTO_ANC_STRENGTH: Byte = 0x2E
    const val IN_CASE_TONE: Byte = 0x31
    const val HEARING_ASSIST: Byte = 0x33
    const val ALLOW_OFF_OPTION: Byte = 0x34
    const val SLEEP_DETECTION: Byte = 0x35
}

/** Noise control mode values for LISTENING_MODE command */
object NoiseControlMode {
    const val OFF: Byte = 0x01
    const val NOISE_CANCELLATION: Byte = 0x02
    const val TRANSPARENCY: Byte = 0x03
    const val ADAPTIVE: Byte = 0x04
}

/** Battery component identifiers */
object BatteryComponent {
    const val RIGHT = 2
    const val LEFT = 4
    const val CASE = 8
}

/** Battery status values */
object BatteryStatus {
    const val CHARGING = 1
    const val NOT_CHARGING = 2
    const val DISCONNECTED = 4
}

/** Ear detection pod status */
object EarStatus {
    const val IN_EAR: Byte = 0x00
    const val OUT_OF_EAR: Byte = 0x01
    const val IN_CASE: Byte = 0x02
}

/** Stem press types */
object StemPressType {
    const val SINGLE: Byte = 0x05
    const val DOUBLE: Byte = 0x06
    const val TRIPLE: Byte = 0x07
    const val LONG: Byte = 0x08
}

/** Stem bud identifiers */
object StemBud {
    const val LEFT: Byte = 0x01
    const val RIGHT: Byte = 0x02
}
