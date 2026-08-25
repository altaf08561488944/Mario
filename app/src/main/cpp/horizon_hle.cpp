#include <jni.h>
#include <android/log.h>
#include <string>
#include "horizon_ipc.h"
#include "memory_mmu.h"

#define LOG_TAG "SwtcHorizonHLE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ======================================================================
// HORIZON OS HLE & IPC NATIVE JNI BINDINGS
// ======================================================================

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_hle_HorizonServiceManager_nativeProcessIpcSyncRequest(
    JNIEnv* env, jobject /* this */, jint handle, jlong tlsAddress) {
    
    auto& mmu = SwtcMmu::getGlobalMmu();
    uint32_t result = SwtcHorizon::HorizonIpcManager::getInstance().processSendSyncRequest(
        static_cast<uint32_t>(handle),
        static_cast<uint64_t>(tlsAddress),
        mmu
    );
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_hle_HorizonServiceManager_nativeCreateServiceHandle(
    JNIEnv* env, jobject /* this */, jstring serviceName) {
    
    if (!serviceName) return 0;
    const char* nativeStr = env->GetStringUTFChars(serviceName, nullptr);
    std::string sName(nativeStr);
    env->ReleaseStringUTFChars(serviceName, nativeStr);

    uint32_t handle = SwtcHorizon::HorizonIpcManager::getInstance().createHandleForService(sName);
    return static_cast<jint>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_hle_HorizonServiceManager_nativeCloseServiceHandle(
    JNIEnv* env, jobject /* this */, jint handle) {
    SwtcHorizon::HorizonIpcManager::getInstance().closeHandle(static_cast<uint32_t>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_hle_HorizonServiceManager_nativeGetIpcSummary(
    JNIEnv* env, jobject /* this */) {
    std::string summary = SwtcHorizon::HorizonIpcManager::getInstance().getIpcSummary();
    return env->NewStringUTF(summary.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_hle_HorizonServiceManager_nativeResetIpc(
    JNIEnv* env, jobject /* this */) {
    SwtcHorizon::HorizonIpcManager::getInstance().reset();
}

