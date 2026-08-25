#include "horizon_ipc.h"
#include <android/log.h>
#include <cstring>
#include <sstream>
#include <iomanip>
#include <chrono>

#define LOG_TAG "SwtcHorizonIPC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace SwtcHorizon {

// ======================================================================
// SERVICE IMPLEMENTATIONS
// ======================================================================

// 1. SM: Service Manager (sm:)
SmService::SmService() : IpcService("sm:") {}

IpcResponse SmService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // Initialize
            LOGI("[sm:] Initialize client connection (PID: %llu)", (unsigned long long)request.clientPid);
            break;
        }
        case 1: { // GetService
            // Service name is 8 bytes in rawData
            std::string targetService = "unknown";
            if (request.rawData.size() >= 8) {
                char sname[9] = {0};
                std::memcpy(sname, request.rawData.data(), 8);
                targetService = std::string(sname);
                // trim nulls or trailing spaces
                size_t nullPos = targetService.find('\0');
                if (nullPos != std::string::npos) targetService.resize(nullPos);
            }
            LOGI("[sm:] GetService request for: '%s'", targetService.c_str());

            uint32_t handle = HorizonIpcManager::getInstance().createHandleForService(targetService);
            if (handle != 0) {
                resp.moveHandles.push_back(handle);
                resp.resultCode = RESULT_SUCCESS;
            } else {
                LOGW("[sm:] Service '%s' not registered, creating generic mock handle.", targetService.c_str());
                uint32_t fallbackHandle = HorizonIpcManager::getInstance().createHandleForService(targetService);
                resp.moveHandles.push_back(fallbackHandle);
                resp.resultCode = RESULT_SUCCESS;
            }
            break;
        }
        case 2: { // RegisterService
            LOGI("[sm:] RegisterService invoked");
            resp.resultCode = RESULT_SUCCESS;
            break;
        }
        default:
            LOGW("[sm:] Unhandled command ID: %u", request.commandId);
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 2. SET: System Settings (set:sys, set)
SetSysService::SetSysService() : IpcService("set:sys") {}

