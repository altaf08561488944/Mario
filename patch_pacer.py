import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

import_statement = "import com.example.emulator.gpu.FramePacer\n"

if import_statement not in content:
    content = content.replace("import com.example.emulator.gpu.TegraGpuEmulator", "import com.example.emulator.gpu.TegraGpuEmulator\nimport com.example.emulator.gpu.FramePacer")

# Insert FramePacer variable
old_gpu_decl = "private val gpu = TegraGpuEmulator()"
new_gpu_decl = "private val gpu = TegraGpuEmulator()\n    private val framePacer = FramePacer(60)"
content = content.replace(old_gpu_decl, new_gpu_decl)

# Replace simple delay with FramePacer
old_delay = "kotlinx.coroutines.delay(16) // ~60 FPS Exact V-Sync Timing"
new_delay = "framePacer.paceFrame() // Dynamic Frame Pacing & VSync"
content = content.replace(old_delay, new_delay)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

