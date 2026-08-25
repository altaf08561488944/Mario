import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

if "private val telemetryLogger =" not in content:
    content = content.replace("private val cpuCores = Array(4) { id -> Arm64CpuCore(id) }", "private val cpuCores = Array(4) { id -> Arm64CpuCore(id) }\n    private val telemetryLogger = TelemetryLogger()\n    private val jitPrecompiler = JitPreCompiler()")

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)
