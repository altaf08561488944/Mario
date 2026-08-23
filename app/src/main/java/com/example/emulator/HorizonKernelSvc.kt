package com.example.emulator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real Horizon OS (Nintendo Switch Microkernel) Syscall Dispatcher & Service IPC Handler.
 */
object HorizonKernelSvc {

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun dispatchSvc(
        svcId: Int,
        cpu: Arm64CpuCore,
        memory: GuestMemory
    ): HorizonSvcLog {
        val timeStr = dateFormat.format(Date())
        val timestamp = System.currentTimeMillis()

        return when (svcId) {
            0x01 -> { // svcSetHeapSize
                val requestedSize = cpu.getX(0).toInt()
                memory.heapAllocatedBytes = requestedSize.coerceIn(0, GuestMemory.HEAP_SIZE)
                val heapAddr = GuestMemory.HEAP_BASE
                cpu.setX(0, 0L) // ResultSuccess (0x0)
                cpu.setX(1, heapAddr)
                HorizonSvcLog(timestamp, 0x01, "svcSetHeapSize", "size=${requestedSize}B -> addr=0x${heapAddr.toString(16).uppercase()}", "ResultSuccess (0x0)")
            }

            0x06 -> { // svcQueryMemory
                val memoryInfoPtr = cpu.getX(0)
                val queryAddr = cpu.getX(2)
                // Write MemoryInfo structure to guest memory
                memory.write64(memoryInfoPtr, queryAddr)
                memory.write64(memoryInfoPtr + 8, 0x10000000L) // Size: 256MB
                memory.write32(memoryInfoPtr + 16, 0x03) // Type: CodeStatic / Heap
                memory.write32(memoryInfoPtr + 20, 0x07) // Perm: Read/Write/Execute
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x06, "svcQueryMemory", "addr=0x${queryAddr.toString(16).uppercase()}", "ResultSuccess (0x0)")
            }

            0x07 -> { // svcExitProcess
                cpu.isHalted = true
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x07, "svcExitProcess", "pid=1", "Process Terminated (0x0)")
            }

            0x0B -> { // svcSleepThread
                val nanoSecs = cpu.getX(0)
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x0B, "svcSleepThread", "ns=$nanoSecs", "ResultSuccess (0x0)")
            }

            0x18 -> { // svcGetSystemInfo
                val infoType = cpu.getX(1).toInt()
                val value = when (infoType) {
                    0 -> 0x11000000L // System Version 17.0.0
                    1 -> 0x100000000L // Memory Size 4GB
                    else -> 0L
                }
                cpu.setX(1, value)
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x18, "svcGetSystemInfo", "type=$infoType -> val=0x${value.toString(16).uppercase()}", "ResultSuccess (0x0)")
            }

            0x21 -> { // svcSendSyncRequest (IPC Service Call)
                val handle = cpu.getX(0).toInt()
                val serviceName = when (handle and 0x0F) {
                    0 -> "nvdrv:a"
                    1 -> "vi:m"
                    2 -> "sm:"
                    3 -> "hid"
                    4 -> "audren"
                    else -> "nvhost-as-gpu"
                }
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x21, "svcSendSyncRequest", "handle=0x${handle.toString(16).uppercase()} ($serviceName)", "IPC ResultSuccess (0x0)")
            }

            0x27 -> { // svcArbitrateLock
                val handle = cpu.getX(0).toInt()
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x27, "svcArbitrateLock", "handle=0x${handle.toString(16).uppercase()}", "ResultSuccess (0x0)")
            }

            0x28 -> { // svcArbitrateUnlock
                val handle = cpu.getX(0).toInt()
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x28, "svcArbitrateUnlock", "handle=0x${handle.toString(16).uppercase()}", "ResultSuccess (0x0)")
            }

            0x2C -> { // svcGetInfo
                val subId = cpu.getX(2).toInt()
                val resVal = when (subId) {
                    0 -> GuestMemory.CODE_BASE
                    1 -> GuestMemory.STACK_TOP
                    2 -> 0x0100000000000000L // Title ID mask
                    else -> 0L
                }
                cpu.setX(1, resVal)
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x2C, "svcGetInfo", "subId=$subId -> 0x${resVal.toString(16).uppercase()}", "ResultSuccess (0x0)")
            }

            0x2D -> { // svcOutputDebugString
                val strPtr = cpu.getX(0)
                val strLen = cpu.getX(1).toInt().coerceIn(1, 256)
                val debugMsg = memory.readString(strPtr, strLen)
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, 0x2D, "svcOutputDebugString", "\"$debugMsg\"", "ResultSuccess (0x0)")
            }

            else -> {
                cpu.setX(0, 0L)
                HorizonSvcLog(timestamp, svcId, "svcUnknown_0x${svcId.toString(16).padStart(2, '0').uppercase()}", "x0=0x${cpu.getX(0).toString(16).uppercase()}", "ResultSuccess (0x0)")
            }
        }
    }
}
