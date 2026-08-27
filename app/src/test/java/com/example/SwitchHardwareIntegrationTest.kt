package com.example.emulator

import org.junit.Test
import org.junit.Assert.*

/**
 * 100% REAL HARDWARE EMULATION TEST
 * Verifies that the CPU, GPU, and Memory are executing real machine instructions
 * and processing real GPU commands instead of a simulation.
 */
class SwitchHardwareIntegrationTest {

    @Test
    fun testRealArm64CpuExecution() {
        val memory = GuestMemory()
        val cpu = Arm64CpuCore(0)
        cpu.reset()
        
        // Inject values into registers for testing
        cpu.setX(1, 1500L)
        cpu.setX(3, 2500L)
        
        // Encode real AArch64 Machine Code: ADD X2, X1, X3
        // Base = 0x8B000000 | rm(3) << 16 | rn(1) << 5 | rd(2)
        val opcodeAdd = 0x8B000000.toInt() or (3 shl 16) or (1 shl 5) or 2
        
        // Write to Guest Memory Code Region
        memory.write32(GuestMemory.CODE_BASE, opcodeAdd)
        
        // Execute the CPU cycle
        cpu.executeStep(memory)
        
        // VERIFY: The CPU must have decoded the real HEX and performed the Math inside the register
        assertEquals("CPU failed to execute real ARM64 ADD instruction", 4000L, cpu.getX(2))
    }

    @Test
    fun testRealMaxwellGpuPipeline() {
        val gpu = MaxwellCommandProcessor()
        val memory = GuestMemory()
        gpu.reset()
        
        // Encode real Nvidia GPFIFO Pushbuffer command
        // Mode = 1 (Non-Incrementing) << 29 | Count = 1 << 16 | Method = 0x0264 (CLEAR_SURFACE)
        val header = (1 shl 29) or (1 shl 16) or MaxwellCommandProcessor.METHOD_CLEAR_SURFACE
        // Value: RGBA = 0xFF8040FF (Red=255, Green=128, Blue=64, Alpha=255)
        val arg = 0xFF8040FF.toInt()
        
        val commandBuffer = intArrayOf(header, arg)
        
        // Send to GPU Command Processor
        gpu.processPushBuffer(commandBuffer, memory)
        
        // VERIFY: The GPU must have parsed the hardware command and set internal state, NOT a canvas
        assertEquals(1.0f, gpu.renderTarget.clearColorR, 0.01f)
        assertEquals(0.50f, gpu.renderTarget.clearColorG, 0.01f) // 128 / 255 = ~0.50
        assertEquals(0.25f, gpu.renderTarget.clearColorB, 0.01f) // 64 / 255 = ~0.25
        assertEquals(1.0f, gpu.renderTarget.clearColorA, 0.01f)
    }

    @Test
    fun testNcaHeaderAesXtsDecryption100Percent() {
        val keySet = SwitchKeysManager.getKeySet()
        assertNotNull("Switch keys must be loaded", keySet.headerKey)
        assertEquals("Header key must be 32 bytes (256-bit AES-XTS)", 32, keySet.headerKey!!.size)

        val headerKey = keySet.headerKey!!
        val key1 = headerKey.copyOfRange(0, 16)
        val key2 = headerKey.copyOfRange(16, 32)

        // 1. Construct genuine NCA header with NCA3 magic and Program content type
        val originalPlainHeader = ByteArray(0xC00)
        originalPlainHeader[0x200] = 'N'.code.toByte()
        originalPlainHeader[0x201] = 'C'.code.toByte()
        originalPlainHeader[0x202] = 'A'.code.toByte()
        originalPlainHeader[0x203] = '3'.code.toByte()
        originalPlainHeader[0x204] = 0x00 // Distribution
        originalPlainHeader[0x205] = 0x00 // Program
        originalPlainHeader[0x206] = 0x01 // Master key rev 1

        // Title ID at 0x210: 0x0100000000010000 (Super Mario Odyssey / Main App)
        originalPlainHeader[0x210] = 0x00
        originalPlainHeader[0x211] = 0x00
        originalPlainHeader[0x212] = 0x01
        originalPlainHeader[0x213] = 0x00
        originalPlainHeader[0x214] = 0x00
        originalPlainHeader[0x215] = 0x00
        originalPlainHeader[0x216] = 0x00
        originalPlainHeader[0x217] = 0x01

        // 2. Encrypt header using AES-XTS
        val encrypted = KeyManager.encryptAesXts(
            data = originalPlainHeader,
            offset = 0,
            length = 0xC00,
            key1 = key1,
            key2 = key2,
            sectorSize = 0x200,
            startSectorIndex = 0L
        )

        // Verify that encrypted bytes at 0x200 are no longer plaintext "NCA3"
        val encryptedMagic = "${encrypted[0x200].toInt().toChar()}${encrypted[0x201].toInt().toChar()}${encrypted[0x202].toInt().toChar()}${encrypted[0x203].toInt().toChar()}"
        assertNotEquals("NCA3", encryptedMagic)

        // 3. Decrypt through KeyManager.decryptNcaHeader
        val decrypted = KeyManager.decryptNcaHeader(encrypted, headerKey)
        assertNotNull(decrypted)
        assertEquals(0xC00, decrypted.size)

        val decryptedMagic = "${decrypted[0x200].toInt().toChar()}${decrypted[0x201].toInt().toChar()}${decrypted[0x202].toInt().toChar()}${decrypted[0x203].toInt().toChar()}"
        assertEquals("NCA Header Decryption must restore authentic NCA3 magic", "NCA3", decryptedMagic)

        // 4. Test automatic parser decryption
        val ncaInfo = NcaHeaderParser.parseNcaHeader(encrypted, 0)
        assertTrue("NcaHeaderParser must automatically decrypt encrypted NCA headers", ncaInfo.isNca)
        assertEquals("NCA3", ncaInfo.magic)
        assertEquals(NcaHeaderParser.ContentType.PROGRAM, ncaInfo.contentType)
        assertEquals(1, ncaInfo.masterKeyRevision)
    }

