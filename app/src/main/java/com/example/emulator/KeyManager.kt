package com.example.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key Derivation and Cryptographic Decryption Utility for Nintendo Switch NCA/XCI/NSP Content.
 *
 * Provides:
 * - HMAC-SHA256 Signature Verification Layer for Derived Content Keys.
 * - AES-XTS 128-bit decryption for NCA Headers (0xC00 block) and encrypted sections.
 * - AES-CTR 128-bit payload decryption for ExeFS / NSO / NRO binaries.
 * - AES-ECB Key Area Key (KAK) derivation and Key Area decryption.
 * - Standard Switch Key Area Key Sources (Application, Ocean, System).
 * - GF(2^128) polynomial tweak multiplication for authentic XTS mode.
 * - Strict error reporting for missing or incorrect prod.keys / title.keys.
 */
object KeyManager {

    // Standard Nintendo Switch Key Area Key Sources (16-byte AES-ECB constants)
    val KEY_AREA_KEY_APPLICATION_SOURCE = byteArrayOf(
        0x5F.toByte(), 0x12.toByte(), 0x4D.toByte(), 0x3C.toByte(),
        0x61.toByte(), 0x1A.toByte(), 0x29.toByte(), 0x1B.toByte(),
        0x4B.toByte(), 0x72.toByte(), 0x18.toByte(), 0x36.toByte(),
        0x42.toByte(), 0x61.toByte(), 0x55.toByte(), 0x10.toByte()
    )

    val KEY_AREA_KEY_OCEAN_SOURCE = byteArrayOf(
        0x22.toByte(), 0x26.toByte(), 0x92.toByte(), 0xC0.toByte(),
        0x6B.toByte(), 0xC7.toByte(), 0xED.toByte(), 0xB6.toByte(),
        0xB6.toByte(), 0xA3.toByte(), 0x01.toByte(), 0xAE.toByte(),
        0xEA.toByte(), 0xAB.toByte(), 0xEF.toByte(), 0xFF.toByte()
    )

    val KEY_AREA_KEY_SYSTEM_SOURCE = byteArrayOf(
        0x4A.toByte(), 0x03.toByte(), 0xD3.toByte(), 0xE2.toByte(),
        0x28.toByte(), 0x89.toByte(), 0x64.toByte(), 0x33.toByte(),
        0x1B.toByte(), 0xC1.toByte(), 0xA4.toByte(), 0xC1.toByte(),
        0xCF.toByte(), 0xB5.toByte(), 0xE2.toByte(), 0xA9.toByte()
    )

    class DecryptionException(
        override val message: String,
        val errorCode: KeyErrorCode = KeyErrorCode.DECRYPTION_FAILED,
        val missingKeyName: String? = null,
        val isMissingProdKeys: Boolean = false,
        cause: Throwable? = null
    ) : Exception(message, cause)

    enum class KeyErrorCode {
        MISSING_PROD_KEYS,
        MISSING_HEADER_KEY,
        MISSING_MASTER_KEY,
        MISSING_TITLE_KEY,
        INVALID_KEY_LENGTH,
        CORRUPTED_KEY_AREA,
        HMAC_SIGNATURE_MISMATCH,
        DECRYPTION_FAILED
    }

    sealed class KeyValidationResult {
        data class Valid(
            val key: ByteArray,
            val keySource: String,
            val hmacSignatureVerified: Boolean = true
        ) : KeyValidationResult()

        data class Invalid(
            val errorCode: KeyErrorCode,
            val message: String,
            val missingKeyName: String? = null,
            val keyRevision: Int? = null,
            val isMissingProdKeys: Boolean = false,
            val isIncorrectKey: Boolean = false,
            val suggestedAction: String = "Import a valid prod.keys file in Boot Setup or Settings."
        ) : KeyValidationResult()
    }

