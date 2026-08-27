package com.example.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Real Guest Virtual Memory Manager (MMU) for Nintendo Switch AArch64 Architecture.
 *
 * Implements On-Demand Sparse Chunk Allocation (64KB chunks) & Dedicated VRAM Buffer (8MB)
 * to prevent OutOfMemoryError on Android host devices while supporting the full 4GB/64GB Switch Virtual Address space.
 *
 * Address Space Layout:
 * - Code / Executable Segment (.text, .rodata, .data): 0x7100000000 - 0x717FFFFFFF
 * - Main Stack Segment: 0x7F70000000 - 0x7F7FFFFFFF (Initial SP = 0x7F7FFFF000)
 * - Heap / Dynamic RAM Segment: 0x7F80000000 - 0x7FFFFFFFFF
 * - VRAM Framebuffer Segment: 0x9000000000 - 0x90007FFFFF (8MB @ 0x9000000000)
 */
class GuestMemory {

    companion object {
        const val CODE_BASE = 0x7100000000L
        const val TLS_BASE = 0x7000000000L
        const val STACK_TOP = 0x7F7FFFF000L
        const val STACK_BASE = 0x7F7F000000L
        const val HEAP_BASE = 0x7F80000000L
        const val VRAM_BASE = 0x9000000000L

        const val CODE_SIZE = 64 * 1024 * 1024  // Virtual range: 64MB Executable Code
        const val TLS_SIZE = 1 * 1024 * 1024    // Virtual range: 1MB Thread Local Storage
        const val STACK_SIZE = 16 * 1024 * 1024 // Virtual range: 16MB Stack
        const val HEAP_SIZE = 64 * 1024 * 1024  // Virtual range: 64MB Heap
        const val VRAM_SIZE = 8 * 1024 * 1024   // 8MB Dedicated Linear Framebuffer (1920x1080 ARGB)
        const val CHUNK_SIZE = 64 * 1024        // 64KB Sparse Chunks on-demand
    }

    // Sparse 64KB memory chunks mapped on-demand
    private val memoryChunks = ConcurrentHashMap<Long, ByteBuffer>()

    // Dedicated VRAM buffer for fast zero-copy Display and Compose Canvas rendering
    private val vramBuffer: ByteBuffer = ByteBuffer.allocateDirect(VRAM_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    var heapAllocatedBytes: Int = 0

    // Read 8-bit byte
    fun read8(addr: Long): Int {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = false) ?: return 0
        return if (offset in 0 until buffer.capacity()) buffer.get(offset).toInt() and 0xFF else 0
    }

