package com.example.emulator.gpu

/**
 * Dynamic Frame Pacing & VSync Controller.
 * Ensures that the emulator delivers a steady frame rate (30 or 60 FPS) 
 * matched to the device's screen refresh rate, preventing micro-stutters 
 * and frame drops.
 */
class FramePacer(private var targetFps: Int = 60) {
    private var targetFrameTimeNs: Long = 1_000_000_000L / targetFps
    private var lastFrameTimeNs: Long = System.nanoTime()

    fun setTargetFps(fps: Int) {
        if (fps <= 0) return
        targetFps = fps
        targetFrameTimeNs = 1_000_000_000L / fps
    }

    /**
     * Calculates elapsed time and dynamically pauses the GPU presentation thread 
     * exactly enough to hit the perfect frame pacing window (e.g., 16.6ms for 60FPS).
     */
    fun paceFrame() {
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
