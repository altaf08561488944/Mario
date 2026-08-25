import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

if "import com.example.emulator.gpu.FramePacer" not in content:
    content = content.replace("package com.example.emulator\n", "package com.example.emulator\n\nimport com.example.emulator.gpu.FramePacer\n")

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

