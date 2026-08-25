package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.GameLifecycleState
import com.example.emulator.SwitchCoreState
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.ActiveEmulationSession

@Composable
fun ActiveEmulationScreen(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onToggleDocked: () -> Unit,
    onStopEmulation: () -> Unit,
    onQuickSave: (() -> Unit)? = null,
    onRunDevSelfTest: (() -> Unit)? = null
) {
    var selectedHudTab by remember { mutableIntStateOf(0) } // 0: CANVAS, 1: CPU ARM64, 2: TEGRA GPU, 3: HORIZON SVC

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top Control Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(NeonRed, NeonBlue))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when (coreState.lifecycleState) {
                            GameLifecycleState.FAILED -> NeonRed
                            GameLifecycleState.PLAYABLE, GameLifecycleState.FIRST_FRAME -> NeonGreen
                            GameLifecycleState.EXECUTING -> NeonBlue
                            else -> NeonYellow
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = session.gameTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "State: ${coreState.lifecycleState.displayName} • ${coreState.fps} FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onQuickSave != null) {
                            Button(
                                onClick = onQuickSave,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("quick_save_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Quick Save", tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                            }
                        }

                        OutlinedButton(
                            onClick = onToggleDocked,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = if (coreState.isDockedMode) NeonYellow else Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (coreState.isDockedMode) "DOCKED" else "HANDHELD", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = onStopEmulation,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("exit_emulation_button")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EXIT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-Navigation HUD Tabs
            TabRow(
                selectedTabIndex = selectedHudTab,
                containerColor = SurfaceDark,
                contentColor = NeonBlue
            ) {
                Tab(
                    selected = selectedHudTab == 0,
                    onClick = { selectedHudTab = 0 },
                    text = { Text("GAME DISPLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedHudTab == 1,
                    onClick = { selectedHudTab = 1 },
                    text = { Text("ARM64 CPU", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedHudTab == 2,
                    onClick = { selectedHudTab = 2 },
                    text = { Text("TEGRA GPU", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedHudTab == 3,
                    onClick = { selectedHudTab = 3 },
                    text = { Text("HORIZON SVC", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main View Area depending on tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            ) {
                when (selectedHudTab) {
                    0 -> GameDisplayCanvas(session, coreState, onRunDevSelfTest)
                    1 -> Arm64CpuViewer(coreState)
                    2 -> TegraGpuViewer(coreState)
                    3 -> HorizonSvcViewer(coreState)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Advanced Virtual Gamepad Overlay (Comfortable Play)
            VirtualGamepadOverlay()
        }
    }
}

@Composable
private fun GameDisplayCanvas(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onRunDevSelfTest: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (coreState.lifecycleState == GameLifecycleState.FAILED || coreState.errorMessage != null) {
            // Failure Screen Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = NeonRed,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "GAME LOAD FAILED",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonRed,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonRed, NeonYellow)))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Diagnostic Reason: ${coreState.errorMessage ?: "Unknown Loader Failure"}",
                            fontWeight = FontWeight.Bold,
                            color = NeonYellow,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = coreState.errorDetail ?: "Unable to locate or decrypt guest NSO/NRO executable binary.",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (onRunDevSelfTest != null) {
                    OutlinedButton(
                        onClick = onRunDevSelfTest,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Developer ARM64 CPU Self-Test Mode", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        } else if (coreState.frameBitmap != null) {
            Image(
                bitmap = coreState.frameBitmap.asImageBitmap(),
                contentDescription = "Live VRAM Framebuffer",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = NeonBlue,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = session.gameTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = coreState.loaderMessage.ifEmpty { "Pipeline Step: ${coreState.lifecycleState.displayName}..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonGreen,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Arm64CpuViewer(coreState: SwitchCoreState) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "ARM64 CORTEX-A57 INTERPRETER (AArch64)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NeonBlue
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pipeline: ${coreState.lifecycleState.displayName}",
            style = MaterialTheme.typography.labelSmall,
            color = if (coreState.lifecycleState == GameLifecycleState.FAILED) NeonRed else NeonYellow
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Guest Process Information
        coreState.guestProcess?.let { process ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "GUEST PROCESS: ${process.processName} [Title ID: ${process.titleId}]",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Entry Point: 0x${process.entryPoint.toString(16).uppercase()} | SP: 0x${process.stackPointer.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White)
                    Text("Mapped Segments: ${process.mappedSegments.joinToString(", ")}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Loaded Modules: ${process.modules.joinToString(", ")}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceVariantDark
        ) {
            Text(
                text = "DISASSEMBLY PIPELINE: ${coreState.lastDisassembly}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (coreState.lastDisassembly.contains("UNSUPPORTED_INSTRUCTION")) NeonRed else NeonGreen,
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        coreState.cpuCores.forEach { core ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "CORE #${core.coreId} ${if (core.coreId == coreState.currentCore) "[ACTIVE EXECUTION]" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (core.coreId == coreState.currentCore) NeonGreen else Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PC: 0x${core.pc.toString(16).uppercase()} | SP: 0x${core.sp.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonYellow)
                    Text("X0: 0x${core.x0.toString(16).uppercase()} | X1: 0x${core.x1.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White)
                    Text("X2: 0x${core.x2.toString(16).uppercase()} | X3: 0x${core.x3.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White)
                    Text("NZCV: ${core.nzcv} | Executed: ${core.instructionsExecuted} instructions", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TegraGpuViewer(coreState: SwitchCoreState) {
    val gpu = coreState.gpuState
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "NVIDIA TEGRA X1 MAXWELL GPU (GM20B)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
        Spacer(modifier = Modifier.height(12.dp))

        GpuStatRow("CUDA Cores", "${gpu.cudaCoresActive} Active Cores")
        GpuStatRow("VRAM Allocation", "%.1f MB / 4096 MB".format(gpu.vramAllocatedMb))
        GpuStatRow("Texture Memory", "%.1f MB".format(gpu.textureMemoryUsedMb))
        GpuStatRow("Draw Calls / Frame", "${gpu.drawCallsPerFrame} Maxwell 3D Commands")
        GpuStatRow("Frame Time", "%.2f ms".format(gpu.frameTimeMs))
        GpuStatRow("Vulkan Pipeline", gpu.vulkanPipelineBound)
        GpuStatRow("Heap Memory", "%.1f MB".format(coreState.heapMemoryUsageMb))
    }
}

@Composable
private fun HorizonSvcViewer(coreState: SwitchCoreState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "HORIZON OS KERNEL SVC CALL LOG",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NeonYellow
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (coreState.svcLogs.isEmpty()) {
            Text("Waiting for ARM64 SVC instructions...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(coreState.svcLogs) { log ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceVariantDark
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "[0x${log.svcNumber.toString(16).padStart(2, '0').uppercase()}] ${log.svcName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.svcName == "UNSUPPORTED_INSTRUCTION") NeonRed else Color.White
                                )
                                Text(
                                    text = log.argumentsHex,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = log.returnCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.svcName == "UNSUPPORTED_INSTRUCTION") NeonRed else NeonGreen,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GpuStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun VirtualGamepadOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Left Joy-Con Area (D-Pad & Analog)
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GamepadButton("L", NeonBlue)
                GamepadButton("ZL", NeonBlue)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                GamepadAnalogStick(NeonBlue)
            }
        }
        
        // Right Joy-Con Area (ABXY & Analog)
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GamepadButton("R", NeonRed)
                GamepadButton("ZR", NeonRed)
            }
            Spacer(modifier = Modifier.height(16.dp))
            // ABXY Diamond
            Box(modifier = Modifier.size(100.dp)) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) { GamepadRoundButton("X", NeonRed) }
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { GamepadRoundButton("B", NeonRed) }
                Box(modifier = Modifier.align(Alignment.CenterStart)) { GamepadRoundButton("Y", NeonRed) }
                Box(modifier = Modifier.align(Alignment.CenterEnd)) { GamepadRoundButton("A", NeonRed) }
            }
        }
    }
}

@Composable
private fun GamepadButton(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun GamepadRoundButton(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.7f))
            .border(1.dp, color.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun GamepadAnalogStick(color: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, color.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.DarkGray)
                .border(1.dp, color.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape)
        )
    }
}
