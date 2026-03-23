package me.arnabsaha.airpodscompanion

import me.arnabsaha.airpodscompanion.ble.scanner.ManufacturerDataParser
import org.junit.Assert.*
import org.junit.Test

class ManufacturerDataParserTest {

    private val parser = ManufacturerDataParser()

    // Real AirPods Pro 2 USB-C advertisement data (type=0x07, length=0x19)
    private val validAdData = byteArrayOf(
        0x07, 0x19,                     // type=Nearby, length=25
        0x01,                           // paired=true
        0x24, 0x20,                     // model=0x2420 (AirPods Pro 2 USB-C)
        0x20,                           // status: primaryLeft=true, inCase=false
        0xAA.toByte(),                  // battery: left=0xA(100%), right=0xA(100%)
        0x50,                           // case battery=0x0(0%) + charging flags=0x5
        0x00,                           // lid byte (bit3=0 → lid OPEN since inverted)
        0x00,                           // color=white
        0x04                            // connectionState=idle
    )

    @Test
    fun `parse valid AirPods Pro 2 advertisement`() {
        val result = parser.parse(validAdData, "AA:BB:CC:DD:EE:FF", -55)
        assertNotNull(result)
        assertEquals(0x2420.toShort(), result!!.deviceModel)
        assertEquals("AirPods Pro 2 (USB-C)", result.modelName)
        assertTrue(result.isPaired)
        assertEquals(100, result.leftBattery)
        assertEquals(100, result.rightBattery)
        assertTrue(result.primaryIsLeft)
        assertEquals(-55, result.rssi)
    }

    @Test
    fun `parse returns null for non-Nearby type`() {
        val data = validAdData.clone()
        data[0] = 0x01 // Wrong type
        assertNull(parser.parse(data, "AA:BB:CC:DD:EE:FF", -50))
    }

    @Test
    fun `parse returns null for wrong length`() {
        val data = validAdData.clone()
        data[1] = 0x10 // Wrong length
        assertNull(parser.parse(data, "AA:BB:CC:DD:EE:FF", -50))
    }

    @Test
    fun `parse returns null for too-short data`() {
        assertNull(parser.parse(byteArrayOf(0x07, 0x19), "AA:BB:CC:DD:EE:FF", -50))
    }

    @Test
    fun `battery 0xF means unavailable (-1)`() {
        val data = validAdData.clone()
        data[6] = 0xFF.toByte() // Left=0xF, Right=0xF
        val result = parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)
        assertNotNull(result)
        assertEquals(-1, result!!.leftBattery)
        assertEquals(-1, result.rightBattery)
    }

    @Test
    fun `battery values 0-9 map to 0-90 percent`() {
        val data = validAdData.clone()
        data[6] = 0x59 // Left=5(50%), Right=9(90%)
        val result = parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)
        assertNotNull(result)
        assertEquals(50, result!!.leftBattery)
        assertEquals(90, result.rightBattery)
    }

    @Test
    fun `battery values 0xA-0xE map to 100 percent`() {
        val data = validAdData.clone()
        data[6] = 0xBA.toByte() // Left=0xB(100%), Right=0xA(100%)
        val result = parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)
        assertNotNull(result)
        assertEquals(100, result!!.leftBattery)
        assertEquals(100, result.rightBattery)
    }

    @Test
    fun `left-right swap when primaryIsLeft is false`() {
        val data = validAdData.clone()
        data[5] = 0x00 // primaryIsLeft = false
        data[6] = 0x59 // high=5, low=9
        val result = parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)
        assertNotNull(result)
        // When primaryIsLeft=false, high nibble=right, low=left
        assertEquals(90, result!!.leftBattery)
        assertEquals(50, result.rightBattery)
    }

    @Test
    fun `lid open when bit 3 is 0 (inverted polarity)`() {
        val data = validAdData.clone()
        data[8] = 0x00 // bit3=0 → lid OPEN
        assertTrue(parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)!!.isLidOpen)

        data[8] = 0x08 // bit3=1 → lid CLOSED
        assertFalse(parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)!!.isLidOpen)
    }

    @Test
    fun `model name mapping`() {
        val models = mapOf(
            0x0E20.toShort() to "AirPods Pro",
            0x1420.toShort() to "AirPods Pro 2",
            0x2420.toShort() to "AirPods Pro 2 (USB-C)",
            0x0A20.toShort() to "AirPods Max",
            0x1920.toShort() to "AirPods 4"
        )
        models.forEach { (model, name) ->
            val data = validAdData.clone()
            data[3] = (model.toInt() shr 8).toByte()
            data[4] = (model.toInt() and 0xFF).toByte()
            assertEquals(name, parser.parse(data, "AA:BB:CC:DD:EE:FF", -50)!!.modelName)
        }
    }
}
