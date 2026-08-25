#include "vulkan_validation.h"
#include <android/log.h>
#include <chrono>
#include <sstream>
#include <iomanip>
#include <cstring>

#define LOG_TAG "SwtcVulkanValidation"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace SwtcVulkan {

static const std::vector<const char*> validationLayers = {
    "VK_LAYER_KHRONOS_validation"
};

static VKAPI_ATTR VkBool32 VKAPI_CALL debugCallback(
    VkDebugUtilsMessageSeverityFlagBitsEXT messageSeverity,
    VkDebugUtilsMessageTypeFlagsEXT messageType,
    const VkDebugUtilsMessengerCallbackDataEXT* pCallbackData,
    void* pUserData) {

    DiagnosticSeverity severity = DiagnosticSeverity::Info;
    const char* typeStr = "GENERAL";

    if (messageType & VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT) {
        typeStr = "SPEC_VALIDATION";
    } else if (messageType & VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT) {
        typeStr = "PERFORMANCE";
    }

    if (messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
        severity = DiagnosticSeverity::ValidationFatal;
        LOGE("[Vulkan Validation ERROR] [%s] %s", typeStr, pCallbackData->pMessage);
    } else if (messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) {
        severity = DiagnosticSeverity::Warning;
        LOGW("[Vulkan Validation WARN] [%s] %s", typeStr, pCallbackData->pMessage);
    } else {
        severity = DiagnosticSeverity::Info;
        LOGI("[Vulkan Validation INFO] [%s] %s", typeStr, pCallbackData->pMessage);
    }

    VulkanValidationManager::getInstance().recordDiagnostic(
        severity, typeStr, pCallbackData->pMessage ? pCallbackData->pMessage : "Unknown validation message");

    return VK_FALSE;
}

VulkanValidationManager& VulkanValidationManager::getInstance() {
    static VulkanValidationManager instance;
    return instance;
}

bool VulkanValidationManager::checkValidationLayerSupport() {
    uint32_t layerCount = 0;
    vkEnumerateInstanceLayerProperties(&layerCount, nullptr);

    if (layerCount == 0) {
        LOGW("No Vulkan instance layers available on device.");
        return false;
    }

    std::vector<VkLayerProperties> availableLayers(layerCount);
    vkEnumerateInstanceLayerProperties(&layerCount, availableLayers.data());

    for (const char* layerName : validationLayers) {
        bool layerFound = false;
        for (const auto& layerProperties : availableLayers) {
            if (strcmp(layerName, layerProperties.layerName) == 0) {
                layerFound = true;
                break;
            }
        }
        if (!layerFound) {
            LOGW("Vulkan validation layer '%s' is not supported on this device.", layerName);
            return false;
        }
    }

    LOGI("Vulkan validation layers verified and supported.");
    return true;
}

std::vector<const char*> VulkanValidationManager::getRequiredExtensions() {
    std::vector<const char*> extensions = { "VK_KHR_surface", "VK_KHR_android_surface" };
    if (validationLayersEnabled) {
        extensions.push_back(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
    }
    return extensions;
}

void VulkanValidationManager::populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT& createInfo) {
    createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT;
    createInfo.messageSeverity = VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                                 VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                 VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
    createInfo.messageType = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                             VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                             VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    createInfo.pfnUserCallback = debugCallback;
    createInfo.pUserData = nullptr;
}

bool VulkanValidationManager::setupDebugMessenger(VkInstance instance) {
    if (!validationLayersEnabled || instance == VK_NULL_HANDLE) return false;

    VkDebugUtilsMessengerCreateInfoEXT createInfo;
    populateDebugMessengerCreateInfo(createInfo);

    auto func = (PFN_vkCreateDebugUtilsMessengerEXT) vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT");
    if (func != nullptr) {
        VkResult res = func(instance, &createInfo, nullptr, &debugMessenger);
        if (res == VK_SUCCESS) {
            isDebugMessengerActive = true;
            LOGI("Vulkan Debug Utils Messenger successfully registered.");
            recordDiagnostic(DiagnosticSeverity::Info, "VulkanSystem", "Vulkan validation messenger registered.");
            return true;
        } else {
            LOGW("vkCreateDebugUtilsMessengerEXT failed with result: %d", res);
        }
    } else {
        LOGI("VK_EXT_debug_utils extension not directly available, continuing with software diagnostic tracker.");
    }
    return false;
}

void VulkanValidationManager::cleanupDebugMessenger(VkInstance instance) {
    if (debugMessenger != VK_NULL_HANDLE && instance != VK_NULL_HANDLE) {
        auto func = (PFN_vkDestroyDebugUtilsMessengerEXT) vkGetInstanceProcAddr(instance, "vkDestroyDebugUtilsMessengerEXT");
        if (func != nullptr) {
            func(instance, debugMessenger, nullptr);
        }
        debugMessenger = VK_NULL_HANDLE;
    }
    isDebugMessengerActive = false;
}

void VulkanValidationManager::recordDiagnostic(DiagnosticSeverity severity, const std::string& tag, 
                                              const std::string& message, uint32_t maxwellMethod) {
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();

    std::lock_guard<std::mutex> lock(logMutex);
    if (diagnosticLogs.size() >= MAX_DIAGNOSTIC_LOGS) {
        diagnosticLogs.pop_front();
    }

    if (severity == DiagnosticSeverity::Error || severity == DiagnosticSeverity::ValidationFatal) {
        gpuState.validationErrors++;
    } else if (severity == DiagnosticSeverity::Warning) {
        gpuState.validationWarnings++;
    }

    diagnosticLogs.push_back({static_cast<uint64_t>(now), severity, tag, message, maxwellMethod});
}

