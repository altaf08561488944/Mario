import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

old_loop = """    private fun startExecutionLoop(titleName: String, titleId: String) {
        executionJob = scope.launch {
            var frameCounter = 0L
            val svcLogHistory = mutableListOf<HorizonSvcLog>()

            while (engineState.value.isRunning) {
                delay(16) // ~60 FPS Frame Rate
                frameCounter++

                val activeCores = cpuCores.filter { !it.isHalted }
                var lastDisasm = "NOP"
                var activeCoreIndex = 0

                if (activeCores.isNotEmpty()) {
                    for (core in activeCores) {
                        activeCoreIndex = core.coreId
                        for (step in 0 until 12) {
                            if (core.isHalted) break
                            val svcLog = core.executeStep(memory)
                            lastDisasm = core.lastDisassembly
                            if (svcLog != null) {
                                svcLogHistory.add(0, svcLog)
                                if (svcLogHistory.size > 20) svcLogHistory.removeLast()
                            }
                        }
                    }
                }

                // Render Frame
                val renderWidth = if (_engineState.value.isDockedMode) 1920 else 1280
                val renderHeight = if (_engineState.value.isDockedMode) 1080 else 720

                // Perform MMIO register & display surface verification via VRAMController
                val hasGuestFrame = gpu.hasValidGuestFramebuffer(memory, renderWidth, renderHeight, _engineState.value.isDevSelfTest)
                val currentLifecycle = when {
                    _engineState.value.isDevSelfTest -> GameLifecycleState.PLAYABLE
                    hasGuestFrame && _engineState.value.lifecycleState == GameLifecycleState.EXECUTING -> GameLifecycleState.FIRST_FRAME
                    hasGuestFrame && _engineState.value.lifecycleState == GameLifecycleState.FIRST_FRAME -> GameLifecycleState.PLAYABLE
                    else -> _engineState.value.lifecycleState
                }

                val frameBuffer = gpu.renderFrame(memory, renderWidth, renderHeight, _engineState.value.isDevSelfTest)

                // VRAM Controller Frame Submission
                if (hasGuestFrame && !_engineState.value.isDevSelfTest) {
                    gpu.vramController.submitFrame(0)
                    gpu.vramController.writebackToGuestMemory(memory)
                }

                _engineState.value = _engineState.value.copy(
                    lifecycleState = currentLifecycle,
                    cpuCores = cpuCores.map { it.toCpuRegisterState() },
                    gpuState = gpu.toGpuTelemetryState(),
                    svcLogs = svcLogHistory.toList(),
                    lastDisassembly = lastDisasm,
                    currentCore = activeCoreIndex,
                    activeFrameBuffer = frameBuffer,
                    hasProducedFrame = hasGuestFrame || _engineState.value.isDevSelfTest,
                    framesRendered = frameCounter
                )
            }
        }
    }"""

new_loop = """    private fun startExecutionLoop(titleName: String, titleId: String) {
        // Multi-core parallel execution pipeline to eliminate CPU lag
        executionJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val svcLogHistory = java.util.concurrent.CopyOnWriteArrayList<HorizonSvcLog>()
            var lastDisasm = "NOP"
            var activeCoreIndex = 0
            
            // Spawn 4 independent CPU core threads
            val coreJobs = cpuCores.map { core ->
                kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.Default) {
                    while (engineState.value.isRunning && !core.isHalted) {
                        for (step in 0 until 50) { // Batch execution for higher performance
                            val svcLog = core.executeStep(memory)
                            if (core.coreId == 0) { 
                                lastDisasm = core.lastDisassembly 
                                activeCoreIndex = 0
                            }
                            if (svcLog != null) {
                                svcLogHistory.add(0, svcLog)
                                if (svcLogHistory.size > 20) svcLogHistory.removeAt(svcLogHistory.size - 1)
                            }
                        }
                        kotlinx.coroutines.yield() // Yield to prevent thread locking
                    }
                }
            }

            // Dedicated GPU / Presentation Thread
            kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.Main) {
                var frameCounter = 0L
                while (engineState.value.isRunning) {
                    kotlinx.coroutines.delay(16) // ~60 FPS Exact V-Sync Timing
                    frameCounter++

                    val renderWidth = if (_engineState.value.isDockedMode) 1920 else 1280
                    val renderHeight = if (_engineState.value.isDockedMode) 1080 else 720

                    val hasGuestFrame = gpu.hasValidGuestFramebuffer(memory, renderWidth, renderHeight, _engineState.value.isDevSelfTest)
                    val currentLifecycle = when {
                        _engineState.value.isDevSelfTest -> GameLifecycleState.PLAYABLE
                        hasGuestFrame && _engineState.value.lifecycleState == GameLifecycleState.EXECUTING -> GameLifecycleState.FIRST_FRAME
                        hasGuestFrame && _engineState.value.lifecycleState == GameLifecycleState.FIRST_FRAME -> GameLifecycleState.PLAYABLE
                        else -> _engineState.value.lifecycleState
                    }

                    val frameBuffer = gpu.renderFrame(memory, renderWidth, renderHeight, _engineState.value.isDevSelfTest)

                    // VRAM Controller Swap Chain Frame Submission
                    if (hasGuestFrame && !_engineState.value.isDevSelfTest) {
                        gpu.vramController.submitFrame(0)
                        gpu.vramController.writebackToGuestMemory(memory)
                    }

                    _engineState.value = _engineState.value.copy(
                        lifecycleState = currentLifecycle,
                        cpuCores = cpuCores.map { it.toCpuRegisterState() },
                        gpuState = gpu.toGpuTelemetryState(),
                        svcLogs = svcLogHistory.toList(),
                        lastDisassembly = lastDisasm,
                        currentCore = activeCoreIndex,
                        activeFrameBuffer = frameBuffer,
                        hasProducedFrame = hasGuestFrame || _engineState.value.isDevSelfTest,
                        framesRendered = frameCounter
                    )
                }
            }
        }
    }"""

if old_loop in content:
    with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
        f.write(content.replace(old_loop, new_loop))
else:
    print("Could not find the loop block in SwitchCoreEngine.kt!")
