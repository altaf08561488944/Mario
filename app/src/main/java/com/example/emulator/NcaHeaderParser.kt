package com.example.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Nintendo Content Archive (NCA2 / NCA3) Header & Section Parser.
 *
 * NCA Structure Layout:
 * - Offset 0x000 - 0x1FF: RSA Signatures
 * - Offset 0x200 - 0x203: Magic ("NCA3" or "NCA2")
 * - Offset 0x204: Distribution Type
 * - Offset 0x205: Content Type (0 = Program, 1 = Meta, 2 = Control, 3 = Manual, 4 = SubSDK)
 * - Offset 0x206: Key Generation / Kaek Index
 * - Offset 0x210 - 0x217: Title ID (uint64, Little Endian)
 * - Offset 0x220 - 0x23F: Crypto Keys / Key Area
 * - Offset 0x240 - 0x2BF: 4 Section Header Entries (0x20 bytes each)
 * - Offset 0x400+: Section Data (Section 0 = ExeFS containing NSO/NRO binaries, Section 1 = RomFS)
 */
object NcaHeaderParser {

    enum class ContentType(val id: Int, val label: String) {
        PROGRAM(0, "Program Executable"),
        META(1, "Meta / CNMT"),
        CONTROL(2, "Control / Icon & Metadata"),
        MANUAL(3, "Manual"),
        SUB_SDK(4, "SubSDK"),
        UNKNOWN(-1, "Unknown")
    }

    data class NcaSectionHeader(
        val sectionIndex: Int,
        val mediaOffsetSectors: Int, // In 0x200 (512 byte) sectors
        val mediaEndOffsetSectors: Int,
        val startByteOffset: Long,
        val sizeBytes: Long,
        val fsType: Int, // 0 = PFS0 / ExeFS, 1 = RomFS
        val cryptoType: Int // 0 = None, 1 = AES-XTS, 2 = AES-CTR
    )

    data class NcaInfo(
        val isNca: Boolean,
        val magic: String,
        val contentType: ContentType,
        val titleIdHex: String,
        val sdkVersion: String,
        val distributionType: Int,
        val masterKeyRevision: Int,
        val sections: List<NcaSectionHeader>,
        val headerOffset: Int
    )

    data class ExeFsFileEntry(
        val name: String,
        val offsetInExeFs: Long,
        val sizeBytes: Long,
        val absoluteDataOffset: Long
    )

