package com.example.emulator.cpu

import android.util.Log

/**
 * Native CPU Core (JNI Bridge).
 * Bridges the Kotlin emulator logic to the high-performance C++ ARM64 JIT Engine.
 */
class NativeCpuCore {
    var isLoaded = false
        private set

    external fun nativeInitialize()
    external fun nativeExecute(ticks: Int): Int

    fun initialize() {
        try {
            // Both GPU and CPU C++ engines are compiled into this single .so file
            System.loadLibrary("vulkan_backend") 
            nativeInitialize()
            isLoaded = true
            Log.i("NativeCpuCore", "C++ ARM64 JIT Engine hooked successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeCpuCore", "libvulkan_backend.so not found! C++ CPU Engine offline.")
            isLoaded = false
        }
    }

    fun execute(ticks: Int) {
        if (isLoaded) {
            nativeExecute(ticks)
        }
    }
}