IpcResponse SetSysService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // GetLanguageCode (e.g. "en-US" = 0x000053552D6E65)
            uint64_t languageCode = 0x000053552D6E65ULL; // "en-US"
            resp.data.resize(sizeof(uint64_t));
            std::memcpy(resp.data.data(), &languageCode, sizeof(uint64_t));
            break;
        }
        case 3: { // GetFirmwareVersion
            // Write 0x100 bytes dummy firmware version structure (17.0.0)
            resp.data.resize(0x100, 0);
            resp.data[0] = 17; // Major
            resp.data[1] = 0;  // Minor
            resp.data[2] = 0;  // Micro
            std::string ver = "17.0.0 (SWTC NOOS HLE)";
            std::memcpy(resp.data.data() + 4, ver.c_str(), ver.size());
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 3. TIME: System Clock Services (time:u, time:s)
TimeService::TimeService() : IpcService("time:u") {}

IpcResponse TimeService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    auto nowSec = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();

    switch (request.commandId) {
        case 0: { // GetStandardUserSystemClock
            uint32_t sessionHandle = HorizonIpcManager::getInstance().createHandleForService("time:u:system");
            resp.moveHandles.push_back(sessionHandle);
            break;
        }
        case 1: { // GetStandardNetworkSystemClock
            uint32_t sessionHandle = HorizonIpcManager::getInstance().createHandleForService("time:u:network");
            resp.moveHandles.push_back(sessionHandle);
            break;
        }
        case 100: { // GetCurrentTime
            uint64_t timeVal = static_cast<uint64_t>(nowSec);
            resp.data.resize(sizeof(uint64_t));
            std::memcpy(resp.data.data(), &timeVal, sizeof(uint64_t));
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 4. HID: Human Interface Devices (hid, hid:sys)
HidService::HidService() : IpcService("hid") {}

IpcResponse HidService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // CreateAppletResource
            uint32_t appletResourceHandle = HorizonIpcManager::getInstance().createHandleForService("hid:AppletResource");
            resp.moveHandles.push_back(appletResourceHandle);
            LOGI("[hid] Created AppletResource handle: 0x%X", appletResourceHandle);
            break;
        }
        case 1: { // ActivateDebugPad
            break;
        }
        case 100: { // SetSupportedNpadStyleSet
            LOGI("[hid] SetSupportedNpadStyleSet configured");
            break;
        }
        case 101: { // ActivateNpad
            LOGI("[hid] Npad activated for player index");
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 5. VI: Visual Interface Display Subsystem (vi:m, vi:u)
ViService::ViService() : IpcService("vi:m") {}

IpcResponse ViService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // GetDisplayService
            uint32_t displayServiceHandle = HorizonIpcManager::getInstance().createHandleForService("vi:u");
            resp.moveHandles.push_back(displayServiceHandle);
            LOGI("[vi:m] GetDisplayService returned handle: 0x%X", displayServiceHandle);
            break;
        }
        case 1010: { // OpenDisplay (Default)
            uint64_t displayId = 0x01; // Display 1 (Default)
            resp.data.resize(sizeof(uint64_t));
            std::memcpy(resp.data.data(), &displayId, sizeof(uint64_t));
            LOGI("[vi:u] OpenDisplay -> Display ID 1");
            break;
        }
        case 2020: { // OpenLayer
            uint64_t layerId = 0x100;
            resp.data.resize(sizeof(uint64_t));
            std::memcpy(resp.data.data(), &layerId, sizeof(uint64_t));
            LOGI("[vi:u] OpenLayer -> Layer ID 0x100");
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 6. NVDRV: NVIDIA Tegra Driver (nvdrv, nvdrv:a)
NvdrvService::NvdrvService() : IpcService("nvdrv:a") {}

IpcResponse NvdrvService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // Open device node (e.g. /dev/nvhost-ctrl, /dev/nvhost-as-gpu, /dev/nvmap)
            uint32_t fd = 0x10; // Emulated file descriptor
            resp.data.resize(8, 0);
            std::memcpy(resp.data.data(), &fd, sizeof(uint32_t));
            LOGI("[nvdrv:a] Open device node -> fd 0x%X", fd);
            break;
        }
        case 1: { // Ioctl
            uint32_t errorCode = 0; // Success
            resp.data.resize(sizeof(uint32_t));
            std::memcpy(resp.data.data(), &errorCode, sizeof(uint32_t));
            break;
        }
        case 2: { // Close device node
            uint32_t errorCode = 0;
            resp.data.resize(sizeof(uint32_t));
            std::memcpy(resp.data.data(), &errorCode, sizeof(uint32_t));
            break;
        }
        case 3: { // Initialize nvdrv
            uint32_t errorCode = 0;
            resp.data.resize(sizeof(uint32_t));
            std::memcpy(resp.data.data(), &errorCode, sizeof(uint32_t));
            LOGI("[nvdrv:a] nvdrv driver subsystem initialized.");
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 7. FSP-SRV: File System Proxy (fsp-srv, fsp-pr)
FspSrvService::FspSrvService() : IpcService("fsp-srv") {}

IpcResponse FspSrvService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // Initialize
            LOGI("[fsp-srv] Initialized client file system session");
            break;
        }
        case 1: { // OpenUserSaveDataFileSystem
            uint32_t fsHandle = HorizonIpcManager::getInstance().createHandleForService("fsp-srv:save");
            resp.moveHandles.push_back(fsHandle);
            LOGI("[fsp-srv] OpenUserSaveDataFileSystem -> handle 0x%X", fsHandle);
            break;
        }
        case 18: { // OpenSdCardFileSystem
            uint32_t sdHandle = HorizonIpcManager::getInstance().createHandleForService("fsp-srv:sdcard");
            resp.moveHandles.push_back(sdHandle);
            LOGI("[fsp-srv] OpenSdCardFileSystem -> handle 0x%X", sdHandle);
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 8. APPLET: Applet OE / AE (appletOE, appletAE)
AppletOeService::AppletOeService() : IpcService("appletOE") {}

IpcResponse AppletOeService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // OpenSession
            uint32_t sessionHandle = HorizonIpcManager::getInstance().createHandleForService("appletOE:session");
            resp.moveHandles.push_back(sessionHandle);
            LOGI("[appletOE] OpenSession -> handle 0x%X", sessionHandle);
            break;
        }
        case 10: { // GetOperationMode (0: Handheld, 1: Docked)
            uint8_t opMode = 0; // Handheld mode
            resp.data.push_back(opMode);
            break;
        }
        case 11: { // GetPerformanceMode (0: Normal/Handheld, 1: Boost/Docked)
            uint32_t perfMode = 0;
            resp.data.resize(sizeof(uint32_t));
            std::memcpy(resp.data.data(), &perfMode, sizeof(uint32_t));
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// 9. AUDREN: Audio Renderer (audren:u)
AudRenService::AudRenService() : IpcService("audren:u") {}

IpcResponse AudRenService::dispatch(const IpcParsedRequest& request) {
    IpcResponse resp;
    resp.resultCode = RESULT_SUCCESS;

    switch (request.commandId) {
        case 0: { // OpenAudioRenderer
            uint32_t rendererHandle = HorizonIpcManager::getInstance().createHandleForService("audren:renderer");
            resp.moveHandles.push_back(rendererHandle);
            LOGI("[audren:u] OpenAudioRenderer -> handle 0x%X", rendererHandle);
            break;
        }
        case 1: { // GetAudioDeviceService
            uint32_t devServiceHandle = HorizonIpcManager::getInstance().createHandleForService("audren:dev");
            resp.moveHandles.push_back(devServiceHandle);
            break;
        }
        default:
            resp.resultCode = RESULT_SUCCESS;
            break;
    }
    return resp;
}

// ======================================================================
// HORIZON IPC MANAGER CORE
// ======================================================================

HorizonIpcManager& HorizonIpcManager::getInstance() {
    static HorizonIpcManager instance;
    return instance;
}

HorizonIpcManager::HorizonIpcManager() {
    initializeDefaultServices();
}

void HorizonIpcManager::initializeDefaultServices() {
    registerService("sm:", std::make_shared<SmService>());
    registerService("set:sys", std::make_shared<SetSysService>());
    registerService("set", std::make_shared<SetSysService>());
    registerService("time:u", std::make_shared<TimeService>());
    registerService("time:s", std::make_shared<TimeService>());
    registerService("hid", std::make_shared<HidService>());
    registerService("hid:sys", std::make_shared<HidService>());
    registerService("vi:m", std::make_shared<ViService>());
    registerService("vi:u", std::make_shared<ViService>());
    registerService("nvdrv:a", std::make_shared<NvdrvService>());
    registerService("nvdrv", std::make_shared<NvdrvService>());
    registerService("fsp-srv", std::make_shared<FspSrvService>());
    registerService("fsp-pr", std::make_shared<FspSrvService>());
    registerService("appletOE", std::make_shared<AppletOeService>());
    registerService("appletAE", std::make_shared<AppletOeService>());
    registerService("audren:u", std::make_shared<AudRenService>());
    registerService("audout:u", std::make_shared<AudRenService>());

    // Seed root handle 0x100 for SM:
    uint32_t smHandle = nextHandle.fetch_add(1);
    handleTable[smHandle] = registeredServices["sm:"];
    handleNames[smHandle] = "sm:";

    LOGI("Horizon IPC Manager initialized with %zu core HLE OS services.", registeredServices.size());
}

void HorizonIpcManager::registerService(const std::string& name, std::shared_ptr<IpcService> service) {
    std::lock_guard<std::mutex> lock(managerMutex);
    registeredServices[name] = service;
}

uint32_t HorizonIpcManager::createHandleForService(const std::string& name) {
    std::lock_guard<std::mutex> lock(managerMutex);
    uint32_t handle = nextHandle.fetch_add(1);

    auto it = registeredServices.find(name);
    if (it != registeredServices.end()) {
        handleTable[handle] = it->second;
    } else {
        // Create generic placeholder service with the given name
        auto genericService = std::make_shared<SmService>();
        handleTable[handle] = genericService;
    }
    handleNames[handle] = name;
    return handle;
}

std::shared_ptr<IpcService> HorizonIpcManager::getServiceByHandle(uint32_t handle) const {
    std::lock_guard<std::mutex> lock(managerMutex);
    auto it = handleTable.find(handle);
    if (it != handleTable.end()) {
        return it->second;
    }
    return nullptr;
}

void HorizonIpcManager::closeHandle(uint32_t handle) {
    std::lock_guard<std::mutex> lock(managerMutex);
    handleTable.erase(handle);
    handleNames.erase(handle);
}

size_t HorizonIpcManager::getActiveHandleCount() const {
    std::lock_guard<std::mutex> lock(managerMutex);
    return handleTable.size();
}

void HorizonIpcManager::reset() {
    std::lock_guard<std::mutex> lock(managerMutex);
    handleTable.clear();
    handleNames.clear();
    nextHandle.store(0x100);
    totalRequestsProcessed.store(0);
    totalSvcSyncCalls.store(0);
    initializeDefaultServices();
}

// CMIF Request Parsing from TLS
IpcParsedRequest HorizonIpcManager::parseTlsRequest(uint64_t tlsAddress, SwtcMmu::UnifiedMemoryManager& mmu) {
    IpcParsedRequest req;

    uint32_t word0 = mmu.read32(tlsAddress);
    uint32_t word1 = mmu.read32(tlsAddress + 4);

    req.type = static_cast<IpcMessageType>(word0 & 0xFFFF);
    uint32_t numX = (word0 >> 16) & 0xF;
    uint32_t numA = (word0 >> 20) & 0xF;
    uint32_t numB = (word0 >> 24) & 0xF;
    uint32_t numW = (word0 >> 28) & 0xF;

    uint32_t rawDataSizeWords = word1 & 0x3FF;
    bool hasHandleDesc = ((word1 >> 31) & 0x1) != 0;

    uint64_t currentOffset = tlsAddress + 8;

    // Handle Descriptor Parsing
    if (hasHandleDesc) {
        uint32_t handleDesc = mmu.read32(currentOffset);
        currentOffset += 4;

        bool sendPid = (handleDesc & 1) != 0;
        uint32_t copyCount = (handleDesc >> 1) & 0xF;
        uint32_t moveCount = (handleDesc >> 5) & 0xF;

        if (sendPid) {
            req.clientPid = mmu.read64(currentOffset);
            currentOffset += 8;
        }

        for (uint32_t i = 0; i < copyCount; ++i) {
            req.copyHandles.push_back(mmu.read32(currentOffset));
            currentOffset += 4;
        }

        for (uint32_t i = 0; i < moveCount; ++i) {
            req.moveHandles.push_back(mmu.read32(currentOffset));
            currentOffset += 4;
        }
    }

    // Skip X descriptors (8 bytes each)
    for (uint32_t i = 0; i < numX; ++i) {
        uint32_t x0 = mmu.read32(currentOffset);
        uint32_t x1 = mmu.read32(currentOffset + 4);
        DescriptorX desc;
        desc.counter = (x0 >> 6) & 0x7;
        desc.address = ((uint64_t)(x1) << 32) | (x0 & 0xFFFFFF);
        desc.size = (x0 >> 16) & 0xFFFF;
        req.descX.push_back(desc);
        currentOffset += 8;
    }

    // Skip A descriptors (12 bytes each)
    for (uint32_t i = 0; i < numA; ++i) {
        uint32_t a0 = mmu.read32(currentOffset);
        uint32_t a1 = mmu.read32(currentOffset + 4);
        uint32_t a2 = mmu.read32(currentOffset + 8);
        DescriptorABW desc;
        desc.size = a0;
        desc.address = ((uint64_t)(a2 & 0xFFFF) << 32) | a1;
        desc.flags = (a2 >> 16) & 0xFFFF;
        req.descA.push_back(desc);
        currentOffset += 12;
    }

    // Skip B & W descriptors
    currentOffset += (numB * 12) + (numW * 12);

    // 16-byte alignment before Data Payload / SFCI
    uint64_t padding = (16 - (currentOffset % 16)) % 16;
    currentOffset += padding;

    uint32_t magic = mmu.read32(currentOffset);
    if (magic == CMIF_IN_HEADER_MAGIC) { // "SFCI"
        req.commandId = mmu.read32(currentOffset + 8);
        uint64_t dataOffset = currentOffset + 16;
        size_t byteCount = rawDataSizeWords * 4;
        if (byteCount > 16) {
            req.rawData.resize(byteCount - 16);
            for (size_t i = 0; i < req.rawData.size(); ++i) {
                req.rawData[i] = mmu.read8(dataOffset + i);
            }
        }
    } else {
        // Raw or Direct Command
        req.commandId = mmu.read32(currentOffset);
    }

    return req;
}

// CMIF Response Writing to TLS
void HorizonIpcManager::writeTlsResponse(uint64_t tlsAddress, const IpcResponse& response, SwtcMmu::UnifiedMemoryManager& mmu) {
    uint32_t totalDataWords = 4 + static_cast<uint32_t>((response.data.size() + 3) / 4);
    
    // Header Word 0: Type 0 (Response)
    mmu.write32(tlsAddress, 0x00000000);

    // Header Word 1: Size in words + Handle Descriptor flag
    bool hasHandles = !response.copyHandles.empty() || !response.moveHandles.empty();
    uint32_t word1 = totalDataWords & 0x3FF;
    if (hasHandles) {
        word1 |= (1U << 31);
    }
    mmu.write32(tlsAddress + 4, word1);

    uint64_t currentOffset = tlsAddress + 8;

    if (hasHandles) {
        uint32_t copyCount = static_cast<uint32_t>(response.copyHandles.size());
        uint32_t moveCount = static_cast<uint32_t>(response.moveHandles.size());
        uint32_t handleDesc = (copyCount << 1) | (moveCount << 5);
        mmu.write32(currentOffset, handleDesc);
        currentOffset += 4;

        for (uint32_t h : response.copyHandles) {
            mmu.write32(currentOffset, h);
            currentOffset += 4;
        }
        for (uint32_t h : response.moveHandles) {
            mmu.write32(currentOffset, h);
            currentOffset += 4;
        }
    }

    // 16-byte alignment for SFCO data header
    uint64_t padding = (16 - (currentOffset % 16)) % 16;
    currentOffset += padding;

    // SFCO Header
    mmu.write32(currentOffset, CMIF_OUT_HEADER_MAGIC); // "SFCO"
    mmu.write32(currentOffset + 4, 0);                  // Version / Reserved
    mmu.write32(currentOffset + 8, response.resultCode); // Result Code
    mmu.write32(currentOffset + 12, 0);                 // Padding

    // Payload data
    uint64_t dataOffset = currentOffset + 16;
    for (size_t i = 0; i < response.data.size(); ++i) {
        mmu.write8(dataOffset + i, response.data[i]);
    }
}

uint32_t HorizonIpcManager::processSendSyncRequest(uint32_t sessionHandle, uint64_t tlsAddress, SwtcMmu::UnifiedMemoryManager& mmu) {
    totalSvcSyncCalls.fetch_add(1, std::memory_order_relaxed);

    auto service = getServiceByHandle(sessionHandle);
    if (!service) {
        LOGW("svcSendSyncRequest: Unknown session handle 0x%X", sessionHandle);
        IpcResponse errResp;
        errResp.resultCode = RESULT_INVALID_HANDLE;
        writeTlsResponse(tlsAddress, errResp, mmu);
        return RESULT_INVALID_HANDLE;
    }

    IpcParsedRequest request = parseTlsRequest(tlsAddress, mmu);
    totalRequestsProcessed.fetch_add(1, std::memory_order_relaxed);

    LOGI("IPC -> [%s] Cmd: %u, Type: %u, Handles In: (C:%zu, M:%zu), DataLen: %zu bytes",
         service->getName().c_str(), request.commandId, static_cast<uint32_t>(request.type),
         request.copyHandles.size(), request.moveHandles.size(), request.rawData.size());

    IpcResponse response = service->dispatch(request);
    writeTlsResponse(tlsAddress, response, mmu);

    return response.resultCode;
}

std::string HorizonIpcManager::getIpcSummary() const {
    std::lock_guard<std::mutex> lock(managerMutex);
    std::ostringstream ss;
    ss << "=== HORIZON IPC & HLE MANAGER ===\n";
    ss << "Total SVC SendSyncRequests: " << totalSvcSyncCalls.load() << "\n";
    ss << "Total IPC Dispatches: " << totalRequestsProcessed.load() << "\n";
    ss << "Active Service Handles: " << handleTable.size() << "\n";
    ss << "Registered Services (" << registeredServices.size() << "):\n";
    for (const auto& kv : registeredServices) {
        ss << " - " << kv.first << "\n";
    }
    ss << "Open Handles:\n";
    for (const auto& kv : handleNames) {
        ss << " [0x" << std::hex << kv.first << std::dec << "] -> " << kv.second << "\n";
    }
    return ss.str();
}

} // namespace SwtcHorizon
