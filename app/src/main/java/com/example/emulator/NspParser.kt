package com.example.emulator

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

    fun parseHeader(data: ByteArray): Pfs0Header {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read Magic "PFS0"
        val magicBytes = ByteArray(4)
        buffer.get(magicBytes)
        val magic = String(magicBytes)

        if (magic != "PFS0") {
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
        var end = offset
        while (end < stringTable.size && stringTable[end] != 0.toByte()) {
            end++
        }
        return String(stringTable, offset, end - offset)
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
}
