import re

with open("app/src/main/java/com/example/ui/screens/ActiveEmulationScreen.kt", "r") as f:
    code = f.read()

# Add onSurfaceReady to signature
sig_find = r'fun ActiveEmulationScreen\(\n    session: ActiveEmulationSession,\n    coreState: SwitchCoreState,\n    onToggleDocked: \(\) -> Unit = \{\},\n    onStopEmulation: \(\) -> Unit = \{\},\n    onRunDevSelfTest: \(\(\) -> Unit\)\? = null\n\) \{'
sig_replace = """fun ActiveEmulationScreen(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onToggleDocked: () -> Unit = {},
    onStopEmulation: () -> Unit = {},
    onRunDevSelfTest: (() -> Unit)? = null,
    onSurfaceReady: ((android.view.Surface) -> Unit)? = null
) {"""
code = re.sub(sig_find, sig_replace, code)

# Replace session.vulkanGpu.setSurface with onSurfaceReady
cb_find = r'session\.vulkanGpu\.setSurface\(holder\.surface\)'
cb_replace = 'onSurfaceReady?.invoke(holder.surface)'
code = re.sub(cb_find, cb_replace, code)

# Also update GameDisplayCanvas signature
gd_find = r'private fun GameDisplayCanvas\(\n    session: ActiveEmulationSession,\n    coreState: SwitchCoreState,\n    onRunDevSelfTest: \(\(\) -> Unit\)\? = null\n\) \{'
gd_replace = """private fun GameDisplayCanvas(
    session: ActiveEmulationSession,
    coreState: SwitchCoreState,
    onRunDevSelfTest: (() -> Unit)? = null,
    onSurfaceReady: ((android.view.Surface) -> Unit)? = null
) {"""
code = re.sub(gd_find, gd_replace, code)

gd_call_find = r'GameDisplayCanvas\(session, coreState, onRunDevSelfTest\)'
gd_call_replace = 'GameDisplayCanvas(session, coreState, onRunDevSelfTest, onSurfaceReady)'
code = re.sub(gd_call_find, gd_call_replace, code)

with open("app/src/main/java/com/example/ui/screens/ActiveEmulationScreen.kt", "w") as f:
    f.write(code)

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

ma_find = r'onStopEmulation = \{ viewModel\.stopEmulationSession\(\) \},'
ma_replace = 'onStopEmulation = { viewModel.stopEmulationSession() },\n                        onSurfaceReady = { surface -> viewModel.setVulkanSurface(surface) },'
code = re.sub(ma_find, ma_replace, code)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "r") as f:
    code = f.read()

vm_add = """
    fun setVulkanSurface(surface: android.view.Surface) {
        switchCoreEngine.vulkanGpu.setSurface(surface)
    }
"""
code = code.replace("fun updateCoreSettings", vm_add + "\n    fun updateCoreSettings")

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.write(code)

