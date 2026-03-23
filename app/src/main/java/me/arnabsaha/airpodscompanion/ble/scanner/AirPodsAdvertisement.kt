package me.arnabsaha.airpodscompanion.ble.scanner

/**
 * Parsed AirPods BLE advertisement data from Apple 0x004C manufacturer data.
 *
 * This is extracted from the "Nearby" beacon (type=0x07, length=0x19)
 * that AirPods broadcast continuously when the case lid is open
 * or earbuds are out of the case.
 */
data class AirPodsAdvertisement(
    val address: String,
    val deviceModel: Short,
    val modelName: String,
    val isPaired: Boolean,

    // Battery (0-100, or -1 if unavailable)
    val leftBattery: Int,
    val rightBattery: Int,
    val caseBattery: Int,

    // Charging state
    val isLeftCharging: Boolean,
    val isRightCharging: Boolean,
    val isCaseCharging: Boolean,

    // Ear / Case state
    val primaryIsLeft: Boolean,
    val isInCase: Boolean,
    val isLidOpen: Boolean,

    // Connection state
    val connectionState: Int,

    // Device color
    val color: Int,

    // Signal strength
    val rssi: Int,

    // Raw manufacturer data (for debug / crypto verification)
    val rawManufacturerData: ByteArray,

    // Timestamp
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isLeftInEar: Boolean get() = !isInCase && leftBattery >= 0
    val isRightInEar: Boolean get() = !isInCase && rightBattery >= 0
    val isBothBatteryAvailable: Boolean get() = leftBattery >= 0 && rightBattery >= 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AirPodsAdvertisement) return false
        return address == other.address && deviceModel == other.deviceModel
    }

    override fun hashCode(): Int = address.hashCode() * 31 + deviceModel.hashCode()

    override fun toString(): String =
        "AirPods($modelName addr=$address L=${leftBattery}% R=${rightBattery}% C=${caseBattery}% rssi=$rssi)"
}
