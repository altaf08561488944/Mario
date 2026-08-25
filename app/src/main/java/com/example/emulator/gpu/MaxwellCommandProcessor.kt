package com.example.emulator.gpu

import android.util.Log

/**
 * Bridges the Kotlin CPU thread to the C++ Maxwell GPU Command Queue.
 * Fulfills Target #2 (GPU Command Processor) and Target #11 (Multithreading).
 */
class MaxwellCommandProcessor {
    var isRunning = false
        private set

    external fun nativeStartQueue()
    external fun nativeStopQueue()
    external fun nativePushCommand(method: Int, argument: Int, subchannel: Int)

    fun start() {
        if (!isRunning) {
            try {
                nativeStartQueue()
                isRunning = true
                Log.i("MaxwellCommandProcessor", "C++ Maxwell Command Queue Worker started.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("MaxwellCommandProcessor", "Failed to start native queue. Library missing.")
            }
        }
    }

    fun stop() {
        if (isRunning) {
            nativeStopQueue()
            isRunning = false
        }
    }

    /**
     * Asynchronously pushes a Maxwell command into the C++ ring buffer.
     * The CPU thread will NOT block while Vulkan processes this command.
     */
    fun pushCommand(method: Int, argument: Int, subchannel: Int = 0) {
        if (isRunning) {
            nativePushCommand(method, argument, subchannel)
        }
    }
}
