package com.example.emulator.hle

import com.example.emulator.memory.MemoryManagementUnit

/**
 * Horizon OS IPC (Inter-Process Communication) Manager.
 * Parses CMIF (Command Interface) messages between the guest application and HLE services.
 */
class HorizonIpc(private val mmu: MemoryManagementUnit) {

    data class IpcRequest(
        val type: Int,
        val commandId: Int,
        val dataBytes: ByteArray,
        val rawHeader: IntArray
    )

    /**
     * Parses an IPC Request from the Thread Local Storage (TLS) buffer.
     * Switch IPC uses a complex header containing type, lengths, pointers, and data words.
     */
    fun parseRequest(tlsAddress: Long): IpcRequest {
        val word0 = mmu.read32(tlsAddress)
        val word1 = mmu.read32(tlsAddress + 4)
        
        val type = word0 and 0xFFFF
        val numXDescriptors = (word0 ushr 16) and 0xF
        val numADescriptors = (word0 ushr 20) and 0xF
        val numBDescriptors = (word0 ushr 24) and 0xF
        val numWDescriptors = (word0 ushr 28) and 0xF
        
        val rawDataSizeWords = word1 and 0x3FF
        val cDescriptorFlags = (word1 ushr 10) and 0xF
        val hasHandleDescriptor = ((word1 ushr 31) and 0x1) == 1

        var currentOffset = tlsAddress + 8

        // Skip Handle Descriptor if present
        if (hasHandleDescriptor) {
            val handleWord = mmu.read32(currentOffset)
            val sendPid = (handleWord and 0x1) != 0
            val copyHandles = (handleWord ushr 1) and 0xF
            val moveHandles = (handleWord ushr 5) and 0xF
            currentOffset += 4 + (if (sendPid) 8 else 0) + (copyHandles * 4) + (moveHandles * 4)
        }

        // Skip X, A, B, W descriptors
        currentOffset += (numXDescriptors * 8) + (numADescriptors * 12) + (numBDescriptors * 12) + (numWDescriptors * 12)

        // Ensure 16-byte alignment for SFCI/SFCO magic
        val padding = (16 - (currentOffset % 16)) % 16
        currentOffset += padding

        // Read Data Header (SFCI)
        // val magic = mmu.read32(currentOffset) // Should be "SFCI" (0x49434653)
        val commandId = mmu.read32(currentOffset + 8)
        
        val dataBytes = ByteArray((rawDataSizeWords * 4))
        mmu.writeBytes(currentOffset + 16, dataBytes)

        return IpcRequest(
            type = type,
            commandId = commandId,
            dataBytes = dataBytes,
            rawHeader = intArrayOf(word0, word1)
        )
    }

    /**
     * Writes an IPC Response (SFCO) back to the TLS buffer.
     */
    fun writeResponse(tlsAddress: Long, commandId: Int, resultCode: Int, outData: ByteArray) {
        // Simplified SFCO creation
        mmu.write32(tlsAddress, 0x00000000) // Type 0 (Regular Response)
        mmu.write32(tlsAddress + 4, (outData.size / 4) + 4) 
        
        var currentOffset = tlsAddress + 8
        val padding = (16 - (currentOffset % 16)) % 16
        currentOffset += padding

        mmu.write32(currentOffset, 0x4F434653) // "SFCO"
        mmu.write32(currentOffset + 4, 0)
        mmu.write32(currentOffset + 8, resultCode)
        mmu.write32(currentOffset + 12, 0) // Padding
        
        if (outData.isNotEmpty()) {
            mmu.writeBytes(currentOffset + 16, outData)
        }
    }
}
