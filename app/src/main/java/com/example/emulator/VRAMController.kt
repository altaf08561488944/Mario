package com.example.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real VRAM & Display Memory-Mapped Register Controller.
 *
 * Manages memory-mapped I/O (MMIO) registers and hardware display buffer structures
 * for the Nintendo Switch (Tegra X1 / Horizon Display subsystem), replacing crude
 * memory scans with structured register state, layer configuration, and real framebuffer metadata.
 *
 * Memory Map:
 * - VRAM Base: 0x9000000000L
 * - Display MMIO Registers: 0x9000000000L - 0x9000001000L (4KB control register block)
 * - Layer 0 (Primary Color Framebuffer): 0x9000100000L - 0x9000700000L
 * - Layer 1 (Overlay / Secondary Framebuffer): 0x9000700000L - 0x9000D00000L
 * - Maxwell Depth/Stencil Buffer: 0x9000D00000L - 0x9001000000L
 */
class VRAMController {

    companion object {
        const val MMIO_BASE = GuestMemory.VRAM_BASE
        const val MMIO_SIZE = 0x1000 // 4KB MMIO Register space

        // Primary Framebuffer base address in VRAM
        const val PRIMARY_FRAMEBUFFER_BASE = GuestMemory.VRAM_BASE + 0x100000L
        const val SECONDARY_FRAMEBUFFER_BASE = GuestMemory.VRAM_BASE + 0x700000L
        const val DEPTH_BUFFER_BASE = GuestMemory.VRAM_BASE + 0xD00000L

        // MMIO Register Offsets
        const val REG_DISPLAY_STATUS = 0x0000
        const val REG_DISPLAY_CONTROL = 0x0004
        const val REG_FRAME_COUNT = 0x0008
        const val REG_ACTIVE_LAYER = 0x000C
        const val REG_LAYER0_FB_BASE_LO = 0x0010
        const val REG_LAYER0_FB_BASE_HI = 0x0014
        const val REG_LAYER0_WIDTH = 0x0018
        const val REG_LAYER0_HEIGHT = 0x001C
        const val REG_LAYER0_STRIDE = 0x0020
        const val REG_LAYER0_FORMAT = 0x0024
        const val REG_LAYER0_FLAGS = 0x0028
        const val REG_LAYER1_FB_BASE_LO = 0x0030
        const val REG_LAYER1_FB_BASE_HI = 0x0034
        const val REG_LAYER1_WIDTH = 0x0038
        const val REG_LAYER1_HEIGHT = 0x003C
        const val REG_LAYER1_FORMAT = 0x0040
        const val REG_VSYNC_TIMESTAMP = 0x0048
        const val REG_FLIP_INTERVAL = 0x0050
        const val REG_FENCE_VALUE = 0x0054
        const val REG_DIRTY_RECT_X = 0x0060
        const val REG_DIRTY_RECT_Y = 0x0064
        const val REG_DIRTY_RECT_W = 0x0068
        const val REG_DIRTY_RECT_H = 0x006C

        // Display Status Flags
        const val STATUS_DISPLAY_ENABLED = 0x01
        const val STATUS_BUFFER_BOUND = 0x02
        const val STATUS_FRAME_SUBMITTED = 0x04
        const val STATUS_VSYNC_LATCHED = 0x08
        const val STATUS_DIRTY = 0x10

        // Surface Formats
        const val FORMAT_RGBA8 = 0x01
        const val FORMAT_BGRA8 = 0x02
        const val FORMAT_RGB565 = 0x03
        const val FORMAT_RGBA16F = 0x04
    }

    enum class FramebufferState {
        UNINITIALIZED,
        CONFIGURED,
        ACTIVE,
        DISPLAYING
    }

    data class DisplayLayer(
        var layerId: Int = 0,
        var isEnabled: Boolean = false,
        var framebufferAddress: Long = PRIMARY_FRAMEBUFFER_BASE,
        var width: Int = 1280,
        var height: Int = 720,
        var strideBytes: Int = 1280 * 4,
        var format: Int = FORMAT_RGBA8,
        var isTiled: Boolean = false,
        var submittedFrames: Long = 0L,
        var lastPresentTimestamp: Long = 0L,
        var state: FramebufferState = FramebufferState.UNINITIALIZED
    ) {
        val totalSizeBytes: Int get() = strideBytes * height
    }

    data class VsyncInfo(
        var vsyncCount: Long = 0L,
        var lastVsyncTimeNs: Long = System.nanoTime(),
        var flipInterval: Int = 1,
        var currentFence: Long = 0L
    )

