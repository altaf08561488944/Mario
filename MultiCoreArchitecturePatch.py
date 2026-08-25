import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

old_state = """data class SwitchCoreState(
    val isRunning: Boolean = false,
    val lifecycleState: GameLifecycleState = GameLifecycleState.NO_FILE,
    val gameTitle: String = "No Game Loaded",
    val titleId: String = "0000000000000000",
    val sourceFormat: String = "",
    val romMetadata: RomMetadata? = null,
    val isDockedMode: Boolean = true,
    val guestProcess: GuestProcess? = null,
    val cpuCores: List<CpuRegisterState> = emptyList(),
    val gpuState: GpuEngineState = GpuEngineState(),
    val loaderMessage: String = "",
    val errorMessage: String = "",
    val errorDetail: String = "",
    val svcLogs: List<HorizonSvcLog> = emptyList(),
    val currentCore: Int = 0,
    val lastDisassembly: String = "NOP",
    val fps: Int = 0,
    val frameNumber: Long = 0,
    val hasProducedFrame: Boolean = false,
    val activeFrameBuffer: android.graphics.Bitmap? = null,
    val frameBitmap: android.graphics.Bitmap? = null,
    val heapMemoryUsageMb: Float = 0f,
    val isDevSelfTest: Boolean = false
)"""

new_state = """import com.example.emulator.memory.MemoryManagementUnit
import com.example.emulator.cpu.JitExecutionEngine
import com.example.emulator.gpu.VulkanTranslator
import com.example.emulator.hle.HorizonServiceManager
import com.example.emulator.audio.AudioSubsystem

data class SwitchCoreState(
    val isRunning: Boolean = false,
    val lifecycleState: GameLifecycleState = GameLifecycleState.NO_FILE,
    val gameTitle: String = "No Game Loaded",
    val titleId: String = "0000000000000000",
    val sourceFormat: String = "",
    val romMetadata: RomMetadata? = null,
    val isDockedMode: Boolean = true,
    val guestProcess: GuestProcess? = null,
    val cpuCores: List<CpuRegisterState> = emptyList(),
    val gpuState: GpuEngineState = GpuEngineState(),
    val loaderMessage: String = "",
    val errorMessage: String = "",
    val errorDetail: String = "",
    val svcLogs: List<HorizonSvcLog> = emptyList(),
    val currentCore: Int = 0,
    val lastDisassembly: String = "NOP",
    val fps: Int = 0,
    val frameNumber: Long = 0,
    val hasProducedFrame: Boolean = false,
    val activeFrameBuffer: android.graphics.Bitmap? = null,
    val frameBitmap: android.graphics.Bitmap? = null,
    val heapMemoryUsageMb: Float = 0f,
    val isDevSelfTest: Boolean = false
)"""

old_props = """    val memory = GuestMemory()
    val gpu = TegraGpuEmulator()
    val keysManager = KeyManager()"""

new_props = """    // Advanced Virtual Machine Subsystems
    val memory = GuestMemory() 
    val mmu = MemoryManagementUnit() // Advanced Virtual Memory
    val jitEngine = JitExecutionEngine() // ARM64 IR Translation
    val vulkanGpu = VulkanTranslator() // Maxwell to Vulkan Layer
    val horizonOs = HorizonServiceManager() // HLE IPC Services
    val audioSubsystem = AudioSubsystem() // PCM Audio Mixing
    
    val gpu = TegraGpuEmulator()
    val keysManager = KeyManager()"""

content = content.replace(old_state, new_state)
content = content.replace(old_props, new_props)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