    // Write 8-bit byte
    fun write8(addr: Long, value: Int) {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = true) ?: return
        if (offset in 0 until buffer.capacity()) {
            buffer.put(offset, value.toByte())
        }
    }

    // Read 16-bit short (Little Endian)
    fun read16(addr: Long): Int {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = false) ?: return 0
        return if (offset in 0..buffer.capacity() - 2) {
            buffer.getShort(offset).toInt() and 0xFFFF
        } else {
            val b0 = read8(addr)
            val b1 = read8(addr + 1)
            b0 or (b1 shl 8)
        }
    }

    // Write 16-bit short (Little Endian)
    fun write16(addr: Long, value: Int) {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = true) ?: return
        if (offset in 0..buffer.capacity() - 2) {
            buffer.putShort(offset, value.toShort())
        } else {
            write8(addr, value and 0xFF)
            write8(addr + 1, (value ushr 8) and 0xFF)
        }
    }

    // Read 32-bit int (Little Endian)
    fun read32(addr: Long): Int {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = false) ?: return 0
        return if (offset in 0..buffer.capacity() - 4) {
            buffer.getInt(offset)
        } else {
            val b0 = read8(addr)
            val b1 = read8(addr + 1)
            val b2 = read8(addr + 2)
            val b3 = read8(addr + 3)
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
    }

    // Write 32-bit int (Little Endian)
    fun write32(addr: Long, value: Int) {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = true) ?: return
        if (offset in 0..buffer.capacity() - 4) {
            buffer.putInt(offset, value)
        } else {
            write8(addr, value and 0xFF)
            write8(addr + 1, (value ushr 8) and 0xFF)
            write8(addr + 2, (value ushr 16) and 0xFF)
            write8(addr + 3, (value ushr 24) and 0xFF)
        }
    }

    // Read 64-bit long (Little Endian)
    fun read64(addr: Long): Long {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = false) ?: return 0L
        return if (offset in 0..buffer.capacity() - 8) {
            buffer.getLong(offset)
        } else {
            val low = read32(addr).toLong() and 0xFFFFFFFFL
            val high = read32(addr + 4).toLong() and 0xFFFFFFFFL
            low or (high shl 32)
        }
    }

    // Write 64-bit long (Little Endian)
    fun write64(addr: Long, value: Long) {
        val (buffer, offset) = resolveAddress(addr, createIfMissing = true) ?: return
        if (offset in 0..buffer.capacity() - 8) {
            buffer.putLong(offset, value)
        } else {
            write32(addr, (value and 0xFFFFFFFFL).toInt())
            write32(addr + 4, ((value ushr 32) and 0xFFFFFFFFL).toInt())
        }
    }

    // Load binary byte array into Virtual Memory at specified startAddress with fast block transfers
    fun loadBinary(startAddress: Long, data: ByteArray, srcOffset: Int = 0, length: Int = data.size - srcOffset) {
        var bytesRemaining = length.coerceAtMost(data.size - srcOffset)
        var currentSrcOffset = srcOffset
        var currentDstAddr = startAddress

        while (bytesRemaining > 0) {
            val offsetInChunk = (currentDstAddr and 0xFFFFL).toInt()
            val spaceInChunk = CHUNK_SIZE - offsetInChunk
            val toWrite = minOf(bytesRemaining, spaceInChunk)

            val (buffer, _) = resolveAddress(currentDstAddr, createIfMissing = true) ?: break
            val prevPos = buffer.position()
            buffer.position(offsetInChunk)
            buffer.put(data, currentSrcOffset, toWrite)
            buffer.position(prevPos)

            currentSrcOffset += toWrite
            currentDstAddr += toWrite
            bytesRemaining -= toWrite
        }
    }

    // Read byte block from Virtual Memory with fast block transfers
    fun readBytes(startAddress: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        var bytesRemaining = length
        var currentDstOffset = 0
        var currentSrcAddr = startAddress

        while (bytesRemaining > 0) {
            val offsetInChunk = (currentSrcAddr and 0xFFFFL).toInt()
            val spaceInChunk = CHUNK_SIZE - offsetInChunk
            val toRead = minOf(bytesRemaining, spaceInChunk)

            val resolved = resolveAddress(currentSrcAddr, createIfMissing = false)
            if (resolved != null) {
                val (buffer, _) = resolved
                val prevPos = buffer.position()
                buffer.position(offsetInChunk)
                buffer.get(result, currentDstOffset, toRead)
                buffer.position(prevPos)
            }

            currentDstOffset += toRead
            currentSrcAddr += toRead
            bytesRemaining -= toRead
        }
        return result
    }

    // Read null-terminated string from memory
    fun readString(startAddress: Long, maxLength: Int = 256): String {
        val sb = StringBuilder()
        for (i in 0 until maxLength) {
            val b = read8(startAddress + i)
            if (b == 0) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    // Returns raw VRAM ARGB integer array for Direct Compose Canvas rendering
    fun getVramPixels(width: Int = 1280, height: Int = 720, framebufferOffset: Int = 0): IntArray {
        val size = width * height
        val pixels = IntArray(size)
        val copyBuffer = vramBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val validOffset = framebufferOffset.coerceIn(0, (VRAM_SIZE - (size * 4)).coerceAtLeast(0))
        copyBuffer.position(validOffset)
        for (i in 0 until size) {
            if (copyBuffer.remaining() >= 4) {
                pixels[i] = copyBuffer.int
            } else {
                pixels[i] = 0xFF000000.toInt()
            }
        }
        return pixels
    }

    // Checks if the active VRAM region contains rendered pixels
    fun hasNonZeroVramData(width: Int = 1280, height: Int = 720, framebufferOffset: Int = 0): Boolean {
        val copyBuffer = vramBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val validOffset = framebufferOffset.coerceIn(0, (VRAM_SIZE - 4096).coerceAtLeast(0))
        copyBuffer.position(validOffset)
        val checkLimit = (width * height).coerceAtMost(1024)
        for (i in 0 until checkLimit) {
            if (copyBuffer.remaining() >= 4) {
                val pixel = copyBuffer.int
                if (pixel != 0 && pixel != 0xFF000000.toInt()) {
                    return true
                }
            } else break
        }
        return false
    }

    // Address translation helper with sparse 64KB on-demand chunking
    private fun resolveAddress(addr: Long, createIfMissing: Boolean): Pair<ByteBuffer, Int>? {
        if (addr in VRAM_BASE until (VRAM_BASE + VRAM_SIZE)) {
            val offset = (addr - VRAM_BASE).toInt()
            return if (offset in 0 until VRAM_SIZE) Pair(vramBuffer, offset) else null
        }

        val chunkKey = addr ushr 16
        val chunkOffset = (addr and 0xFFFFL).toInt()

        var chunk = memoryChunks[chunkKey]
        if (chunk == null && createIfMissing) {
            chunk = ByteBuffer.allocate(CHUNK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val existing = memoryChunks.putIfAbsent(chunkKey, chunk)
            if (existing != null) {
                chunk = existing
            }
        }

        return if (chunk != null) Pair(chunk, chunkOffset) else null
    }
}
