package com.example.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real Guest Virtual Memory Manager (MMU) for Nintendo Switch AArch64 Architecture.
 *
 * Address Space Layout:
 * - Code / Executable Segment (.text, .rodata, .data): 0x7100000000 - 0x717FFFFFFF (2GB)
 * - Main Stack Segment: 0x7F70000000 - 0x7F7FFFFFFF (16MB, Initial SP = 0x7F7FFFF000)
 * - Heap / Dynamic RAM Segment: 0x7F80000000 - 0x7FFFFFFFFF (2GB)
 * - VRAM Framebuffer Segment: 0x9000000000 - 0x9003FFFFFF (64MB, 1280x720 ARGB Buffer @ 0x9000000000)
 */
class GuestMemory {

    companion object {
        const val CODE_BASE = 0x7100000000L
        const val TLS_BASE = 0x7000000000L
        const val STACK_TOP = 0x7F7FFFF000L
        const val STACK_BASE = 0x7F7F000000L
        const val HEAP_BASE = 0x7F80000000L
        const val VRAM_BASE = 0x9000000000L

        const val CODE_SIZE = 64 * 1024 * 1024  // 64MB Executable Code Buffer
        const val TLS_SIZE = 1 * 1024 * 1024    // 1MB Thread Local Storage Buffer
        const val STACK_SIZE = 16 * 1024 * 1024 // 16MB Stack Buffer
        const val HEAP_SIZE = 64 * 1024 * 1024  // 64MB Heap Buffer
        const val VRAM_SIZE = 16 * 1024 * 1024  // 16MB Framebuffer (Supports up to 1920x1080 ARGB)
    }

    private val codeBuffer = ByteBuffer.allocateDirect(CODE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private val tlsBuffer = ByteBuffer.allocateDirect(TLS_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private val stackBuffer = ByteBuffer.allocateDirect(STACK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private val heapBuffer = ByteBuffer.allocateDirect(HEAP_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private val vramBuffer = ByteBuffer.allocateDirect(VRAM_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    var heapAllocatedBytes: Int = 0

    // Read 8-bit byte
    fun read8(addr: Long): Int {
        val (buffer, offset) = resolveAddress(addr) ?: return 0
        return if (offset in 0 until buffer.capacity()) buffer.get(offset).toInt() and 0xFF else 0
    }

    // Write 8-bit byte
    fun write8(addr: Long, value: Int) {
        val (buffer, offset) = resolveAddress(addr) ?: return
        if (offset in 0 until buffer.capacity()) {
            buffer.put(offset, value.toByte())
        }
    }

    // Read 16-bit short (Little Endian)
    fun read16(addr: Long): Int {
        val (buffer, offset) = resolveAddress(addr) ?: return 0
        return if (offset in 0 until buffer.capacity() - 1) buffer.getShort(offset).toInt() and 0xFFFF else 0
    }

    // Write 16-bit short (Little Endian)
    fun write16(addr: Long, value: Int) {
        val (buffer, offset) = resolveAddress(addr) ?: return
        if (offset in 0 until buffer.capacity() - 1) {
            buffer.putShort(offset, value.toShort())
        }
    }

    // Read 32-bit int (Little Endian)
    fun read32(addr: Long): Int {
        val (buffer, offset) = resolveAddress(addr) ?: return 0
        return if (offset in 0 until buffer.capacity() - 3) buffer.getInt(offset) else 0
    }

    // Write 32-bit int (Little Endian)
    fun write32(addr: Long, value: Int) {
        val (buffer, offset) = resolveAddress(addr) ?: return
        if (offset in 0 until buffer.capacity() - 3) {
            buffer.putInt(offset, value)
        }
    }

    // Read 64-bit long (Little Endian)
    fun read64(addr: Long): Long {
        val (buffer, offset) = resolveAddress(addr) ?: return 0L
        return if (offset in 0 until buffer.capacity() - 7) buffer.getLong(offset) else 0L
    }

    // Write 64-bit long (Little Endian)
    fun write64(addr: Long, value: Long) {
        val (buffer, offset) = resolveAddress(addr) ?: return
        if (offset in 0 until buffer.capacity() - 7) {
            buffer.putLong(offset, value)
        }
    }

    // Load binary byte array into Virtual Memory at specified startAddress
    fun loadBinary(startAddress: Long, data: ByteArray) {
        for (i in data.indices) {
            write8(startAddress + i, data[i].toInt() and 0xFF)
        }
    }

    // Read byte block from Virtual Memory
    fun readBytes(startAddress: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = read8(startAddress + i).toByte()
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

    // Address translation helper
    private fun resolveAddress(addr: Long): Pair<ByteBuffer, Int>? {
        return when {
            addr in CODE_BASE until (CODE_BASE + CODE_SIZE) -> Pair(codeBuffer, (addr - CODE_BASE).toInt())
            addr in TLS_BASE until (TLS_BASE + TLS_SIZE) -> Pair(tlsBuffer, (addr - TLS_BASE).toInt())
            addr in STACK_BASE until (STACK_BASE + STACK_SIZE) -> Pair(stackBuffer, (addr - STACK_BASE).toInt())
            addr in HEAP_BASE until (HEAP_BASE + HEAP_SIZE) -> Pair(heapBuffer, (addr - HEAP_BASE).toInt())
            addr in VRAM_BASE until (VRAM_BASE + VRAM_SIZE) -> Pair(vramBuffer, (addr - VRAM_BASE).toInt())
            else -> null
        }
    }
}
