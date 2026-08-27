package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing imported Nintendo Switch game files (NSP / XCI)
 * and their full metadata in the SWTC NOOS library.
 */
@Entity(tableName = "imported_game_files")
data class ImportedGameFileEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleId: String,
    val publisher: String = "Nintendo",
    val version: String = "1.0.0",
    val format: String, // "NSP" or "XCI"
    val filePath: String,
    val sizeBytes: Long,
    val iconPath: String? = null,
    val bannerPath: String? = null,
    val sdkVersion: String? = "16.0.0",
    val masterKeyRevision: Int = 16,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastPlayedTimestamp: Long = 0L,
    val playTimeMinutes: Long = 0L,
    val isFavorite: Boolean = false,
    val isPlayable: Boolean = true,
    val isNcaDecrypted: Boolean = false,
    val category: String = "Game"
)
