import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

# Add import for NativeCpuCore
if "import com.example.emulator.cpu.NativeCpuCore" not in content:
    content = content.replace("import com.example.emulator.cpu.JitExecutionEngine", "import com.example.emulator.cpu.JitExecutionEngine\nimport com.example.emulator.cpu.NativeCpuCore")

# Instantiate NativeCpuCore
if "private val nativeCpu = NativeCpuCore()" not in content:
    content = content.replace("private val cpu = JitExecutionEngine()", "private val cpu = JitExecutionEngine()\n    private val nativeCpu = NativeCpuCore()")

# Initialize NativeCpuCore
if "nativeCpu.initialize()" not in content:
    content = content.replace("cpu.initialize()", "cpu.initialize()\n        nativeCpu.initialize()")

# Execute NativeCpuCore in loop
if "nativeCpu.execute(1000)" not in content:
    content = content.replace("cpu.executeCycle()", "cpu.executeCycle()\n                nativeCpu.execute(1000)")

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

