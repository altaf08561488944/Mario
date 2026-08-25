#pragma once

#include <vulkan/vulkan.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdint>
#include <atomic>

namespace SwtcVulkan {

struct PipelineCacheHeader {
    uint32_t headerLength;
    uint32_t headerVersion;
    uint32_t vendorID;
    uint32_t deviceID;
    uint8_t pipelineCacheUUID[VK_UUID_SIZE];
};

struct PipelineCacheStats {
    std::atomic<uint32_t> pipelinesCreated{0};
    std::atomic<uint32_t> cacheHits{0};
    std::atomic<uint32_t> cacheMisses{0};
    size_t loadedByteSize{0};
    size_t savedByteSize{0};
    bool isLoaded{false};
    std::string cacheFilePath;
};

class VulkanPipelineCacheManager {
private:
    VkPipelineCache pipelineCache = VK_NULL_HANDLE;
    VkDevice logicalDevice = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties deviceProperties{};
    
    mutable std::mutex cacheMutex;
    PipelineCacheStats stats;

    VulkanPipelineCacheManager() = default;

public:
    static VulkanPipelineCacheManager& getInstance();

    void init(VkDevice device, VkPhysicalDevice physDevice);
    void cleanup();

    // Load serialized cache from persistent storage (.bin/.cache file)
    bool loadFromFile(const std::string& filePath);

    // Serialize and write compiled shaders to persistent storage
    bool saveToFile(const std::string& filePath);

    // Check if valid cache data matches the physical device UUID
    bool validateHeader(const std::vector<uint8_t>& data) const;

    VkPipelineCache getHandle() const { return pipelineCache; }

    void recordPipelineCreated(bool hitCache = false);
    void clearCacheFile(const std::string& filePath);

    std::string getCacheInfoSummary() const;
    size_t getCachedDataSize() const;
};

} // namespace SwtcVulkan
