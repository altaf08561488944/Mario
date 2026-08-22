package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.SwtcDatabase
import com.example.data.entity.BootConfigEntity
import com.example.data.entity.MyFolderFileEntity
import com.example.data.entity.VirtualCartridgeEntity
import com.example.data.repository.SwtcRepository
import com.example.emulator.EmulatorTargetInfo
import com.example.emulator.TargetEmulatorManager
import com.example.storage.VirtualStorageStats
import com.example.system.RealHardwareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class SwtcTab {
    BOOT_SETUP,
    VIRTUAL_STORAGE,
    MY_FOLDER,
    CARTRIDGE_LIBRARY,
    WEB_ENVIRONMENT,
    HARDWARE_MONITOR
}

data class ConversionProgress(
    val isConverting: Boolean = false,
    val statusText: String = "",
    val progress: Float = 0f,
    val targetFileName: String = "",
    val logs: List<String> = emptyList()
)

data class ActiveEmulationSession(
    val isRunning: Boolean = false,
    val gameTitle: String = "",
    val titleId: String = "",
    val sourceFormat: String = "",
    val fps: Int = 60
)

class SwtcViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SwtcRepository
    
    val bootConfig: StateFlow<BootConfigEntity?>
    val cartridges: StateFlow<List<VirtualCartridgeEntity>>
    val folderFiles: StateFlow<List<MyFolderFileEntity>>

    private val _selectedTab = MutableStateFlow(SwtcTab.BOOT_SETUP)
    val selectedTab: StateFlow<SwtcTab> = _selectedTab.asStateFlow()

    private val _conversionState = MutableStateFlow(ConversionProgress())
    val conversionState: StateFlow<ConversionProgress> = _conversionState.asStateFlow()

    private val _hardwareInfo = MutableStateFlow<RealHardwareInfo?>(null)
    val hardwareInfo: StateFlow<RealHardwareInfo?> = _hardwareInfo.asStateFlow()

    private val _installedEmulators = MutableStateFlow<List<EmulatorTargetInfo>>(emptyList())
    val installedEmulators: StateFlow<List<EmulatorTargetInfo>> = _installedEmulators.asStateFlow()

    private val _activeSession = MutableStateFlow(ActiveEmulationSession())
    val activeSession: StateFlow<ActiveEmulationSession> = _activeSession.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        val dao = SwtcDatabase.getDatabase(application).swtcDao()
        repository = SwtcRepository(application, dao)

        bootConfig = repository.getBootConfigFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        cartridges = repository.getAllCartridgesFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        folderFiles = repository.getAllFolderFilesFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            // Initialize default boot config if absent
            repository.getBootConfig()
            
            // Inspect device
            val hw = repository.inspectDeviceHardware()
            _hardwareInfo.value = hw
            
            // Check installed emulators
            _installedEmulators.value = TargetEmulatorManager.getInstalledTargetEmulators(application)

            // Seed sample files for My Folder if empty
            repository.refreshAndScanFolderFiles()
            
            // If boot is completed, switch to Virtual Storage or Library
            val currentBoot = repository.getBootConfig()
            if (currentBoot.isBooted) {
                _selectedTab.value = SwtcTab.MY_FOLDER
            }
        }
    }

    fun selectTab(tab: SwtcTab) {
        _selectedTab.value = tab
    }

    fun updateDnsSetting(dnsMode: String, customDns: String) {
        viewModelScope.launch {
            val current = repository.getBootConfig()
            val updated = current.copy(dnsMode = dnsMode, customDns = customDns)
            repository.updateBootConfig(updated)
            showUserMessage("DNS setting updated: ${if (dnsMode == "GOOGLE_DNS") "8.8.8.8" else customDns}")
        }
    }

    fun selectBiosFile(name: String, path: String) {
        viewModelScope.launch {
            val current = repository.getBootConfig()
            val updated = current.copy(
                biosName = name,
                biosPath = path,
                isBiosVerified = true
            )
            repository.updateBootConfig(updated)
            showUserMessage("BIOS file set: $name")
        }
    }

    fun selectFirmwareFile(name: String, path: String) {
        viewModelScope.launch {
            val current = repository.getBootConfig()
            val updated = current.copy(
                firmwareName = name,
                firmwarePath = path,
                isFirmwareVerified = true
            )
            repository.updateBootConfig(updated)
            showUserMessage("Firmware package set: $name")
        }
    }

    fun executeLetsGoBoot() {
        viewModelScope.launch {
            val current = repository.getBootConfig()
            _conversionState.value = ConversionProgress(
                isConverting = true,
                statusText = "INITIALIZING SWTC NOOS ENVIRONMENT...",
                progress = 0.2f
            )

            kotlinx.coroutines.delay(600)

            _conversionState.value = ConversionProgress(
                isConverting = true,
                statusText = "CHECKING BIOS & FIRMWARE INTEGRITY...",
                progress = 0.5f
            )

            kotlinx.coroutines.delay(600)

            _conversionState.value = ConversionProgress(
                isConverting = true,
                statusText = "CONFIGURING VIRTUAL STORAGE & DNS (${if (current.dnsMode == "GOOGLE_DNS") "8.8.8.8" else current.customDns})...",
                progress = 0.8f
            )

            kotlinx.coroutines.delay(600)

            val updated = current.copy(isBooted = true)
            repository.updateBootConfig(updated)

            _conversionState.value = ConversionProgress(
                isConverting = false,
                statusText = "BOOT SUCCESSFUL!",
                progress = 1.0f
            )

            showUserMessage("SWTC NOOS Booted Successfully!")
            _selectedTab.value = SwtcTab.MY_FOLDER
        }
    }

    fun resetBootSetup() {
        viewModelScope.launch {
            val current = repository.getBootConfig()
            val updated = current.copy(isBooted = false)
            repository.updateBootConfig(updated)
            _selectedTab.value = SwtcTab.BOOT_SETUP
            showUserMessage("SWTC NOOS reset to Boot Setup mode.")
        }
    }

    fun setVirtualStorageCapacity(capacityGb: Int) {
        viewModelScope.launch {
            val current = repository.getBootConfig()
            val updated = current.copy(storageCapacityGb = capacityGb)
            repository.updateBootConfig(updated)
            showUserMessage("Virtual Storage Capacity set to $capacityGb GB.")
        }
    }

    fun getVirtualStorageStats(): VirtualStorageStats {
        val capacity = bootConfig.value?.storageCapacityGb ?: 128
        return repository.getVirtualStorageStats(capacity)
    }

    fun convertFileToSup(fileEntity: MyFolderFileEntity) {
        viewModelScope.launch {
            _conversionState.value = ConversionProgress(
                isConverting = true,
                statusText = "PREPARING FILE...",
                progress = 0.1f,
                targetFileName = fileEntity.name
            )

            try {
                repository.convertFileToSup(fileEntity) { status, progress ->
                    _conversionState.value = _conversionState.value.copy(
                        statusText = status,
                        progress = progress
                    )
                }
                showUserMessage("Converted ${fileEntity.name} to .SUP container!")
            } catch (e: Exception) {
                showUserMessage("Error converting to .SUP: ${e.message}")
            } finally {
                _conversionState.value = ConversionProgress(isConverting = false)
            }
        }
    }

    fun convertFileToCartridge(fileEntity: MyFolderFileEntity) {
        viewModelScope.launch {
            _conversionState.value = ConversionProgress(
                isConverting = true,
                statusText = "PREPARING FILE...",
                progress = 0.1f,
                targetFileName = fileEntity.name
            )

            try {
                val result = repository.convertFileToCartridge(fileEntity) { status, progress ->
                    _conversionState.value = _conversionState.value.copy(
                        statusText = status,
                        progress = progress,
                        logs = _conversionState.value.logs + status
                    )
                }
                showUserMessage("Virtual Cartridge Built: ${result.cartridge.title}!")
                _selectedTab.value = SwtcTab.CARTRIDGE_LIBRARY
            } catch (e: Exception) {
                showUserMessage("Error building cartridge: ${e.message}")
            } finally {
                _conversionState.value = ConversionProgress(isConverting = false)
            }
        }
    }

    fun createSampleGameInMyFolder(name: String, format: String, sizeMb: Int) {
        viewModelScope.launch {
            val sizeBytes = sizeMb.toLong() * 1024L * 1024L
            val fileType = when (format.uppercase()) {
                "NSP", "XCI" -> "GAME"
                "NRO", "NSO" -> "HOMEBREW"
                "SUP" -> "SUP_CONTAINER"
                "KEYS" -> "BIOS_KEY"
                "ZIP" -> "FIRMWARE"
                else -> "GAME"
            }
            val fileName = "$name.$format".lowercase()
            repository.addDemoSampleGameFile(fileName, fileType, sizeBytes)
            showUserMessage("Created file: $fileName in My Folder")
        }
    }

    private val switchCoreEngine = com.example.emulator.SwitchCoreEngine()
    val coreEngineState: StateFlow<com.example.emulator.SwitchCoreState> = switchCoreEngine.engineState

    fun launchCartridge(cartridge: VirtualCartridgeEntity) {
        val emulators = installedEmulators.value.filter { it.isInstalled }
        if (emulators.isNotEmpty()) {
            val target = emulators.first()
            TargetEmulatorManager.launchEmulatorApp(getApplication(), target.packageName)
            showUserMessage("Launching ${cartridge.title} via ${target.appName}...")
        } else {
            switchCoreEngine.startEmulation(cartridge, isDocked = true)
            _activeSession.value = ActiveEmulationSession(
                isRunning = true,
                gameTitle = cartridge.title,
                titleId = cartridge.titleId,
                sourceFormat = cartridge.sourceFormat,
                fps = 60
            )
            showUserMessage("Started SWTC Core Engine for ${cartridge.title}")
        }
    }

    fun launchCartridgeDirectInternal(cartridge: VirtualCartridgeEntity) {
        switchCoreEngine.startEmulation(cartridge, isDocked = true)
        _activeSession.value = ActiveEmulationSession(
            isRunning = true,
            gameTitle = cartridge.title,
            titleId = cartridge.titleId,
            sourceFormat = cartridge.sourceFormat,
            fps = 60
        )
        showUserMessage("Started SWTC Core Engine for ${cartridge.title}")
    }

    fun toggleDockedMode() {
        switchCoreEngine.toggleDockedMode()
    }

    fun stopEmulationSession() {
        switchCoreEngine.stopEmulation()
        _activeSession.value = ActiveEmulationSession(isRunning = false)
    }

    fun deleteCartridge(id: String) {
        viewModelScope.launch {
            repository.deleteCartridge(id)
            showUserMessage("Removed Virtual Cartridge.")
        }
    }

    fun refreshFolderFiles() {
        viewModelScope.launch {
            repository.refreshAndScanFolderFiles()
            showUserMessage("My Folder refreshed.")
        }
    }

    fun showUserMessage(msg: String) {
        _userMessage.value = msg
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
