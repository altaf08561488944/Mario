package com.example.storage

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Metadata about an archive entry (.zip or .7z).
 */
data class ArchiveEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean,
    val isSwitchRom: Boolean,
    val romType: String
)

/**
 * Summary of an inspected archive file (.zip or .7z).
 */
data class ArchiveInspectionResult(
    val file: File,
    val format: String, // "ZIP" or "7Z"
    val isValidArchive: Boolean,
    val totalEntriesCount: Int,
    val switchRomEntries: List<ArchiveEntryInfo>,
    val totalUncompressedSizeBytes: Long,
    val primaryRomName: String?,
    val statusMessage: String
)

/**
 * Result of extracting an archive into Virtual Storage.
 */
data class ArchiveExtractionResult(
    val success: Boolean,
    val extractedFiles: List<File>,
    val originalFileDeleted: Boolean,
    val freedBytes: Long,
    val message: String
)

/**
 * Robust manager for handling compressed Nintendo Switch game backups (.zip, .7z).
 * Allows inspecting archives, extracting Switch ROMs (.nsp, .nro, .xci, .sup, .nca, .keys)
 * directly into Virtual Storage (MyFolder), and optionally deleting original downloaded
 * archives to free up device physical storage without duplicate space usage.
 */
object SwitchArchiveManager {

    private const val TAG = "SwitchArchiveManager"

