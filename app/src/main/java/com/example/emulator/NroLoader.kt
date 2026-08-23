package com.example.emulator

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Nintendo Switch Executable & Container Loader (.nro, .nso, .nsp ExeFS, .xci, .nca).
 * Full Pipeline:
 * XCI / NSP -> Partition Discovery (HFS0 / PFS0) -> Program NCA -> Header & Section Decryption -> ExeFS Section -> NSO0/NRO0 Executable -> Segment Memory Mapping (.text/.rodata/.data) -> GuestProcess Creation
 */
object NroLoader {

    sealed class LoadResult {
        data class Success(
            val guestProcess: GuestProcess,
            val message: String,
            val format: String,
            val titleId: String,
            val executableName: String,
            val textBytes: Int,
            val rodataBytes: Int,
            val dataBytes: Int
        ) : LoadResult()

        data class Failure(
            val reason: String,
            val errorDetail: String,
            val requiredKeyMissing: Boolean = false,
            val suggestedAction: String = "Provide valid prod.keys or re-dump cartridge."
        ) : LoadResult()
    }

    data class NroHeader(
        val isNro: Boolean,
        val textOffset: Int,
        val textSize: Int,
        val rodataOffset: Int,
        val rodataSize: Int,
        val dataOffset: Int,
        val dataSize: Int,
        val bssSize: Int
    )

    data class NsoHeader(
        val isNso: Boolean,
        val textFileOff: Int,
        val textMemOff: Int,
        val textSize: Int,
        val rodataFileOff: Int,
        val rodataMemOff: Int,
        val rodataSize: Int,
        val dataFileOff: Int,
        val dataMemOff: Int,
        val dataSize: Int,
        val bssSize: Int,
        val flags: Int
    )

    fun loadExecutableIntoMemory(
        file: File,
        memory: GuestMemory,
        cpu: Arm64CpuCore
    ): LoadResult {
        // 1. File existence & size validation
        if (!file.exists()) {
            return LoadResult.Failure(
                reason = "FILE_NOT_FOUND",
                errorDetail = "Selected game file does not exist at path: ${file.absolutePath}"
            )
        }

        if (file.length() < 0x40) {
            return LoadResult.Failure(
                reason = "INVALID_FILE_SIZE",
                errorDetail = "File size (${file.length()} bytes) is below minimum Switch container header size (64 bytes)."
            )
        }

        return try {
            val fileBytes = file.readBytes()

            // 2. Direct NRO0 Executable Header (Magic "NRO0" at 0x10)
            val nroHeader = parseNroHeader(fileBytes, 0)
            if (nroHeader.isNro) {
                return loadNroSegments(fileBytes, 0, nroHeader, memory, cpu, file.name, "NRO Homebrew Executable")
            }

            // 3. Direct NSO0 Executable Header (Magic "NSO0" at 0x0)
            val nsoHeader = parseNsoHeader(fileBytes, 0)
            if (nsoHeader.isNso) {
                return loadNsoSegments(fileBytes, 0, nsoHeader, memory, cpu, file.name, "NSO Native Executable")
            }

            // 4. XCI Container Full Pipeline (XCI -> HFS0 -> NCA -> ExeFS -> NSO/NRO)
            val xciResult = parseXciPipeline(fileBytes, memory, cpu, file)
            if (xciResult != null) {
                return xciResult
            }

            // 5. NSP / PFS0 Container Pipeline (NSP -> PFS0 -> NCA -> ExeFS -> NSO/NRO)
            val nspResult = parseNspPipeline(fileBytes, memory, cpu, file)
            if (nspResult != null) {
                return nspResult
            }

            // 6. Direct Standalone NCA Pipeline (NCA -> Header Decryption -> Section Decryption -> ExeFS -> NSO/NRO)
            val ncaResult = parseNcaPipeline(fileBytes, memory, cpu, file)
            if (ncaResult != null) {
                return ncaResult
            }

            // 7. Embedded NRO/NSO Search
            val embeddedNroOffset = findMagicOffset(fileBytes, "NRO0")
            if (embeddedNroOffset >= 0x10) {
                val headerOffset = embeddedNroOffset - 0x10
                val embeddedNro = parseNroHeader(fileBytes, headerOffset)
                if (embeddedNro.isNro) {
                    return loadNroSegments(fileBytes, headerOffset, embeddedNro, memory, cpu, "${file.name} (Embedded NRO)", "Embedded NRO Container")
                }
            }

            val embeddedNsoOffset = findMagicOffset(fileBytes, "NSO0")
            if (embeddedNsoOffset >= 0) {
                val embeddedNso = parseNsoHeader(fileBytes, embeddedNsoOffset)
                if (embeddedNso.isNso) {
                    return loadNsoSegments(fileBytes, embeddedNsoOffset, embeddedNso, memory, cpu, "${file.name} (Embedded NSO)", "Embedded NSO Container")
                }
            }

            // NO SYNTHETIC FALLBACK FOR REAL GAME LOADS - STRICT FAILURE REPORTING
            val keySet = SwitchKeysManager.getKeySet()
            val hasKeys = keySet.isLoaded
            val keyCount = keySet.loadedKeyCount

            LoadResult.Failure(
                reason = "CONTENT_ACCESS_FAILED",
                errorDetail = "Container structure parsed (${file.name}), but no decryptable/unencrypted NSO or NRO executable binary was found in ExeFS partitions. (prod.keys loaded: $hasKeys, keys: $keyCount)",
                requiredKeyMissing = !hasKeys
            )

        } catch (e: Exception) {
            LoadResult.Failure(
                reason = "CONTAINER_PARSING_ERROR",
                errorDetail = "Error while parsing Switch cartridge container: ${e.message}"
            )
        }
    }

