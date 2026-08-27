package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.storage.NcaDecryptionStatus
import com.example.storage.ScannedRomResult
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.RomScanState
import java.io.File

@Composable
fun RomScannerDialog(
    scanState: RomScanState,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onLaunchGame: (File) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Discovered Games, 1: Live Terminal Log
    val logListState = rememberLazyListState()

    // Auto-scroll logs to bottom during scan
    LaunchedEffect(scanState.logs.size) {
        if (scanState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(scanState.logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, Brush.linearGradient(listOf(NeonRed, NeonBlue, NeonGreen)), RoundedCornerShape(24.dp)),
            color = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(NeonRed, NeonBlue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "LEGAL ROM & NCA SCANNER",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Local Storage & AES-XTS Decryption Pipeline",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonGreen
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_rom_scanner_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Banner & Realtime Metrics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(SurfaceBorder, NeonBlue.copy(alpha = 0.5f))))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (scanState.isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = NeonGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Scanning & Decrypting...",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NeonGreen
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Scan Ready / Idle",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NeonBlue
                                    )
                                }
                            }

                            Text(
                                text = "${(scanState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = scanState.progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NeonGreen,
                            trackColor = Color.Black.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = scanState.currentAction.ifEmpty { "Ready to inspect storage for .nsp, .xci, .nro, .nca backup files" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (scanState.currentPath.isNotEmpty()) {
                            Text(
                                text = "Path: ${scanState.currentPath}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Metric Stat Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill(
                                icon = Icons.Default.Storage,
                                title = "Scanned",
                                value = "${scanState.scannedFilesCount}",
                                color = NeonBlue,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                icon = Icons.Default.Games,
                                title = "ROMs",
                                value = "${scanState.validRomCount}",
                                color = NeonRed,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                icon = Icons.Default.LockOpen,
                                title = "Decrypted",
                                value = "${scanState.ncaDecryptedCount}",
                                color = NeonGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                icon = Icons.Default.CheckCircle,
                                title = "Added",
                                value = "${scanState.addedToLibraryCount}",
                                color = NeonYellow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs (Discovered Games vs Live Cryptographic Terminal Log)
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Black.copy(alpha = 0.3f),
                    contentColor = NeonBlue,
                    indicator = {},
                    divider = {}
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Games, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (activeTab == 0) NeonGreen else Color.Gray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discovered Games (${scanState.discoveredGames.size})", fontWeight = FontWeight.Bold, color = if (activeTab == 0) Color.White else Color.Gray)
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (activeTab == 1) NeonBlue else Color.Gray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Decryption Log (${scanState.logs.size})", fontWeight = FontWeight.Bold, color = if (activeTab == 1) Color.White else Color.Gray)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    if (activeTab == 0) {
                        if (scanState.discoveredGames.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (scanState.isScanning) "Searching local storage for game backups..." else "No Switch ROMs found in scanned directories yet.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (!scanState.isScanning) {
                                    Button(
                                        onClick = onStartScan,
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Run Storage & NCA Decryption Scan")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(scanState.discoveredGames) { rom ->
                                    ScannedGameItemCard(rom = rom, onLaunch = { onLaunchGame(rom.file) })
                                }
                            }
                        }
                    } else {
                        // Terminal Logs
                        LazyColumn(
                            state = logListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (scanState.logs.isEmpty()) {
                                item {
                                    Text(
                                        text = "Awaiting scan trigger. Press 'Start Scan' to inspect storage & execute AES-XTS decryption on NCA headers.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                items(scanState.logs) { line ->
                                    val textColor = when {
                                        line.contains("✅") || line.contains("100% OK") -> NeonGreen
                                        line.contains("⚠️") || line.contains("Warning") -> NeonYellow
                                        line.contains("❌") || line.contains("Error") || line.contains("Failed") -> NeonRed
                                        line.contains("🔑") || line.contains("AES-XTS") -> NeonBlue
                                        else -> Color(0xFFDDDDDD)
                                    }
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color.Gray, SurfaceBorder)))
                    ) {
                        Text("Close", color = Color.White)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStartScan,
                            enabled = !scanState.isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("start_rom_scan_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (scanState.isScanning) "Scanning..." else "Scan Storage Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.LightGray, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = color,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ScannedGameItemCard(
    rom: ScannedRomResult,
    onLaunch: () -> Unit
) {
    val formatColor = when (rom.format.uppercase()) {
        "NSP" -> NeonRed
        "NRO" -> NeonBlue
        "XCI" -> NeonYellow
        "SUP" -> NeonGreen
        "NCA" -> Color(0xFFFF9800)
        else -> Color.White
    }

    val (statusColor, statusLabel) = when (rom.ncaDecryptionStatus) {
        NcaDecryptionStatus.DECRYPTED_SUCCESS -> NeonGreen to "AES-XTS Decrypted (100% OK)"
        NcaDecryptionStatus.ALREADY_PLAINTEXT -> NeonBlue to "Plaintext NCA / Container"
        NcaDecryptionStatus.HOMEBREW_NO_ENCRYPTION -> NeonBlue to "Homebrew (Unencrypted)"
        NcaDecryptionStatus.MISSING_PROD_KEYS -> NeonYellow to "Missing prod.keys"
        NcaDecryptionStatus.DECRYPTION_FAILED -> NeonRed to "Decryption Failed"
        NcaDecryptionStatus.CORRUPT_OR_UNSUPPORTED -> Color.Gray to "Unsupported"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(SurfaceBorder, formatColor.copy(alpha = 0.3f))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(formatColor.copy(alpha = 0.15f))
                        .border(1.dp, formatColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rom.format,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = formatColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = rom.titleName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = rom.titleId,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = NeonGreen,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "•",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = formatBytes(rom.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                        if (rom.masterKeyRevision > 0) {
                            Text(
                                text = "• Key Rev ${rom.masterKeyRevision}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonYellow,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Play", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}
