package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import android.view.SurfaceHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.GameLifecycleState
import com.example.emulator.SwitchCoreState
import com.example.emulator.input.SwitchButton
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.viewmodel.ActiveEmulationSession
import kotlin.math.roundToInt

@Composable
fun ActiveEmulationScreen(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onToggleDocked: () -> Unit,
    onStopEmulation: () -> Unit,
    onQuickSave: (() -> Unit)? = null,
    onRunDevSelfTest: (() -> Unit)? = null,
    onJoystick: (Float, Float) -> Unit = { _, _ -> },
    onButton: (SwitchButton, Boolean) -> Unit = { _, _ -> },
    onSurfaceReady: ((android.view.Surface) -> Unit)? = null
) {
    var selectedHudTab by remember { mutableIntStateOf(0) } // 0: GAMEPLAY, 1: CPU ARM64, 2: TEGRA GPU, 3: HORIZON SVC

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
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
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        val statusColor = when {
                            coreState.isBooting -> NeonYellow
                            coreState.lifecycleState == GameLifecycleState.FAILED -> NeonRed
                            coreState.lifecycleState == GameLifecycleState.PLAYABLE || coreState.lifecycleState == GameLifecycleState.FIRST_FRAME -> NeonGreen
                            coreState.lifecycleState == GameLifecycleState.EXECUTING -> NeonBlue
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (coreState.isBooting) "Memproses 10s (${(coreState.bootProgress * 100).toInt()}%)"
                                else "State: ${coreState.lifecycleState.displayName} • ${coreState.fps} FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onQuickSave != null) {
                            Button(
                                onClick = onQuickSave,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("quick_save_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Quick Save", tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("SAVE", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Black)
                            }
                        }

                        OutlinedButton(
                            onClick = onToggleDocked,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = if (coreState.isDockedMode) NeonYellow else Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (coreState.isDockedMode) "1080p" else "720p",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (coreState.isDockedMode) NeonYellow else Color.White
                            )
                        }

                        IconButton(
                            onClick = onStopEmulation,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(NeonRed.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Stop Emulation", tint = NeonRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Emulation HUD Tabs
            TabRow(
                selectedTabIndex = selectedHudTab,
                containerColor = SurfaceDark,
                contentColor = NeonBlue,
                divider = {}
            ) {
                Tab(
                    selected = selectedHudTab == 0,
                    onClick = { selectedHudTab = 0 },
                    text = { Text("GAMEPLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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

            Spacer(modifier = Modifier.height(6.dp))

            // Main View Area depending on tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            ) {
                when (selectedHudTab) {
                    0 -> GameDisplayCanvas(session, coreState, onRunDevSelfTest, onSurfaceReady)
                    1 -> Arm64CpuViewer(coreState)
                    2 -> TegraGpuViewer(coreState)
                    3 -> HorizonSvcViewer(coreState)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Interactive Virtual Gamepad Overlay
            VirtualGamepadOverlay(onJoystick = onJoystick, onButton = onButton)
        }
    }
}

@Composable
private fun GameDisplayCanvas(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onRunDevSelfTest: (() -> Unit)? = null,
    onSurfaceReady: ((android.view.Surface) -> Unit)? = null
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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = NeonRed,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "GAME LOAD FAILED",
                    style = MaterialTheme.typography.titleMedium,
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
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Diagnostic Reason: ${coreState.errorMessage ?: "Unknown Loader Failure"}",
                            fontWeight = FontWeight.Bold,
                            color = NeonYellow,
                            fontSize = 12.sp
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
                Spacer(modifier = Modifier.height(14.dp))
                if (onRunDevSelfTest != null) {
                    OutlinedButton(
                        onClick = onRunDevSelfTest,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Developer ARM64 CPU Self-Test Mode", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        } else if (coreState.isBooting || (coreState.bootProgress in 0.01f..0.99f)) {
            // 10-Second High-Tech Multi-Stage Boot Processing Screen
            val infiniteTransition = rememberInfiniteTransition(label = "boot_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Animated Switch Joy-Con Icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.scale(pulseScale)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp, 50.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 3.dp, bottomEnd = 3.dp))
                            .background(NeonBlue)
                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 3.dp, bottomEnd = 3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Black))
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp, 50.dp)
                            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 3.dp, bottomStart = 3.dp))
                            .background(NeonRed)
                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 3.dp, bottomStart = 3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Black))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "MEMPROSES GAME NSP (10s)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonYellow,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = session.gameTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { coreState.bootProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonGreen,
                    trackColor = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(coreState.bootProgress * 100).toInt()}% SELESAI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    val remainingSec = ((1f - coreState.bootProgress) * 10f).coerceAtLeast(0f)
                    Text(
                        text = "Sisa: ~%.1fs".format(remainingSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonBlue, NeonGreen)))
                ) {
                    Text(
                        text = coreState.loaderMessage,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
                } else if (coreState.frameBitmap != null) {
            Image(
                bitmap = coreState.frameBitmap.asImageBitmap(),
                contentDescription = "Real-Time Switch Gameplay Frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = NeonBlue,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = session.gameTitle,
                    style = MaterialTheme.typography.titleMedium,
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
            .padding(14.dp)
    ) {
        Text(
            text = "ARM CORTEX-A57 (4-CORE ARM64 JIT RUNTIME)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NeonBlue
        )
        Spacer(modifier = Modifier.height(6.dp))

        Text("Current Executing Core: Core #${coreState.currentCore}", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
        Text("Last Disassembly: ${coreState.lastDisassembly}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White)
        Spacer(modifier = Modifier.height(10.dp))

        coreState.cpuCores.forEach { core ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Core #${core.coreId}", fontWeight = FontWeight.Bold, color = if (core.isHalted) Color.Gray else NeonGreen)
                        Text(if (core.isHalted) "HALTED" else "RUNNING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (core.isHalted) Color.Gray else NeonGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PC: 0x${core.pc.toString(16).uppercase()} | SP: 0x${core.sp.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonYellow)
                    Text("X0: 0x${core.x0.toString(16).uppercase()} | X1: 0x${core.x1.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.White)
                    Text("X2: 0x${core.x2.toString(16).uppercase()} | X3: 0x${core.x3.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.White)
                    Text("NZCV: ${core.nzcv} | Executed: ${core.instructionsExecuted} instructions", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .padding(14.dp)
    ) {
        Text(
            text = "NVIDIA TEGRA X1 MAXWELL GPU (GM20B)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
        Spacer(modifier = Modifier.height(10.dp))

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
            .padding(14.dp)
    ) {
        Text(
            text = "HORIZON OS KERNEL SVC CALL LOG",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NeonYellow
        )
        Spacer(modifier = Modifier.height(6.dp))

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
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun VirtualGamepadOverlay(
    onJoystick: (Float, Float) -> Unit = { _, _ -> },
    onButton: (SwitchButton, Boolean) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // Left Joy-Con Area (L, ZL, -, D-Pad & Analog)
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InteractiveGamepadButton("L", NeonBlue) { pressed -> onButton(SwitchButton.L, pressed) }
                InteractiveGamepadButton("ZL", NeonBlue) { pressed -> onButton(SwitchButton.ZL, pressed) }
                InteractiveGamepadButton("—", NeonBlue) { pressed -> onButton(SwitchButton.MINUS, pressed) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Interactive Analog Stick
                InteractiveAnalogStick(color = NeonBlue, onJoystick = onJoystick)

                // D-Pad Cross
                Box(modifier = Modifier.size(68.dp)) {
                    Box(modifier = Modifier.align(Alignment.TopCenter)) {
                        InteractiveRoundButton("▲", NeonBlue, size = 24) { pressed -> onButton(SwitchButton.DPAD_UP, pressed) }
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        InteractiveRoundButton("▼", NeonBlue, size = 24) { pressed -> onButton(SwitchButton.DPAD_DOWN, pressed) }
                    }
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        InteractiveRoundButton("◀", NeonBlue, size = 24) { pressed -> onButton(SwitchButton.DPAD_LEFT, pressed) }
                    }
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        InteractiveRoundButton("▶", NeonBlue, size = 24) { pressed -> onButton(SwitchButton.DPAD_RIGHT, pressed) }
                    }
                }
            }
        }
        
        // Right Joy-Con Area (+, ZR, R, ABXY Diamond)
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InteractiveGamepadButton("+", NeonRed) { pressed -> onButton(SwitchButton.PLUS, pressed) }
                InteractiveGamepadButton("ZR", NeonRed) { pressed -> onButton(SwitchButton.ZR, pressed) }
                InteractiveGamepadButton("R", NeonRed) { pressed -> onButton(SwitchButton.R, pressed) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // ABXY Diamond
            Box(modifier = Modifier.size(86.dp)) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    InteractiveRoundButton("X", NeonRed, size = 30) { pressed -> onButton(SwitchButton.X, pressed) }
                }
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    InteractiveRoundButton("B", NeonRed, size = 30) { pressed -> onButton(SwitchButton.B, pressed) }
                }
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    InteractiveRoundButton("Y", NeonRed, size = 30) { pressed -> onButton(SwitchButton.Y, pressed) }
                }
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    InteractiveRoundButton("A", NeonRed, size = 30) { pressed -> onButton(SwitchButton.A, pressed) }
                }
            }
        }
    }
}

@Composable
private fun InteractiveGamepadButton(
    label: String,
    color: Color,
    onStateChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(44.dp, 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (isPressed) color.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.6f))
            .border(1.dp, if (isPressed) color else color.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onStateChange(true)
                        tryAwaitRelease()
                        isPressed = false
                        onStateChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun InteractiveRoundButton(
    label: String,
    color: Color,
    size: Int = 30,
    onStateChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (isPressed) color.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.8f))
            .border(1.5.dp, if (isPressed) Color.White else color.copy(alpha = 0.7f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onStateChange(true)
                        tryAwaitRelease()
                        isPressed = false
                        onStateChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPressed) Color.White else Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size / 2.3f).sp
        )
    }
}

@Composable
private fun InteractiveAnalogStick(
    color: Color,
    onJoystick: (Float, Float) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val maxRadius = 30f

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        offsetX = 0f
                        offsetY = 0f
                        onJoystick(0f, 0f)
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                        onJoystick(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val newX = (offsetX + dragAmount.x).coerceIn(-maxRadius, maxRadius)
                    val newY = (offsetY + dragAmount.y).coerceIn(-maxRadius, maxRadius)
                    offsetX = newX
                    offsetY = newY
                    val normX = (newX / maxRadius).coerceIn(-1f, 1f)
                    val normY = (newY / maxRadius).coerceIn(-1f, 1f)
                    onJoystick(normX, normY)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
                .border(1.5.dp, color, CircleShape)
        )
    }
}