    /**
     * Dedicated Developer Mode CPU Self-Test payload generator.
     * ONLY invoked when explicitly requested by Developer CPU/ARM64 Diagnostic tests.
     */
    fun loadDevCpuSelfTestPayload(
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        testName: String = "ARM64_CPU_SELF_TEST"
    ): LoadResult.Success {
        val syntheticBytes = generateGenuineArm64ExecutablePayload(testName)
        memory.loadBinary(GuestMemory.CODE_BASE, syntheticBytes)
        cpu.reset(startPc = GuestMemory.CODE_BASE, initialSp = GuestMemory.STACK_TOP)

        val process = GuestProcess(
            titleId = "0100000000000000",
            processName = "[DEV_MODE_CPU_TEST] $testName",
            entryPoint = GuestMemory.CODE_BASE,
            isAlive = true,
            mappedSegments = listOf(".text (${syntheticBytes.size}B)"),
            stackPointer = GuestMemory.STACK_TOP,
            heapAddress = GuestMemory.HEAP_BASE,
            modules = listOf("cpu_test"),
            loadedExecutableName = "dev_cpu_test.nso"
        )

        return LoadResult.Success(
            guestProcess = process,
            message = "🧪 [DEV_MODE_CPU_TEST] Synthetic AArch64 Test Executable (${syntheticBytes.size} bytes) -> Loaded @ 0x7100000000",
            format = "DEV_MODE_TEST",
            titleId = "0100000000000000",
            executableName = "dev_cpu_test.nso",
            textBytes = syntheticBytes.size,
            rodataBytes = 0,
            dataBytes = 0
        )
    }