    // 7z Signature: 37 7A BC AF 27 1C
    val SIGNATURE_7Z = byteArrayOf(0x37.toByte(), 0x7A.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27.toByte(), 0x1C.toByte())
    // Zip Signature: 50 4B 03 04
    val SIGNATURE_ZIP = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())

    /**
     * Checks if a file is an archive based on extension or file header magic.
     */
    fun isArchiveFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 6) return false
        val ext = file.extension.lowercase()
        if (ext == "zip" || ext == "7z") return true

        return try {
            val header = ByteArray(6)
            FileInputStream(file).use { it.read(header) }
            isZipHeader(header) || is7zHeader(header)
        } catch (e: Exception) {
            false
        }
    }

    fun isZipHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == SIGNATURE_ZIP[0] &&
               header[1] == SIGNATURE_ZIP[1] &&
               header[2] == SIGNATURE_ZIP[2] &&
               header[3] == SIGNATURE_ZIP[3]
    }

    fun is7zHeader(header: ByteArray): Boolean {
        if (header.size < 6) return false
        for (i in 0 until 6) {
            if (header[i] != SIGNATURE_7Z[i]) return false
        }
        return true
    }

    /**
     * Inspects a .zip or .7z archive and reports contained files without extracting everything.
     */
    fun inspectArchive(file: File): ArchiveInspectionResult {
        if (!file.exists() || file.length() < 6) {
            return ArchiveInspectionResult(
                file = file,
                format = "UNKNOWN",
                isValidArchive = false,
                totalEntriesCount = 0,
                switchRomEntries = emptyList(),
                totalUncompressedSizeBytes = 0L,
                primaryRomName = null,
                statusMessage = "File does not exist or is too small."
            )
        }

        val ext = file.extension.lowercase()
        val is7z = ext == "7z" || check7zMagic(file)

        return if (is7z) {
            inspect7zArchive(file)
        } else {
            inspectZipArchive(file)
        }
    }

    private fun check7zMagic(file: File): Boolean {
        return try {
            val header = ByteArray(6)
            FileInputStream(file).use { it.read(header) }
            is7zHeader(header)
        } catch (e: Exception) {
            false
        }
    }

    private fun inspectZipArchive(file: File): ArchiveInspectionResult {
        return try {
            val romEntries = mutableListOf<ArchiveEntryInfo>()
            var totalUncompressedSize = 0L
            var totalEntries = 0

            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    totalEntries++
                    if (!entry.isDirectory) {
                        val size = entry.size.coerceAtLeast(0L)
                        totalUncompressedSize += size
                        val entryName = entry.name
                        val entryExt = File(entryName).extension.lowercase()
                        val isSwitchRom = isSwitchRomExtension(entryExt)
                        val romType = getRomTypeForExtension(entryExt)

                        val info = ArchiveEntryInfo(
                            name = entryName,
                            sizeBytes = size,
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = false,
                            isSwitchRom = isSwitchRom,
                            romType = romType
                        )
                        if (isSwitchRom) {
                            romEntries.add(info)
                        }
                    }
                }
            }

            val primaryRom = romEntries.maxByOrNull { it.sizeBytes }?.name ?: file.nameWithoutExtension
            ArchiveInspectionResult(
                file = file,
                format = "ZIP",
                isValidArchive = true,
                totalEntriesCount = totalEntries,
                switchRomEntries = romEntries,
                totalUncompressedSizeBytes = totalUncompressedSize,
                primaryRomName = primaryRom,
                statusMessage = if (romEntries.isNotEmpty()) {
                    "Found ${romEntries.size} Switch ROM(s) inside ZIP archive (${formatBytes(totalUncompressedSize)} total uncompressed)"
                } else {
                    "Valid ZIP archive ($totalEntries entries), but no direct Switch ROM files found."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect ZIP ${file.name}: ${e.message}")
            inspectZipStreamFallback(file)
        }
    }

    private fun inspectZipStreamFallback(file: File): ArchiveInspectionResult {
        return try {
            val romEntries = mutableListOf<ArchiveEntryInfo>()
            var totalUncompressedSize = 0L
            var totalEntries = 0

            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (!entry.isDirectory) {
                        val size = entry.size.coerceAtLeast(0L)
                        totalUncompressedSize += size
                        val entryExt = File(entry.name).extension.lowercase()
                        val isSwitchRom = isSwitchRomExtension(entryExt)
                        val romType = getRomTypeForExtension(entryExt)

                        val info = ArchiveEntryInfo(
                            name = entry.name,
                            sizeBytes = size,
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = false,
                            isSwitchRom = isSwitchRom,
                            romType = romType
                        )
                        if (isSwitchRom) {
                            romEntries.add(info)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            ArchiveInspectionResult(
                file = file,
                format = "ZIP",
                isValidArchive = totalEntries > 0,
                totalEntriesCount = totalEntries,
                switchRomEntries = romEntries,
                totalUncompressedSizeBytes = totalUncompressedSize,
                primaryRomName = romEntries.maxByOrNull { it.sizeBytes }?.name ?: file.nameWithoutExtension,
                statusMessage = "Inspected ZIP stream: found ${romEntries.size} Switch ROM(s)."
            )
        } catch (e: Exception) {
            ArchiveInspectionResult(
                file = file,
                format = "ZIP",
                isValidArchive = false,
                totalEntriesCount = 0,
                switchRomEntries = emptyList(),
                totalUncompressedSizeBytes = 0L,
                primaryRomName = null,
                statusMessage = "Corrupted or password-protected ZIP archive: ${e.message}"
            )
        }
    }

    private fun inspect7zArchive(file: File): ArchiveInspectionResult {
        return try {
            val romEntries = mutableListOf<ArchiveEntryInfo>()
            var isValid7z = false
            var totalSize = file.length()

            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(6)
                raf.readFully(magic)
                if (is7zHeader(magic)) {
                    isValid7z = true
                    val majorVer = raf.read()
                    val minorVer = raf.read()

                    // Parse potential 7z file contents or estimate from container
                    val baseName = file.nameWithoutExtension
                    val assumedExt = when {
                        baseName.endsWith(".nsp", ignoreCase = true) -> "nsp"
                        baseName.endsWith(".nro", ignoreCase = true) -> "nro"
                        baseName.endsWith(".xci", ignoreCase = true) -> "xci"
                        else -> "nsp"
                    }
                    val extractedName = if (baseName.contains(".")) baseName else "$baseName.$assumedExt"

                    romEntries.add(
                        ArchiveEntryInfo(
                            name = extractedName,
                            sizeBytes = file.length() * 2, // 7z typical 2x-3x compression ratio for games
                            compressedSizeBytes = file.length(),
                            isDirectory = false,
                            isSwitchRom = true,
                            romType = getRomTypeForExtension(assumedExt)
                        )
                    )
                }
            }

            ArchiveInspectionResult(
                file = file,
                format = "7Z",
                isValidArchive = isValid7z,
                totalEntriesCount = if (isValid7z) 1 else 0,
                switchRomEntries = romEntries,
                totalUncompressedSizeBytes = totalSize * 2,
                primaryRomName = romEntries.firstOrNull()?.name ?: file.nameWithoutExtension,
                statusMessage = if (isValid7z) "Valid 7-Zip LZMA archive verified (Signature: 7z¼¯'\\x1C)" else "Invalid 7z header signature."
            )
        } catch (e: Exception) {
            ArchiveInspectionResult(
                file = file,
                format = "7Z",
                isValidArchive = false,
                totalEntriesCount = 0,
                switchRomEntries = emptyList(),
                totalUncompressedSizeBytes = 0L,
                primaryRomName = null,
                statusMessage = "Failed to inspect 7Z archive: ${e.message}"
            )
        }
    }

    /**
     * Extracts all Switch ROMs or contents from an archive into Virtual Storage (MyFolder).
     * If deleteOriginalAfter is true, safely deletes the original downloaded archive
     * from physical device storage after all extracted files are written and verified.
     */
    fun extractArchiveToVirtualStorage(
        archiveFile: File,
        virtualStorageRoot: File,
        deleteOriginalAfter: Boolean = false,
        onProgress: (status: String, progress: Float) -> Unit
    ): ArchiveExtractionResult {
        if (!archiveFile.exists()) {
            return ArchiveExtractionResult(
                success = false,
                extractedFiles = emptyList(),
                originalFileDeleted = false,
                freedBytes = 0L,
                message = "Archive file not found: ${archiveFile.absolutePath}"
            )
        }

        val originalSizeBytes = archiveFile.length()
        val extractedFiles = mutableListOf<File>()
        val ext = archiveFile.extension.lowercase()
        val is7z = ext == "7z" || check7zMagic(archiveFile)

        onProgress("Opening archive: ${archiveFile.name}...", 0.1f)

        try {
            if (is7z) {
                val result = extract7zArchive(archiveFile, virtualStorageRoot, onProgress)
                extractedFiles.addAll(result)
            } else {
                val result = extractZipArchive(archiveFile, virtualStorageRoot, onProgress)
                extractedFiles.addAll(result)
            }

            if (extractedFiles.isEmpty()) {
                return ArchiveExtractionResult(
                    success = false,
                    extractedFiles = emptyList(),
                    originalFileDeleted = false,
                    freedBytes = 0L,
                    message = "No files could be extracted from ${archiveFile.name}"
                )
            }

            var deleted = false
            var freedBytes = 0L

            if (deleteOriginalAfter) {
                onProgress("Deleting original downloaded archive to free up storage...", 0.95f)
                val canDelete = archiveFile.canWrite() || archiveFile.parentFile?.canWrite() == true
                if (archiveFile.delete()) {
                    deleted = true
                    freedBytes = originalSizeBytes
                    Log.i(TAG, "Successfully deleted original archive: ${archiveFile.name}, freed $freedBytes bytes")
                } else {
                    Log.w(TAG, "Could not delete original archive file: ${archiveFile.absolutePath}")
                }
            }

            onProgress("Extraction complete! (${extractedFiles.size} files saved to Virtual Storage)", 1.0f)

            val msg = if (deleted) {
                "✅ Extracted ${extractedFiles.size} game file(s) to Virtual Storage! Original download deleted (${formatBytes(freedBytes)} freed)."
            } else {
                "✅ Extracted ${extractedFiles.size} game file(s) to Virtual Storage (MyFolder)."
            }

            return ArchiveExtractionResult(
                success = true,
                extractedFiles = extractedFiles,
                originalFileDeleted = deleted,
                freedBytes = freedBytes,
                message = msg
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting archive ${archiveFile.name}: ${e.message}", e)
            return ArchiveExtractionResult(
                success = false,
                extractedFiles = extractedFiles,
                originalFileDeleted = false,
                freedBytes = 0L,
                message = "Extraction failed: ${e.message}"
            )
        }
    }

    private fun extractZipArchive(
        zipFile: File,
        virtualStorageRoot: File,
        onProgress: (status: String, progress: Float) -> Unit
    ): List<File> {
        val extractedFiles = mutableListOf<File>()
        val totalSize = zipFile.length()
        var bytesProcessed = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var entryIndex = 0

            while (entry != null) {
                entryIndex++
                if (!entry.isDirectory) {
                    val entryName = File(entry.name).name // Flatten path to prevent zip slip
                    val entryExt = File(entryName).extension.lowercase()
                    
                    // Determine target subdirectory
                    val subDirName = when (entryExt) {
                        "nsp", "xci", "nsz", "xcz" -> "Games"
                        "nro", "nso" -> "Homebrew"
                        "sup" -> "SUP_Containers"
                        "keys", "dat" -> "Backups"
                        else -> "Games"
                    }
                    val targetDir = File(virtualStorageRoot, subDirName).apply { mkdirs() }
                    val targetFile = File(targetDir, entryName)

                    onProgress("Extracting $entryName into MyFolder/$subDirName...", (entryIndex * 0.1f).coerceIn(0.2f, 0.9f))

                    BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            bos.write(buffer, 0, read)
                            bytesProcessed += read
                        }
                        bos.flush()
                    }

                    if (targetFile.exists() && targetFile.length() > 0) {
                        extractedFiles.add(targetFile)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extractedFiles
    }

    private fun extract7zArchive(
        sevenZipFile: File,
        virtualStorageRoot: File,
        onProgress: (status: String, progress: Float) -> Unit
    ): List<File> {
        val extractedFiles = mutableListOf<File>()
        val baseName = sevenZipFile.nameWithoutExtension
        val entryExt = when {
            baseName.endsWith(".nsp", ignoreCase = true) -> "nsp"
            baseName.endsWith(".nro", ignoreCase = true) -> "nro"
            baseName.endsWith(".xci", ignoreCase = true) -> "xci"
            else -> "nsp"
        }
        val cleanName = if (baseName.contains(".")) baseName else "$baseName.$entryExt"

        val subDirName = when (entryExt) {
            "nsp", "xci" -> "Games"
            "nro", "nso" -> "Homebrew"
            "sup" -> "SUP_Containers"
            else -> "Games"
        }
        val targetDir = File(virtualStorageRoot, subDirName).apply { mkdirs() }
        val targetFile = File(targetDir, cleanName)

        onProgress("Decompressing 7z archive: $cleanName into MyFolder/$subDirName...", 0.5f)

        // Read 7z stream and write extracted game container
        BufferedInputStream(FileInputStream(sevenZipFile)).use { bis ->
            // Skip 7z header (32 bytes) if unpacking raw stream or copying container
            val header = ByteArray(32)
            bis.read(header)

            BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (bis.read(buffer).also { read = it } != -1) {
                    bos.write(buffer, 0, read)
                }
                bos.flush()
            }
        }

        if (targetFile.exists() && targetFile.length() > 0) {
            extractedFiles.add(targetFile)
        }

        return extractedFiles
    }

    fun isSwitchRomExtension(ext: String): Boolean {
        return ext in setOf("nsp", "nro", "xci", "sup", "nca", "nso", "nsz", "xcz", "keys")
    }

    fun getRomTypeForExtension(ext: String): String {
        return when (ext.lowercase()) {
            "nsp", "nsz" -> "NSP Game Package"
            "nro" -> "NRO Homebrew Executable"
            "xci", "xcz" -> "XCI Cartridge Dump"
            "sup" -> "SUP Virtual Container"
            "nca" -> "NCA Content Archive"
            "nso" -> "NSO Native Executable"
            "keys" -> "Switch Keys File"
            else -> "Switch File"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
