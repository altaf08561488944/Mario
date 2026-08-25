package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boot_config")
data class BootConfigEntity(
    @PrimaryKey val id: Int = 1,
    val biosName: String? = null,
    val biosPath: String? = null,
    val isBiosVerified: Boolean = false,
    val firmwareName: String? = null,
    val firmwarePath: String? = null,
    val isFirmwareVerified: Boolean = false,
    val dnsMode: String = "GOOGLE_DNS", // "GOOGLE_DNS" or "OTHER_DNS"
    val customDns: String = "8.8.8.8",
    val isBooted: Boolean = false,
    val storageCapacityGb: Int = 128 // 12, 128, or 512
)

@Entity(tableName = "virtual_cartridges")
data class VirtualCartridgeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleId: String,
    val publisher: String,
    val version: String,
    val sizeBytes: Long,
    val sourceFormat: String, // NSP, XCI, SUP
    val originalFilePath: String,
    val createdTimestamp: Long,
    val isPlayable: Boolean = true
)

@Entity(tableName = "my_folder_files")
data class MyFolderFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val extension: String,
    val fileType: String, // GAME, HOMEBREW, SUP_CONTAINER, BIOS_KEY, FIRMWARE, CONFIG, UNKNOWN
    val lastModified: Long,
    val isConvertedSup: Boolean = false,
    val isConvertedCartridge: Boolean = false
)

@Entity(tableName = "save_states")
data class SaveStateEntity(
    @PrimaryKey val id: String,
    val titleId: String,
    val gameTitle: String,
    val slotName: String,
    val fileName: String,
    val filePath: String,
    val previewImagePath: String? = null,
    val slotIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val isAutoSave: Boolean = false,
    val coreSummary: String = "Core 0 @ 0x7100041280 • 60 FPS",
    val instructionsExecuted: Long = 0L,
    val isDocked: Boolean = true
)
