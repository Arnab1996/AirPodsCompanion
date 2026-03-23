package me.arnabsaha.airpodscompanion

import me.arnabsaha.airpodscompanion.utils.BluetoothCryptography
import org.junit.Assert.*
import org.junit.Test

class BluetoothCryptographyTest {

    @Test
    fun `e function produces 16-byte output`() {
        val key = ByteArray(16) { it.toByte() }
        val data = ByteArray(16) { (it + 16).toByte() }
        val result = BluetoothCryptography.e(key, data)
        assertEquals(16, result.size)
    }

    @Test
    fun `e function is deterministic`() {
        val key = ByteArray(16) { it.toByte() }
        val data = ByteArray(16) { (it + 16).toByte() }
        val result1 = BluetoothCryptography.e(key, data)
        val result2 = BluetoothCryptography.e(key, data)
        assertArrayEquals(result1, result2)
    }

    @Test
    fun `ah function produces 3-byte output`() {
        val key = ByteArray(16) { it.toByte() }
        val r = byteArrayOf(0x01, 0x02, 0x03)
        val result = BluetoothCryptography.ah(key, r)
        assertEquals(3, result.size)
    }

    @Test
    fun `ah function is deterministic`() {
        val key = ByteArray(16) { 0xAB.toByte() }
        val r = byteArrayOf(0x11, 0x22, 0x33)
        val result1 = BluetoothCryptography.ah(key, r)
        val result2 = BluetoothCryptography.ah(key, r)
        assertArrayEquals(result1, result2)
    }

    @Test
    fun `verifyRPA returns false for non-RPA address`() {
        // Non-RPA address (bits 7-6 of MSB != 01)
        val irk = ByteArray(16) { 0x00 }
        assertFalse(BluetoothCryptography.verifyRPA("00:11:22:33:44:55", irk))
    }

    @Test
    fun `verifyRPA returns false for wrong-length IRK`() {
        assertFalse(BluetoothCryptography.verifyRPA("40:11:22:33:44:55", ByteArray(8)))
    }

    @Test
    fun `decryptManufacturerData returns null for wrong-size input`() {
        assertNull(BluetoothCryptography.decryptManufacturerData(ByteArray(8), ByteArray(16)))
        assertNull(BluetoothCryptography.decryptManufacturerData(ByteArray(16), ByteArray(8)))
    }

    @Test
    fun `decryptManufacturerData produces 16-byte output for valid input`() {
        val encData = ByteArray(16) { it.toByte() }
        val encKey = ByteArray(16) { (it + 32).toByte() }
        val result = BluetoothCryptography.decryptManufacturerData(encData, encKey)
        assertNotNull(result)
        assertEquals(16, result!!.size)
    }

    @Test
    fun `decryptManufacturerData is reversible`() {
        val plaintext = ByteArray(16) { (it * 7).toByte() }
        val key = ByteArray(16) { (it + 100).toByte() }

        // Encrypt
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plaintext)

        // Decrypt with our function
        val decrypted = BluetoothCryptography.decryptManufacturerData(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }
}
