package com.example.data.repository

import android.content.Context
import com.example.data.dao.SwtcDao
import com.example.data.entity.BootConfigEntity
import com.example.data.entity.MyFolderFileEntity
import com.example.data.entity.SaveStateEntity
import com.example.data.entity.VirtualCartridgeEntity
import com.example.emulator.SwitchCoreState
import com.example.storage.CartridgeBuildResult
import com.example.storage.SaveStateManager
import com.example.storage.SupContainerProcessor
import com.example.storage.VirtualStorageManager
import com.example.storage.VirtualStorageStats
import com.example.storage.LibraryScannerService
import com.example.system.HardwareInspector
import com.example.system.RealHardwareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri

class SwtcRepository(
    private val context: Context,
    private val dao: SwtcDao
) {
    private val storageManager = VirtualStorageManager(context)
    private val supProcessor = SupContainerProcessor(context)
    private val libraryScanner = LibraryScannerService(context, dao)

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

    suspend fun scanAndPopulateLibrary(): Int = withContext(Dispatchers.IO) {
        libraryScanner.scanAndPopulateLibrary()
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

    // ==========================================
    // SAVE STATE OPERATIONS
    // ==========================================

    fun getAllSaveStatesFlow(): Flow<List<SaveStateEntity>> = dao.getAllSaveStatesFlow()

    fun getSaveStatesForTitleFlow(titleId: String): Flow<List<SaveStateEntity>> = dao.getSaveStatesForTitleFlow(titleId)

    suspend fun createSaveState(
        gameTitle: String,
        titleId: String,
        slotName: String,
        coreState: SwitchCoreState?,
        slotIndex: Int = 0,
        isAutoSave: Boolean = false
    ): SaveStateEntity = withContext(Dispatchers.IO) {
        val entity = SaveStateManager.createSaveState(
            context = context,
            gameTitle = gameTitle,
            titleId = titleId,
            slotName = slotName,
            coreState = coreState,
            slotIndex = slotIndex,
            isAutoSave = isAutoSave
        )
        dao.insertSaveState(entity)
        entity
    }

    suspend fun renameSaveState(id: String, newName: String) = withContext(Dispatchers.IO) {
        dao.renameSaveState(id, newName)
    }

    suspend fun deleteSaveState(id: String) = withContext(Dispatchers.IO) {
        val state = dao.getSaveStateById(id)
        if (state != null) {
            SaveStateManager.deleteStateFiles(state)
            dao.deleteSaveState(id)
        }
    }

    suspend fun exportSaveState(state: SaveStateEntity, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        SaveStateManager.exportStateToUri(context, state, targetUri)
    }

    suspend fun importSaveState(sourceUri: Uri): SaveStateEntity? = withContext(Dispatchers.IO) {
        val state = SaveStateManager.importStateFromUri(context, sourceUri)
        if (state != null) {
            dao.insertSaveState(state)
        }
        state
    }

    suspend fun initializeDemoSaveStatesIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllSaveStatesFlow().firstOrNull()
        if (existing.isNullOrEmpty()) {
            createSaveState(
                gameTitle = "Super Mario Odyssey",
                titleId = "0100000000010000",
                slotName = "Slot 1 - Cascade Kingdom Power Moon 12",
                coreState = null,
                slotIndex = 1,
                isAutoSave = false
            )
            createSaveState(
                gameTitle = "Super Mario Odyssey",
                titleId = "0100000000010000",
                slotName = "Auto Save - Metro Kingdom Rooftops",
                coreState = null,
                slotIndex = 0,
                isAutoSave = true
            )
            createSaveState(
                gameTitle = "The Legend of Zelda: Tears of the Kingdom",
                titleId = "0100F2C0115B6000",
                slotName = "Slot 1 - Great Sky Island Temple",
                coreState = null,
                slotIndex = 1,
                isAutoSave = false
            )
        }
    }
}
