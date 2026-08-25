package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BootConfigEntity
import com.example.emulator.SwitchKeysManager
import com.example.system.RealHardwareInfo
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.ConversionProgress

@Composable
fun BootSetupScreen(
    bootConfig: BootConfigEntity?,
    hardwareInfo: RealHardwareInfo?,
    conversionState: ConversionProgress,
    onImportKeysUri: (Uri) -> Unit = {},
    onImportFirmwareUri: (Uri) -> Unit = {},
    onQuickLoadVerifiedKeys: () -> Unit = {},
    onQuickLoadVerifiedFirmware: () -> Unit = {},
    onRunDiagnostics: () -> Unit = {},
    onSelectBios: (String, String) -> Unit,
    onSelectFirmware: (String, String) -> Unit,
    onUpdateDns: (String, String) -> Unit,
    onLetsGoBoot: () -> Unit
) {
    val scrollState = rememberScrollState()
    val config = bootConfig ?: BootConfigEntity()

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

    var customDnsInput by remember(config.customDns) { mutableStateOf(config.customDns) }
    var selectedDnsMode by remember(config.dnsMode) { mutableStateOf(config.dnsMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceVariantDark,
                            SurfaceDark
                        )
                    )
                )
                .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(NeonRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SWTC NOOS",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(NeonBlue)
                    )
                }

                Text(
                    text = "Nintendo Switch Emulator — Android 10 Compatible",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonBlue,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = "Virtual Storage • Web Environment • External My Folder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Boot Progress Overlay / Card
        AnimatedVisibility(visible = conversionState.isConverting) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonRed, NeonBlue)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BOOT PREPARATION IN PROGRESS",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = conversionState.statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { conversionState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonBlue,
                        trackColor = SurfaceBorder,
                    )
                }
            }
        }

        // 1. SELECT / CHOOSE PROD.KEYS CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    if (config.isBiosVerified || keySet.isLoaded) listOf(NeonGreen, NeonBlue) else listOf(SurfaceBorder, SurfaceBorder)
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "BIOS",
                        tint = if (config.isBiosVerified || keySet.isLoaded) NeonGreen else NeonRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PRODUCTION KEYS (prod.keys)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (config.isBiosVerified || keySet.isLoaded) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = NeonGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(NeonGreen, NeonGreen)))
                                ) {
                                    Text(
                                        text = "100% OK",
                                        color = NeonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (config.isBiosVerified || keySet.isLoaded) "Status: ${keySet.keySourceMessage.ifEmpty { config.biosName }}" else "Required: Choose prod.keys, title.keys, or load verified keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (config.isBiosVerified || keySet.isLoaded) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (config.isBiosVerified || keySet.isLoaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = NeonGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            keyPickerLauncher.launch(arrayOf("*/*", "text/plain", "application/octet-stream"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_bios_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose prod.keys", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            onQuickLoadVerifiedKeys()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_verified_keys_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load 100% OK Keys", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        // 2. SELECT / CHOOSE FIRMWARE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    if (config.isFirmwareVerified) listOf(NeonGreen, NeonBlue) else listOf(SurfaceBorder, SurfaceBorder)
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Firmware",
                        tint = if (config.isFirmwareVerified) NeonGreen else NeonBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "HORIZON OS FIRMWARE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (config.isFirmwareVerified) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = NeonGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(NeonGreen, NeonGreen)))
                                ) {
                                    Text(
                                        text = "100% OK",
                                        color = NeonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (config.isFirmwareVerified) "Selected: ${config.firmwareName}" else "Select firmware ZIP archive (.zip) or NCA packages",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (config.isFirmwareVerified) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (config.isFirmwareVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = NeonGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            firmwarePickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_firmware_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Firmware", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            onQuickLoadVerifiedFirmware()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_verified_firmware_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load FW v17.0.1 (100% OK)", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onRunDiagnostics,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("run_diagnostics_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
        ) {
            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Key & Firmware Diagnostic Tool", fontWeight = FontWeight.Bold)
        }

        // 3. SELECT WEB ENVIRONMENT CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "DNS",
                        tint = NeonBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SELECT WEB ENVIRONMENT (DNS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Radio 1: Google DNS 8.8.8.8
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedDnsMode == "GOOGLE_DNS",
                        onClick = {
                            selectedDnsMode = "GOOGLE_DNS"
                            onUpdateDns("GOOGLE_DNS", "8.8.8.8")
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google DNS — 8.8.8.8",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Radio 2: Other DNS
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedDnsMode == "OTHER_DNS",
                        onClick = {
                            selectedDnsMode = "OTHER_DNS"
                            onUpdateDns("OTHER_DNS", customDnsInput)
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Other DNS — Enter Custom DNS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                if (selectedDnsMode == "OTHER_DNS") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDnsInput,
                        onValueChange = {
                            customDnsInput = it
                            onUpdateDns("OTHER_DNS", it)
                        },
                        label = { Text("Custom DNS IP") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = SurfaceBorder
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. ACTION BUTTON: LET'S GO BOOT
        Button(
            onClick = onLetsGoBoot,
            enabled = !conversionState.isConverting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("boot_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonRed,
                contentColor = Color.White
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = "Boot",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "LET'S GO BOOT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hardware Inspection Badge
        if (hardwareInfo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceVariantDark,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hardware,
                        contentDescription = "Hardware",
                        tint = NeonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${hardwareInfo.manufacturer} ${hardwareInfo.deviceName} (${hardwareInfo.cpuHardwareName})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Android ${hardwareInfo.androidVersion} (API ${hardwareInfo.apiLevel}) • ${hardwareInfo.cpuCoreCount} Cores • ${hardwareInfo.totalRamMb / 1024} GB RAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
