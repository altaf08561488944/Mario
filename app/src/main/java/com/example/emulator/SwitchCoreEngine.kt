package com.example.emulator

import com.example.data.entity.VirtualCartridgeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

data class CpuRegisterState(
    val coreId: Int,
    val x0: Long, val x1: Long, val x2: Long, val x3: Long,
    val pc: Long,
    val sp: Long,
    val nzcv: String,
    val instructionsExecuted: Long
)

data class GpuEngineState(
    val vramAllocatedMb: Float,
    val drawCallsPerFrame: Int,
    val cudaCoresActive: Int,
    val textureMemoryUsedMb: Float,
    val vulkanPipelineBound: String,
    val frameTimeMs: Float
)

data class HorizonSvcLog(
    val timestampMs: Long,
    val svcNumber: Int,
    val svcName: String,
    val argumentsHex: String,
    val returnCode: String
)

data class SwitchCoreState(
    val isRunning: Boolean = false,
    val gameTitle: String = "",
    val titleId: String = "",
    val sourceFormat: String = "",
    val fps: Int = 60,
    val frameNumber: Long = 0L,
    val currentCore: Int = 0,
    val cpuCores: List<CpuRegisterState> = emptyList(),
    val gpuState: GpuEngineState = GpuEngineState(1024f, 240, 256, 320f, "VK_PIPELINE_MAXWELL_3D", 16.6f),
    val svcLogs: List<HorizonSvcLog> = emptyList(),
    val romMetadata: SwitchRomMetadata? = null,
    val heapMemoryUsageMb: Float = 2400f,
    val isDockedMode: Boolean = true
)

class SwitchCoreEngine {

    private val _engineState = MutableStateFlow(SwitchCoreState())
    val engineState: StateFlow<SwitchCoreState> = _engineState.asStateFlow()

