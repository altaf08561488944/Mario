package com.example.emulator.gpu

/**
 * Dynamic Frame Pacing & VSync Controller.
 * Ensures that the emulator delivers a steady frame rate (30 or 60 FPS) 
 * matched to the device's screen refresh rate, preventing micro-stutters 
 * and frame drops. Supports configurable VSync and Frame Skipping.
 */
class FramePacer(private var targetFps: Int = 60) {
    private var targetFrameTimeNs: Long = 1_000_000_000L / targetFps
    private var lastFrameTimeNs: Long = System.nanoTime()

    var vsyncEnabled: Boolean = true
        private set

    var frameSkipCount: Int = 0 // 0 = Off, 1 = Skip 1, 2 = Skip 2, 3 = Skip 3, -1 = Auto
        private set

    private var skippedFramesCounter: Int = 0

    fun setTargetFps(fps: Int) {
        if (fps <= 0) return
        targetFps = fps
        targetFrameTimeNs = 1_000_000_000L / fps
    }

    fun configure(enableVsync: Boolean, frameSkip: Int) {
        this.vsyncEnabled = enableVsync
        this.frameSkipCount = frameSkip
    }

    /**
     * Determines whether the current graphics frame should be skipped to maintain engine speed.
     */
    fun shouldSkipCurrentFrame(): Boolean {
        if (frameSkipCount == 0) return false

        if (frameSkipCount < 0) {
            // Auto frame skip: skip frame if current render time exceeds target frame time
            val elapsed = System.nanoTime() - lastFrameTimeNs
            return elapsed > targetFrameTimeNs * 1.2
        }

        skippedFramesCounter++
        if (skippedFramesCounter <= frameSkipCount) {
            return true
        } else {
            skippedFramesCounter = 0
            return false
        }
    }

    /**
     * Calculates elapsed time and dynamically pauses the GPU presentation thread 
     * exactly enough to hit the perfect frame pacing window (e.g., 16.6ms for 60FPS).
     */
    fun paceFrame() {
        if (!vsyncEnabled) {
            // Unlocked VSync: Yield immediately without locking frame rate
            Thread.yield()
            lastFrameTimeNs = System.nanoTime()
            return
        }

        val now = System.nanoTime()
        val elapsed = now - lastFrameTimeNs
        val sleepTimeNs = targetFrameTimeNs - elapsed

        if (sleepTimeNs > 0) {
            val sleepMs = sleepTimeNs / 1_000_000L
            val sleepNs = (sleepTimeNs % 1_000_000L).toInt()
            try {
                // Precise hybrid sleep to lock the frame time
                Thread.sleep(sleepMs, sleepNs)
            } catch (e: InterruptedException) {
                // Thread interrupted during shutdown, ignore
            }
        } else {
            // Frame drop condition: Emulator is lagging behind target FPS.
            // We don't sleep, we instantly yield to let the CPU catch up.
            Thread.yield()
        }
        
        // Reset the timer for the next frame
        lastFrameTimeNs = System.nanoTime()
    }
}