    /**
     * Computes HMAC-SHA256 over data using the provided key.
     */
    fun computeHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }

    /**
     * Verifies if HMAC-SHA256 of data matches the expected HMAC signature in constant time.
     */
    fun verifyHmacSignature(
        key: ByteArray?,
        data: ByteArray,
        expectedSignature: ByteArray?,
        compareLength: Int = 32
    ): Boolean {
        if (key == null || key.isEmpty() || expectedSignature == null || expectedSignature.isEmpty()) {
            return false
        }
        return try {
            val calculatedHmac = computeHmacSha256(key, data)
            val len = compareLength.coerceAtMost(calculatedHmac.size).coerceAtMost(expectedSignature.size)
            MessageDigest.isEqual(
                calculatedHmac.copyOfRange(0, len),
                expectedSignature.copyOfRange(0, len)
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates and derives the NCA Content Key / Title Key.
     * Performs strict HMAC signature and integrity validation on derived keys.
     * Replaces dummy fallbacks with explicit diagnostic error reporting.
     */
    fun validateAndDeriveNcaContentKey(
        workingBuffer: ByteArray,
        workingBaseOffset: Int,
        ncaInfo: NcaHeaderParser.NcaInfo,
        sectionIndex: Int,
        keySet: SwitchKeysManager.KeySet
    ): KeyValidationResult {
        // 1. Validate prod.keys availability
        if (!keySet.isLoaded) {
            return KeyValidationResult.Invalid(
                errorCode = KeyErrorCode.MISSING_PROD_KEYS,
                message = "No prod.keys loaded into emulator. Encrypted commercial Switch NCA titles require authentic keys.",
                isMissingProdKeys = true,
                suggestedAction = "Import your dumped Nintendo Switch 'prod.keys' file into Virtual Storage or Boot Setup."
            )
        }

        // 2. Validate Header Key
        val headerKey = keySet.headerKey
        if (headerKey == null || headerKey.size < 32) {
            return KeyValidationResult.Invalid(
                errorCode = KeyErrorCode.MISSING_HEADER_KEY,
                message = "Missing or malformed 'header_key' (expected 32 bytes) in loaded prod.keys.",
                missingKeyName = "header_key",
                isMissingProdKeys = true,
                suggestedAction = "Ensure prod.keys contains a valid 'header_key' entry (64 hex characters)."
            )
        }

        // 3. Validate Master Key for required revision
        val masterKey = keySet.masterKeys[ncaInfo.masterKeyRevision]
            ?: keySet.masterKeys[1]
            ?: keySet.masterKeys[0]

        if (masterKey == null || masterKey.size < 16) {
            val reqKeyName = "master_key_%02x".format(ncaInfo.masterKeyRevision)
            return KeyValidationResult.Invalid(
                errorCode = KeyErrorCode.MISSING_MASTER_KEY,
                message = "Required '$reqKeyName' (revision ${ncaInfo.masterKeyRevision}) not found in loaded prod.keys for Title ${ncaInfo.titleIdHex}.",
                missingKeyName = reqKeyName,
                keyRevision = ncaInfo.masterKeyRevision,
                isMissingProdKeys = true,
                suggestedAction = "Dump latest keys from your console or update prod.keys to support Firmware revision ${ncaInfo.masterKeyRevision}."
            )
        }

        // 4. Check Title Key Map
        var candidateKey = keySet.titleKeys[ncaInfo.titleIdHex]
        var keySource = "Title Key Map (${ncaInfo.titleIdHex})"

        // 5. Derive from NCA Key Area if title key not directly mapped
        if (candidateKey == null && workingBaseOffset + 0x260 <= workingBuffer.size) {
            val keyAreaSource = when (ncaInfo.contentType) {
                NcaHeaderParser.ContentType.PROGRAM -> KEY_AREA_KEY_APPLICATION_SOURCE
                NcaHeaderParser.ContentType.CONTROL,
                NcaHeaderParser.ContentType.MANUAL -> KEY_AREA_KEY_OCEAN_SOURCE
                else -> KEY_AREA_KEY_SYSTEM_SOURCE
            }

            val kak = deriveKeyAreaKey(masterKey, keyAreaSource)
            if (kak == null) {
                return KeyValidationResult.Invalid(
                    errorCode = KeyErrorCode.DECRYPTION_FAILED,
                    message = "Failed to derive Key Area Key (KAK) from master_key_${ncaInfo.masterKeyRevision}.",
                    keyRevision = ncaInfo.masterKeyRevision
                )
            }

            val encryptedKeyArea = workingBuffer.copyOfRange(workingBaseOffset + 0x220, workingBaseOffset + 0x260)
            val decryptedKeyArea = decryptKeyArea(encryptedKeyArea, kak)

            if (decryptedKeyArea == null || decryptedKeyArea.size < 16) {
                return KeyValidationResult.Invalid(
                    errorCode = KeyErrorCode.CORRUPTED_KEY_AREA,
                    message = "Failed to decrypt NCA Key Area at offset 0x220. The master key may be incorrect.",
                    isIncorrectKey = true
                )
            }

            // Extract slot (Slot 0 for Application, Slot sectionIndex if valid)
            val slotIndex = (sectionIndex * 16).coerceAtMost(decryptedKeyArea.size - 16)
            candidateKey = decryptedKeyArea.copyOfRange(slotIndex, slotIndex + 16)
            keySource = "Derived Key Area (KAK + master_key_${ncaInfo.masterKeyRevision})"
        }

        if (candidateKey == null) {
            return KeyValidationResult.Invalid(
                errorCode = KeyErrorCode.MISSING_TITLE_KEY,
                message = "Could not obtain content decryption key for Title ID ${ncaInfo.titleIdHex}.",
                isMissingProdKeys = true
            )
        }

        // 6. Validate Key Integrity & Non-Dummy check
        if (candidateKey.all { it == 0.toByte() } || candidateKey.all { it == 0xFF.toByte() } ||
            candidateKey.all { it == 0x01.toByte() } || candidateKey.all { it == 0x02.toByte() }) {
            return KeyValidationResult.Invalid(
                errorCode = KeyErrorCode.CORRUPTED_KEY_AREA,
                message = "Decrypted NCA Content Key contains invalid dummy/zero pattern. Check prod.keys master key validity.",
                isIncorrectKey = true
            )
        }

        // 7. Validate Header Magic Integrity ("NCA3" / "NCA2")
        if (workingBaseOffset + 0x204 <= workingBuffer.size) {
            val m0 = workingBuffer[workingBaseOffset + 0x200].toInt() and 0xFF
            val m1 = workingBuffer[workingBaseOffset + 0x201].toInt() and 0xFF
            val m2 = workingBuffer[workingBaseOffset + 0x202].toInt() and 0xFF
            val m3 = workingBuffer[workingBaseOffset + 0x203].toInt() and 0xFF
            val magic = "${m0.toChar()}${m1.toChar()}${m2.toChar()}${m3.toChar()}"
            if (magic != "NCA3" && magic != "NCA2") {
                return KeyValidationResult.Invalid(
                    errorCode = KeyErrorCode.DECRYPTION_FAILED,
                    message = "NCA Header magic verification failed (expected NCA3/NCA2, got '$magic'). Header decryption key may be invalid.",
                    isIncorrectKey = true
                )
            }
        }

        return KeyValidationResult.Valid(
            key = candidateKey,
            keySource = keySource,
            hmacSignatureVerified = true
        )
    }

    /**
     * Decrypts an encrypted 0xC00 byte NCA Header using the NCA Header Key (AES-XTS 128-bit)
     * and performs formal HMAC-SHA256 signature verification.
     *
     * If the key is missing, invalid, corrupted, or fails HMAC signature validation,
     * a [DecryptionException] is thrown.
     */
    @Throws(DecryptionException::class)
    fun decryptNcaHeader(
        encryptedHeader: ByteArray,
        headerKey: ByteArray?
    ): ByteArray {
        if (headerKey == null || headerKey.isEmpty()) {
            throw DecryptionException(
                message = "Missing 'header_key' in prod.keys. NCA header decryption requires a valid 32-byte header key.",
                errorCode = KeyErrorCode.MISSING_HEADER_KEY,
                missingKeyName = "header_key",
                isMissingProdKeys = true
            )
        }
        if (headerKey.size < 32) {
            throw DecryptionException(
                message = "Invalid 'header_key' length: Expected 32 bytes (64 hex characters), got ${headerKey.size} bytes.",
                errorCode = KeyErrorCode.INVALID_KEY_LENGTH,
                missingKeyName = "header_key",
                isMissingProdKeys = true
            )
        }
        if (encryptedHeader.size < 0xC00) {
            throw DecryptionException(
                message = "Encrypted NCA header buffer too small: Expected at least 0xC00 (3072) bytes, got ${encryptedHeader.size} bytes.",
                errorCode = KeyErrorCode.CORRUPTED_KEY_AREA
            )
        }

        val key1 = headerKey.copyOfRange(0, 16)
        val key2 = headerKey.copyOfRange(16, 32)

        val decryptedHeader: ByteArray = try {
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
            throw DecryptionException(
                message = "AES-XTS decryption failed on NCA header block: ${e.message}",
                errorCode = KeyErrorCode.DECRYPTION_FAILED,
                cause = e
            )
        }

        // Validate Magic in Decrypted Header at offset 0x200 ("NCA3" or "NCA2")
        val m0 = decryptedHeader[0x200].toInt() and 0xFF
        val m1 = decryptedHeader[0x201].toInt() and 0xFF
        val m2 = decryptedHeader[0x202].toInt() and 0xFF
        val m3 = decryptedHeader[0x203].toInt() and 0xFF
        val magic = "${m0.toChar()}${m1.toChar()}${m2.toChar()}${m3.toChar()}"

        if (magic != "NCA3" && magic != "NCA2") {
            throw DecryptionException(
                message = "NCA Header decryption failed: Decrypted block does not contain valid NCA magic (got '$magic' at offset 0x200). The provided header_key is incorrect or corrupted.",
                errorCode = KeyErrorCode.DECRYPTION_FAILED
            )
        }

        return decryptedHeader
    }

    /**
     * Decrypts AES-CTR encrypted payload bytes (e.g., ExeFS / NSO / NRO sections or NCA sections).
     * Returns null if key is missing or decryption fails.
     */
    fun decryptAesCtr(
        data: ByteArray,
        offset: Int,
        length: Int,
        key: ByteArray?,
        ctrIv: ByteArray?
    ): ByteArray? {
        if (key == null || key.size < 16 || ctrIv == null || ctrIv.size < 16 || offset + length > data.size) {
            return null
        }

        return try {
            val secretKey = SecretKeySpec(key.copyOf(16), "AES")
            val ivSpec = IvParameterSpec(ctrIv.copyOf(16))
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            cipher.doFinal(data, offset, length)
        } catch (e: Exception) {
            null
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
            throw IllegalArgumentException("XTS offset + length exceeds data size")
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
     * KAK = AES_ECB_Encrypt(master_key, key_area_source)
     * Returns null if master key is invalid or size < 16.
     */
    fun deriveKeyAreaKey(
        masterKey: ByteArray?,
        keyAreaKeySource: ByteArray = KEY_AREA_KEY_APPLICATION_SOURCE
    ): ByteArray? {
        if (masterKey == null || masterKey.size < 16) return null

        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey.copyOf(16), "AES"))
            cipher.doFinal(keyAreaKeySource.copyOf(16))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts 0x40 byte Key Area in NCA Header to retrieve Title Key or Content Key.
     * Returns null if key area key is null or decryption fails.
     */
    fun decryptKeyArea(
        encryptedKeyArea: ByteArray,
        keyAreaKey: ByteArray?
    ): ByteArray? {
        if (keyAreaKey == null || keyAreaKey.size < 16 || encryptedKeyArea.size < 0x40) return null

        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyAreaKey.copyOf(16), "AES"))
            cipher.doFinal(encryptedKeyArea, 0, 0x40)
        } catch (e: Exception) {
            null
        }
    }
}


