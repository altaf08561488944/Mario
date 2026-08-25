#include "pipeline_cache.h"
#include <android/log.h>
#include <fstream>
#include <sstream>
#include <cstring>
#include <cstdio>
#include <sys/stat.h>

#define LOG_TAG "SwtcPipelineCache"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace SwtcVulkan {

VulkanPipelineCacheManager& VulkanPipelineCacheManager::getInstance() {
    static VulkanPipelineCacheManager instance;
    return instance;
}

void VulkanPipelineCacheManager::init(VkDevice device, VkPhysicalDevice physDevice) {
    std::lock_guard<std::mutex> lock(cacheMutex);
    logicalDevice = device;
    physicalDevice = physDevice;
    if (physicalDevice != VK_NULL_HANDLE) {
        vkGetPhysicalDeviceProperties(physicalDevice, &deviceProperties);
    }
}

void VulkanPipelineCacheManager::cleanup() {
    std::lock_guard<std::mutex> lock(cacheMutex);
    if (pipelineCache != VK_NULL_HANDLE && logicalDevice != VK_NULL_HANDLE) {
        vkDestroyPipelineCache(logicalDevice, pipelineCache, nullptr);
        pipelineCache = VK_NULL_HANDLE;
    }
    stats.isLoaded = false;
}

bool VulkanPipelineCacheManager::validateHeader(const std::vector<uint8_t>& data) const {
    if (data.size() < sizeof(PipelineCacheHeader)) {
        LOGW("Pipeline cache file too small (%zu bytes), ignoring.", data.size());
        return false;
    }

    const auto* header = reinterpret_cast<const PipelineCacheHeader*>(data.data());

    if (header->headerVersion != VK_PIPELINE_CACHE_HEADER_VERSION_ONE) {
        LOGW("Pipeline cache version mismatch (%u vs expected %u). Discarding cache.", 
             header->headerVersion, VK_PIPELINE_CACHE_HEADER_VERSION_ONE);
        return false;
    }

    if (header->vendorID != deviceProperties.vendorID) {
        LOGW("Pipeline cache vendor ID mismatch (0x%X vs 0x%X). Discarding cache.",
             header->vendorID, deviceProperties.vendorID);
        return false;
    }

    if (header->deviceID != deviceProperties.deviceID) {
        LOGW("Pipeline cache device ID mismatch (0x%X vs 0x%X). Discarding cache.",
             header->deviceID, deviceProperties.deviceID);
        return false;
    }

    if (memcmp(header->pipelineCacheUUID, deviceProperties.pipelineCacheUUID, VK_UUID_SIZE) != 0) {
        LOGW("Pipeline cache GPU Driver UUID mismatch. Discarding old driver cache.");
        return false;
    }

    return true;
}

bool VulkanPipelineCacheManager::loadFromFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(cacheMutex);
    stats.cacheFilePath = filePath;

    if (logicalDevice == VK_NULL_HANDLE) {
        LOGE("Cannot load pipeline cache: Logical Device not initialized.");
        return false;
    }

    // Destroy existing cache if already allocated
    if (pipelineCache != VK_NULL_HANDLE) {
        vkDestroyPipelineCache(logicalDevice, pipelineCache, nullptr);
        pipelineCache = VK_NULL_HANDLE;
    }

    std::vector<uint8_t> cacheData;
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    
    if (file.is_open()) {
        std::streamsize fileSize = file.tellg();
        if (fileSize > 0) {
            file.seekg(0, std::ios::beg);
            cacheData.resize(static_cast<size_t>(fileSize));
            if (file.read(reinterpret_cast<char*>(cacheData.data()), fileSize)) {
                LOGI("Read %zu bytes of cached shader pipelines from: %s", cacheData.size(), filePath.c_str());
            } else {
                cacheData.clear();
            }
        }
        file.close();
    }

    bool hasValidCachedData = !cacheData.empty() && validateHeader(cacheData);

    VkPipelineCacheCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO;

    if (hasValidCachedData) {
        createInfo.initialDataSize = cacheData.size();
        createInfo.pInitialData = cacheData.data();
        stats.loadedByteSize = cacheData.size();
        stats.isLoaded = true;
        LOGI("Reusing %zu bytes from persistent Vulkan pipeline cache.", cacheData.size());
    } else {
        createInfo.initialDataSize = 0;
        createInfo.pInitialData = nullptr;
        stats.loadedByteSize = 0;
        stats.isLoaded = false;
        LOGI("Creating fresh in-memory Vulkan pipeline cache (no valid disk cache found).");
    }

    VkResult res = vkCreatePipelineCache(logicalDevice, &createInfo, nullptr, &pipelineCache);
    if (res != VK_SUCCESS) {
        LOGE("vkCreatePipelineCache failed with error code: %d", res);
        pipelineCache = VK_NULL_HANDLE;
        stats.isLoaded = false;
        return false;
    }

    return true;
}

