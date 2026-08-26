import re

with open("app/src/main/java/com/example/emulator/gpu/VulkanTranslator.kt", "r") as f:
    code = f.read()

# Add setSurface to JNI 
jni_pattern = r'private external fun nativeInitializeVulkan\(\): Boolean'
jni_replace = 'private external fun nativeSetSurface(surface: android.view.Surface)\n    private external fun nativeInitializeVulkan(): Boolean'
code = re.sub(jni_pattern, jni_replace, code)

# Add setSurface wrapper function
wrapper_pattern = r'fun setValidationLayersEnabled\(enabled: Boolean\) \{'
wrapper_replace = """fun setSurface(surface: android.view.Surface) {
        if (isInitialized) {
            try {
                nativeSetSurface(surface)
            } catch(e: UnsatisfiedLinkError) {
                // stub
            }
        }
    }
    
    fun setValidationLayersEnabled(enabled: Boolean) {"""
code = re.sub(wrapper_pattern, wrapper_replace, code)

with open("app/src/main/java/com/example/emulator/gpu/VulkanTranslator.kt", "w") as f:
    f.write(code)
