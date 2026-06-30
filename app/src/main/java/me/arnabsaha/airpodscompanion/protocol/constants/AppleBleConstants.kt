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

    // Device Model IDs (big-endian in the advertisement). Verified against CapOd's model set.
    object DeviceModel {
        // ── AirPods ──
        const val AIRPODS_1: Short = 0x0220
        const val AIRPODS_2: Short = 0x0F20
        const val AIRPODS_3: Short = 0x1320
        const val AIRPODS_4: Short = 0x1920
        const val AIRPODS_4_ANC: Short = 0x1B20
        const val AIRPODS_PRO: Short = 0x0E20
        const val AIRPODS_PRO_2: Short = 0x1420
        const val AIRPODS_PRO_2_USB_C: Short = 0x2420
        const val AIRPODS_PRO_3: Short = 0x2720
        const val AIRPODS_MAX: Short = 0x0A20
        const val AIRPODS_MAX_USB_C: Short = 0x1F20
        const val AIRPODS_MAX_2: Short = 0x2D20

        // ── Beats ──
        const val POWERBEATS_3: Short = 0x0320
        const val POWERBEATS_4: Short = 0x0D20
        const val POWERBEATS_PRO: Short = 0x0B20
        const val POWERBEATS_PRO_2: Short = 0x1D20
        const val BEATS_X: Short = 0x0520
        const val BEATS_SOLO_3: Short = 0x0620
        const val BEATS_SOLO_4: Short = 0x2520
        const val BEATS_SOLO_PRO: Short = 0x0C20
        const val BEATS_SOLO_BUDS: Short = 0x2620
        const val BEATS_STUDIO_BUDS: Short = 0x1120
        const val BEATS_STUDIO_BUDS_PLUS: Short = 0x1620
        const val BEATS_STUDIO_PRO: Short = 0x1720
        const val BEATS_FLEX: Short = 0x1020
        const val BEATS_FIT_PRO: Short = 0x1220

        fun nameFor(model: Short): String = when (model) {
            AIRPODS_1 -> "AirPods (1st gen)"
            AIRPODS_2 -> "AirPods (2nd gen)"
            AIRPODS_3 -> "AirPods (3rd gen)"
            AIRPODS_4 -> "AirPods 4"
            AIRPODS_4_ANC -> "AirPods 4 (ANC)"
            AIRPODS_PRO -> "AirPods Pro"
            AIRPODS_PRO_2 -> "AirPods Pro 2"
            AIRPODS_PRO_2_USB_C -> "AirPods Pro 2 (USB-C)"
            AIRPODS_PRO_3 -> "AirPods Pro 3"
            AIRPODS_MAX -> "AirPods Max"
            AIRPODS_MAX_USB_C -> "AirPods Max (USB-C)"
            AIRPODS_MAX_2 -> "AirPods Max (2nd gen)"
            POWERBEATS_3 -> "Powerbeats 3"
            POWERBEATS_4 -> "Powerbeats 4"
            POWERBEATS_PRO -> "Powerbeats Pro"
            POWERBEATS_PRO_2 -> "Powerbeats Pro 2"
            BEATS_X -> "Beats X"
            BEATS_SOLO_3 -> "Beats Solo 3"
            BEATS_SOLO_4 -> "Beats Solo 4"
            BEATS_SOLO_PRO -> "Beats Solo Pro"
            BEATS_SOLO_BUDS -> "Beats Solo Buds"
            BEATS_STUDIO_BUDS -> "Beats Studio Buds"
            BEATS_STUDIO_BUDS_PLUS -> "Beats Studio Buds+"
            BEATS_STUDIO_PRO -> "Beats Studio Pro"
            BEATS_FLEX -> "Beats Flex"
            BEATS_FIT_PRO -> "Beats Fit Pro"
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
