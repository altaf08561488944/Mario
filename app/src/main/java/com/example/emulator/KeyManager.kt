package com.example.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key Derivation and Cryptographic Decryption Utility for Nintendo Switch NCA/XCI/NSP Content.
 *
 * Provides:
 * - AES-XTS 128-bit decryption for NCA Headers (0xC00 block) and encrypted sections.
 * - AES-CTR 128-bit payload decryption for ExeFS / NSO / NRO binaries.
 * - AES-ECB Key Area Key (KAK) derivation and Key Area decryption.
 * - GF(2^128) polynomial tweak multiplication for authentic XTS mode.
 */
object KeyManager {

    /**
     * Decrypts an encrypted 0xC00 byte NCA Header using the NCA Header Key (AES-XTS 128-bit).
     * Header Key consists of 32 bytes:
     * - Bytes 0..15: Key 1 (Data Cipher Key)
     * - Bytes 16..31: Key 2 (Tweak Cipher Key)
     */
    fun decryptNcaHeader(
        encryptedHeader: ByteArray,
        headerKey: ByteArray
    ): ByteArray {
        if (encryptedHeader.size < 0xC00 || headerKey.size < 32) {
            return encryptedHeader
        }

        val key1 = headerKey.copyOfRange(0, 16)
        val key2 = headerKey.copyOfRange(16, 32)

        return try {
            decryptAesXts(
                data = encryptedHeader,
                offset = 0,
                length = 0xC00,
                key1 = key1,
                key2 = key2,
                sectorSize = 0x200,
                startSectorIndex = 0L
            )
        } catch (e: Exception) {
            encryptedHeader
        }
    }

    /**
     * Decrypts AES-CTR encrypted payload bytes (e.g., ExeFS / NSO / NRO sections or NCA sections).
     */
    fun decryptAesCtr(
        data: ByteArray,
        offset: Int,
        length: Int,
        key: ByteArray,
        ctrIv: ByteArray
    ): ByteArray {
        if (key.size < 16 || ctrIv.size < 16 || offset + length > data.size) {
            return data.copyOfRange(offset, (offset + length).coerceAtMost(data.size))
        }

        return try {
            val secretKey = SecretKeySpec(key.copyOf(16), "AES")
            val ivSpec = IvParameterSpec(ctrIv.copyOf(16))
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            cipher.doFinal(data, offset, length)
        } catch (e: Exception) {
            data.copyOfRange(offset, (offset + length).coerceAtMost(data.size))
        }
    }

    /**
     * Decrypts AES-XTS encrypted sector block (e.g. NCA Header or AES-XTS sections).
     * Uses IEEE 1619 AES-XTS mode with GF(2^128) tweak feedback polynomial multiplication (x^128 + x^7 + x^2 + x + 1 => 0x87).
     */
    fun decryptAesXts(
        data: ByteArray,
        offset: Int,
        length: Int,
        key1: ByteArray,
        key2: ByteArray,
        sectorSize: Int = 0x200,
        startSectorIndex: Long = 0L
    ): ByteArray {
        if (offset + length > data.size) {
            return data.copyOfRange(offset, data.size)
        }

        val result = ByteArray(length)
        System.arraycopy(data, offset, result, 0, length)

        val cipherData = Cipher.getInstance("AES/ECB/NoPadding")
        val cipherTweak = Cipher.getInstance("AES/ECB/NoPadding")

        cipherData.init(Cipher.DECRYPT_MODE, SecretKeySpec(key1.copyOf(16), "AES"))
        cipherTweak.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key2.copyOf(16), "AES"))

        val numSectors = length / sectorSize

        for (sec in 0 until numSectors) {
            val sectorIndex = startSectorIndex + sec
            val secOffset = sec * sectorSize

            // Generate initial tweak T = Encrypt(Key2, sectorIndex_as_16byte_LE)
            val tweakBlock = ByteArray(16)
            ByteBuffer.wrap(tweakBlock).order(ByteOrder.LITTLE_ENDIAN).putLong(0, sectorIndex)
            val initialTweak = cipherTweak.doFinal(tweakBlock)
            var currentTweak = initialTweak.clone()

            // Process 16-byte blocks inside sector
            for (blk in 0 until (sectorSize / 16)) {
                val blkOffset = secOffset + (blk * 16)
                if (blkOffset + 16 > length) break

                // C = Block XOR Tweak
                val c = ByteArray(16)
                for (i in 0 until 16) {
                    c[i] = (result[blkOffset + i].toInt() xor currentTweak[i].toInt()).toByte()
                }

                // P = Decrypt(Key1, C)
                val p = cipherData.doFinal(c)

                // Block = P XOR Tweak
                for (i in 0 until 16) {
                    result[blkOffset + i] = (p[i].toInt() xor currentTweak[i].toInt()).toByte()
                }

                // Multiply tweak in GF(2^128)
                currentTweak = multiplyTweakGf128(currentTweak)
            }
        }

        return result
    }

    /**
     * GF(2^128) polynomial multiplication for AES-XTS tweak progression.
     */
    private fun multiplyTweakGf128(tweak: ByteArray): ByteArray {
        val nextTweak = ByteArray(16)
        var carry = 0
        for (i in 0 until 16) {
            val b = tweak[i].toInt() and 0xFF
            val nextCarry = (b and 0x80) ushr 7
            nextTweak[i] = (((b shl 1) and 0xFF) or carry).toByte()
            carry = nextCarry
        }
        if (carry != 0) {
            nextTweak[0] = (nextTweak[0].toInt() xor 0x87).toByte()
        }
        return nextTweak
    }

    /**
     * Derives Key Area Key (KAK) using Master Key and Key Area Key Source.
     */
    fun deriveKeyAreaKey(
        masterKey: ByteArray,
        keyAreaKeySource: ByteArray
    ): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey.copyOf(16), "AES"))
            cipher.doFinal(keyAreaKeySource.copyOf(16))
        } catch (e: Exception) {
            masterKey.copyOf(16)
        }
    }

    /**
     * Decrypts 0x40 byte Key Area in NCA Header to retrieve Title Key or Content Key.
     */
    fun decryptKeyArea(
        encryptedKeyArea: ByteArray,
        keyAreaKey: ByteArray
    ): ByteArray {
        if (encryptedKeyArea.size < 0x40 || keyAreaKey.size < 16) return encryptedKeyArea

        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyAreaKey.copyOf(16), "AES"))
            cipher.doFinal(encryptedKeyArea, 0, 0x40)
        } catch (e: Exception) {
            encryptedKeyArea
        }
    }
}
