package com.example.emulator

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Nintendo Switch XCI (Game Card) Container Parser & HFS0 Partition Enumerator.
 *
 * XCI File Layout:
 * - 0x000 - 0x0FF: Card Header Signature (RSA-2048)
 * - 0x100 - 0x1FF: Game Card Header ("HEAD" magic at 0x100)
 * - 0x200+: Root HFS0 Partition Header (Root HFS0 magic at 0x200)
 *
 * HFS0 Partitions inside Root HFS0:
 * - update: System update NCAs
 * - normal: Normal game NCAs / Manual
 * - secure: Primary game NCAs (Program, Control, Legal, Meta)
 * - logo: Boot logo NCAs
 */
object XCIContainerParser {

    private const val XCI_MAGIC = "HEAD"
    private const val HFS0_MAGIC = "HFS0"
    private const val XCI_HEADER_OFFSET = 0x100
    private const val DEFAULT_ROOT_HFS0_OFFSET = 0x200L

    data class XciHeader(
        val magic: String,
        val isValid: Boolean,
        val masterKeyRevision: Int,
        val rootHfs0Offset: Long,
        val packageIdHex: String,
        val cardSizeFlag: Int,
        val headerFlags: Int,
        val titleIdHex: String
    )

    data class Hfs0FileEntry(
        val name: String,
        val offset: Long, // Absolute offset in the XCI file
        val size: Long,
        val hashedSize: Int,
        val hashHex: String,
        val isPartition: Boolean = false
    )

    data class Hfs0Partition(
        val magic: String,
        val headerOffset: Long,
        val fileCount: Int,
        val stringTableSize: Int,
        val dataBaseOffset: Long,
        val entries: List<Hfs0FileEntry>
    )

    data class XciContainerInfo(
        val isValidXci: Boolean,
        val xciHeader: XciHeader,
        val rootHfs0: Hfs0Partition?,
        val partitions: Map<String, Hfs0Partition>,
        val securePartition: Hfs0Partition?,
        val secureNcaEntries: List<Hfs0FileEntry>,
        val statusMessage: String
    )

    /**
     * Validates XCI signature magic ("HEAD" at offset 0x100).
     */
    fun validateSignature(data: ByteArray, offset: Int = XCI_HEADER_OFFSET): Boolean {
        if (data.size < offset + 4) return false
        return data[offset] == 'H'.code.toByte() &&
               data[offset + 1] == 'E'.code.toByte() &&
               data[offset + 2] == 'A'.code.toByte() &&
               data[offset + 3] == 'D'.code.toByte()
    }

