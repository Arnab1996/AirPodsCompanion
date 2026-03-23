package me.arnabsaha.airpodscompanion.ble.scanner

import android.util.Log
import me.arnabsaha.airpodscompanion.protocol.constants.AppleBleConstants

/**
 * Parses Apple 0x004C manufacturer-specific BLE advertisement data
 * into structured AirPodsAdvertisement objects.
 *
 * Manufacturer data layout (after the 2-byte company ID 0x4C 0x00):
 * [0]   = Type (0x07 = Nearby proximity beacon)
 * [1]   = Length (0x19 = 25 bytes)
 * [2]   = Paired status (0x01 = paired to a device)
 * [3-4] = Device model (big-endian, e.g. 0x1420 = AirPods Pro 2)
 * [5]   = Status bits: bit5 = primary is left, bit6 = buds in case
 * [6]   = Pod battery: high nibble = left, low nibble = right
 * [7]   = Case battery (low nibble) + charging flags (high nibble)
 * [8]   = Lid open/flags: bit3 = lid state
 * [9]   = Color: 0x00=White, 0x01=Black, etc.
 * [10]  = Connection state: 0x00=Disconnected, 0x04=Idle, 0x05=Music, 0x06=Call
 * [11+] = Encrypted payload (16 bytes, used for RPA verification)
 */
class ManufacturerDataParser {

    companion object {
        private const val TAG = "MfgDataParser"

        // Minimum size: type(1) + length(1) + paired(1) + model(2) + status(1)
        //             + podBatt(1) + caseBatt(1) + lid(1) + color(1) + connState(1) = 11
        private const val MIN_DATA_LENGTH = 11
    }

    /**
     * Parse raw manufacturer data (after company ID extraction) into an AirPodsAdvertisement.
     *
     * @param manufacturerData The bytes after the 0x4C 0x00 company ID
     * @param address BLE device address
     * @param rssi Signal strength
     * @return Parsed advertisement, or null if not a valid AirPods Nearby beacon
     */
    fun parse(manufacturerData: ByteArray, address: String, rssi: Int): AirPodsAdvertisement? {
        if (manufacturerData.size < MIN_DATA_LENGTH) {
            Log.v(TAG, "Data too short: ${manufacturerData.size} bytes")
            return null
        }

        val type = manufacturerData[0].toInt() and 0xFF
        val length = manufacturerData[1].toInt() and 0xFF

        // Must be "Nearby" type (0x07) with length 0x19 (25)
        if (type != 0x07 || length != 0x19) {
            return null
        }

        val isPaired = (manufacturerData[2].toInt() and 0xFF) == 0x01

        // Device model (big-endian)
        val deviceModel = (
            ((manufacturerData[3].toInt() and 0xFF) shl 8) or
            (manufacturerData[4].toInt() and 0xFF)
        ).toShort()

        val modelName = AppleBleConstants.DeviceModel.nameFor(deviceModel)

        // Status bits
        val statusByte = manufacturerData[5].toInt() and 0xFF
        val primaryIsLeft = (statusByte and 0x20) != 0
        val isInCase = (statusByte and 0x40) != 0

        // Pod battery: high nibble = left, low nibble = right
        // BUT if primaryIsLeft is false, the nibbles are swapped
        val podBatteryByte = manufacturerData[6].toInt() and 0xFF
        val rawHighNibble = (podBatteryByte shr 4) and 0x0F
        val rawLowNibble = podBatteryByte and 0x0F
        val rawLeftBattery = if (primaryIsLeft) rawHighNibble else rawLowNibble
        val rawRightBattery = if (primaryIsLeft) rawLowNibble else rawHighNibble

        // Case battery + charging flags
        val caseBatteryByte = manufacturerData[7].toInt() and 0xFF
        val rawCaseBattery = caseBatteryByte and 0x0F
        val chargingFlags = (caseBatteryByte shr 4) and 0x0F

        // Charging: bit 0 = left, bit 1 = right, bit 2 = case
        // But in the high nibble of byte[7], the charging bits work differently:
        // We check bit 7 (0x80 mask on the raw value) for each component.
        // LibrePods uses the high nibble as flags.
        val isLeftCharging = (chargingFlags and 0x01) != 0
        val isRightCharging = (chargingFlags and 0x02) != 0
        val isCaseCharging = (chargingFlags and 0x04) != 0

        // Lid open: byte[8], bit 3 — NOTE: 0 = open, 1 = closed (inverted from naive reading)
        val lidByte = manufacturerData[8].toInt() and 0xFF
        val isLidOpen = (lidByte and 0x08) == 0

        // Color: byte[9]
        val color = if (manufacturerData.size > 9) manufacturerData[9].toInt() and 0xFF else 0

        // Connection state: byte[10]
        val connectionState = if (manufacturerData.size > 10) manufacturerData[10].toInt() and 0xFF else 0

        // Convert battery nibbles to percentages
        val leftBattery = AppleBleConstants.batteryToPercent(rawLeftBattery)
        val rightBattery = AppleBleConstants.batteryToPercent(rawRightBattery)
        val caseBattery = AppleBleConstants.batteryToPercent(rawCaseBattery)

        return AirPodsAdvertisement(
            address = address,
            deviceModel = deviceModel,
            modelName = modelName,
            isPaired = isPaired,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            primaryIsLeft = primaryIsLeft,
            isInCase = isInCase,
            isLidOpen = isLidOpen,
            connectionState = connectionState,
            color = color,
            rssi = rssi,
            rawManufacturerData = manufacturerData
        )
    }

    /**
     * Extract Apple manufacturer data from a raw BLE scan record.
     * Walks the AD structure to find type 0xFF with company ID 0x004C.
     *
     * @return The manufacturer data bytes AFTER the company ID, or null if not found.
     */
    fun extractAppleDataFromScanRecord(scanRecord: ByteArray): ByteArray? {
        var i = 0
        while (i < scanRecord.size - 1) {
            val adLength = scanRecord[i].toInt() and 0xFF
            if (adLength == 0) break
            if (i + adLength >= scanRecord.size) break

            val adType = scanRecord[i + 1].toInt() and 0xFF

            // 0xFF = Manufacturer Specific Data
            if (adType == 0xFF && adLength >= 4) {
                // Company ID is little-endian: 0x4C 0x00 = Apple
                val companyId = (scanRecord[i + 2].toInt() and 0xFF) or
                    ((scanRecord[i + 3].toInt() and 0xFF) shl 8)

                if (companyId == AppleBleConstants.APPLE_COMPANY_ID) {
                    // Return data after the 2-byte company ID
                    return scanRecord.copyOfRange(i + 4, i + 1 + adLength)
                }
            }
            i += adLength + 1
        }
        return null
    }
}
