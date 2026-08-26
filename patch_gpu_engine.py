import re

with open("app/src/main/cpp/gpu_engine.cpp", "r") as f:
    code = f.read()

header_add = """#include "gpu_engine.h"
#include <android/log.h>
#include "vulkan_validation.h"
"""
code = code.replace('#include "gpu_engine.h"\n#include <android/log.h>', header_add)

process_command = """void GpuEngine::processCommand(uint32_t cmd) {
    // Parse Maxwell 3D / Host1x Command Headers
    uint32_t mode = (cmd >> 28) & 0xF;
    
    if (mode == 1 || mode == 2 || mode == 3 || mode == 4) {
        uint32_t method = (cmd >> 0) & 0x1FFF;
        uint32_t subchannel = (cmd >> 13) & 0x7;
        uint32_t count = (cmd >> 16) & 0xFFF;
        
        auto& valMgr = SwtcVulkan::VulkanValidationManager::getInstance();
        auto& mmu = SwtcMmu::getGlobalMmu();
        
        if (mode == 1 || mode == 2) { 
            for (uint32_t i = 0; i < count; i++) {
                if (pb_state.remaining_words == 0) {
                    valMgr.recordDiagnostic(SwtcVulkan::DiagnosticSeverity::Error, "PushBuffer", "Unexpected End of PushBuffer during payload read.", method);
                    break;
                }
                
                uint32_t arg = mmu.read32(pb_state.current_addr);
                pb_state.current_addr += 4;
                pb_state.remaining_words--;
                
                uint32_t current_method = method + (mode == 1 ? i : 0);
                
                // Intercept and Validate Maxwell API Misuse
                valMgr.validateMaxwellMethod(current_method, arg, subchannel);
                
                if (current_method == 0x0D74) { // CLEAR_COLOR
                    valMgr.recordDiagnostic(SwtcVulkan::DiagnosticSeverity::Info, "Maxwell3D", "CLEAR_COLOR executed", current_method);
                } else if (current_method == 0x585) { // DRAW_VERTEX_ARRAY
                    // In a complete implementation, topology, count, first are tracked from registers
                    // valMgr.validateMaxwellDraw(topology, count, first);
                }
            }
        } else if (mode == 3) { // INLINE
            valMgr.validateMaxwellMethod(method, count, subchannel);
        }
    } else {
        SwtcVulkan::VulkanValidationManager::getInstance().recordDiagnostic(
            SwtcVulkan::DiagnosticSeverity::Warning, "PushBuffer", "Unknown PushBuffer Command Mode: " + std::to_string(mode));
    }
}"""

code = re.sub(r'void GpuEngine::processCommand\(uint32_t cmd\) \{[\s\S]*', process_command, code)

with open("app/src/main/cpp/gpu_engine.cpp", "w") as f:
    f.write(code)

