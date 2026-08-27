package com.example.emulator

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PFS0 (Partition File System 0) / HFS0 Parser.
 * Used for extracting and reading .nsp (Nintendo Submission Package) files.
 * NSP files are essentially PFS0 containers holding NCA files and metadata (tik, cert).
 */
class NspParser {

    data class Pfs0FileEntry(
        val name: String,
        val offset: Long,
        val size: Long
    )

    data class Pfs0Header(
        val magic: String, // "PFS0"
        val fileCount: Int,
        val stringTableSize: Int,
        val entries: List<Pfs0FileEntry>
    )

    fun parseHeader(file: File): Pfs0Header {
        RandomAccessFile(file, "r").use { raf ->
            val headerBuf = ByteArray(0x10)
            raf.seek(0)
            raf.readFully(headerBuf)

            val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
            val magicBytes = ByteArray(4)
            bb.get(magicBytes)
            val magic = String(magicBytes, Charsets.US_ASCII)

            if (magic != "PFS0" && magic != "HFS0") {
                throw IllegalArgumentException("Invalid NSP/PFS0 magic: $magic. Expected PFS0 or HFS0.")
            }

            val fileCount = bb.int
            val stringTableSize = bb.int

            val entrySize = if (magic == "HFS0") 0x40 else 0x18
            val entriesBuf = ByteArray(fileCount * entrySize)
            raf.readFully(entriesBuf)

            val strTableBuf = ByteArray(stringTableSize)
            raf.readFully(strTableBuf)

            val rawBuffer = ByteBuffer.wrap(entriesBuf).order(ByteOrder.LITTLE_ENDIAN)
            val entries = mutableListOf<Pfs0FileEntry>()

            for (i in 0 until fileCount) {
                val offset = rawBuffer.long
                val size = rawBuffer.long
                val nameOffset = rawBuffer.int
                rawBuffer.int // Reserved / padding
                if (magic == "HFS0") {
                    rawBuffer.position(rawBuffer.position() + 0x28) // Skip hash & reserved
                }
                val name = readNullTerminatedString(strTableBuf, nameOffset)
                entries.add(Pfs0FileEntry(name, offset, size))
            }

            return Pfs0Header(magic, fileCount, stringTableSize, entries)
        }
    }

    fun extractFile(file: File, header: Pfs0Header, fileName: String): ByteArray? {
        val entry = header.entries.find { it.name == fileName } ?: return null
        val dataOffset = 16 + (header.fileCount * (if (header.magic == "HFS0") 0x40 else 0x18)) + header.stringTableSize + entry.offset
        
        if (dataOffset + entry.size > file.length()) {
            throw IndexOutOfBoundsException("File $fileName exceeds NSP file size.")
        }
        
        val maxReadBytes = 64 * 1024 * 1024 // Limit single buffer extraction to 64MB for memory safety
        val readSize = entry.size.coerceAtMost(maxReadBytes.toLong()).toInt()
        val result = ByteArray(readSize)
        
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(dataOffset)
            raf.readFully(result)
        }
        return result
    }

    fun parseHeader(data: ByteArray): Pfs0Header {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read Magic "PFS0"
        val magicBytes = ByteArray(4)
        buffer.get(magicBytes)
        val magic = String(magicBytes)

        if (magic != "PFS0" && magic != "HFS0") {
            throw IllegalArgumentException("Invalid NSP/PFS0 magic: $magic. Expected PFS0.")
        }

        val fileCount = buffer.int
        val stringTableSize = buffer.int
        buffer.int // Reserved/Padding

        val rawEntries = mutableListOf<Triple<Long, Long, Int>>() // Offset, Size, StringTableOffset
        for (i in 0 until fileCount) {
            val offset = buffer.long
            val size = buffer.long
            val nameOffset = buffer.int
            buffer.int // Reserved/Padding
            rawEntries.add(Triple(offset, size, nameOffset))
        }

        val stringTableBase = buffer.position()
        val stringTableBytes = ByteArray(stringTableSize)
        buffer.get(stringTableBytes)

        val entries = rawEntries.map { (offset, size, nameOffset) ->
            val name = readNullTerminatedString(stringTableBytes, nameOffset)
            Pfs0FileEntry(name, offset, size)
        }

        return Pfs0Header(magic, fileCount, stringTableSize, entries)
    }

    private fun readNullTerminatedString(stringTable: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= stringTable.size) return ""
        var end = offset
        while (end < stringTable.size && stringTable[end] != 0.toByte()) {
            end++
        }
        return String(stringTable, offset, end - offset, Charsets.UTF_8)
    }

    fun extractFile(nspData: ByteArray, header: Pfs0Header, fileName: String): ByteArray? {
        val entry = header.entries.find { it.name == fileName } ?: return null
        val dataOffset = 16 + (header.fileCount * 24) + header.stringTableSize + entry.offset
        
        if (dataOffset + entry.size > nspData.size) {
            throw IndexOutOfBoundsException("File $fileName exceeds NSP data size.")
        }
        
        val result = ByteArray(entry.size.toInt())
        System.arraycopy(nspData, dataOffset.toInt(), result, 0, entry.size.toInt())
        return result
    }

    // ==========================================
    // NATIVE C++ PFS0 & NCA MOUNTING BINDINGS
    // ==========================================
    external fun nativeMountNsp(nspByteArray: ByteArray): Boolean
    external fun nativeGetExeFsFileCount(): Int
    external fun nativeGetExeFsFileName(index: Int): String
    external fun nativeGetExeFsFileSize(index: Int): Long
    external fun nativeUnmountNsp()

    companion object {
        init {
            try {
                System.loadLibrary("vulkan_backend")
            } catch (e: UnsatisfiedLinkError) {
                // Ignore if in unit test environment without native lib
            }
        }
    }
}