    private fun parseXciPipeline(
        data: ByteArray,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        file: File
    ): LoadResult.Success? {
        if (data.size < 0x200) return null
        // Check XCI Header Magic "HEAD" at offset 0x100
        if (data[0x100] != 'H'.code.toByte() || data[0x101] != 'E'.code.toByte() ||
            data[0x102] != 'A'.code.toByte() || data[0x103] != 'D'.code.toByte()) {
            return null
        }

        val rootHfs0Offset = ByteBuffer.wrap(data, 0x200, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (rootHfs0Offset !in 0x200 until data.size - 0x10) return null

        return parseHfs0ForNcaExeFs(data, rootHfs0Offset, memory, cpu, file.name, "XCI Game Cartridge")
    }

    private fun parseNspPipeline(
        data: ByteArray,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        file: File
    ): LoadResult.Success? {
        if (data.size < 0x10) return null
        if ((data[0] == 'P'.code.toByte() || data[0] == 'H'.code.toByte()) &&
            data[1] == 'F'.code.toByte() && data[2] == 'S'.code.toByte() && data[3] == '0'.code.toByte()) {
            return parseHfs0ForNcaExeFs(data, 0, memory, cpu, file.name, "NSP Package Container")
        }
        return null
    }

    private fun parseHfs0ForNcaExeFs(
        data: ByteArray,
        hfs0Offset: Int,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        containerName: String,
        formatName: String
    ): LoadResult.Success? {
        if (data.size < hfs0Offset + 0x10) return null
        val numFiles = ByteBuffer.wrap(data, hfs0Offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val stringTableSize = ByteBuffer.wrap(data, hfs0Offset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int

        var currentEntryOffset = hfs0Offset + 0x10
        val dataBaseOffset = currentEntryOffset + (numFiles * 0x40) + stringTableSize

        for (i in 0 until numFiles) {
            if (currentEntryOffset + 0x40 > data.size) break
            val entryBuffer = ByteBuffer.wrap(data, currentEntryOffset, 0x40).order(ByteOrder.LITTLE_ENDIAN)
            val fileOffset = entryBuffer.long.toInt()
            val fileSize = entryBuffer.long.toInt()

            currentEntryOffset += 0x40

            val absOffset = dataBaseOffset + fileOffset
            if (absOffset + fileSize <= data.size && fileSize > 0x400) {

                // 1. Check if this entry is a nested HFS0 partition (e.g. "secure", "normal")
                if (data[absOffset] == 'H'.code.toByte() && data[absOffset + 1] == 'F'.code.toByte() &&
                    data[absOffset + 2] == 'S'.code.toByte() && data[absOffset + 3] == '0'.code.toByte()) {
                    val nestedResult = parseHfs0ForNcaExeFs(data, absOffset, memory, cpu, containerName, formatName)
                    if (nestedResult != null) return nestedResult
                }

                // 2. Check if this entry is an NCA file (Plain or Encrypted)
                var ncaInfo = NcaHeaderParser.parseNcaHeader(data, absOffset)
                var decryptedNcaData: ByteArray? = null

                if (!ncaInfo.isNca) {
                    val keySet = SwitchKeysManager.getKeySet()
                    if (keySet.headerKey != null && absOffset + 0xC00 <= data.size) {
                        val encryptedHeader = data.copyOfRange(absOffset, absOffset + 0xC00)
                        val decryptedHeader = KeyManager.decryptNcaHeader(encryptedHeader, keySet.headerKey)
                        val decryptedInfo = NcaHeaderParser.parseNcaHeader(decryptedHeader, 0)
                        if (decryptedInfo.isNca) {
                            ncaInfo = decryptedInfo
                            decryptedNcaData = data.copyOfRange(absOffset, (absOffset + fileSize).coerceAtMost(data.size))
                            System.arraycopy(decryptedHeader, 0, decryptedNcaData, 0, 0xC00)
                        }
                    }
                }

                if (ncaInfo.isNca) {
                    val workingBuffer = decryptedNcaData ?: data
                    val workingBaseOffset = if (decryptedNcaData != null) 0 else absOffset
                    val ncaResult = processNcaContent(workingBuffer, workingBaseOffset, ncaInfo, memory, cpu, containerName, formatName)
                    if (ncaResult != null) return ncaResult
                }
            }
        }
        return null
    }

    private fun parseNcaPipeline(
        data: ByteArray,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        file: File
    ): LoadResult.Success? {
        if (data.size < 0x400) return null
        var ncaInfo = NcaHeaderParser.parseNcaHeader(data, 0)
        var workingBuffer = data

        if (!ncaInfo.isNca) {
            val keySet = SwitchKeysManager.getKeySet()
            val headerKey = keySet.headerKey
            if (headerKey != null && data.size >= 0xC00) {
                val encryptedHeader = data.copyOfRange(0, 0xC00)
                val decryptedHeader = KeyManager.decryptNcaHeader(encryptedHeader, headerKey)
                val decryptedInfo = NcaHeaderParser.parseNcaHeader(decryptedHeader, 0)
                if (decryptedInfo.isNca) {
                    ncaInfo = decryptedInfo
                    workingBuffer = data.clone()
                    System.arraycopy(decryptedHeader, 0, workingBuffer, 0, 0xC00)
                }
            }
        }

        if (!ncaInfo.isNca) return null

        return processNcaContent(workingBuffer, 0, ncaInfo, memory, cpu, file.name, "NCA Content Archive")
    }

    private fun processNcaContent(
        workingBuffer: ByteArray,
        workingBaseOffset: Int,
        ncaInfo: NcaHeaderParser.NcaInfo,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        containerName: String,
        formatName: String
    ): LoadResult.Success? {
        val keySet = SwitchKeysManager.getKeySet()

        for (section in ncaInfo.sections) {
            val sectionStart = workingBaseOffset + section.startByteOffset.toInt()
            val sectionSize = section.sizeBytes.toInt()

            if (sectionStart + sectionSize <= workingBuffer.size && sectionSize > 0) {
                var sectionBytes = workingBuffer.copyOfRange(sectionStart, sectionStart + sectionSize)

                // Decrypt encrypted NCA section using KeyManager
                if (section.cryptoType != 0) {
                    val masterKey = keySet.masterKeys[ncaInfo.masterKeyRevision]
                        ?: keySet.masterKeys[1]
                        ?: keySet.masterKeys[0]
                        ?: ByteArray(16) { 0x01 }

                    val headerKey = keySet.headerKey ?: ByteArray(32) { 0x02 }

                    when (section.cryptoType) {
                        1 -> { // AES-XTS
                            val key1 = masterKey.copyOf(16)
                            val key2 = if (headerKey.size >= 32) headerKey.copyOfRange(16, 32) else masterKey.copyOf(16)
                            sectionBytes = KeyManager.decryptAesXts(
                                data = sectionBytes,
                                offset = 0,
                                length = sectionBytes.size,
                                key1 = key1,
                                key2 = key2
                            )
                        }
                        2, 3, 4 -> { // AES-CTR
                            var ctrKey = keySet.titleKeys[ncaInfo.titleIdHex]

                            if (ctrKey == null && workingBaseOffset + 0x260 <= workingBuffer.size) {
                                val encryptedKeyArea = workingBuffer.copyOfRange(workingBaseOffset + 0x220, workingBaseOffset + 0x260)
                                val keyAreaKeySource = byteArrayOf(
                                    0x5F, 0x12, 0x4D, 0x3C, 0x61, 0x1A, 0x29, 0x1B,
                                    0x4B, 0x72, 0x18, 0x36, 0x42, 0x61, 0x55, 0x10
                                )
                                val kak = KeyManager.deriveKeyAreaKey(masterKey, keyAreaKeySource)
                                val decryptedKeyArea = KeyManager.decryptKeyArea(encryptedKeyArea, kak)
                                if (decryptedKeyArea.size >= 16) {
                                    ctrKey = decryptedKeyArea.copyOf(16)
                                }
                            }

                            if (ctrKey == null) {
                                ctrKey = masterKey
                            }

                            val ctrIv = ByteArray(16)
                            ByteBuffer.wrap(ctrIv).order(ByteOrder.BIG_ENDIAN).putInt(0, section.sectionIndex)

                            sectionBytes = KeyManager.decryptAesCtr(
                                data = sectionBytes,
                                offset = 0,
                                length = sectionBytes.size,
                                key = ctrKey,
                                ctrIv = ctrIv
                            )
                        }
                    }
                }

                val exeFsFiles = NcaHeaderParser.parseExeFsSection(sectionBytes, section.copy(startByteOffset = 0))
                for (exeFile in exeFsFiles) {
                    if (exeFile.name == "main" || exeFile.name == "main.nso" || exeFile.name == "main.nro" || exeFile.name == "rtld") {
                        val exeAbsOff = exeFile.absoluteDataOffset.toInt()
                        if (exeAbsOff + exeFile.sizeBytes <= sectionBytes.size) {
                            val nsoHeader = parseNsoHeader(sectionBytes, exeAbsOff)
                            if (nsoHeader.isNso) {
                                return loadNsoSegments(sectionBytes, exeAbsOff, nsoHeader, memory, cpu, exeFile.name, formatName)
                            }
                            val nroHeader = parseNroHeader(sectionBytes, exeAbsOff)
                            if (nroHeader.isNro) {
                                return loadNroSegments(sectionBytes, exeAbsOff, nroHeader, memory, cpu, exeFile.name, formatName)
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseNroHeader(data: ByteArray, baseOffset: Int): NroHeader {
        if (data.size < baseOffset + 0x40) return NroHeader(false, 0, 0, 0, 0, 0, 0, 0)
        val magicOffset = baseOffset + 0x10
        if (data[magicOffset] == 'N'.code.toByte() &&
            data[magicOffset + 1] == 'R'.code.toByte() &&
            data[magicOffset + 2] == 'O'.code.toByte() &&
            data[magicOffset + 3] == '0'.code.toByte()) {

            val buffer = ByteBuffer.wrap(data, baseOffset + 0x20, 0x20).order(ByteOrder.LITTLE_ENDIAN)
            val textOff = buffer.int
            val textSize = buffer.int
            val rodataOff = buffer.int
            val rodataSize = buffer.int
            val dataOff = buffer.int
            val dataSize = buffer.int
            val bssSize = buffer.int

            return NroHeader(
                isNro = true,
                textOffset = textOff,
                textSize = textSize,
                rodataOffset = rodataOff,
                rodataSize = rodataSize,
                dataOffset = dataOff,
                dataSize = dataSize,
                bssSize = bssSize
            )
        }
        return NroHeader(false, 0, 0, 0, 0, 0, 0, 0)
    }

    private fun parseNsoHeader(data: ByteArray, baseOffset: Int): NsoHeader {
        if (data.size < baseOffset + 0x60) return NsoHeader(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        if (data[baseOffset] == 'N'.code.toByte() &&
            data[baseOffset + 1] == 'S'.code.toByte() &&
            data[baseOffset + 2] == 'O'.code.toByte() &&
            data[baseOffset + 3] == '0'.code.toByte()) {

            val flags = ByteBuffer.wrap(data, baseOffset + 0x0C, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val buffer = ByteBuffer.wrap(data, baseOffset + 0x10, 0x50).order(ByteOrder.LITTLE_ENDIAN)
            val textFileOff = buffer.int
            val textMemOff = buffer.int
            val textSize = buffer.int
            val rodataFileOff = buffer.int
            val rodataMemOff = buffer.int
            val rodataSize = buffer.int
            val dataFileOff = buffer.int
            val dataMemOff = buffer.int
            val dataSize = buffer.int
            val bssSize = buffer.int

            return NsoHeader(
                isNso = true,
                textFileOff = textFileOff,
                textMemOff = textMemOff,
                textSize = textSize,
                rodataFileOff = rodataFileOff,
                rodataMemOff = rodataMemOff,
                rodataSize = rodataSize,
                dataFileOff = dataFileOff,
                dataMemOff = dataMemOff,
                dataSize = dataSize,
                bssSize = bssSize,
                flags = flags
            )
        }
        return NsoHeader(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    private fun loadNroSegments(
        data: ByteArray,
        baseOffset: Int,
        header: NroHeader,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        filename: String,
        formatName: String
    ): LoadResult.Success {
        if (header.textSize > 0 && baseOffset + header.textOffset + header.textSize <= data.size) {
            val textBytes = data.copyOfRange(baseOffset + header.textOffset, baseOffset + header.textOffset + header.textSize)
            memory.loadBinary(GuestMemory.CODE_BASE + header.textOffset, textBytes)
        }
        if (header.rodataSize > 0 && baseOffset + header.rodataOffset + header.rodataSize <= data.size) {
            val rodataBytes = data.copyOfRange(baseOffset + header.rodataOffset, baseOffset + header.rodataOffset + header.rodataSize)
            memory.loadBinary(GuestMemory.CODE_BASE + header.rodataOffset, rodataBytes)
        }
        if (header.dataSize > 0 && baseOffset + header.dataOffset + header.dataSize <= data.size) {
            val dataBytes = data.copyOfRange(baseOffset + header.dataOffset, baseOffset + header.dataOffset + header.dataSize)
            memory.loadBinary(GuestMemory.CODE_BASE + header.dataOffset, dataBytes)
        }

        val entryPoint = GuestMemory.CODE_BASE + header.textOffset
        cpu.reset(startPc = entryPoint, initialSp = GuestMemory.STACK_TOP)

        val titleId = "0100" + filename.hashCode().toUInt().toString(16).padStart(12, '0').take(12).uppercase()
        val process = GuestProcess(
            titleId = titleId,
            processName = filename,
            entryPoint = entryPoint,
            isAlive = true,
            mappedSegments = listOf(".text (${header.textSize}B)", ".rodata (${header.rodataSize}B)", ".data (${header.dataSize}B)"),
            stackPointer = GuestMemory.STACK_TOP,
            heapAddress = GuestMemory.HEAP_BASE,
            modules = listOf(filename, "rtld", "sdk"),
            loadedExecutableName = filename
        )

        return LoadResult.Success(
            guestProcess = process,
            message = "✅ Loaded Real NRO0 Executable ($filename) -> .text=${header.textSize}B, .rodata=${header.rodataSize}B, .data=${header.dataSize}B @ 0x7100000000",
            format = formatName,
            titleId = titleId,
            executableName = filename,
            textBytes = header.textSize,
            rodataBytes = header.rodataSize,
            dataBytes = header.dataSize
        )
    }

    private fun loadNsoSegments(
        data: ByteArray,
        baseOffset: Int,
        header: NsoHeader,
        memory: GuestMemory,
        cpu: Arm64CpuCore,
        filename: String,
        formatName: String
    ): LoadResult.Success {
        if (header.textSize > 0 && baseOffset + header.textFileOff + header.textSize <= data.size) {
            val textBytes = data.copyOfRange(baseOffset + header.textFileOff, baseOffset + header.textFileOff + header.textSize)
            memory.loadBinary(GuestMemory.CODE_BASE + header.textMemOff, textBytes)
        }
        if (header.rodataSize > 0 && baseOffset + header.rodataFileOff + header.rodataSize <= data.size) {
            val rodataBytes = data.copyOfRange(baseOffset + header.rodataFileOff, baseOffset + header.rodataFileOff + header.rodataSize)
            memory.loadBinary(GuestMemory.CODE_BASE + header.rodataMemOff, rodataBytes)
        }
        if (header.dataSize > 0 && baseOffset + header.dataFileOff + header.dataSize <= data.size) {
            val dataBytes = data.copyOfRange(baseOffset + header.dataFileOff, baseOffset + header.dataFileOff + header.dataSize)
            memory.loadBinary(GuestMemory.CODE_BASE + header.dataMemOff, dataBytes)
        }

        val entryPoint = GuestMemory.CODE_BASE + header.textMemOff
        cpu.reset(startPc = entryPoint, initialSp = GuestMemory.STACK_TOP)

        val titleId = "0100" + filename.hashCode().toUInt().toString(16).padStart(12, '0').take(12).uppercase()
        val process = GuestProcess(
            titleId = titleId,
            processName = filename,
            entryPoint = entryPoint,
            isAlive = true,
            mappedSegments = listOf(".text (${header.textSize}B)", ".rodata (${header.rodataSize}B)", ".data (${header.dataSize}B)"),
            stackPointer = GuestMemory.STACK_TOP,
            heapAddress = GuestMemory.HEAP_BASE,
            modules = listOf(filename, "rtld", "sdk"),
            loadedExecutableName = filename
        )

        return LoadResult.Success(
            guestProcess = process,
            message = "✅ Loaded Real NSO0 Executable ($filename) -> .text=${header.textSize}B, .rodata=${header.rodataSize}B, .data=${header.dataSize}B @ 0x7100000000",
            format = formatName,
            titleId = titleId,
            executableName = filename,
            textBytes = header.textSize,
            rodataBytes = header.rodataSize,
            dataBytes = header.dataSize
        )
    }

    private fun findMagicOffset(data: ByteArray, magic: String): Int {
        val bytes = magic.toByteArray(Charsets.US_ASCII)
        for (i in 0 until (data.size - bytes.size)) {
            var match = true
            for (j in bytes.indices) {
                if (data[i + j] != bytes[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun generateGenuineArm64ExecutablePayload(titleName: String): ByteArray {
        val opcodes = intArrayOf(
            0xA9BF7BFD.toInt(), // stp x29, x30, [sp, #-16]!
            0xD2820000.toInt(), // movz x0, #0x1000
            0xD4000021.toInt(), // svc #0x01 (svcSetHeapSize)
            0xD2800020.toInt(), // movz x0, #1
            0xD4000301.toInt(), // svc #0x18 (svcGetSystemInfo)
            0xD2800000.toInt(), // movz x0, #0
            0xD4000421.toInt(), // svc #0x21 (svcSendSyncRequest nvdrv:a)
            0xD2800020.toInt(), // movz x0, #1
            0xD2800001.toInt(), // movz x1, #0
            0xF9000020.toInt(), // str x0, [x1]
            0x91000400.toInt(), // add x0, x0, #1
            0xEB00001F.toInt(), // cmp x0, x0
            0x17FFFFFA.toInt()  // b -6 (Loop back)
        )

        val bytes = ByteArray(opcodes.size * 4)
        for (i in opcodes.indices) {
            val op = opcodes[i]
            bytes[i * 4] = (op and 0xFF).toByte()
            bytes[i * 4 + 1] = ((op ushr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((op ushr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((op ushr 24) and 0xFF).toByte()
        }
        return bytes
    }
}
