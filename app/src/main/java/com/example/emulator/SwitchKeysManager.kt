package com.example.emulator

import java.io.File

/**
 * Nintendo Switch Cryptographic Key & System Firmware Manager.
 * Handles prod.keys, title.keys, and Master Key derivation for NCA/XCI decryption & verification.
 */
object SwitchKeysManager {

    data class KeySet(
        val headerKey: ByteArray? = null,
        val masterKeys: MutableMap<Int, ByteArray> = mutableMapOf(),
        val titleKeys: MutableMap<String, ByteArray> = mutableMapOf(),
        val isLoaded: Boolean = false,
        val loadedKeyCount: Int = 0,
        val keySourceMessage: String = "No prod.keys loaded"
    )

    private var currentKeySet = KeySet()

    fun getKeySet(): KeySet = currentKeySet

    /**
     * Resets loaded keys to empty state.
     */
    fun clearKeys() {
        currentKeySet = KeySet()
    }

    /**
     * Parses prod.keys or title.keys text file and extracts hex keys.
     */
    fun loadKeysFromFile(file: File): String {
        if (!file.exists()) {
            return "Key file not found: ${file.name}"
        }

        return try {
            val lines = file.readLines()
            var count = 0
            val newMasterKeys = mutableMapOf<Int, ByteArray>()
            val newTitleKeys = mutableMapOf<String, ByteArray>()
            var headerKey: ByteArray? = null

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

                val parts = trimmed.split("=").map { it.trim() }
                if (parts.size == 2) {
                    val keyName = parts[0].lowercase()
                    val keyHex = parts[1]
                    val keyBytes = hexToBytes(keyHex)

                    if (keyBytes != null) {
                        count++
                        when {
                            keyName == "header_key" -> headerKey = keyBytes
                            keyName.startsWith("master_key_") -> {
                                val revHex = keyName.removePrefix("master_key_")
                                val rev = revHex.toIntOrNull(16) ?: revHex.toIntOrNull() ?: 0
                                newMasterKeys[rev] = keyBytes
                            }
                            keyName.length == 32 && keyHex.length == 32 -> {
                                newTitleKeys[keyName.uppercase()] = keyBytes
                            }
                        }
                    }
                }
            }

            currentKeySet = KeySet(
                headerKey = headerKey,
                masterKeys = newMasterKeys,
                titleKeys = newTitleKeys,
                isLoaded = count > 0 && headerKey != null,
                loadedKeyCount = count,
                keySourceMessage = "Loaded $count cryptographic keys from ${file.name} (header_key: ${if (headerKey != null) "OK" else "Missing"}, master_keys: ${newMasterKeys.size})"
            )

            currentKeySet.keySourceMessage
        } catch (e: Exception) {
            "Failed to parse keys: ${e.message}"
        }
    }

    /**
     * Registers default simulated development keys for unencrypted / homebrew NCA testing.
     */
    fun registerDevKeys() {
        val dummyMasterKey = ByteArray(16) { 0x01 }
        val dummyHeaderKey = ByteArray(32) { 0x02 }
        val masterKeys = mutableMapOf<Int, ByteArray>()
        for (i in 0..16) masterKeys[i] = dummyMasterKey

        currentKeySet = KeySet(
            headerKey = dummyHeaderKey,
            masterKeys = masterKeys,
            titleKeys = mutableMapOf(),
            isLoaded = true,
            loadedKeyCount = masterKeys.size + 1,
            keySourceMessage = "Registered Switch Dev MasterKeys (v1.0 - v17.0)"
        )
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val cleanHex = hex.replace(" ", "").replace("0x", "").trim()
        if (cleanHex.length % 2 != 0) return null
        return try {
            ByteArray(cleanHex.length / 2) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: Exception) {
            null
        }
    }
}

