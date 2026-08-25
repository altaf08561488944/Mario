import sys

content = open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt").read()

if "fun updateCoreSettings" not in content:
    content = content.replace("fun toggleDockedMode()", "fun updateCoreSettings(targetFps: Int) {\n        switchCoreEngine.applySettings(targetFps)\n    }\n\n    fun toggleDockedMode()")

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.write(content)

