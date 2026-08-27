package com.example.storage

import android.content.Context
import com.example.data.entity.MyFolderFileEntity
import java.io.File
import java.util.UUID

data class VirtualStorageStats(
    val capacityGb: Int,
    val capacityBytes: Long,
    val usedBytes: Long,
    val freeVirtualBytes: Long,
    val usedPercentage: Float
)

class VirtualStorageManager(private val context: Context) {

    fun getStorageDir(): File {
        val externalDir = context.getExternalFilesDir("MyFolder")
            ?: File(context.filesDir, "MyFolder")
        if (!externalDir.exists()) {
            externalDir.mkdirs()
        }
        
        // Ensure subdirectories exist
        File(externalDir, "Games").mkdirs()
        File(externalDir, "Homebrew").mkdirs()
        File(externalDir, "Backups").mkdirs()
        File(externalDir, "Configs").mkdirs()
        File(externalDir, "SUP_Containers").mkdirs()
        File(externalDir, "Cartridges").mkdirs()
        
        return externalDir
    }

    fun calculateStats(capacityGb: Int, extraUsedBytes: Long = 0L): VirtualStorageStats {
        val capacityBytes = capacityGb.toLong() * 1024L * 1024L * 1024L
        val dir = getStorageDir()
        val realFilesUsedBytes = calculateFolderSize(dir) + extraUsedBytes
        val used = realFilesUsedBytes.coerceAtMost(capacityBytes)
        val free = (capacityBytes - used).coerceAtLeast(0L)
        val percentage = if (capacityBytes > 0) (used.toFloat() / capacityBytes.toFloat()) * 100f else 0f

        return VirtualStorageStats(
            capacityGb = capacityGb,
            capacityBytes = capacityBytes,
            usedBytes = used,
            freeVirtualBytes = free,
            usedPercentage = percentage
        )
    }

    private fun calculateFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var length = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            length += calculateFolderSize(child)
        }
        return length
    }

    fun scanFiles(): List<MyFolderFileEntity> {
        val rootDir = getStorageDir()
        val fileList = mutableListOf<MyFolderFileEntity>()
        scanRecursively(rootDir, fileList)
        return fileList
    }

    private fun scanRecursively(dir: File, result: MutableList<MyFolderFileEntity>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanRecursively(file, result)
            } else {
                val name = file.name
                val ext = file.extension.lowercase()
                val type = when (ext) {
                    "nsp", "xci", "nsz", "xcz" -> "GAME"
                    "nro", "nso" -> "HOMEBREW"
                    "sup" -> "SUP_CONTAINER"
                    "zip", "7z" -> "ARCHIVE"
                    "keys", "dat", "bin" -> "BIOS_KEY"
                    "json", "ini", "cfg" -> "CONFIG"
                    else -> "UNKNOWN"
                }

                result.add(
                    MyFolderFileEntity(
                        id = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString(),
                        name = name,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        extension = ext,
                        fileType = type,
                        lastModified = file.lastModified(),
                        isConvertedSup = ext == "sup",
                        isConvertedCartridge = dir.name == "Cartridges"
                    )
                )
            }
        }
    }

    /**
     * Imports a source file into virtual storage (MyFolder), placing it in the
     * appropriate subdirectory based on its file extension.
     *
     * If deleteOriginalAfter is true, safely deletes the source file from the original
     * downloads/storage location after copying and verifying size to free up physical space.
     */
    fun importFileToVirtualStorage(sourceFile: File, deleteOriginalAfter: Boolean = false): File {
        if (!sourceFile.exists()) return sourceFile
        val rootDir = getStorageDir()
        val ext = sourceFile.extension.lowercase()
        val subDirName = when (ext) {
            "nsp", "xci", "nsz", "xcz" -> "Games"
            "nro", "nso" -> "Homebrew"
            "sup" -> "SUP_Containers"
            "zip", "7z" -> "Archives"
            "keys", "dat" -> "Backups"
            else -> "Games"
        }
        val targetDir = File(rootDir, subDirName).apply { mkdirs() }
        val targetFile = File(targetDir, sourceFile.name)

        if (sourceFile.absolutePath != targetFile.absolutePath) {
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (deleteOriginalAfter && targetFile.exists() && targetFile.length() == sourceFile.length()) {
                try {
                    sourceFile.delete()
                } catch (e: Exception) {
                    // Ignore deletion failure if read-only
                }
            }
        }

        return targetFile
    }

    /**
     * Deletes the original downloaded file from storage if the virtual copy exists.
     */
    fun deleteOriginalDownloadFile(originalFilePath: String): Boolean {
        return try {
            val file = File(originalFilePath)
            if (file.exists() && file.isFile) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
