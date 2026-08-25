package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.emulator.diagnostics.KeyAndFirmwareDiagnostics
import com.example.emulator.diagnostics.KeyAndFirmwareDiagnostics.DiagnosticStatus
import com.example.emulator.diagnostics.KeyAndFirmwareDiagnostics.KeyFirmwareReport
import com.example.emulator.diagnostics.KeyAndFirmwareDiagnostics.LaunchReadiness
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark

@Composable
fun DiagnosticToolDialog(
    report: KeyFirmwareReport?,
    onDismiss: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onQuickFixKeys: () -> Unit,
    onQuickFixFirmware: () -> Unit
) {
    if (report == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("diagnostic_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Diagnostics",
                            tint = NeonBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Key & Firmware Diagnostic Tool",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Pre-launch Cryptographic & Integrity Audit",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onRunDiagnostics) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-run Diagnostics",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                HorizontalDivider(color = SurfaceVariantDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // Readiness & Score Card
                ReadinessSummaryCard(report = report)

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Fix Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onQuickFixKeys,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Fix Keys", fontSize = 12.sp, color = Color.White)
                    }

                    Button(
                        onClick = onQuickFixFirmware,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Fix FW v17.0.1", fontSize = 12.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Diagnostic Log & Verification Checklist",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Detailed Checklist List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(report.checks) { check ->
                        DiagnosticCheckItemRow(item = check)
                    }

                    if (report.missingKeysList.isNotEmpty() || report.missingServicesList.isNotEmpty()) {
                        item {
                            MissingItemsNoticeCard(
                                missingKeys = report.missingKeysList,
                                missingServices = report.missingServicesList
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("Close Diagnostic Tool", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun ReadinessSummaryCard(report: KeyFirmwareReport) {
    val (statusColor, badgeBg) = when (report.readiness) {
        LaunchReadiness.READY -> NeonGreen to NeonGreen.copy(alpha = 0.15f)
        LaunchReadiness.READY_WITH_WARNINGS -> Color(0xFFFFCC00) to Color(0xFFFFCC00).copy(alpha = 0.15f)
        LaunchReadiness.BLOCKED -> NeonRed to NeonRed.copy(alpha = 0.15f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = report.summaryTitle,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Text(
                    text = "Score: ${report.overallScorePercent}%",
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { report.overallScorePercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = SurfaceDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = report.summaryDescription,
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun DiagnosticCheckItemRow(item: KeyAndFirmwareDiagnostics.DiagnosticCheckItem) {
    val (statusColor, statusLabel) = when (item.status) {
        DiagnosticStatus.PASS -> NeonGreen to "PASS"
        DiagnosticStatus.WARNING -> Color(0xFFFFCC00) to "WARN"
        DiagnosticStatus.FAIL -> NeonRed to "FAIL"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = item.detailMessage,
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (item.expected != null || item.actual != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (item.expected != null) {
                        Text(
                            text = "Expected: ${item.expected}",
                            color = Color.DarkGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (item.actual != null) {
                        Text(
                            text = "Actual: ${item.actual}",
                            color = if (item.status == DiagnosticStatus.PASS) NeonGreen else Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MissingItemsNoticeCard(missingKeys: List<String>, missingServices: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Missing Components Audit", color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (missingKeys.isNotEmpty()) {
                Text(
                    text = "• Missing prod.keys slots: ${missingKeys.joinToString(", ")}",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (missingServices.isNotEmpty()) {
                Text(
                    text = "• Missing Firmware Services: ${missingServices.joinToString(", ")}",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
