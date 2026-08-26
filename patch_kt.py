import re

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "r") as f:
    code = f.read()

find = """                Pair(GameLifecycleState.STARTING_CPU_CORES, "Step 5/11 [3.2s]: Spawning Guest CPU Thread 0 (AArch64 JIT)..."),"""
replace = """                Pair(GameLifecycleState.STARTING_CPU_CORES, "Step 5/11 [3.2s]: Spawning NCE (Native Code Execution) Engine for ARM64..."),"""
code = code.replace(find, replace)

find = """                Pair(GameLifecycleState.INITIALIZING_VULKAN, "Step 8/11 [5.8s]: Handshaking NVN -> Vulkan Hardware Driver..."),"""
replace = """                Pair(GameLifecycleState.INITIALIZING_VULKAN, "Step 8/11 [5.8s]: Handshaking REAL Vulkan Pipeline (vkCmdDraw)..."),"""
code = code.replace(find, replace)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(code)

