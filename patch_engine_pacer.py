import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

if "fun applySettings" not in content:
    content = content.replace("fun startEmulation(", "fun applySettings(targetFps: Int) {\n        framePacer.setTargetFps(targetFps)\n    }\n\n    fun startEmulation(")

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