    fun parseNcaHeader(data: ByteArray, headerBaseOffset: Int = 0): NcaInfo {
        if (data.size < headerBaseOffset + 0x400) {
            return NcaInfo(false, "", ContentType.UNKNOWN, "", "", 0, 0, emptyList(), headerBaseOffset)
        }

        var workingData = data
        var workingOffset = headerBaseOffset

        val magicOffset = workingOffset + 0x200
        val m0 = workingData[magicOffset].toInt() and 0xFF
        val m1 = workingData[magicOffset + 1].toInt() and 0xFF
        val m2 = workingData[magicOffset + 2].toInt() and 0xFF
        val m3 = workingData[magicOffset + 3].toInt() and 0xFF
        var magic = "${m0.toChar()}${m1.toChar()}${m2.toChar()}${m3.toChar()}"

        var isNca = magic == "NCA3" || magic == "NCA2"

        // If not plaintext NCA, attempt AES-XTS Header Decryption with active header_key
        if (!isNca && data.size >= headerBaseOffset + 0x400) {
            try {
                val headerKey = SwitchKeysManager.getKeySet().headerKey
                if (headerKey != null && headerKey.size == 32) {
                    val sliceLen = 0xC00.coerceAtMost(data.size - headerBaseOffset)
                    val rawEncrypted = data.copyOfRange(headerBaseOffset, headerBaseOffset + sliceLen)
                    val decrypted = KeyManager.decryptNcaHeader(rawEncrypted, headerKey)
                    if (decrypted.size >= 0x400) {
                        workingData = decrypted
                        workingOffset = 0
                        val dm0 = workingData[0x200].toInt() and 0xFF
                        val dm1 = workingData[0x201].toInt() and 0xFF
                        val dm2 = workingData[0x202].toInt() and 0xFF
                        val dm3 = workingData[0x203].toInt() and 0xFF
                        magic = "${dm0.toChar()}${dm1.toChar()}${dm2.toChar()}${dm3.toChar()}"
                        isNca = magic == "NCA3" || magic == "NCA2"
                    }
                }
            } catch (e: Exception) {
                // Keep isNca = false if decryption fails
            }
        }

        if (!isNca) {
            return NcaInfo(false, magic, ContentType.UNKNOWN, "", "", 0, 0, emptyList(), headerBaseOffset)
        }

        val distType = workingData[workingOffset + 0x204].toInt() and 0xFF
        val rawContentType = workingData[workingOffset + 0x205].toInt() and 0xFF
        val contentType = ContentType.values().firstOrNull { it.id == rawContentType } ?: ContentType.UNKNOWN

        val kaekIndex = workingData[workingOffset + 0x206].toInt() and 0xFF
        val masterKeyRev = kaekIndex.coerceAtLeast(1)

        // Read Title ID at 0x210 (uint64, Little Endian)
        val titleIdBuffer = ByteBuffer.wrap(workingData, workingOffset + 0x210, 8).order(ByteOrder.LITTLE_ENDIAN)
        val titleIdLong = titleIdBuffer.long
        val titleIdHex = "0100" + "%012X".format(titleIdLong and 0x00FFFFFF_FFFFFFFFL)

        // Read SDK Version at 0x21C
        val sdkMajor = workingData[workingOffset + 0x21F].toInt() and 0xFF
        val sdkMinor = workingData[workingOffset + 0x21E].toInt() and 0xFF
        val sdkVersion = "$sdkMajor.$sdkMinor.0"

        // Parse 4 Section Headers starting at 0x240
        val sections = mutableListOf<NcaSectionHeader>()
        for (i in 0 until 4) {
            val sectionHeaderOff = workingOffset + 0x240 + (i * 0x20)
            val mediaOff = ByteBuffer.wrap(workingData, sectionHeaderOff, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val mediaEndOff = ByteBuffer.wrap(workingData, sectionHeaderOff + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            if (mediaOff > 0 && mediaEndOff > mediaOff) {
                val startByte = mediaOff.toLong() * 0x200L
                val endByte = mediaEndOff.toLong() * 0x200L
                val sizeBytes = endByte - startByte

                val fsType = workingData[sectionHeaderOff + 8].toInt() and 0xFF
                val cryptoType = workingData[sectionHeaderOff + 9].toInt() and 0xFF

                sections.add(
                    NcaSectionHeader(
                        sectionIndex = i,
                        mediaOffsetSectors = mediaOff,
                        mediaEndOffsetSectors = mediaEndOff,
                        startByteOffset = startByte,
                        sizeBytes = sizeBytes,
                        fsType = fsType,
                        cryptoType = cryptoType
                    )
                )
            }
        }

        return NcaInfo(
            isNca = true,
            magic = magic,
            contentType = contentType,
            titleIdHex = titleIdHex,
            sdkVersion = sdkVersion,
            distributionType = distType,
            masterKeyRevision = masterKeyRev,
            sections = sections,
            headerOffset = headerBaseOffset
        )
    }

    /**
     * Parses Section 0 (ExeFS PFS0 Partition) inside NCA and returns extracted file entries (`main`, `rtld`, `sdk`, `main.npdm`).
     */
    fun parseExeFsSection(data: ByteArray, sectionHeader: NcaSectionHeader): List<ExeFsFileEntry> {
        val sectionAbsStart = sectionHeader.startByteOffset.toInt()
        if (data.size < sectionAbsStart + 0x10) return emptyList()

        // PFS0 Header: Magic "PFS0" (4 bytes), NumFiles (int, 4 bytes), StringTableSize (int, 4 bytes), Reserved (4 bytes)
        val p0 = data[sectionAbsStart].toChar()
        val p1 = data[sectionAbsStart + 1].toChar()
        val p2 = data[sectionAbsStart + 2].toChar()
        val p3 = data[sectionAbsStart + 3].toChar()
        val magic = "$p0$p1$p2$p3"

        if (magic != "PFS0" && magic != "HFS0") return emptyList()

        val numFiles = ByteBuffer.wrap(data, sectionAbsStart + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val stringTableSize = ByteBuffer.wrap(data, sectionAbsStart + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int

        val entriesHeaderOff = sectionAbsStart + 0x10
        val stringTableOff = entriesHeaderOff + (numFiles * 0x18)
        val fileDataAbsBase = stringTableOff + stringTableSize

        val fileEntries = mutableListOf<ExeFsFileEntry>()

        for (i in 0 until numFiles) {
            val entryOff = entriesHeaderOff + (i * 0x18)
            if (entryOff + 0x18 > data.size) break

            val buffer = ByteBuffer.wrap(data, entryOff, 0x18).order(ByteOrder.LITTLE_ENDIAN)
            val fileDataOff = buffer.long
            val fileSize = buffer.long
            val nameStrOff = buffer.int

            // Read string from string table
            val absNameOff = stringTableOff + nameStrOff
            val fileName = readNullTerminatedString(data, absNameOff)

            val absDataOff = fileDataAbsBase + fileDataOff

            fileEntries.add(
                ExeFsFileEntry(
                    name = fileName,
                    offsetInExeFs = fileDataOff,
                    sizeBytes = fileSize,
                    absoluteDataOffset = absDataOff
                )
            )
        }

        return fileEntries
    }

    private fun readNullTerminatedString(data: ByteArray, startOffset: Int): String {
        val sb = StringBuilder()
        var curr = startOffset
        while (curr < data.size) {
            val b = data[curr].toInt() and 0xFF
            if (b == 0) break
            sb.append(b.toChar())
            curr++
        }
        return sb.toString()
    }
}
