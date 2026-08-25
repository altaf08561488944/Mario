#include <jni.h>
#include <android/log.h>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <atomic>
#include <vector>
#include "vulkan_validation.h"

#define LOG_TAG "SwtcMaxwellQueue"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ======================================================================
// SWTC NOOS - MAXWELL GPU COMMAND PROCESSOR & QUEUE MANAGEMENT
// ======================================================================

struct MaxwellCommand {
    uint32_t method;
    uint32_t argument;
    uint32_t subchannel;
};

class GpuCommandQueue {
private:
    std::queue<MaxwellCommand> queue;
    std::mutex queueMutex;
    std::condition_variable cv;
    std::thread workerThread;
    std::atomic<bool> isRunning{false};

    void workerLoop() {
        LOGI("Maxwell GPU Command Worker Thread started. Awaiting GPFIFO pushes...");
        
        // This thread runs entirely decoupled from the CPU Emulation thread,
        // fulfilling the Performance Architecture specification (Target #11).
        while (isRunning) {
            MaxwellCommand cmd;
            {
                std::unique_lock<std::mutex> lock(queueMutex);
                cv.wait(lock, [this] { return !queue.empty() || !isRunning; });
                
                if (!isRunning && queue.empty()) {
                    break;
                }
                
                cmd = queue.front();
                queue.pop();
            }
            
            // Route command to the Vulkan Backend or Shader Recompiler
            processCommand(cmd);
        }
        LOGI("Maxwell GPU Command Worker Thread stopped.");
    }

    void processCommand(const MaxwellCommand& cmd) {
        // Validate Maxwell 3D command & detect rendering anomalies
        SwtcVulkan::VulkanValidationManager::getInstance().validateMaxwellMethod(
            cmd.method, cmd.argument, cmd.subchannel);
    }

public:
    void start() {
        if (isRunning) return;
        isRunning = true;
        workerThread = std::thread(&GpuCommandQueue::workerLoop, this);
    }

    void stop() {
        if (!isRunning) return;
        isRunning = false;
        cv.notify_all();
        if (workerThread.joinable()) {
            workerThread.join();
        }
    }

    void pushCommand(uint32_t method, uint32_t argument, uint32_t subchannel) {
        {
            std::lock_guard<std::mutex> lock(queueMutex);
            queue.push({method, argument, subchannel});
        }
        cv.notify_one();
    }
};

GpuCommandQueue commandQueue;

// JNI BINDINGS FOR KOTLIN
extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_MaxwellCommandProcessor_nativeStartQueue(JNIEnv* env, jobject /* this */) {
    commandQueue.start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_MaxwellCommandProcessor_nativeStopQueue(JNIEnv* env, jobject /* this */) {
    commandQueue.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_MaxwellCommandProcessor_nativePushCommand(JNIEnv* env, jobject /* this */, jint method, jint argument, jint subchannel) {
    // This allows the Kotlin CPU emulation thread to asynchronously push
    // GPU macro commands into the native C++ ring buffer without blocking.
    commandQueue.pushCommand(method, argument, subchannel);
}
