import sys

content = open("app/src/main/cpp/vulkan_backend.cpp").read()

# Add includes if not present
includes = """#include <chrono>
#include <thread>
"""
if "<chrono>" not in content:
    content = content.replace("#include <vector>", "#include <vector>\n#include <chrono>\n#include <thread>")

pacer_code = """
// ======================================================================
// SWTC NOOS - NATIVE FRAME PACER & VSYNC (Target #11)
// ======================================================================
class NativeFramePacer {
private:
    std::chrono::high_resolution_clock::time_point lastFrameTime;
    double targetFrameTimeMs;
    int targetFps;

public:
    NativeFramePacer(int fps = 60) {
        setTargetFps(fps);
        lastFrameTime = std::chrono::high_resolution_clock::now();
    }

    void setTargetFps(int fps) {
        targetFps = fps;
        if (fps <= 0) targetFrameTimeMs = 0.0;
        else targetFrameTimeMs = 1000.0 / fps;
        LOGI("Native FramePacer: Target FPS set to %d (%.2f ms/frame)", fps, targetFrameTimeMs);
    }

    void pace() {
        if (targetFrameTimeMs <= 0.0) return;
        
        auto now = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double, std::milli> elapsed = now - lastFrameTime;
        double sleepTimeMs = targetFrameTimeMs - elapsed.count();
        
        if (sleepTimeMs > 0.0) {
            std::this_thread::sleep_for(std::chrono::duration<double, std::milli>(sleepTimeMs));
        }
        
        // Reset lastFrameTime to now after waking up
        lastFrameTime = std::chrono::high_resolution_clock::now();
    }
};

NativeFramePacer nativePacer(60);

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSetTargetFps(JNIEnv* env, jobject /* this */, jint fps) {
    nativePacer.setTargetFps(fps);
}
"""

if "class NativeFramePacer" not in content:
    content = content.replace("// JNI BINDINGS", pacer_code + "\n// JNI BINDINGS")

if "nativePacer.pace();" not in content:
    content = content.replace("if (!vkCtx.isInitialized) return;", "if (!vkCtx.isInitialized) return;\n    \n    // Enforce strict frame pacing to maintain 34-60 FPS without stutter\n    nativePacer.pace();")

with open("app/src/main/cpp/vulkan_backend.cpp", "w") as f:
    f.write(content)
