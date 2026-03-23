package me.arnabsaha.airpodscompanion.utils

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Bluetooth cryptography utilities for AirPods RPA (Resolvable Private Address) verification.
 *
 * AirPods use BLE privacy with rotating MAC addresses. To identify a known pair of AirPods,
 * we need the IRK (Identity Resolving Key) and optionally the ENC_KEY (Encryption Key).
 *
 * The IRK is used to verify that a BLE address belongs to our paired AirPods
 * by computing ah(IRK, prand) and comparing it to the hash portion of the address.
 *
 * Reference: Bluetooth Core Spec Vol 3, Part H, Section 2.2.2
 */
object BluetoothCryptography {

    private const val TAG = "BtCrypto"

    /**
     * Verify a Resolvable Private Address (RPA) against a known IRK.
     *
     * RPA format (6 bytes, reversed from string representation):
     *   bytes[0..2] = hash (3 bytes)
     *   bytes[3..5] = prand (3 bytes, MSB has bits 7-6 = 01)
     *
     * @param address BLE address string "XX:XX:XX:XX:XX:XX"
     * @param irk Identity Resolving Key (16 bytes)
     * @return true if the address was generated with this IRK
     */
    fun verifyRPA(address: String, irk: ByteArray): Boolean {
        if (irk.size != 16) {
            Log.e(TAG, "IRK must be 16 bytes, got ${irk.size}")
            return false
        }

        return try {
            // Convert address string to bytes (reversed — BLE is little-endian)
            val rpa = address.split(":")
                .map { it.toInt(16).toByte() }
                .reversed()
                .toByteArray()

            if (rpa.size != 6) {
                Log.e(TAG, "Invalid address length: ${rpa.size}")
                return false
            }

            // Check that it's actually an RPA (bits 7-6 of MSB = 01)
            val msb = rpa[5].toInt() and 0xC0
            if (msb != 0x40) {
                Log.v(TAG, "Not an RPA (MSB bits 7-6 = 0x%02X, expected 0x40)".format(msb))
                return false
            }

            val prand = rpa.copyOfRange(3, 6)   // Random part
            val hash = rpa.copyOfRange(0, 3)     // Hash part

            val computedHash = ah(irk, prand)
            hash.contentEquals(computedHash)
        } catch (e: Exception) {
            Log.e(TAG, "RPA verification failed: $e")
            false
        }
    }

    /**
     * Bluetooth ah() function: generates a 24-bit hash from a key and plaintext.
     *
     * ah(k, r) = e(k, r') mod 2^24
     * where r' = r zero-padded to 16 bytes
     *
     * @param k 16-byte key (IRK)
     * @param r 3-byte random value (prand)
     * @return 3-byte hash
     */
    fun ah(k: ByteArray, r: ByteArray): ByteArray {
        val rPadded = ByteArray(16)
        r.copyInto(rPadded, 0, 0, minOf(r.size, 3))
        val encrypted = e(k, rPadded)
        return encrypted.copyOfRange(0, 3) // First 3 bytes
    }

    /**
     * Bluetooth e() function: AES-128 encryption with byte-swapped key and data.
     *
     * The Bluetooth spec defines e(key, data) with MSB-first byte ordering,
     * but Java's AES operates LSB-first. So we reverse both key and data
     * before encryption, then reverse the result.
     *
     * @param key 16-byte AES key
     * @param data 16-byte plaintext
     * @return 16-byte ciphertext
     */
    fun e(key: ByteArray, data: ByteArray): ByteArray {
        val swappedKey = key.reversedArray()
        val swappedData = data.reversedArray()

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val secretKey = SecretKeySpec(swappedKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        return cipher.doFinal(swappedData).reversedArray()
    }

    /**
     * Decrypt the encrypted portion of Apple manufacturer data using ENC_KEY.
     * This is used to extract the IRK from the advertisement for verification.
     *
     * @param encryptedData Last 16 bytes of the manufacturer data
     * @param encKey 16-byte encryption key
     * @return Decrypted 16 bytes, or null on failure
     */
    fun decryptManufacturerData(encryptedData: ByteArray, encKey: ByteArray): ByteArray? {
        if (encryptedData.size != 16 || encKey.size != 16) return null

        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val secretKey = SecretKeySpec(encKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: $e")
            null
        }
    }
}
