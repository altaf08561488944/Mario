#pragma once

#include <vulkan/vulkan.h>
#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <cstdint>

namespace SwtcVulkan {

enum class DiagnosticSeverity {
    Verbose,
    Info,
    Warning,
    Error,
    ValidationFatal
};

struct DiagnosticMessage {
    uint64_t timestampMs;
    DiagnosticSeverity severity;
    std::string tag;
    std::string message;
    uint32_t maxwellMethod;
};

class VulkanValidationManager {
private:
    VkDebugUtilsMessengerEXT debugMessenger = VK_NULL_HANDLE;
    VkDebugReportCallbackEXT debugReportCallback = VK_NULL_HANDLE;
    bool validationLayersEnabled = true;
    bool isDebugMessengerActive = false;

    static constexpr size_t MAX_DIAGNOSTIC_LOGS = 200;
    std::deque<DiagnosticMessage> diagnosticLogs;
    mutable std::mutex logMutex;

    // Track Maxwell GPU Translated State
    struct GpuStateSnapshot {
        uint32_t currentTopology = 0;
        uint32_t activeVertexCount = 0;
        uint64_t boundTextureAddress = 0;
        uint32_t boundSamplerId = 0;
        uint32_t drawCallCount = 0;
        uint32_t validationErrors = 0;
        uint32_t validationWarnings = 0;
    } gpuState;

    VulkanValidationManager() = default;

public:
    static VulkanValidationManager& getInstance();

    bool areValidationLayersRequested() const { return validationLayersEnabled; }
    void setValidationLayersEnabled(bool enabled) { validationLayersEnabled = enabled; }

    bool checkValidationLayerSupport();
    std::vector<const char*> getRequiredExtensions();
    void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT& createInfo);

    bool setupDebugMessenger(VkInstance instance);
    void cleanupDebugMessenger(VkInstance instance);

    void recordDiagnostic(DiagnosticSeverity severity, const std::string& tag, 
                          const std::string& message, uint32_t maxwellMethod = 0);

    // Maxwell GPU Command Execution Validation
    void validateMaxwellDraw(uint32_t topology, uint32_t count, uint32_t first);
    void validateMaxwellTextureBind(uint32_t samplerId, uint64_t address);
    void validateMaxwellMethod(uint32_t method, uint32_t argument, uint32_t subchannel);

    // Diagnostic log querying
    size_t getLogCount() const;
    std::string getLogFormatted(size_t index) const;
    void clearLogs();
    std::string dumpGpuStateSummary() const;
};

} // namespace SwtcVulkan
