package me.arnabsaha.airpodscompanion.protocol.aap

import android.util.Log

/**
 * Constructs Smart Routing packets for the AirPods takeover protocol.
 * Required for head tracking — the AirPods won't stream orientation data
 * unless this device performs a full takeover.
 *
 * Based on LibrePods' AACPManager smart routing implementation.
 */
object SmartRoutingPackets {

    private const val TAG = "SmartRouting"
    private val HEADER = byteArrayOf(0x04, 0x00, 0x04, 0x00)
    private const val OPCODE_SMART_ROUTING: Byte = 0x10

    /**
     * Reverse a MAC address string to bytes.
     * "14:14:7D:EB:E0:65" → [0x65, 0xE0, 0xEB, 0x7D, 0x14, 0x14]
     */
    fun reverseMac(mac: String): ByteArray {
        return mac.split(":").map { it.toInt(16).toByte() }.reversed().toByteArray()
    }

    /**
     * Build the Media Information packet (opcode 0x10).
     * Tells the AirPods about this device and its streaming state.
     */
    fun createMediaInfoPacket(targetMac: String, selfMac: String): ByteArray {
        val reversedMac = reverseMac(targetMac)

        // TLV-encoded payload matching LibrePods' createMediaInformationPacket
        val payload = buildTlvPayload(
            "PlayingApp" to "me.arnabsaha.airpodscompanion",
            "HostStreamingState" to "NO",
            "btAddress" to selfMac,
            "btName" to "Android",
            "otherDevice" to "Android"
        )

        val lengthBytes = byteArrayOf((payload.size and 0xFF).toByte(), ((payload.size shr 8) and 0xFF).toByte())

        return HEADER + byteArrayOf(OPCODE_SMART_ROUTING, 0x00) +
            reversedMac + lengthBytes + payload
    }

    /**
     * Build the Hijack Request packet.
     * Requests audio routing priority from the AirPods.
     */
    fun createHijackRequestPacket(targetMac: String): ByteArray {
        val reversedMac = reverseMac(targetMac)

        val payload = buildTlvPayload(
            "localscore" to "0d",
            "reason" to "Hijackv2",
            "audioRoutingScore" to "",
            "audioRoutingSetOwnershipToFalse" to ""
        )

        val lengthBytes = byteArrayOf((payload.size and 0xFF).toByte(), ((payload.size shr 8) and 0xFF).toByte())

        return HEADER + byteArrayOf(OPCODE_SMART_ROUTING, 0x00) +
            reversedMac + lengthBytes + payload
    }

    /**
     * Build the Show UI packet.
     * Triggers the "Switch to this device?" prompt on other connected devices.
     */
    fun createShowUIPacket(targetMac: String): ByteArray {
        val reversedMac = reverseMac(targetMac)

        val payload = buildTlvPayload(
            "SmartRoutingKeyShowNearbyUI" to "",
            "localscore" to "0d",
            "reason" to "Hijackv2",
            "audioRoutingScore" to "",
            "audioRoutingSetOwnershipToFalse" to ""
        )

        val lengthBytes = byteArrayOf((payload.size and 0xFF).toByte(), ((payload.size shr 8) and 0xFF).toByte())

        return HEADER + byteArrayOf(OPCODE_SMART_ROUTING, 0x00) +
            reversedMac + lengthBytes + payload
    }

    /**
     * Build a simple TLV (Type-Length-Value) payload from key-value pairs.
     * LibrePods uses a specific encoding: length byte + 0x00 + 0x09 + key_string + value_encoding
     */
    private fun buildTlvPayload(vararg pairs: Pair<String, String>): ByteArray {
        val result = mutableListOf<Byte>()

        // Payload count header
        result.add(0x01)
        result.add(pairs.size.toByte())

        for ((key, value) in pairs) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val valueBytes = value.toByteArray(Charsets.UTF_8)

            // Key: length + type(0x09) + string
            result.add(keyBytes.size.toByte())
            result.add(0x00)
            result.add(0x09)
            result.addAll(keyBytes.toList())

            // Value: depends on whether it's empty or has content
            if (valueBytes.isNotEmpty()) {
                result.add(valueBytes.size.toByte())
                result.add(0x00)
                result.add(0x09)
                result.addAll(valueBytes.toList())
            } else {
                // Empty value — just a marker
                result.add(0x00)
            }
        }

        return result.toByteArray()
    }
}
