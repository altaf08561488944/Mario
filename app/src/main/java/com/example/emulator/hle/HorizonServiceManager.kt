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
        return when (handle) {
            0x100 -> handleSmService(request)
            0x101 -> handleFspSrvService(request)
            0x102 -> handleNvdrvService(request)
            0x103 -> handleAppletService(request)
            0x104 -> handleAudrenService(request)
            0x105 -> handleViService(request)
            0x106 -> handleHidService(request)
            0x107 -> handleSetSysService(request)
            0x108 -> handleTimeService(request)
            else -> {
                Log.d("HorizonOS", "IPC request to handle 0x${handle.toString(16)} (cmd: ${request.commandId})")
                val fallbackResp = ByteArray(8)
                fallbackResp[0] = 0 // Result Code 0 (Success)
                fallbackResp
            }
        }
    }

    // --- Service Implementations ---

    private fun handleSmService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "sm: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // Initialize
                ByteArray(0)
            }
            1 -> { // GetService
                // Extract requested service name (up to 8 bytes)
                val sname = if (request.dataBytes.size >= 8) {
                    String(request.dataBytes, 0, 8).trim('\u0000', ' ')
                } else "unknown"
                Log.i("HorizonOS", "sm: GetService requested for: '$sname'")
                val newHandle = createServiceHandle(sname)
                val response = ByteArray(8)
                response[0] = (newHandle and 0xFF).toByte()
                response[1] = ((newHandle shr 8) and 0xFF).toByte()
                response[2] = ((newHandle shr 16) and 0xFF).toByte()
                response[3] = ((newHandle shr 24) and 0xFF).toByte()
                response
            }
            2 -> { // RegisterService
                ByteArray(0)
            }
            else -> ByteArray(0)
        }
    }

    private fun handleFspSrvService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "fsp-srv: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> ByteArray(0) // Initialize
            1 -> { // OpenUserSaveDataFileSystem
                val fsHandle = createServiceHandle("fsp-srv:save")
                val resp = ByteArray(4)
                resp[0] = (fsHandle and 0xFF).toByte()
                resp[1] = ((fsHandle shr 8) and 0xFF).toByte()
                resp
            }
            18 -> { // OpenSdCardFileSystem
                val sdHandle = createServiceHandle("fsp-srv:sdcard")
                val resp = ByteArray(4)
                resp[0] = (sdHandle and 0xFF).toByte()
                resp[1] = ((sdHandle shr 8) and 0xFF).toByte()
                resp
            }
            else -> ByteArray(0)
        }
    }

    private fun handleNvdrvService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "nvdrv: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // Open device node (returns fd)
                val resp = ByteArray(8)
                resp[0] = 0x10 // fd 0x10
                resp
            }
            1 -> { // Ioctl
                val resp = ByteArray(4)
                resp[0] = 0 // Error Code 0 (Success)
                resp
            }
            2 -> ByteArray(4) // Close
            3 -> ByteArray(4) // Initialize
            else -> ByteArray(4)
        }
    }

    private fun handleAppletService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "appletOE: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // OpenSession
                val sessionHandle = createServiceHandle("appletOE:session")
                val resp = ByteArray(4)
                resp[0] = (sessionHandle and 0xFF).toByte()
                resp
            }
            10 -> { // GetOperationMode (0: Handheld, 1: Docked)
                byteArrayOf(0)
            }
            11 -> { // GetPerformanceMode (0: Normal/Handheld, 1: Boost/Docked)
                val resp = ByteArray(4)
                resp[0] = 0
                resp
            }
            else -> ByteArray(0)
        }
    }

    private fun handleAudrenService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "audren: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // OpenAudioRenderer
                val rendererHandle = createServiceHandle("audren:renderer")
                val resp = ByteArray(4)
                resp[0] = (rendererHandle and 0xFF).toByte()
                resp
            }
            1 -> { // GetAudioDeviceService
                val devHandle = createServiceHandle("audren:dev")
                val resp = ByteArray(4)
                resp[0] = (devHandle and 0xFF).toByte()
                resp
            }
            else -> ByteArray(0)
        }
    }

    private fun handleViService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "vi: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // GetDisplayService
                val viHandle = createServiceHandle("vi:u")
                val resp = ByteArray(4)
                resp[0] = (viHandle and 0xFF).toByte()
                resp
            }
            1010 -> { // OpenDisplay
                val resp = ByteArray(8)
                resp[0] = 1 // Display ID 1
                resp
            }
            2020 -> { // OpenLayer
                val resp = ByteArray(8)
                resp[0] = 0x00
                resp[1] = 0x01 // Layer ID 0x100
                resp
            }
            else -> ByteArray(0)
        }
    }

    private fun handleHidService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "hid: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // CreateAppletResource
                val appletHandle = createServiceHandle("hid:AppletResource")
                val resp = ByteArray(4)
                resp[0] = (appletHandle and 0xFF).toByte()
                resp
            }
            else -> ByteArray(0)
        }
    }

    private fun handleSetSysService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "set:sys: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            0 -> { // GetLanguageCode ("en-US" = 0x000053552D6E65)
                val resp = ByteArray(8)
                val langCode = 0x000053552D6E65L
                for (i in 0..7) {
                    resp[i] = ((langCode ushr (i * 8)) and 0xFFL).toByte()
                }
                resp
            }
            3 -> { // GetFirmwareVersion (17.0.0)
                val resp = ByteArray(0x100)
                resp[0] = 17 // Major
                resp[1] = 0  // Minor
                resp[2] = 0  // Micro
                val verStr = "17.0.0 (SWTC NOOS)"
                System.arraycopy(verStr.toByteArray(), 0, resp, 4, verStr.length)
                resp
            }
            else -> ByteArray(0)
        }
    }

    private fun handleTimeService(request: HorizonIpc.IpcRequest): ByteArray {
        Log.d("HorizonOS", "time: Command ID ${request.commandId} received.")
        return when (request.commandId) {
            100 -> { // GetCurrentTime
                val nowSec = System.currentTimeMillis() / 1000L
                val resp = ByteArray(8)
                for (i in 0..7) {
                    resp[i] = ((nowSec shr (i * 8)) and 0xFFL).toByte()
                }
                resp
            }
            else -> ByteArray(0)
        }
    }
}