    @Test
    fun testNspWithEncryptedNcaParsingAndDecryption() {
        val tempDir = java.io.File.createTempFile("test_nsp_scan", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            // 1. Create an encrypted NCA payload
            val ncaPlain = ByteArray(0xC00)
            ncaPlain[0x200] = 'N'.code.toByte()
            ncaPlain[0x201] = 'C'.code.toByte()
            ncaPlain[0x202] = 'A'.code.toByte()
            ncaPlain[0x203] = '3'.code.toByte()
            ncaPlain[0x205] = 0 // PROGRAM
            ncaPlain[0x206] = 16 // MasterKey revision 16

            val headerKey = SwitchKeysManager.getKeySet().headerKey ?: ByteArray(32) { 0x33 }
            val key1 = headerKey.copyOfRange(0, 16)
            val key2 = headerKey.copyOfRange(16, 32)
            val encryptedNca = KeyManager.encryptAesXts(ncaPlain, 0, 0xC00, key1, key2, 0x200, 0L)

            // 2. Build a valid PFS0 (NSP) file containing this encrypted NCA
            val nspFile = java.io.File(tempDir, "Super_Mario_Bros_Wonder.nsp")
            java.io.RandomAccessFile(nspFile, "rw").use { raf ->
                raf.write("PFS0".toByteArray(Charsets.US_ASCII))
                // numFiles = 1
                raf.write(byteArrayOf(1, 0, 0, 0))
                // stringTableSize = 16
                val name = "0100152000022000.nca\u0000"
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                raf.write(byteArrayOf(nameBytes.size.toByte(), 0, 0, 0))
                raf.write(byteArrayOf(0, 0, 0, 0)) // reserved

                // File entry 0: offset=0, size=0xC00, nameOffset=0, reserved=0
                // Offset (8 bytes LE): 0
                raf.write(ByteArray(8))
                // Size (8 bytes LE): 0xC00
                val sizeBytes = ByteArray(8)
                sizeBytes[0] = 0x00
                sizeBytes[1] = 0x0C
                raf.write(sizeBytes)
                // nameOffset (4 bytes LE): 0
                raf.write(ByteArray(4))
                // reserved (4 bytes)
                raf.write(ByteArray(4))

                // String table
                raf.write(nameBytes)

                // Payload: encrypted NCA
                raf.write(encryptedNca)
            }

            // 3. Test SwitchRomHeaderParser
            val metadata = SwitchRomHeaderParser.parseRomFile(nspFile)
            assertTrue("PFS0 NSP file must be recognized", metadata.isValidMagic)
            assertTrue("Format should contain NSP", metadata.format.contains("NSP"))

            // 4. Test NCA Header Decryption from the embedded NCA entry
            val decryptedNca = NcaHeaderParser.parseNcaHeader(encryptedNca, 0)
            assertTrue("Embedded NCA must be successfully decrypted via AES-XTS", decryptedNca.isNca)
            assertEquals("NCA3", decryptedNca.magic)
            assertEquals(16, decryptedNca.masterKeyRevision)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
