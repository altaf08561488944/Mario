import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

# Add imports
if "import com.example.emulator.telemetry.TelemetryLogger" not in content:
    content = content.replace("import com.example.emulator.gpu.FramePacer", "import com.example.emulator.gpu.FramePacer\nimport com.example.emulator.telemetry.TelemetryLogger\nimport com.example.emulator.cpu.JitPreCompiler")

# Add properties
if "private val telemetryLogger =" not in content:
    content = content.replace("private val cpu = JitExecutionEngine()", "private val cpu = JitExecutionEngine()\n    private val telemetryLogger = TelemetryLogger()\n    private val jitPrecompiler = JitPreCompiler()")

# Start workers
if "telemetryLogger.startLogging()" not in content:
    content = content.replace("executionJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {", "executionJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {\n            telemetryLogger.startLogging()\n            jitPrecompiler.startWorker()")

# Stop workers
if "telemetryLogger.stopLoggingAndExport()" not in content:
    content = content.replace("executionJob?.cancel()", "telemetryLogger.stopLoggingAndExport()\n        jitPrecompiler.stopWorker()\n        executionJob?.cancel()")

# In loop
if "telemetryLogger.logFps" not in content:
    content = content.replace("framePacer.paceFrame() // Dynamic Frame Pacing & VSync", "framePacer.paceFrame() // Dynamic Frame Pacing & VSync\n                    telemetryLogger.logFps(60f)\n                    if (frameCounter % 60L == 0L) jitPrecompiler.simulateHotPathDetection()")

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)