    val layer0 = DisplayLayer(layerId = 0, framebufferAddress = PRIMARY_FRAMEBUFFER_BASE)
    val layer1 = DisplayLayer(layerId = 1, framebufferAddress = SECONDARY_FRAMEBUFFER_BASE)
    
    // Double Buffering (Swap Chain) Implementation for Tear-Free presentation
    var frontBufferAddress: Long = PRIMARY_FRAMEBUFFER_BASE
    var backBufferAddress: Long = SECONDARY_FRAMEBUFFER_BASE
    val vsyncInfo = VsyncInfo()

    var isDisplayEngineActive: Boolean = false
    var currentActiveLayerIndex: Int = 0
    var displayStatus: Int = 0
    var totalFramesPresented: Long = 0L

    // Internal 4KB MMIO Register Bank
    private val mmioRegisterBank = ByteBuffer.allocate(MMIO_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    init {
        initializeDefaultState()
    }

    /**
     * Initializes default display and layer state.
     */
    fun initializeDefaultState() {
        layer0.isEnabled = true
        layer0.width = 1280
        layer0.height = 720
        layer0.strideBytes = 1280 * 4
        layer0.format = FORMAT_RGBA8
        layer0.framebufferAddress = PRIMARY_FRAMEBUFFER_BASE
        layer0.state = FramebufferState.CONFIGURED

        layer1.isEnabled = false
        layer1.framebufferAddress = SECONDARY_FRAMEBUFFER_BASE
        layer1.state = FramebufferState.UNINITIALIZED

        isDisplayEngineActive = true
        currentActiveLayerIndex = 0
        displayStatus = STATUS_DISPLAY_ENABLED or STATUS_BUFFER_BOUND
        totalFramesPresented = 0L

        syncRegistersToMemory()
    }

    /**
     * Syncs high-level controller state into MMIO register bank.
     */
    private fun syncRegistersToMemory() {
        mmioRegisterBank.putInt(REG_DISPLAY_STATUS, displayStatus)
        mmioRegisterBank.putInt(REG_DISPLAY_CONTROL, if (isDisplayEngineActive) 1 else 0)
        mmioRegisterBank.putLong(REG_FRAME_COUNT, totalFramesPresented)
        mmioRegisterBank.putInt(REG_ACTIVE_LAYER, currentActiveLayerIndex)

        // Layer 0
        mmioRegisterBank.putInt(REG_LAYER0_FB_BASE_LO, (layer0.framebufferAddress and 0xFFFFFFFFL).toInt())
        mmioRegisterBank.putInt(REG_LAYER0_FB_BASE_HI, ((layer0.framebufferAddress ushr 32) and 0xFFFFFFFFL).toInt())
        mmioRegisterBank.putInt(REG_LAYER0_WIDTH, layer0.width)
        mmioRegisterBank.putInt(REG_LAYER0_HEIGHT, layer0.height)
        mmioRegisterBank.putInt(REG_LAYER0_STRIDE, layer0.strideBytes)
        mmioRegisterBank.putInt(REG_LAYER0_FORMAT, layer0.format)
        mmioRegisterBank.putInt(REG_LAYER0_FLAGS, if (layer0.isEnabled) 1 else 0)

        // Layer 1
        mmioRegisterBank.putInt(REG_LAYER1_FB_BASE_LO, (layer1.framebufferAddress and 0xFFFFFFFFL).toInt())
        mmioRegisterBank.putInt(REG_LAYER1_FB_BASE_HI, ((layer1.framebufferAddress ushr 32) and 0xFFFFFFFFL).toInt())
        mmioRegisterBank.putInt(REG_LAYER1_WIDTH, layer1.width)
        mmioRegisterBank.putInt(REG_LAYER1_HEIGHT, layer1.height)
        mmioRegisterBank.putInt(REG_LAYER1_FORMAT, layer1.format)
    }

    /**
     * Intercepts and handles MMIO register writes from CPU or GPU memory operations.
     */
    fun handleMmioWrite(offset: Int, value: Int) {
        if (offset !in 0 until MMIO_SIZE - 3) return
        mmioRegisterBank.putInt(offset, value)

        when (offset) {
            REG_DISPLAY_CONTROL -> {
                isDisplayEngineActive = (value and 0x01) != 0
                if (isDisplayEngineActive) {
                    displayStatus = displayStatus or STATUS_DISPLAY_ENABLED
                } else {
                    displayStatus = displayStatus and STATUS_DISPLAY_ENABLED.inv()
                }
            }
            REG_ACTIVE_LAYER -> {
                currentActiveLayerIndex = value.coerceIn(0, 1)
            }
            REG_LAYER0_WIDTH -> {
                if (value in 320..3840) {
                    layer0.width = value
                    layer0.strideBytes = value * 4
                }
            }
            REG_LAYER0_HEIGHT -> {
                if (value in 240..2160) {
                    layer0.height = value
                }
            }
            REG_LAYER0_FORMAT -> {
                layer0.format = value
            }
            REG_LAYER0_FLAGS -> {
                layer0.isEnabled = (value and 0x01) != 0
                if (layer0.isEnabled) layer0.state = FramebufferState.ACTIVE
            }
            REG_FLIP_INTERVAL -> {
                vsyncInfo.flipInterval = value.coerceAtLeast(1)
            }
            REG_FENCE_VALUE -> {
                vsyncInfo.currentFence = value.toLong() and 0xFFFFFFFFL
            }
        }
    }

    /**
     * Intercepts and reads MMIO register value.
     */
    fun handleMmioRead(offset: Int): Int {
        if (offset !in 0 until MMIO_SIZE - 3) return 0
        return mmioRegisterBank.getInt(offset)
    }

    /**
     * Binds a guest framebuffer surface from Horizon VI or Maxwell engine.
     */
    fun bindFramebufferSurface(
        layerId: Int,
        address: Long,
        width: Int,
        height: Int,
        format: Int = FORMAT_RGBA8
    ) {
        val targetLayer = if (layerId == 0) layer0 else layer1
        targetLayer.framebufferAddress = address
        targetLayer.width = width
        targetLayer.height = height
        targetLayer.strideBytes = width * 4
        targetLayer.format = format
        targetLayer.isEnabled = true
        targetLayer.state = FramebufferState.ACTIVE

        displayStatus = displayStatus or STATUS_BUFFER_BOUND
        syncRegistersToMemory()
    }

    /**
     * Signals a frame presentation from the guest engine.
     */
    fun submitFrame(layerId: Int = 0) {
        val targetLayer = if (layerId == 0) layer0 else layer1
        
        // Double Buffering: Swap front and back buffer pointers to prevent visual tearing
        val temp = frontBufferAddress
        frontBufferAddress = backBufferAddress
        backBufferAddress = temp
        
        targetLayer.framebufferAddress = frontBufferAddress
        targetLayer.submittedFrames++
        targetLayer.lastPresentTimestamp = System.currentTimeMillis()
        targetLayer.state = FramebufferState.DISPLAYING

        totalFramesPresented++
        vsyncInfo.vsyncCount++
        vsyncInfo.lastVsyncTimeNs = System.nanoTime()

        displayStatus = displayStatus or STATUS_FRAME_SUBMITTED or STATUS_DIRTY
        syncRegistersToMemory()
    }

    /**
     * Determines whether a valid, active display frame has been established by the guest system.
     * Uses formal register flags and framebuffer state instead of crude single-byte memory scans.
     */
    fun hasValidFramebufferState(isDevSelfTest: Boolean = false): Boolean {
        if (isDevSelfTest) return true

        // Check if display engine is active and primary layer is bound and actively presenting
        val isLayerActive = layer0.isEnabled && (
            layer0.state == FramebufferState.DISPLAYING ||
            layer0.state == FramebufferState.ACTIVE ||
            layer0.submittedFrames > 0
        )

        val hasStatusFlag = (displayStatus and (STATUS_DISPLAY_ENABLED or STATUS_BUFFER_BOUND)) ==
            (STATUS_DISPLAY_ENABLED or STATUS_BUFFER_BOUND)

        val isFirmwareSurfaceActive = FirmwareParser.DisplayService.isSurfaceRegistered &&
            FirmwareParser.DisplayService.submittedFrameCount > 0

        return (isLayerActive && (totalFramesPresented > 0 || hasStatusFlag)) || isFirmwareSurfaceActive
    }

    /**
     * Flushes current register state to Guest Memory MMIO region.
     */
    fun writebackToGuestMemory(memory: GuestMemory) {
        syncRegistersToMemory()
        val copy = mmioRegisterBank.duplicate()
        copy.position(0)
        for (i in 0 until MMIO_SIZE step 4) {
            val regValue = copy.getInt(i)
            memory.write32(MMIO_BASE + i, regValue)
        }
    }
}
