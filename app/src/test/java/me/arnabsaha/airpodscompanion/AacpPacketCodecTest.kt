package me.arnabsaha.airpodscompanion

import me.arnabsaha.airpodscompanion.protocol.aap.AacpPacketCodec
import org.junit.Assert.*
import org.junit.Test

class AacpPacketCodecTest {

    @Test
    fun `decode standard AACP packet`() {
        val data = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x02)
        val packet = AacpPacketCodec.decode(data)
        assertNotNull(packet)
        assertEquals(0x09.toByte(), packet!!.opcode)
        assertEquals(0x00.toByte(), packet.secondaryId)
        assertEquals(2, packet.payload.size)
        assertEquals(0x0D.toByte(), packet.payload[0])
        assertEquals(0x02.toByte(), packet.payload[1])
    }

    @Test
    fun `decode handshake packet`() {
        val data = byteArrayOf(0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00)
        val packet = AacpPacketCodec.decode(data)
        assertNotNull(packet)
        assertEquals(0x01.toByte(), packet!!.opcode)
    }

    @Test
    fun `decode returns null for too-short data`() {
        assertNull(AacpPacketCodec.decode(byteArrayOf(0x04, 0x00, 0x04)))
    }

    @Test
    fun `decode returns null for unknown header`() {
        assertNull(AacpPacketCodec.decode(byteArrayOf(0x05, 0x00, 0x04, 0x00, 0x09, 0x00)))
    }

    @Test
    fun `encodeControlCommand produces correct bytes`() {
        val result = AacpPacketCodec.encodeControlCommand(0x0D, 0x02)
        assertEquals(8, result.size)
        // Header
        assertEquals(0x04.toByte(), result[0])
        assertEquals(0x00.toByte(), result[1])
        assertEquals(0x04.toByte(), result[2])
        assertEquals(0x00.toByte(), result[3])
        // Opcode 0x09
        assertEquals(0x09.toByte(), result[4])
        assertEquals(0x00.toByte(), result[5])
        // Command ID + value
        assertEquals(0x0D.toByte(), result[6])
        assertEquals(0x02.toByte(), result[7])
    }

    @Test
    fun `encodePacket produces correct bytes`() {
        val result = AacpPacketCodec.encodePacket(0x0F, byteArrayOf(0xFF.toByte()))
        assertEquals(7, result.size)
        assertEquals(0x0F.toByte(), result[4])
        assertEquals(0x00.toByte(), result[5])
        assertEquals(0xFF.toByte(), result[6])
    }

    @Test
    fun `isAacpPacket identifies valid headers`() {
        assertTrue(AacpPacketCodec.isAacpPacket(byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09)))
        assertTrue(AacpPacketCodec.isAacpPacket(byteArrayOf(0x00, 0x00, 0x04, 0x00, 0x01)))
        assertFalse(AacpPacketCodec.isAacpPacket(byteArrayOf(0x05, 0x00, 0x04, 0x00)))
        assertFalse(AacpPacketCodec.isAacpPacket(byteArrayOf(0x04, 0x00)))
    }

    @Test
    fun `toHex formats correctly`() {
        val hex = AacpPacketCodec.toHex(byteArrayOf(0x04, 0x00, 0xFF.toByte()))
        assertEquals("04 00 FF", hex)
    }

    @Test
    fun `decode battery packet with 3 components`() {
        val data = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, // header
            0x04, 0x00,             // opcode=BATTERY, secondary=0
            0x03,                    // count=3
            0x02, 0x01, 0x64, 0x02, 0x01, // RIGHT: 100%, NOT_CHARGING
            0x04, 0x01, 0x63, 0x02, 0x01, // LEFT: 99%, NOT_CHARGING
            0x08, 0x01, 0x00, 0x04, 0x01  // CASE: 0%, DISCONNECTED
        )
        val packet = AacpPacketCodec.decode(data)
        assertNotNull(packet)
        assertEquals(0x04.toByte(), packet!!.opcode)
        assertEquals(22, packet.rawBytes.size)
    }

    @Test
    fun `decode ear detection packet`() {
        // Both ears in
        val data = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x06, 0x00, 0x00, 0x00)
        val packet = AacpPacketCodec.decode(data)
        assertNotNull(packet)
        assertEquals(0x06.toByte(), packet!!.opcode)
        assertEquals(0x00.toByte(), packet.payload[0]) // left=IN_EAR
        assertEquals(0x00.toByte(), packet.payload[1]) // right=IN_EAR
    }
}