bool VulkanPipelineCacheManager::saveToFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(cacheMutex);

    if (logicalDevice == VK_NULL_HANDLE || pipelineCache == VK_NULL_HANDLE) {
        LOGW("Cannot save pipeline cache: Cache or Logical Device not active.");
        return false;
    }

    size_t dataSize = 0;
    VkResult res = vkGetPipelineCacheData(logicalDevice, pipelineCache, &dataSize, nullptr);
    if (res != VK_SUCCESS || dataSize == 0) {
        LOGW("vkGetPipelineCacheData returned size 0 or failed (res: %d). No data to serialize.", res);
        return false;
    }

    std::vector<uint8_t> cacheData(dataSize);
    res = vkGetPipelineCacheData(logicalDevice, pipelineCache, &dataSize, cacheData.data());
    if (res != VK_SUCCESS) {
        LOGE("Failed to extract pipeline cache binary data (res: %d)", res);
        return false;
    }

    // Write atomically to temporary file then replace
    std::string tempPath = filePath + ".tmp";
    std::ofstream outFile(tempPath, std::ios::binary | std::ios::trunc);
    if (!outFile.is_open()) {
        LOGE("Failed to open persistent storage path for writing: %s", tempPath.c_str());
        return false;
    }

    outFile.write(reinterpret_cast<const char*>(cacheData.data()), static_cast<std::streamsize>(cacheData.size()));
    outFile.close();

    // Rename temp file to target
    if (std::rename(tempPath.c_str(), filePath.c_str()) != 0) {
        LOGW("Rename temp cache failed, performing direct copy.");
        std::ofstream directFile(filePath, std::ios::binary | std::ios::trunc);
        if (directFile.is_open()) {
            directFile.write(reinterpret_cast<const char*>(cacheData.data()), static_cast<std::streamsize>(cacheData.size()));
            directFile.close();
        }
    }

    stats.savedByteSize = cacheData.size();
    LOGI("Successfully serialized %zu bytes of compiled Vulkan shaders to: %s", cacheData.size(), filePath.c_str());
    return true;
}

void VulkanPipelineCacheManager::recordPipelineCreated(bool hitCache) {
    stats.pipelinesCreated.fetch_add(1, std::memory_order_relaxed);
    if (hitCache) {
        stats.cacheHits.fetch_add(1, std::memory_order_relaxed);
    } else {
        stats.cacheMisses.fetch_add(1, std::memory_order_relaxed);
    }
}

void VulkanPipelineCacheManager::clearCacheFile(const std::string& filePath) {
    std::remove(filePath.c_str());
    std::string tempPath = filePath + ".tmp";
    std::remove(tempPath.c_str());
    LOGI("Persistent shader pipeline cache file deleted: %s", filePath.c_str());
}

size_t VulkanPipelineCacheManager::getCachedDataSize() const {
    std::lock_guard<std::mutex> lock(cacheMutex);
    if (logicalDevice == VK_NULL_HANDLE || pipelineCache == VK_NULL_HANDLE) return 0;
    size_t size = 0;
    vkGetPipelineCacheData(logicalDevice, pipelineCache, &size, nullptr);
    return size;
}

std::string VulkanPipelineCacheManager::getCacheInfoSummary() const {
    std::ostringstream ss;
    ss << "=== VULKAN PIPELINE SHADER CACHE ===\n";
    ss << "Cache Active: " << (pipelineCache != VK_NULL_HANDLE ? "YES" : "NO") << "\n";
    ss << "Initial Data Loaded: " << (stats.isLoaded ? "YES" : "NO") << " (" << (stats.loadedByteSize / 1024) << " KB)\n";
    ss << "Pipelines Compiled: " << stats.pipelinesCreated.load() << "\n";
    ss << "Cache Hits: " << stats.cacheHits.load() << " | Misses: " << stats.cacheMisses.load() << "\n";
    ss << "Current In-Memory Size: " << (getCachedDataSize() / 1024) << " KB\n";
    if (!stats.cacheFilePath.empty()) {
        ss << "Storage Path: " << stats.cacheFilePath << "\n";
    }
    return ss.str();
}

} // namespace SwtcVulkan
