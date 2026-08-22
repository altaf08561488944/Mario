package com.example.data.repository

import android.content.Context
import com.example.data.dao.SwtcDao
import com.example.data.entity.BootConfigEntity
import com.example.data.entity.MyFolderFileEntity
import com.example.data.entity.VirtualCartridgeEntity
import com.example.storage.CartridgeBuildResult
import com.example.storage.SupContainerProcessor
import com.example.storage.VirtualStorageManager
import com.example.storage.VirtualStorageStats
import com.example.system.HardwareInspector
import com.example.system.RealHardwareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class SwtcRepository(
    private val context: Context,
    private val dao: SwtcDao
) {
    private val storageManager = VirtualStorageManager(context)
    private val supProcessor = SupContainerProcessor(context)

    fun getBootConfigFlow(): Flow<BootConfigEntity?> = dao.getBootConfigFlow()

    suspend fun getBootConfig(): BootConfigEntity {
        return dao.getBootConfig() ?: BootConfigEntity().also {
            dao.saveBootConfig(it)
        }
    }

    suspend fun updateBootConfig(config: BootConfigEntity) = withContext(Dispatchers.IO) {
        dao.saveBootConfig(config)
    }

    fun getAllCartridgesFlow(): Flow<List<VirtualCartridgeEntity>> = dao.getAllCartridgesFlow()

    suspend fun insertCartridge(cartridge: VirtualCartridgeEntity) = withContext(Dispatchers.IO) {
        dao.insertCartridge(cartridge)
    }

    suspend fun deleteCartridge(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCartridge(id)
    }

    fun getAllFolderFilesFlow(): Flow<List<MyFolderFileEntity>> = dao.getAllFolderFilesFlow()

    suspend fun refreshAndScanFolderFiles() = withContext(Dispatchers.IO) {
        val scannedFiles = storageManager.scanFiles()
        dao.clearFolderFiles()
        for (file in scannedFiles) {
            dao.insertFolderFile(file)
        }
    }

    suspend fun addDemoSampleGameFile(fileName: String, type: String, sizeBytes: Long) = withContext(Dispatchers.IO) {
        val storageDir = storageManager.getStorageDir()
        val subDirName = when (type) {
            "GAME" -> "Games"
            "HOMEBREW" -> "Homebrew"
            "SUP_CONTAINER" -> "SUP_Containers"
            "BIOS_KEY" -> "Backups"
            else -> "Games"
        }
        val targetDir = File(storageDir, subDirName)
        targetDir.mkdirs()

        val sampleFile = File(targetDir, fileName)
        if (!sampleFile.exists()) {
            sampleFile.writeText("SWTC_NOOS_SAMPLE_CONTENT|Type=$type|Size=$sizeBytes\nTimestamp=${System.currentTimeMillis()}")
        }

        val ext = sampleFile.extension.lowercase()
        val entity = MyFolderFileEntity(
            id = java.util.UUID.nameUUIDFromBytes(sampleFile.absolutePath.toByteArray()).toString(),
            name = sampleFile.name,
            path = sampleFile.absolutePath,
            sizeBytes = if (sizeBytes > 0) sizeBytes else sampleFile.length(),
            extension = ext,
            fileType = type,
            lastModified = System.currentTimeMillis()
        )
        dao.insertFolderFile(entity)
    }

    fun getVirtualStorageStats(capacityGb: Int): VirtualStorageStats {
        return storageManager.calculateStats(capacityGb)
    }

    suspend fun convertFileToSup(
        fileEntity: MyFolderFileEntity,
        onProgress: (String, Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val srcFile = File(fileEntity.path)
        val supDir = File(storageManager.getStorageDir(), "SUP_Containers")
        supDir.mkdirs()

        val createdSup = supProcessor.convertToSup(srcFile, supDir, onProgress)
        refreshAndScanFolderFiles()
        createdSup
    }

    suspend fun convertFileToCartridge(
        fileEntity: MyFolderFileEntity,
        onProgress: (String, Float) -> Unit
    ): CartridgeBuildResult = withContext(Dispatchers.IO) {
        val cartDir = File(storageManager.getStorageDir(), "Cartridges")
        cartDir.mkdirs()

        val result = supProcessor.convertToCartridge(fileEntity, cartDir, onProgress)
        dao.insertCartridge(result.cartridge)
        refreshAndScanFolderFiles()
        result
    }

    fun inspectDeviceHardware(): RealHardwareInfo {
        return HardwareInspector.inspectDevice(context)
    }
}
