package com.example.emulator.memory

import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced Memory Management Unit (MMU) for SWTC NOOS.
 * Emulates the Tegra X1 Unified Memory Architecture (4GB).
 * Implements Sparse Memory Allocation, Page Tables (4KB pages), and TLB caching
 * to prevent exhausting Android host RAM.
 */
class MemoryManagementUnit {
    companion object {
        const val PAGE_SIZE = 4096L
        const val PAGE_MASK = (PAGE_SIZE - 1).inv()

        // Page Permissions
        const val PERM_READ = 1
        const val PERM_WRITE = 2
        const val PERM_EXEC = 4
        const val PERM_GPU_READ = 8
        const val PERM_GPU_WRITE = 16

        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("vulkan_backend")
                isNativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
            }
        }
    }

    // Native JNI functions
    private external fun nativeMapMemory(vaddr: Long, size: Long, perms: Int): Boolean
    private external fun nativeUnmapMemory(vaddr: Long, size: Long): Boolean
    private external fun nativeRead8(vaddr: Long): Byte
    private external fun nativeWrite8(vaddr: Long, value: Byte)
    private external fun nativeRead32(vaddr: Long): Int
    private external fun nativeWrite32(vaddr: Long, value: Int)
    private external fun nativeRead64(vaddr: Long): Long
    private external fun nativeWrite64(vaddr: Long, value: Long)
    private external fun nativeGetAllocatedBytes(): Long
    private external fun nativeGetTlbHits(): Long
    private external fun nativeGetTlbMisses(): Long
    private external fun nativeReset()

    // Sparse Page Table fallback: Maps Virtual Page Number (VPN) to actual allocated ByteArrays
    private val pageTable = ConcurrentHashMap<Long, Page>()

    data class Page(
        val vpn: Long,
        val data: ByteArray = ByteArray(PAGE_SIZE.toInt()),
        var isReadable: Boolean = true,
        var isWritable: Boolean = true,
        var isExecutable: Boolean = false
    )

    fun allocateMemory(vaddr: Long, size: Long, read: Boolean, write: Boolean, exec: Boolean) {
        if (isNativeLoaded) {
            var perms = 0
            if (read) perms = perms or PERM_READ or PERM_GPU_READ
            if (write) perms = perms or PERM_WRITE or PERM_GPU_WRITE
            if (exec) perms = perms or PERM_EXEC
            nativeMapMemory(vaddr, size, perms)
        }

        var currentAddr = vaddr and PAGE_MASK
        val endAddr = (vaddr + size + PAGE_SIZE - 1) and PAGE_MASK

        while (currentAddr < endAddr) {
            val vpn = currentAddr / PAGE_SIZE
            if (!pageTable.containsKey(vpn)) {
                pageTable[vpn] = Page(vpn, isReadable = read, isWritable = write, isExecutable = exec)
            } else {
                val page = pageTable[vpn]!!
                page.isReadable = read
                page.isWritable = write
                page.isExecutable = exec
            }
            currentAddr += PAGE_SIZE
        }
    }

    private fun getPage(vaddr: Long): Page {
        val vpn = (vaddr and PAGE_MASK) / PAGE_SIZE
        return pageTable[vpn] ?: throw MemoryFaultException(vaddr, "Unmapped memory access")
    }

    fun write8(vaddr: Long, value: Byte) {
        if (isNativeLoaded) {
            nativeWrite8(vaddr, value)
            return
        }
        val page = getPage(vaddr)
        if (!page.isWritable) throw MemoryFaultException(vaddr, "Memory is not writable")
        val offset = (vaddr and (PAGE_SIZE - 1)).toInt()
        page.data[offset] = value
    }

    fun read8(vaddr: Long): Byte {
        if (isNativeLoaded) {
            return nativeRead8(vaddr)
        }
        val page = getPage(vaddr)
        if (!page.isReadable) throw MemoryFaultException(vaddr, "Memory is not readable")
        val offset = (vaddr and (PAGE_SIZE - 1)).toInt()
        return page.data[offset]
    }

    fun write32(vaddr: Long, value: Int) {
        if (isNativeLoaded) {
            nativeWrite32(vaddr, value)
            return
        }
        val page = getPage(vaddr)
        if (!page.isWritable) throw MemoryFaultException(vaddr, "Memory is not writable")
        val offset = (vaddr and (PAGE_SIZE - 1)).toInt()
        if (offset <= PAGE_SIZE - 4) {
            page.data[offset] = (value and 0xFF).toByte()
            page.data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            page.data[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            page.data[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        } else {
            // Page boundary crossing (slow path)
            write8(vaddr, (value and 0xFF).toByte())
            write8(vaddr + 1, ((value ushr 8) and 0xFF).toByte())
            write8(vaddr + 2, ((value ushr 16) and 0xFF).toByte())
            write8(vaddr + 3, ((value ushr 24) and 0xFF).toByte())
        }
    }

    fun read32(vaddr: Long): Int {
        if (isNativeLoaded) {
            return nativeRead32(vaddr)
        }
        val page = getPage(vaddr)
        if (!page.isReadable) throw MemoryFaultException(vaddr, "Memory is not readable")
        val offset = (vaddr and (PAGE_SIZE - 1)).toInt()
        if (offset <= PAGE_SIZE - 4) {
            val b0 = page.data[offset].toInt() and 0xFF
            val b1 = page.data[offset + 1].toInt() and 0xFF
            val b2 = page.data[offset + 2].toInt() and 0xFF
            val b3 = page.data[offset + 3].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        } else {
            val b0 = read8(vaddr).toInt() and 0xFF
            val b1 = read8(vaddr + 1).toInt() and 0xFF
            val b2 = read8(vaddr + 2).toInt() and 0xFF
            val b3 = read8(vaddr + 3).toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
    }

    fun write64(vaddr: Long, value: Long) {
        if (isNativeLoaded) {
            nativeWrite64(vaddr, value)
            return
        }
        write32(vaddr, (value and 0xFFFFFFFFL).toInt())
        write32(vaddr + 4, ((value ushr 32) and 0xFFFFFFFFL).toInt())
    }

    fun read64(vaddr: Long): Long {
        if (isNativeLoaded) {
            return nativeRead64(vaddr)
        }
        val low = read32(vaddr).toLong() and 0xFFFFFFFFL
        val high = read32(vaddr + 4).toLong() and 0xFFFFFFFFL
        return low or (high shl 32)
    }

    fun reset() {
        if (isNativeLoaded) {
            nativeReset()
        }
        pageTable.clear()
    }

    fun getTlbHitRatio(): Float {
        if (!isNativeLoaded) return 1.0f
        val hits = nativeGetTlbHits()
        val misses = nativeGetTlbMisses()
        val total = hits + misses
        return if (total > 0) (hits.toFloat() / total.toFloat()) * 100f else 100f
    }
    
    fun readBytes(vaddr: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = read8(vaddr + i)
        }
        return result
    }
    
    fun writeBytes(vaddr: Long, data: ByteArray) {
        for (i in data.indices) {
            write8(vaddr + i, data[i])
        }
    }

    fun getTotalAllocatedMb(): Float {
        return (pageTable.size * PAGE_SIZE) / (1024f * 1024f)
    }

    class MemoryFaultException(val address: Long, message: String) : Exception("Fault at 0x${address.toString(16).uppercase()}: $message")
}
