import sys

content = open("app/src/main/java/com/example/MainActivity.kt").read()

if "import com.example.ui.screens.SettingsScreen" not in content:
    content = content.replace("import com.example.ui.screens.WebEnvironmentScreen", "import com.example.ui.screens.WebEnvironmentScreen\nimport com.example.ui.screens.SettingsScreen\nimport com.example.emulator.settings.EmulatorSettingsManager")

# Inject SettingsManager
if "private lateinit var settingsManager: EmulatorSettingsManager" not in content:
    content = content.replace("private val viewModel: SwtcViewModel by viewModels()", "private val viewModel: SwtcViewModel by viewModels()\n    private lateinit var settingsManager: EmulatorSettingsManager")

if "settingsManager = EmulatorSettingsManager(this)" not in content:
    content = content.replace("setContent {", "settingsManager = EmulatorSettingsManager(this)\n        setContent {")

if "val currentSettings by settingsManager.settings.collectAsStateWithLifecycle()" not in content:
    content = content.replace("val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()", "val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()\n                val currentSettings by settingsManager.settings.collectAsStateWithLifecycle()")

# Add Settings Tab routing
old_routing = """                                    SwtcTab.HARDWARE_MONITOR -> HardwareMonitorScreen(
                                        hardwareInfo = hardwareInfo,
                                        installedEmulators = installedEmulators,
                                        onLaunchEmulator = { pkg ->
                                            com.example.emulator.TargetEmulatorManager.launchEmulatorApp(this@MainActivity, pkg)
                                        }
                                    )
                                }"""
new_routing = """                                    SwtcTab.HARDWARE_MONITOR -> HardwareMonitorScreen(
                                        hardwareInfo = hardwareInfo,
                                        installedEmulators = installedEmulators,
                                        onLaunchEmulator = { pkg ->
                                            com.example.emulator.TargetEmulatorManager.launchEmulatorApp(this@MainActivity, pkg)
                                        }
                                    )
                                    SwtcTab.SETTINGS -> SettingsScreen(
                                        settings = currentSettings,
                                        onSettingsChanged = { newSettings -> settingsManager.updateSettings(newSettings) }
                                    )
                                }"""
content = content.replace(old_routing, new_routing)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

