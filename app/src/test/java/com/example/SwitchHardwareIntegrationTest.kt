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
}