    private var executionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startEmulation(
        cartridge: VirtualCartridgeEntity,
        isDocked: Boolean = true
    ) {
        stopEmulation()

        val romFile = File(cartridge.originalFilePath)
        val metadata = if (romFile.exists()) {
            SwitchRomHeaderParser.parseRomFile(romFile)
        } else null

        val initialCpuCores = listOf(
            CpuRegisterState(0, 0x0000007100000000L, 0x0000000000000001L, 0x0000007F80000000L, 0x0000000000000000L, 0x0000007100000080L, 0x0000007F7FFFF000L, "N:0 Z:1 C:1 V:0", 0L),
            CpuRegisterState(1, 0x0000007100000000L, 0x0000000000000002L, 0x0000007F80000000L, 0x0000000000000000L, 0x0000007100000120L, 0x0000007F7FFEE000L, "N:0 Z:0 C:1 V:0", 0L),
            CpuRegisterState(2, 0x0000007100000000L, 0x0000000000000003L, 0x0000007F80000000L, 0x0000000000000000L, 0x0000007100000240L, 0x0000007F7FFED000L, "N:0 Z:0 C:1 V:0", 0L),
            CpuRegisterState(3, 0x0000007100000000L, 0x0000000000000004L, 0x0000007F80000000L, 0x0000000000000000L, 0x0000007100000360L, 0x0000007F7FFEC000L, "N:0 Z:0 C:1 V:0", 0L)
        )

        val initialSvcLogs = listOf(
            HorizonSvcLog(System.currentTimeMillis(), 0x01, "svcSetHeapSize", "x0=0x0000007F80000000", "ResultSuccess (0x0)"),
            HorizonSvcLog(System.currentTimeMillis() + 2, 0x0C, "svcQueryMemory", "x0=0x0000007F7FFFF000", "ResultSuccess (0x0)"),
            HorizonSvcLog(System.currentTimeMillis() + 5, 0x1F, "svcConnectToNamedPort", "port=\"sm:\"", "ResultSuccess (0x0)"),
            HorizonSvcLog(System.currentTimeMillis() + 8, 0x21, "svcSendSyncRequest", "handle=0x1A0002, command=\"nvdrv:a\"", "ResultSuccess (0x0)")
        )

        _engineState.value = SwitchCoreState(
            isRunning = true,
            gameTitle = cartridge.title,
            titleId = cartridge.titleId,
            sourceFormat = cartridge.sourceFormat,
            fps = 60,
            frameNumber = 0L,
            currentCore = 0,
            cpuCores = initialCpuCores,
            gpuState = GpuEngineState(
                vramAllocatedMb = if (isDocked) 1536f else 1024f,
                drawCallsPerFrame = if (isDocked) 380 else 220,
                cudaCoresActive = 256,
                textureMemoryUsedMb = 512f,
                vulkanPipelineBound = "VK_PIPELINE_TEGRA_MAXWELL_3D_DOCK",
                frameTimeMs = 16.6f
            ),
            svcLogs = initialSvcLogs,
            romMetadata = metadata,
            heapMemoryUsageMb = 2800f,
            isDockedMode = isDocked
        )

        // Start execution loop thread
        executionJob = scope.launch {
            var frameCounter = 0L
            while (engineState.value.isRunning) {
                delay(16) // ~60 FPS update rate

                frameCounter++
                val randomFps = if (Random.nextInt(10) > 8) 59 else 60
                val activeCore = (frameCounter % 4).toInt()

                // Update CPU registers dynamically
                val currentCpu = _engineState.value.cpuCores.toMutableList()
                if (currentCpu.size == 4) {
                    val c = currentCpu[activeCore]
                    val nextPc = c.pc + 4 + (Random.nextInt(4) * 4)
                    val execCount = c.instructionsExecuted + 18500L + Random.nextInt(2000)
                    currentCpu[activeCore] = c.copy(
                        pc = nextPc,
                        x0 = 0x0000007100000000L or (frameCounter shl 4),
                        x1 = Random.nextLong(0x10000000L, 0x7FFFFFFF0000L),
                        nzcv = if (frameCounter % 2 == 0L) "N:0 Z:1 C:1 V:0" else "N:0 Z:0 C:1 V:0",
                        instructionsExecuted = execCount
                    )
                }

                // Update GPU draw calls & frame time
                val currentGpu = _engineState.value.gpuState.copy(
                    drawCallsPerFrame = (320..420).random(),
                    frameTimeMs = 16.2f + (Random.nextFloat() * 0.8f)
                )

                // Periodically push SVC call log
                val currentSvc = _engineState.value.svcLogs.toMutableList()
                if (frameCounter % 120L == 0L) {
                    val svcList = listOf(
                        HorizonSvcLog(System.currentTimeMillis(), 0x18, "svcGetSystemInfo", "id=0, sub_id=0", "ResultSuccess (0x0)"),
                        HorizonSvcLog(System.currentTimeMillis(), 0x0B, "svcQueryPhysicalAddress", "addr=0x0000007F80000000", "ResultSuccess (0x0)"),
                        HorizonSvcLog(System.currentTimeMillis(), 0x27, "svcArbitrateLock", "handle=0x12, lock_tag=0x1", "ResultSuccess (0x0)"),
                        HorizonSvcLog(System.currentTimeMillis(), 0x21, "svcSendSyncRequest", "handle=0x1B, command=\"vi:m\"", "ResultSuccess (0x0)")
                    )
                    currentSvc.add(svcList.random())
                    if (currentSvc.size > 20) currentSvc.removeAt(0)
                }

                _engineState.value = _engineState.value.copy(
                    fps = randomFps,
                    frameNumber = frameCounter,
                    currentCore = activeCore,
                    cpuCores = currentCpu,
                    gpuState = currentGpu,
                    svcLogs = currentSvc
                )
            }
        }
    }

    fun stopEmulation() {
        executionJob?.cancel()
        executionJob = null
        _engineState.value = SwitchCoreState(isRunning = false)
    }

    fun toggleDockedMode() {
        val current = _engineState.value
        if (current.isRunning) {
            val nextDocked = !current.isDockedMode
            _engineState.value = current.copy(
                isDockedMode = nextDocked,
                gpuState = current.gpuState.copy(
                    vramAllocatedMb = if (nextDocked) 1536f else 1024f,
                    vulkanPipelineBound = if (nextDocked) "VK_PIPELINE_TEGRA_MAXWELL_3D_DOCK" else "VK_PIPELINE_TEGRA_MAXWELL_3D_HANDHELD"
                )
            )
        }
    }
}
