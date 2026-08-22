package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.EmulatorTargetInfo
import com.example.system.RealHardwareInfo
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark

@Composable
fun HardwareMonitorScreen(
    hardwareInfo: RealHardwareInfo?,
    installedEmulators: List<EmulatorTargetInfo>,
    onLaunchEmulator: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(listOf(SurfaceVariantDark, SurfaceDark))
                )
                .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Hardware,
                    contentDescription = "Hardware",
                    tint = NeonGreen,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "REAL SYSTEM HARDWARE INSPECTOR",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "100% Real CPU, GPU, RAM & OS Metrics",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (hardwareInfo != null) {
            // Android 10+ Compatibility Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        if (hardwareInfo.isAndroid10Plus) listOf(NeonGreen, NeonBlue) else listOf(NeonRed, NeonYellow)
                    )
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hardwareInfo.isAndroid10Plus) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hardwareInfo.isAndroid10Plus) NeonGreen else NeonRed,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (hardwareInfo.isAndroid10Plus) "Android 10+ Compatible (API ${hardwareInfo.apiLevel})" else "Legacy Android Detected (API ${hardwareInfo.apiLevel})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Target minimum requirement: Android 10 (API 29+). SWTC NOOS frontend and virtual storage layer fully compatible.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid of Hardware Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HardwareCard(
                    title = "CPU & SoC",
                    icon = Icons.Default.DeveloperBoard,
                    accentColor = NeonBlue,
                    modifier = Modifier.weight(1f),
                    details = listOf(
                        "Hardware" to hardwareInfo.cpuHardwareName,
                        "Cores" to "${hardwareInfo.cpuCoreCount} Cores",
                        "ABI" to hardwareInfo.cpuArchitecture
                    )
                )

                HardwareCard(
                    title = "System RAM",
                    icon = Icons.Default.Memory,
                    accentColor = NeonYellow,
                    modifier = Modifier.weight(1f),
                    details = listOf(
                        "Total RAM" to "${hardwareInfo.totalRamMb / 1024} GB (${hardwareInfo.totalRamMb} MB)",
                        "Available" to "${hardwareInfo.availableRamMb} MB",
                        "Low RAM" to if (hardwareInfo.isLowRam) "Yes" else "No"
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HardwareCard(
                    title = "GPU & Graphics",
                    icon = Icons.Default.Speed,
                    accentColor = NeonGreen,
                    modifier = Modifier.weight(1f),
                    details = listOf(
                        "OpenGL ES" to hardwareInfo.openGlEsVersion,
                        "Vulkan" to if (hardwareInfo.hasVulkanSupport) "Supported" else "Not Detected"
                    )
                )

                HardwareCard(
                    title = "Internal Storage",
                    icon = Icons.Default.SdStorage,
                    accentColor = NeonRed,
                    modifier = Modifier.weight(1f),
                    details = listOf(
                        "Total" to "%.1f GB".format(hardwareInfo.internalStorageTotalGb),
                        "Available" to "%.1f GB".format(hardwareInfo.internalStorageAvailableGb)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // TARGET EMULATOR DETECTOR
        Text(
            text = "TARGET EMULATOR APK DETECTOR",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SWTC NOOS acts as the frontend management layer. Select an installed target emulator APK to run games:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                installedEmulators.forEach { target ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceVariantDark
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = if (target.isInstalled) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = target.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (target.isInstalled) "INSTALLED (${target.packageName})" else "Not Detected (${target.packageName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (target.isInstalled) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (target.isInstalled) {
                                Button(
                                    onClick = { onLaunchEmulator(target.packageName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.testTag("launch_target_${target.packageName}")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("LAUNCH", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    details: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            details.forEach { (label, valText) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = valText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
