package me.arnabsaha.airpodscompanion.protocol.aap

import android.util.Log
import me.arnabsaha.airpodscompanion.protocol.constants.AacpConstants

/**
 * Encodes and decodes AACP (Apple Audio Control Protocol) packets.
 *
 * All AACP packets share the structure:
 *   [0-3] = Header: 04 00 04 00 (fixed)
 *   [4]   = Opcode
 *   [5]   = Secondary identifier (depends on opcode)
 *   [6+]  = Payload
 *
 * Exception: The handshake packet uses header 00 00 04 00.
 */
object AacpPacketCodec {

    private const val TAG = "AacpCodec"
    val HEADER = AacpConstants.HEADER  // 04 00 04 00

    /**
     * Decoded AACP packet.
     */
    data class AacpPacket(
        val opcode: Byte,
        val secondaryId: Byte,
        val payload: ByteArray,
        val rawBytes: ByteArray
    ) {
        override fun toString(): String {
            val hex = rawBytes.joinToString(" ") { "%02X".format(it) }
            return "AACP(op=0x%02X, id=0x%02X, len=${payload.size}, raw=[$hex])".format(opcode, secondaryId)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AacpPacket) return false
            return rawBytes.contentEquals(other.rawBytes)
        }

        override fun hashCode(): Int = rawBytes.contentHashCode()
    }

    /**
     * Decode raw bytes from the L2CAP socket into an AacpPacket.
     * Returns null if the data doesn't match the AACP header.
     */
    fun decode(data: ByteArray): AacpPacket? {
        if (data.size < 6) {
            Log.v(TAG, "Packet too short: ${data.size} bytes")
            return null
        }

        // Verify header (04 00 04 00) — also accept handshake header (00 00 04 00)
        val isDataPacket = data[0] == 0x04.toByte() && data[1] == 0x00.toByte() &&
            data[2] == 0x04.toByte() && data[3] == 0x00.toByte()
        val isHandshake = data[0] == 0x00.toByte() && data[1] == 0x00.toByte() &&
            data[2] == 0x04.toByte() && data[3] == 0x00.toByte()

        if (!isDataPacket && !isHandshake) {
            Log.v(TAG, "Unknown header: %02X %02X %02X %02X".format(data[0], data[1], data[2], data[3]))
            return null
        }

        val opcode = data[4]
        val secondaryId = if (data.size > 5) data[5] else 0x00
        val payload = if (data.size > 6) data.copyOfRange(6, data.size) else ByteArray(0)

        return AacpPacket(
            opcode = opcode,
            secondaryId = secondaryId,
            payload = payload,
            rawBytes = data.clone()
        )
    }

    /**
     * Encode a control command packet.
     * Format: [HEADER] [0x09] [0x00] [commandId] [value(s)]
     */
    fun encodeControlCommand(commandId: Byte, vararg values: Byte): ByteArray {
        return HEADER + byteArrayOf(0x09, 0x00, commandId) + values
    }

    /**
     * Encode a generic AACP packet with the given opcode and payload.
     * Format: [HEADER] [opcode] [0x00] [payload]
     */
    fun encodePacket(opcode: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        return HEADER + byteArrayOf(opcode, 0x00) + payload
    }

    /**
     * Check if raw data starts with the AACP header.
     */
    fun isAacpPacket(data: ByteArray): Boolean {
        if (data.size < 4) return false
        return (data[0] == 0x04.toByte() && data[1] == 0x00.toByte() &&
            data[2] == 0x04.toByte() && data[3] == 0x00.toByte()) ||
            (data[0] == 0x00.toByte() && data[1] == 0x00.toByte() &&
                data[2] == 0x04.toByte() && data[3] == 0x00.toByte())
    }

    /**
     * Pretty-print a byte array as hex for debug logging.
     */
    fun toHex(data: ByteArray): String =
        data.joinToString(" ") { "%02X".format(it) }
}
