package com.example.emulator.hle

import com.example.emulator.memory.MemoryManagementUnit

/**
 * Horizon OS Service Manager (HLE).
 * Routes IPC requests to the appropriate emulated service (sm, fs, nvdrv, etc.).
 */
class HorizonServiceManager {

    private var mmu: MemoryManagementUnit? = null
    private var ipcManager: HorizonIpc? = null

    fun initialize(memory: MemoryManagementUnit) {
        this.mmu = memory
        this.ipcManager = HorizonIpc(memory)
    }

    /**
     * Handles an SVC (Supervisor Call) from the guest CPU.
     */
    fun handleSvc(svcNumber: Int, registers: LongArray): Long {
        when (svcNumber) {
            0x1B -> { // svcSendSyncRequest
                val handle = registers[0].toInt()
                val tlsAddress = 0x11000000L // Example TLS address
                
                ipcManager?.let { ipc ->
                    val request = ipc.parseRequest(tlsAddress)
                    val responseBytes = dispatchIpcRequest(handle, request)
                    ipc.writeResponse(tlsAddress, request.commandId, 0, responseBytes)
                }
                return 0L // Result Code: Success
            }
            0x01 -> { // svcSetHeapSize
                // ... handle heap allocation
                return 0L
            }
            // Add other SVCs (SleepThread, ConnectToNamedPort, etc.)
            else -> return 0L
        }
    }

    private fun dispatchIpcRequest(handle: Int, request: HorizonIpc.IpcRequest): ByteArray {
        // In a real emulator, 'handle' determines which service object gets the call.
        // For now, return a dummy empty response.
        return ByteArray(0)
    }
}
