#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>
#include <mutex>
#include <atomic>
#include "memory_mmu.h"

namespace SwtcHorizon {

// CMIF Magics
constexpr uint32_t CMIF_IN_HEADER_MAGIC  = 0x49434653; // "SFCI"
constexpr uint32_t CMIF_OUT_HEADER_MAGIC = 0x4F434653; // "SFCO"

// Result codes
constexpr uint32_t RESULT_SUCCESS = 0x00000000;
constexpr uint32_t RESULT_UNKNOWN_COMMAND = 0x00000E01;
constexpr uint32_t RESULT_SERVICE_NOT_FOUND = 0x00000E02;
constexpr uint32_t RESULT_INVALID_HANDLE = 0x00000E03;

// IPC Message Type
enum class IpcMessageType : uint16_t {
    Invalid = 0,
    LegacyRequest = 1,
    Close = 2,
    LegacyControl = 3,
    Request = 4,
    Control = 5,
    RequestWithContext = 6,
    ControlWithContext = 7
};

struct DescriptorX {
    uint32_t counter;
    uint64_t address;
    uint64_t size;
};

struct DescriptorABW {
    uint64_t address;
    uint64_t size;
    uint32_t flags;
};

struct IpcParsedRequest {
    IpcMessageType type = IpcMessageType::Invalid;
    uint32_t commandId = 0;
    bool isDomain = false;
    uint32_t domainObjectId = 0;
    uint32_t domainCommandType = 0;

    std::vector<uint8_t> rawData;
    std::vector<uint32_t> copyHandles;
    std::vector<uint32_t> moveHandles;
    std::vector<DescriptorX> descX;
    std::vector<DescriptorABW> descA;
    std::vector<DescriptorABW> descB;
    std::vector<DescriptorABW> descW;

    uint64_t clientPid = 0;
};

struct IpcResponse {
    uint32_t resultCode = RESULT_SUCCESS;
    std::vector<uint8_t> data;
    std::vector<uint32_t> copyHandles;
    std::vector<uint32_t> moveHandles;
    std::vector<uint32_t> domainObjects;
};

// Base interface for Horizon HLE Services
class IpcService {
protected:
    std::string serviceName;
public:
    explicit IpcService(std::string name) : serviceName(std::move(name)) {}
    virtual ~IpcService() = default;

    const std::string& getName() const { return serviceName; }
    virtual IpcResponse dispatch(const IpcParsedRequest& request) = 0;
};

// Core Service Implementations
class SmService : public IpcService {
public:
    SmService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class SetSysService : public IpcService {
public:
    SetSysService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class TimeService : public IpcService {
public:
    TimeService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class HidService : public IpcService {
public:
    HidService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class ViService : public IpcService {
public:
    ViService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class NvdrvService : public IpcService {
public:
    NvdrvService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class FspSrvService : public IpcService {
public:
    FspSrvService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class AppletOeService : public IpcService {
public:
    AppletOeService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

class AudRenService : public IpcService {
public:
    AudRenService();
    IpcResponse dispatch(const IpcParsedRequest& request) override;
};

// Handle Table & Manager
class HorizonIpcManager {
private:
    mutable std::mutex managerMutex;
    std::unordered_map<std::string, std::shared_ptr<IpcService>> registeredServices;
    std::unordered_map<uint32_t, std::shared_ptr<IpcService>> handleTable;
    std::unordered_map<uint32_t, std::string> handleNames;
    
    std::atomic<uint32_t> nextHandle{0x100};
    std::atomic<uint64_t> totalRequestsProcessed{0};
    std::atomic<uint64_t> totalSvcSyncCalls{0};

    HorizonIpcManager();

public:
    static HorizonIpcManager& getInstance();
    ~HorizonIpcManager() = default;

    void initializeDefaultServices();
    void registerService(const std::string& name, std::shared_ptr<IpcService> service);

    uint32_t createHandleForService(const std::string& name);
    std::shared_ptr<IpcService> getServiceByHandle(uint32_t handle) const;
    void closeHandle(uint32_t handle);

    // Parse CMIF request from guest Thread Local Storage (TLS) buffer
    IpcParsedRequest parseTlsRequest(uint64_t tlsAddress, SwtcMmu::UnifiedMemoryManager& mmu);

    // Write CMIF response to guest Thread Local Storage (TLS) buffer
    void writeTlsResponse(uint64_t tlsAddress, const IpcResponse& response, SwtcMmu::UnifiedMemoryManager& mmu);

    // Handle Horizon Supervisor Call: svcSendSyncRequest (0x1B)
    uint32_t processSendSyncRequest(uint32_t sessionHandle, uint64_t tlsAddress, SwtcMmu::UnifiedMemoryManager& mmu);

    // Diagnostic summary
    std::string getIpcSummary() const;
    uint64_t getTotalRequests() const { return totalRequestsProcessed.load(std::memory_order_relaxed); }
    uint64_t getTotalSvcSyncCalls() const { return totalSvcSyncCalls.load(std::memory_order_relaxed); }
    size_t getActiveHandleCount() const;
    void reset();
};

} // namespace SwtcHorizon
