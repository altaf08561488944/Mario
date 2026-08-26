package com.example.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.dao.SwtcDao
import com.example.data.entity.VirtualCartridgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryScannerService(
    private val context: Context,
    private val dao: SwtcDao
) {
    suspend fun scanAndPopulateLibrary(): Int = withContext(Dispatchers.IO) {
        val rootsToScan = mutableListOf<File>()
        
        // Scan internal storage (context.filesDir)
        rootsToScan.add(context.filesDir)
        
        // Scan external storage if accessible
        val extStorage = Environment.getExternalStorageDirectory()
        if (extStorage != null && extStorage.exists() && extStorage.canRead()) {
            rootsToScan.add(extStorage)
        }
        
        context.getExternalFilesDir(null)?.let { rootsToScan.add(it) }

        var foundCount = 0
        
        // Get existing paths to avoid duplicates
        val existingCartridges = dao.getAllCartridgesFlow().firstOrNull() ?: emptyList()
        val existingPaths = existingCartridges.map { it.originalFilePath }.toSet()

        for (root in rootsToScan) {
            try {
                root.walkTopDown()
                    .maxDepth(5) // Limit depth to avoid scanning huge directories indefinitely
                    .filter { it.isFile && (it.extension.equals("nsp", ignoreCase = true) || it.extension.equals("nro", ignoreCase = true)) }
                    .forEach { file ->
                        if (!existingPaths.contains(file.absolutePath)) {
                            val ext = file.extension.uppercase()
                            val title = file.nameWithoutExtension.replace("_", " ")
                            val id = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString()
                            
                            val titleId = "0100" + title.hashCode().toUInt().toString(16).padStart(12, '0').take(12).uppercase()
                            
                            val entity = VirtualCartridgeEntity(
                                id = id,
                                title = title,
                                titleId = titleId,
                                publisher = "Local Storage",
                                version = "1.0.0",
                                sizeBytes = file.length(),
                                sourceFormat = ext,
                                originalFilePath = file.absolutePath,
                                createdTimestamp = System.currentTimeMillis(),
                                isPlayable = true
                            )
                            dao.insertCartridge(entity)
                            foundCount++
                            Log.i("LibraryScanner", "Added cartridge to library: $title")
                        }
                    }
            } catch (e: Exception) {
                Log.e("LibraryScanner", "Error scanning $root: ${e.message}")
            }
        }
        
        return@withContext foundCount
    }
}
