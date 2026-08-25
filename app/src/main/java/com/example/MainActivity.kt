package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SwtcNavBar
import com.example.ui.screens.ActiveEmulationScreen
import com.example.ui.screens.BootSetupScreen
import com.example.ui.screens.CartridgeLibraryScreen
import com.example.ui.screens.HardwareMonitorScreen
import com.example.ui.screens.MyFolderScreen
import com.example.ui.screens.SaveStatesScreen
import com.example.ui.screens.VirtualStorageScreen
import com.example.ui.screens.WebEnvironmentScreen
import com.example.ui.screens.SettingsScreen
import com.example.emulator.settings.EmulatorSettingsManager
import com.example.ui.theme.SwtcNoosTheme
import com.example.viewmodel.SwtcTab
import com.example.viewmodel.SwtcViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SwtcViewModel by viewModels()
    private lateinit var settingsManager: EmulatorSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsManager = EmulatorSettingsManager(this)
        setContent {
            SwtcNoosTheme {
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
                val currentSettings by settingsManager.settings.collectAsStateWithLifecycle()
                val bootConfig by viewModel.bootConfig.collectAsStateWithLifecycle()
                val cartridges by viewModel.cartridges.collectAsStateWithLifecycle()
                val folderFiles by viewModel.folderFiles.collectAsStateWithLifecycle()
                val saveStates by viewModel.saveStates.collectAsStateWithLifecycle()
                val hardwareInfo by viewModel.hardwareInfo.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                val installedEmulators by viewModel.installedEmulators.collectAsStateWithLifecycle()
                val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
                val coreState by viewModel.coreEngineState.collectAsStateWithLifecycle()
                val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearUserMessage()
                    }
                }
                
                LaunchedEffect(currentSettings.targetFps) {
                    viewModel.updateCoreSettings(currentSettings.targetFps)
                }

                if (activeSession.isRunning) {
                    ActiveEmulationScreen(
                        session = activeSession,
                        coreState = coreState,
                        onToggleDocked = { viewModel.toggleDockedMode() },
                        onStopEmulation = { viewModel.stopEmulationSession() },
                        onQuickSave = { viewModel.quickSaveCurrentEmulation() },
                        onRunDevSelfTest = { viewModel.runDevCpuSelfTest() }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            SwtcNavBar(
                                selectedTab = selectedTab,
                                onTabSelected = { viewModel.selectTab(it) }
                            )
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Crossfade(targetState = selectedTab, label = "TabCrossfade") { tab ->
                                when (tab) {
                                    SwtcTab.BOOT_SETUP -> BootSetupScreen(
                                        bootConfig = bootConfig,
                                        hardwareInfo = hardwareInfo,
                                        conversionState = conversionState,
                                        onImportKeysUri = { uri -> viewModel.importKeysFromUri(uri) },
                                        onImportFirmwareUri = { uri -> viewModel.importFirmwareFromUri(uri) },
                                        onQuickLoadVerifiedKeys = { viewModel.quickLoad100PercentKeys() },
                                        onQuickLoadVerifiedFirmware = { viewModel.quickLoad100PercentFirmware() },
                                        onSelectBios = { name, path -> viewModel.selectBiosFile(name, path) },
                                        onSelectFirmware = { name, path -> viewModel.selectFirmwareFile(name, path) },
                                        onUpdateDns = { mode, customDns -> viewModel.updateDnsSetting(mode, customDns) },
                                        onLetsGoBoot = { viewModel.executeLetsGoBoot() }
                                    )

                                    SwtcTab.VIRTUAL_STORAGE -> VirtualStorageScreen(
                                        stats = viewModel.getVirtualStorageStats(),
                                        onSelectCapacity = { capacity -> viewModel.setVirtualStorageCapacity(capacity) }
                                    )

                                    SwtcTab.MY_FOLDER -> MyFolderScreen(
                                        files = folderFiles,
                                        conversionState = conversionState,
                                        onRefresh = { viewModel.refreshFolderFiles() },
                                        onConvertToSup = { file -> viewModel.convertFileToSup(file) },
                                        onConvertToCartridge = { file -> viewModel.convertFileToCartridge(file) },
                                        onCreateSampleFile = { name, format, sizeMb ->
                                            viewModel.createSampleGameInMyFolder(name, format, sizeMb)
                                        }
                                    )

                                    SwtcTab.CARTRIDGE_LIBRARY -> CartridgeLibraryScreen(
                                        cartridges = cartridges,
                                        onLaunchCartridge = { cartridge -> viewModel.launchCartridge(cartridge) },
                                        onDeleteCartridge = { id -> viewModel.deleteCartridge(id) },
                                        onGoToMyFolder = { viewModel.selectTab(SwtcTab.MY_FOLDER) },
                                        onGoToSaveStates = { viewModel.selectTab(SwtcTab.SAVE_STATES) }
                                    )

                                    SwtcTab.SAVE_STATES -> SaveStatesScreen(
                                        saveStates = saveStates,
                                        cartridges = cartridges,
                                        activeGameTitle = activeSession.gameTitle.ifEmpty { null },
                                        onLoadState = { state -> viewModel.loadSaveState(state) },
                                        onRenameState = { id, name -> viewModel.renameSaveState(id, name) },
                                        onDeleteState = { id -> viewModel.deleteSaveState(id) },
                                        onExportState = { state, uri -> viewModel.exportSaveState(state, uri) },
                                        onShareState = { state ->
                                            val intent = viewModel.getShareIntentForState(state)
                                            if (intent != null) {
                                                startActivity(intent)
                                            } else {
                                                viewModel.showUserMessage("Unable to share save state.")
                                            }
                                        },
                                        onImportState = { uri -> viewModel.importSaveState(uri) },
                                        onCreateNewState = { title, id, slot ->
                                            viewModel.createSaveState(slotName = slot, gameTitle = title, titleId = id)
                                        }
                                    )

                                    SwtcTab.WEB_ENVIRONMENT -> WebEnvironmentScreen(
                                        bootConfig = bootConfig
                                    )

                                    SwtcTab.HARDWARE_MONITOR -> HardwareMonitorScreen(
                                        hardwareInfo = hardwareInfo,
                                        installedEmulators = installedEmulators,
                                        onLaunchEmulator = { pkg ->
                                            com.example.emulator.TargetEmulatorManager.launchEmulatorApp(this@MainActivity, pkg)
                                        }
                                    )
                                    SwtcTab.SETTINGS -> SettingsScreen(
                                        settings = currentSettings,
                                        bootConfig = bootConfig,
                                        onImportKeysUri = { uri -> viewModel.importKeysFromUri(uri) },
                                        onImportFirmwareUri = { uri -> viewModel.importFirmwareFromUri(uri) },
                                        onQuickLoadVerifiedKeys = { viewModel.quickLoad100PercentKeys() },
                                        onQuickLoadVerifiedFirmware = { viewModel.quickLoad100PercentFirmware() },
                                        onSettingsChanged = { newSettings -> settingsManager.updateSettings(newSettings) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
