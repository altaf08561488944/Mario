#pragma once
#include <stdint.h>
#include <vector>
#include "memory_mmu.h"

// GPU Command Buffer Capture and Parsing (Maxwell 3D)
class GpuEngine {
public:
    static GpuEngine& getInstance();

    // Triggered when CPU submits a Pushbuffer (Command List)
    void executeCommandList(uint64_t gpu_addr, uint32_t num_words);

    // Write/Read GPU MMIO (Memory Mapped I/O)
    void writeGpuRegister(uint32_t offset, uint32_t value);
    uint32_t readGpuRegister(uint32_t offset);

private:
    GpuEngine() = default;
    
    struct PushBufferState {
        uint64_t current_addr;
        uint32_t remaining_words;
    } pb_state;
    
    // Process single instruction header or data from pushbuffer
    void processCommand(uint32_t cmd);
};
