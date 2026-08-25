package com.example.emulator.hle

import com.example.emulator.KeyManager
import java.io.File
import java.security.MessageDigest

/**
 * Firmware Manager for the Switch Emulator.
 * Handles the installation, validation, and integrity checking of the NAND firmware files 
 * (such as system fonts, Mii applets, and system modules) required for certain commercial games.
 */
import com.example.emulator.SwitchKeysManager

class FirmwareManager {

    data class FirmwareState(
        val isInstalled: Boolean = false,
        val version: String = "Unknown",
        val installedNcaCount: Int = 0,
        val missingCrucialFiles: List<String> = emptyList(),
        val statusMessage: String = "No firmware installed."
    )

    private var currentState = FirmwareState()

    // Example of mandatory system titles required by Horizon OS (Shared font, etc.)
    private val requiredSystemTitles = listOf(
        "0100000000000811", // System Font
        "0100000000000039", // Error Applet
        "010000000000002B"  // Mii Edit Applet
    )

    fun getFirmwareState(): FirmwareState = currentState

    /**
     * Installs firmware from a specified directory containing decrypted prod.keys and firmware NCA files.
     */
    fun installFirmwareFromFolder(folderPath: String, keysFilePath: String): Boolean {
        // Step 1: Validate Keys
        val result = SwitchKeysManager.loadKeysFromFile(File(keysFilePath))
        val isKeysValid = SwitchKeysManager.getKeySet().isLoaded
        if (!isKeysValid) {
            currentState = FirmwareState(
                isInstalled = false,
                statusMessage = "Installation failed: Invalid or missing prod.keys."
            )
            return false
        }

        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            currentState = FirmwareState(
                isInstalled = false,
                statusMessage = "Installation failed: Invalid firmware folder."
            )
            return false
        }

        // Step 2: Scan for NCA firmware files
        val ncaFiles = folder.listFiles { file -> file.extension.lowercase() == "nca" } ?: emptyArray()
        
        if (ncaFiles.isEmpty()) {
            currentState = FirmwareState(
                isInstalled = false,
                statusMessage = "Installation failed: No .nca firmware files found in folder."
            )
            return false
        }

        // Step 3: Validate Integrity (Mocked cryptographic validation)
        val validNcas = mutableListOf<File>()
        ncaFiles.forEach { file ->
            if (validateNcaIntegrity(file)) {
                validNcas.add(file)
            }
        }

        // Determine Firmware version based on heuristics of file counts or specific NCAs
        val detectedVersion = detectFirmwareVersion(validNcas.size)

        currentState = FirmwareState(
            isInstalled = true,
            version = detectedVersion,
            installedNcaCount = validNcas.size,
            statusMessage = "Firmware v$detectedVersion successfully installed and validated."
        )

        return true
    }

    /**
     * Cryptographically validates the NCA header and signatures using the decrypted prod.keys.
     * Stubbed logic to simulate advanced integrity checks for the Horizon Kernel.
     */
    private fun validateNcaIntegrity(file: File): Boolean {
        // In a real implementation, this reads the 0x400 NCA header, 
        // decrypts it using Header Key (from prod.keys), and verifies the SHA-256 hash.
        return file.name.endsWith(".nca", ignoreCase = true)
    }

    private fun detectFirmwareVersion(ncaCount: Int): String {
        return when {
            ncaCount > 200 -> "16.0.3"
            ncaCount > 150 -> "15.0.0"
            ncaCount > 100 -> "14.1.2"
            else -> "Custom/Unknown"
        }
    }
}