void VulkanValidationManager::validateMaxwellDraw(uint32_t topology, uint32_t count, uint32_t first) {
    gpuState.drawCallCount++;
    gpuState.currentTopology = topology;
    gpuState.activeVertexCount = count;

    // Validate Maxwell primitive topology
    // Maxwell Topologies: 0=Points, 1=Lines, 2=LineStrip, 3=Triangles, 4=TriangleStrip, 5=TriangleFan, etc.
    if (topology > 15) {
        std::ostringstream ss;
        ss << "Invalid Maxwell primitive topology: " << topology << " (Expected 0..15)";
        recordDiagnostic(DiagnosticSeverity::Error, "MaxwellValidator", ss.str(), 0x585 /* DRAW_ARRAYS */);
    }

    if (count == 0) {
        recordDiagnostic(DiagnosticSeverity::Warning, "MaxwellValidator", 
                         "DrawArrays issued with 0 vertex count (potential redundant state change)", 0x585);
    }

    if (first > 0x10000000) {
        std::ostringstream ss;
        ss << "Suspicious first vertex index: " << first << " (exceeds 256M vertices limit)";
        recordDiagnostic(DiagnosticSeverity::Warning, "MaxwellValidator", ss.str(), 0x585);
    }
}

void VulkanValidationManager::validateMaxwellTextureBind(uint32_t samplerId, uint64_t address) {
    gpuState.boundSamplerId = samplerId;
    gpuState.boundTextureAddress = address;

    if (address == 0) {
        recordDiagnostic(DiagnosticSeverity::Warning, "MaxwellValidator",
                         "Texture bound to NULL address (0x0)", 0x900 /* TEXTURE_SAMPLER */);
    } else if ((address & 0x1F) != 0) { // 32-byte alignment for Maxwell surfaces
        std::ostringstream ss;
        ss << "Unaligned texture surface address 0x" << std::hex << address << " (requires 32-byte alignment)";
        recordDiagnostic(DiagnosticSeverity::Warning, "MaxwellValidator", ss.str(), 0x900);
    }
}

void VulkanValidationManager::validateMaxwellMethod(uint32_t method, uint32_t argument, uint32_t subchannel) {
    // Check known Maxwell 3D methods
    switch (method) {
        case 0x585: // DRAW_ARRAYS
            break;
        case 0x900: // BIND_TEXTURE
            break;
        case 0x8E4: // VIEWPORT_TRANSFORM
            if (argument == 0) {
                recordDiagnostic(DiagnosticSeverity::Verbose, "MaxwellValidator", "Viewport transform disabled", method);
            }
            break;
        case 0x904: // SHADER_PROGRAM
            if (argument == 0) {
                recordDiagnostic(DiagnosticSeverity::Error, "MaxwellValidator", "Shader program pointer is 0x0", method);
            }
            break;
        default:
            if (method > 0x1000) {
                std::ostringstream ss;
                ss << "Unknown or unhandled Maxwell method 0x" << std::hex << method 
                   << " (arg=0x" << argument << ", ch=" << std::dec << subchannel << ")";
                recordDiagnostic(DiagnosticSeverity::Verbose, "MaxwellValidator", ss.str(), method);
            }
            break;
    }
}

size_t VulkanValidationManager::getLogCount() const {
    std::lock_guard<std::mutex> lock(logMutex);
    return diagnosticLogs.size();
}

std::string VulkanValidationManager::getLogFormatted(size_t index) const {
    std::lock_guard<std::mutex> lock(logMutex);
    if (index >= diagnosticLogs.size()) return "";

    const auto& log = diagnosticLogs[index];
    const char* sevStr = "INFO";
    switch (log.severity) {
        case DiagnosticSeverity::Verbose: sevStr = "VERBOSE"; break;
        case DiagnosticSeverity::Info: sevStr = "INFO"; break;
        case DiagnosticSeverity::Warning: sevStr = "WARN"; break;
        case DiagnosticSeverity::Error: sevStr = "ERROR"; break;
        case DiagnosticSeverity::ValidationFatal: sevStr = "FATAL"; break;
    }

    std::ostringstream ss;
    ss << "[" << sevStr << "] [" << log.tag << "] ";
    if (log.maxwellMethod != 0) {
        ss << "(Method 0x" << std::hex << log.maxwellMethod << std::dec << ") ";
    }
    ss << log.message;
    return ss.str();
}

void VulkanValidationManager::clearLogs() {
    std::lock_guard<std::mutex> lock(logMutex);
    diagnosticLogs.clear();
    gpuState.validationErrors = 0;
    gpuState.validationWarnings = 0;
}

std::string VulkanValidationManager::dumpGpuStateSummary() const {
    std::lock_guard<std::mutex> lock(logMutex);
    std::ostringstream ss;
    ss << "=== MAXWELL GPU DIAGNOSTIC STATE ===\n";
    ss << "Validation Layers: " << (validationLayersEnabled ? "ENABLED" : "DISABLED") << "\n";
    ss << "Debug Messenger: " << (isDebugMessengerActive ? "ACTIVE" : "INACTIVE") << "\n";
    ss << "Total Draw Calls: " << gpuState.drawCallCount << "\n";
    ss << "Last Topology: " << gpuState.currentTopology << "\n";
    ss << "Last Vertex Count: " << gpuState.activeVertexCount << "\n";
    ss << "Bound Sampler ID: " << gpuState.boundSamplerId << "\n";
    ss << "Bound Texture Addr: 0x" << std::hex << gpuState.boundTextureAddress << std::dec << "\n";
    ss << "Logged Errors: " << gpuState.validationErrors << "\n";
    ss << "Logged Warnings: " << gpuState.validationWarnings << "\n";
    ss << "Captured Logs: " << diagnosticLogs.size() << "\n";
    return ss.str();
}

} // namespace SwtcVulkan