    /**
     * Validates XCI signature directly from file.
     */
    fun validateSignature(file: File): Boolean {
        if (!file.exists() || file.length() < 0x200) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(XCI_HEADER_OFFSET.toLong())
                val magicBuffer = ByteArray(4)
                raf.readFully(magicBuffer)
                String(magicBuffer, Charsets.US_ASCII) == XCI_MAGIC
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parses XCI Header from byte array.
     */
    fun parseXciHeader(data: ByteArray): XciHeader {
        if (data.size < 0x200) {
            return XciHeader("", false, 0, DEFAULT_ROOT_HFS0_OFFSET, "", 0, 0, "")
        }

        val isValid = validateSignature(data, XCI_HEADER_OFFSET)
        val magic = if (isValid) XCI_MAGIC else ""

        // Card Size Flag at 0x10D
        val cardSizeFlag = data[0x10D].toInt() and 0xFF

        // Master Key Revision at offset 0x10E (lower 4 bits)
        val rawMasterKey = data[0x10E].toInt() and 0x0F
        val masterKeyRev = if (rawMasterKey > 0) rawMasterKey else 16

        // Header Flags at 0x10F
        val headerFlags = data[0x10F].toInt() and 0xFF

        // Package ID at 0x110 - 0x117
        val packageIdBuf = ByteBuffer.wrap(data, 0x110, 8).order(ByteOrder.LITTLE_ENDIAN)
        val packageIdLong = packageIdBuf.long
        val packageIdHex = "%016X".format(packageIdLong)

        // Title ID derived or default
        val titleIdHex = if (packageIdLong != 0L) {
            "0100" + "%012X".format(packageIdLong and 0x00FFFFFF_FFFFFFFFL)
        } else ""

        // Root HFS0 Offset determination
        var rootHfs0Offset = DEFAULT_ROOT_HFS0_OFFSET
        if (data.size >= 0x204) {
            if (data[0x200] == 'H'.code.toByte() && data[0x201] == 'F'.code.toByte() &&
                data[0x202] == 'S'.code.toByte() && data[0x203] == '0'.code.toByte()) {
                rootHfs0Offset = 0x200L
            } else {
                val ptrOffset = ByteBuffer.wrap(data, 0x200, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
                if (ptrOffset >= 0x200L && ptrOffset < data.size) {
                    rootHfs0Offset = ptrOffset
                }
            }
        }

        return XciHeader(
            magic = magic,
            isValid = isValid,
            masterKeyRevision = masterKeyRev,
            rootHfs0Offset = rootHfs0Offset,
            packageIdHex = packageIdHex,
            cardSizeFlag = cardSizeFlag,
            headerFlags = headerFlags,
            titleIdHex = titleIdHex
        )
    }

    /**
     * Parses HFS0 Header from ByteArray.
     */
    fun parseHfs0Header(data: ByteArray, hfs0Offset: Long): Hfs0Partition? {
        val intOffset = hfs0Offset.toInt()
        if (intOffset < 0 || intOffset + 0x10 > data.size) return null

        if (data[intOffset] != 'H'.code.toByte() || data[intOffset + 1] != 'F'.code.toByte() ||
            data[intOffset + 2] != 'S'.code.toByte() || data[intOffset + 3] != '0'.code.toByte()) {
            return null
        }

        val numFiles = ByteBuffer.wrap(data, intOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val stringTableSize = ByteBuffer.wrap(data, intOffset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int

        val entriesOffset = intOffset + 0x10
        val stringTableBase = entriesOffset + (numFiles * 0x40)
        val dataBaseOffset = stringTableBase + stringTableSize

        if (dataBaseOffset > data.size) return null

        val entries = mutableListOf<Hfs0FileEntry>()
        for (i in 0 until numFiles) {
            val entryOff = entriesOffset + (i * 0x40)
            if (entryOff + 0x40 > data.size) break

            val entryBuffer = ByteBuffer.wrap(data, entryOff, 0x40).order(ByteOrder.LITTLE_ENDIAN)
            val fileOffset = entryBuffer.long
            val fileSize = entryBuffer.long
            val stringTableOff = entryBuffer.int
            val hashedSize = entryBuffer.int
            entryBuffer.position(entryOff + 0x20 - intOffset) // skip reserved

            // Hash 32 bytes
            val hashBytes = ByteArray(32)
            System.arraycopy(data, entryOff + 0x20, hashBytes, 0, 32)
            val hashHex = hashBytes.joinToString("") { "%02X".format(it) }

            // Read null-terminated string from string table
            val strAbsOff = stringTableBase + stringTableOff
            val fileName = readNullTerminatedString(data, strAbsOff)

            val absoluteOffset = dataBaseOffset + fileOffset
            val isPartition = fileName == "update" || fileName == "normal" || fileName == "secure" || fileName == "logo"

            entries.add(
                Hfs0FileEntry(
                    name = fileName,
                    offset = absoluteOffset,
                    size = fileSize,
                    hashedSize = hashedSize,
                    hashHex = hashHex,
                    isPartition = isPartition
                )
            )
        }

        return Hfs0Partition(
            magic = HFS0_MAGIC,
            headerOffset = hfs0Offset,
            fileCount = numFiles,
            stringTableSize = stringTableSize,
            dataBaseOffset = dataBaseOffset.toLong(),
            entries = entries
        )
    }

    /**
     * Complete XCI container parsing pipeline for a File.
     */
    fun parseContainer(file: File): XciContainerInfo {
        if (!file.exists()) {
            return createErrorResult("File does not exist: ${file.absolutePath}")
        }
        if (file.length() < 0x400) {
            return createErrorResult("File size (${file.length()} bytes) is too small for a valid XCI container.")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBuffer = ByteArray(0x1000.coerceAtMost(file.length().toInt()))
                raf.seek(0)
                raf.readFully(headerBuffer)

                val xciHeader = parseXciHeader(headerBuffer)
                if (!xciHeader.isValid) {
                    return createErrorResult("Invalid XCI container: 'HEAD' signature missing at offset 0x100.")
                }

                val rootHfs0 = parseHfs0Header(headerBuffer, xciHeader.rootHfs0Offset)
                    ?: return createErrorResult("Failed to parse Root HFS0 partition header at offset 0x${xciHeader.rootHfs0Offset.toString(16).uppercase()}.")

                val partitions = mutableMapOf<String, Hfs0Partition>()

                // Enumerate sub-partitions in Root HFS0
                for (entry in rootHfs0.entries) {
                    if (entry.offset + 0x10 <= file.length()) {
                        raf.seek(entry.offset)
                        val subHeaderBuffer = ByteArray(0x1000.coerceAtMost((file.length() - entry.offset).toInt()))
                        raf.readFully(subHeaderBuffer)

                        val subHfs0 = parseHfs0Header(subHeaderBuffer, entry.offset)
                        if (subHfs0 != null) {
                            partitions[entry.name] = subHfs0
                        }
                    }
                }

                val securePartition = partitions["secure"]
                val secureNcaEntries = securePartition?.entries?.filter { it.name.endsWith(".nca") || !it.isPartition } ?: emptyList()

                XciContainerInfo(
                    isValidXci = true,
                    xciHeader = xciHeader,
                    rootHfs0 = rootHfs0,
                    partitions = partitions,
                    securePartition = securePartition,
                    secureNcaEntries = secureNcaEntries,
                    statusMessage = "Successfully parsed XCI container. Found ${partitions.size} partitions and ${secureNcaEntries.size} NCAs in secure partition."
                )
            }
        } catch (e: Exception) {
            createErrorResult("XCI parsing exception: ${e.message}")
        }
    }

    /**
     * Complete XCI container parsing pipeline for a ByteArray buffer.
     */
    fun parseContainer(data: ByteArray): XciContainerInfo {
        if (data.size < 0x400) {
            return createErrorResult("Data size (${data.size} bytes) is too small for a valid XCI container.")
        }

        val xciHeader = parseXciHeader(data)
        if (!xciHeader.isValid) {
            return createErrorResult("Invalid XCI container: 'HEAD' signature missing at offset 0x100.")
        }

        val rootHfs0 = parseHfs0Header(data, xciHeader.rootHfs0Offset)
            ?: return createErrorResult("Failed to parse Root HFS0 partition header at offset 0x${xciHeader.rootHfs0Offset.toString(16).uppercase()}.")

        val partitions = mutableMapOf<String, Hfs0Partition>()

        // Enumerate sub-partitions in Root HFS0
        for (entry in rootHfs0.entries) {
            if (entry.offset < data.size) {
                val subHfs0 = parseHfs0Header(data, entry.offset)
                if (subHfs0 != null) {
                    partitions[entry.name] = subHfs0
                }
            }
        }

        val securePartition = partitions["secure"]
        val secureNcaEntries = securePartition?.entries?.filter { it.name.endsWith(".nca") || !it.isPartition } ?: emptyList()

        return XciContainerInfo(
            isValidXci = true,
            xciHeader = xciHeader,
            rootHfs0 = rootHfs0,
            partitions = partitions,
            securePartition = securePartition,
            secureNcaEntries = secureNcaEntries,
            statusMessage = "Successfully parsed XCI buffer. Found ${partitions.size} partitions and ${secureNcaEntries.size} NCAs in secure partition."
        )
    }

    /**
     * Locates the 'secure' partition from a Root HFS0 partition.
     */
    fun locateSecurePartition(rootHfs0: Hfs0Partition, data: ByteArray): Hfs0Partition? {
        val secureEntry = rootHfs0.entries.firstOrNull { it.name == "secure" } ?: return null
        return parseHfs0Header(data, secureEntry.offset)
    }

    /**
     * Locates the 'secure' partition from a Root HFS0 partition using RandomAccessFile.
     */
    fun locateSecurePartition(rootHfs0: Hfs0Partition, raf: RandomAccessFile, fileLength: Long): Hfs0Partition? {
        val secureEntry = rootHfs0.entries.firstOrNull { it.name == "secure" } ?: return null
        if (secureEntry.offset >= fileLength) return null

        raf.seek(secureEntry.offset)
        val bufSize = 0x1000.coerceAtMost((fileLength - secureEntry.offset).toInt())
        val buffer = ByteArray(bufSize)
        raf.readFully(buffer)

        return parseHfs0Header(buffer, secureEntry.offset)
    }

    /**
     * Enumerates all NCA file entries in the secure partition.
     */
    fun enumerateSecureNcas(securePartition: Hfs0Partition): List<Hfs0FileEntry> {
        return securePartition.entries.filter { it.name.endsWith(".nca") || !it.isPartition }
    }

    private fun readNullTerminatedString(data: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= data.size) return ""
        var end = offset
        while (end < data.size && data[end] != 0.toByte()) {
            end++
        }
        return String(data, offset, end - offset, Charsets.US_ASCII)
    }

    private fun createErrorResult(message: String): XciContainerInfo {
        return XciContainerInfo(
            isValidXci = false,
            xciHeader = XciHeader("", false, 0, DEFAULT_ROOT_HFS0_OFFSET, "", 0, 0, ""),
            rootHfs0 = null,
            partitions = emptyMap(),
            securePartition = null,
            secureNcaEntries = emptyList(),
            statusMessage = message
        )
    }
}
