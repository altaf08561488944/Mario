import sys

content = open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt").read()

if "fun updateCoreSettings" not in content:
    new_method = """
    fun updateCoreSettings(targetFps: Int) {
        switchCoreEngine.applySettings(targetFps)
    }
    
    fun startEmulationSession"""
    content = content.replace("fun startEmulationSession", new_method)

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.write(content)
