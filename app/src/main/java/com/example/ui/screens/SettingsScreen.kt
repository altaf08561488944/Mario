package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BootConfigEntity
import com.example.emulator.SwitchKeysManager
import com.example.emulator.settings.AudioBackend
import com.example.emulator.settings.CpuAccuracy
import com.example.emulator.settings.EmulatorSettings
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: EmulatorSettings,
    bootConfig: BootConfigEntity? = null,
    onImportKeysUri: (Uri) -> Unit = {},
    onImportFirmwareUri: (Uri) -> Unit = {},
    onQuickLoadVerifiedKeys: () -> Unit = {},
    onQuickLoadVerifiedFirmware: () -> Unit = {},
    onSettingsChanged: (EmulatorSettings) -> Unit
) {
    var currentSettings by remember(settings) { mutableStateOf(settings) }

    val keyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportKeysUri(it) }
    }

    val firmwarePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportFirmwareUri(it) }
    }

    val keySet = SwitchKeysManager.getKeySet()
    val config = bootConfig ?: BootConfigEntity()

    fun updateAndSave(update: EmulatorSettings.() -> EmulatorSettings) {
        currentSettings = currentSettings.update()
        onSettingsChanged(currentSettings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emulator & Horizon OS Settings", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // KEYS & FIRMWARE SECTION
            item {
                SettingsSectionHeader("Horizon OS Keys & Firmware", Icons.Default.Key)
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Production Keys (prod.keys)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (config.isBiosVerified || keySet.isLoaded) {
                                Surface(
                                    color = NeonGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "100% OK",
                                        color = NeonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (config.isBiosVerified || keySet.isLoaded) keySet.keySourceMessage.ifEmpty { config.biosName.orEmpty() } else "No keys installed. Required to decrypt NCAs & XCIs.",
                            color = if (config.isBiosVerified || keySet.isLoaded) NeonGreen else Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Button(
                                onClick = { keyPickerLauncher.launch(arrayOf("*/*", "text/plain", "application/octet-stream")) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Choose prod.keys", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onQuickLoadVerifiedKeys() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load 100% OK Keys", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Horizon System Firmware",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (config.isFirmwareVerified) {
                                Surface(
                                    color = NeonGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "100% OK",
                                        color = NeonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (config.isFirmwareVerified) config.firmwareName.orEmpty() else "No firmware package installed.",
                            color = if (config.isFirmwareVerified) NeonGreen else Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Button(
                                onClick = { firmwarePickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Choose Firmware", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onQuickLoadVerifiedFirmware() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load FW v17.0.1", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // GRAPHICS SECTION
            item {
                SettingsSectionHeader("Graphics & Display", Icons.Default.DesktopWindows)
                
                SettingsSlider(
                    title = "Target FPS (Frame Pacing)",
                    description = "Locks the emulator speed to a specific framerate to prevent stuttering. (Recommended: 34 - 60)",
                    value = currentSettings.targetFps.toFloat(),
                    valueRange = 15f..60f,
                    steps = 44, // 60 - 15 - 1
                    onValueChange = { updateAndSave { copy(targetFps = it.toInt()) } },
                    valueText = "${currentSettings.targetFps} FPS"
                )

                SettingsSwitch(
                    title = "Enable V-Sync",
                    description = "Synchronizes guest frames with host refresh rate to prevent screen tearing.",
                    checked = currentSettings.enableVsync,
                    onCheckedChange = { updateAndSave { copy(enableVsync = it) } }
                )

                SettingsSwitch(
                    title = "Docked Mode",
                    description = "Emulates the console in Docked mode (1080p profile) instead of Handheld (720p).",
                    checked = currentSettings.isDockedMode,
                    onCheckedChange = { updateAndSave { copy(isDockedMode = it) } }
                )

                SettingsSwitch(
                    title = "Asynchronous Shaders",
                    description = "Compiles shaders in the background to heavily reduce micro-stuttering during gameplay.",
                    checked = currentSettings.asynchronousShaders,
                    onCheckedChange = { updateAndSave { copy(asynchronousShaders = it) } }
                )
            }

            // CPU SECTION
            item {
                SettingsSectionHeader("CPU & JIT Engine", Icons.Default.Memory)
                
                Text(
                    text = "CPU Accuracy Mode",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Lower accuracy speeds up emulation but may cause game glitches.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpuAccuracy.entries.forEach { accuracy ->
                        FilterChip(
                            selected = currentSettings.cpuAccuracy == accuracy,
                            onClick = { updateAndSave { copy(cpuAccuracy = accuracy) } },
                            label = { Text(accuracy.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // AUDIO SECTION
            item {
                SettingsSectionHeader("Audio Subsystem", Icons.Default.Speaker)
                
                Text(
                    text = "Audio Backend",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioBackend.entries.forEach { backend ->
                        FilterChip(
                            selected = currentSettings.audioBackend == backend,
                            onClick = { updateAndSave { copy(audioBackend = backend) } },
                            label = { Text(backend.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = NeonRed, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    Divider(color = SurfaceDark, thickness = 2.dp, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
fun SettingsSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = description, color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonRed)
        )
    }
}

@Composable
fun SettingsSlider(
    title: String, 
    description: String, 
    value: Float, 
    valueRange: ClosedFloatingPointRange<Float>, 
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueText: String
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = valueText, color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(text = description, color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = NeonRed, activeTrackColor = NeonRed, inactiveTrackColor = SurfaceDark)
        )
    }
}

