package me.arnabsaha.airpodscompanion.protocol.constants

/**
 * Apple BLE constants for AirPods manufacturer data parsing.
 * Company ID 0x004C (76 decimal) — Apple Inc.
 *
 * Manufacturer data structure (after company ID bytes):
 * [0]   = Type (0x07 = Nearby)
 * [1]   = Length (0x19 = 25)
 * [2]   = Paired status (0x01 = paired)
 * [3-4] = Device model (big-endian)
 * [5]   = Status bits (bit 5 = primary left, bit 6 = in case)
 * [6]   = Pod battery (high nibble = left, low nibble = right)
 * [7]   = Case battery (low nibble) + flags (high nibble)
 * [8]   = Lid/flags (bit 3 = lid state)
 * [9]   = Color
 * [10]  = Connection state
 */
object AppleBleConstants {
    const val APPLE_COMPANY_ID = 0x004C  // 76 decimal

    // Scan filter: first two bytes of manufacturer data after company ID
    const val NEARBY_TYPE: Byte = 0x07
    const val NEARBY_LENGTH: Byte = 0x19  // 25

    // Device Model IDs (big-endian in the advertisement)
    object DeviceModel {
        const val AIRPODS_1: Short = 0x0220
        const val AIRPODS_2: Short = 0x0F20
        const val AIRPODS_3: Short = 0x1320
        const val AIRPODS_4: Short = 0x1920
        const val AIRPODS_4_ANC: Short = 0x1B20
        const val AIRPODS_PRO: Short = 0x0E20
        const val AIRPODS_PRO_2: Short = 0x1420
        const val AIRPODS_PRO_2_USB_C: Short = 0x2420
        const val AIRPODS_MAX: Short = 0x0A20
        const val AIRPODS_MAX_USB_C: Short = 0x1F20

        fun nameFor(model: Short): String = when (model) {
            AIRPODS_1 -> "AirPods (1st gen)"
            AIRPODS_2 -> "AirPods (2nd gen)"
            AIRPODS_3 -> "AirPods (3rd gen)"
            AIRPODS_4 -> "AirPods 4"
            AIRPODS_4_ANC -> "AirPods 4 (ANC)"
            AIRPODS_PRO -> "AirPods Pro"
            AIRPODS_PRO_2 -> "AirPods Pro 2"
            AIRPODS_PRO_2_USB_C -> "AirPods Pro 2 (USB-C)"
            AIRPODS_MAX -> "AirPods Max"
            AIRPODS_MAX_USB_C -> "AirPods Max (USB-C)"
            else -> "Unknown AirPods (0x${"%04X".format(model)})"
        }

        fun isPro2(model: Short): Boolean =
            model == AIRPODS_PRO_2 || model == AIRPODS_PRO_2_USB_C
    }

    // Battery encoding
    const val BATTERY_UNKNOWN = 0x0F
    fun batteryToPercent(raw: Int): Int = when {
        raw == BATTERY_UNKNOWN -> -1
        raw in 0x0A..0x0E -> 100
        raw in 0..9 -> raw * 10
        else -> -1
    }

    // Connection state values (byte [10] in manufacturer data)
    object ConnectionState {
        const val DISCONNECTED = 0x00
        const val IDLE = 0x04
        const val MUSIC = 0x05
        const val CALL = 0x06
    }

    // Color values (byte [9])
    object DeviceColor {
        const val WHITE = 0x00
        const val BLACK = 0x01
        const val RED = 0x02
        const val BLUE = 0x03
        const val PINK = 0x04
        const val ORANGE = 0x05
    }
}
