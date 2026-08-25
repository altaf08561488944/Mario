import sys

content = open("app/src/main/java/com/example/emulator/gpu/VulkanTranslator.kt").read()

if "nativeSetTargetFps" not in content:
    content = content.replace("private external fun nativeSubmitAndPresent()", "private external fun nativeSubmitAndPresent()\n    private external fun nativeSetTargetFps(fps: Int)")
    
if "fun setTargetFps" not in content:
    content = content.replace("fun submitCommandBuffer", "fun setTargetFps(fps: Int) {\n        if (isInitialized) nativeSetTargetFps(fps)\n    }\n\n    fun submitCommandBuffer")

with open("app/src/main/java/com/example/emulator/gpu/VulkanTranslator.kt", "w") as f:
    f.write(content)
