package com.example.emulator.hle

import com.example.emulator.memory.MemoryManagementUnit
import android.util.Log

/**
 * Horizon OS Service Manager (HLE).
 * Routes IPC requests to the appropriate native C++ or Kotlin emulated service (sm, fs, nvdrv, etc.).
 */
class HorizonServiceManager {

    private var mmu: MemoryManagementUnit? = null
    private var ipcManager: HorizonIpc? = null
    private var isNativeLoaded = false

    // Native JNI Bindings
    private external fun nativeProcessIpcSyncRequest(handle: Int, tlsAddress: Long): Int
    private external fun nativeCreateServiceHandle(serviceName: String): Int
    private external fun nativeCloseServiceHandle(handle: Int)
    private external fun nativeGetIpcSummary(): String
    private external fun nativeResetIpc()

    // Service Registry (Fallback mode)
    private val activeServices = mutableMapOf<String, Int>()
    
    fun initialize(memory: MemoryManagementUnit) {
        this.mmu = memory
        this.ipcManager = HorizonIpc(memory)
        
        try {
            System.loadLibrary("vulkan_backend")
            isNativeLoaded = true
            Log.i("HorizonOS", "Native C++ Horizon IPC Manager loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("HorizonOS", "Native Horizon IPC library unavailable, using pure Kotlin HLE fallback.")
            isNativeLoaded = false
        }

        // Register core OS services
        activeServices["sm:"] = 0x100
        activeServices["fsp-srv"] = 0x101
        activeServices["nvdrv:a"] = 0x102
        activeServices["appletOE"] = 0x103
        activeServices["audren:u"] = 0x104
        
        Log.i("HorizonOS", "HLE Kernel Services Initialized (Native=$isNativeLoaded).")
    }

    /**
     * Handles an SVC (Supervisor Call) from the guest CPU.
     */
    fun handleSvc(svcNumber: Int, registers: LongArray): Long {
        when (svcNumber) {
            0x1B -> { // svcSendSyncRequest
                val handle = registers[0].toInt()
                val tlsAddress = 0x11000000L // Default Thread Local Storage address
                
                if (isNativeLoaded) {
                    try {
                        val nativeResult = nativeProcessIpcSyncRequest(handle, tlsAddress)
                        return nativeResult.toLong()
                    } catch (e: UnsatisfiedLinkError) {
                        Log.e("HorizonOS", "Native IPC call failed, falling back to Kotlin HLE", e)
                    }
                }

                // Fallback to Kotlin parser
                ipcManager?.let { ipc ->
                    val request = ipc.parseRequest(tlsAddress)
                    val responseBytes = dispatchIpcRequest(handle, request)
                    ipc.writeResponse(tlsAddress, request.commandId, 0, responseBytes)
                }
                return 0L // Result Code: Success
            }
            0x01 -> { // svcSetHeapSize
                return 0L
            }
            0x1F -> { // svcConnectToNamedPort
                val portName = "sm:"
                val handle = createServiceHandle(portName)
                registers[1] = handle.toLong()
                return 0L
            }
            0x16 -> { // svcCloseHandle
                val handle = registers[0].toInt()
                closeServiceHandle(handle)
                return 0L
            }
            else -> {
                Log.w("HorizonOS", "Unhandled SVC call: 0x${svcNumber.toString(16)}")
                return 0L
            }
        }
    }

    fun createServiceHandle(serviceName: String): Int {
        if (isNativeLoaded) {
            try {
                return nativeCreateServiceHandle(serviceName)
            } catch (e: UnsatisfiedLinkError) {
                // Fallback
            }
        }
        return activeServices[serviceName] ?: 0x1FF
    }

    fun closeServiceHandle(handle: Int) {
        if (isNativeLoaded) {
            try {
                nativeCloseServiceHandle(handle)
            } catch (e: UnsatisfiedLinkError) {
                // Fallback
            }
        }
    }

    fun getIpcSummary(): String {
        if (isNativeLoaded) {
            try {
                return nativeGetIpcSummary()
            } catch (e: UnsatisfiedLinkError) {
                // Fallback
            }
        }
        return "Horizon HLE (Kotlin Fallback Mode)\nActive Services: ${activeServices.keys.joinToString()}"
    }

    fun reset() {
        if (isNativeLoaded) {
            try {
                nativeResetIpc()
            } catch (e: UnsatisfiedLinkError) {
                // Fallback
            }
        }
    }

    private fun dispatchIpcRequest(handle: Int, request: HorizonIpc.IpcRequest): ByteArray {
        // HLE Routing based on registered service handles
        return when (handle) {
            0x100 -> handleSmService(request)
            0x101 -> handleFspSrvService(request)
            0x102 -> handleNvdrvService(request)
            0x103 -> handleAppletService(request)
            else -> {
                Log.w("HorizonOS", "IPC request to unknown handle 0x${handle.toString(16)}")
                ByteArray(0)
            }
        }
    }

    // --- Service Implementations (Stubs returning successful response codes) ---

    private fun handleSmService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "sm: Command ID ${request.commandId} received.")
        if (request.commandId == 1) {
            val response = ByteArray(4)
            response[0] = 0x02
            return response
        }
        return ByteArray(0)
    }

    private fun handleFspSrvService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "fsp-srv: Command ID ${request.commandId} received.")
        return ByteArray(0)
    }

    private fun handleNvdrvService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "nvdrv: Command ID ${request.commandId} received.")
        return ByteArray(0)
    }

    private fun handleAppletService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "appletOE: Command ID ${request.commandId} received.")
        return ByteArray(0)
    }
}

