import re

with open("app/src/main/java/com/example/ui/screens/ActiveEmulationScreen.kt", "r") as f:
    code = f.read()

# Add onSurfaceReady to signature
sig_find = r'fun ActiveEmulationScreen\(\n    session: ActiveEmulationSession,\n    coreState: SwitchCoreState,\n    onToggleDocked: \(\) -> Unit,\n    onStopEmulation: \(\) -> Unit,\n    onQuickSave: \(\(\) -> Unit\)\? = null,\n    onRunDevSelfTest: \(\(\) -> Unit\)\? = null,\n    onJoystick: \(Float, Float\) -> Unit = \{ _, _ -> \},\n    onButton: \(SwitchButton, Boolean\) -> Unit = \{ _, _ -> \}\n\) \{'

sig_replace = """fun ActiveEmulationScreen(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onToggleDocked: () -> Unit,
    onStopEmulation: () -> Unit,
    onQuickSave: (() -> Unit)? = null,
    onRunDevSelfTest: (() -> Unit)? = null,
    onJoystick: (Float, Float) -> Unit = { _, _ -> },
    onButton: (SwitchButton, Boolean) -> Unit = { _, _ -> },
    onSurfaceReady: ((android.view.Surface) -> Unit)? = null
) {"""

code = re.sub(sig_find, sig_replace, code)

with open("app/src/main/java/com/example/ui/screens/ActiveEmulationScreen.kt", "w") as f:
    f.write(code)

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "r") as f:
    code = f.read()

# Remove duplicate setVulkanSurface
vm_code = re.sub(r'    fun setVulkanSurface\(surface: android\.view\.Surface\) \{\n        switchCoreEngine\.vulkanGpu\.setSurface\(surface\)\n    \}\n\n', '', code)
vm_code = vm_code.replace("fun updateCoreSettings", """
    fun setVulkanSurface(surface: android.view.Surface) {
        switchCoreEngine.vulkanGpu.setSurface(surface)
    }
    fun updateCoreSettings""")

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.write(vm_code)

