#pragma once

#include <cstdint>
#include <cstddef>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <atomic>
#include <cstring>
#include <memory>

#ifdef PAGE_SIZE
#undef PAGE_SIZE
#endif

#ifdef PAGE_MASK
#undef PAGE_MASK
#endif

#ifdef PAGE_SHIFT
#undef PAGE_SHIFT
#endif

namespace SwtcMmu {

// Switch 4KB Page granularity
constexpr uint64_t SWTC_PAGE_SHIFT = 12;
constexpr uint64_t SWTC_PAGE_SIZE  = 1ULL << SWTC_PAGE_SHIFT; // 4096 bytes
constexpr uint64_t SWTC_PAGE_MASK  = SWTC_PAGE_SIZE - 1ULL;

// Page permissions
enum PagePermissions : uint32_t {
    PAGE_NONE      = 0,
    PAGE_READ      = 1 << 0,
    PAGE_WRITE     = 1 << 1,
    PAGE_EXEC      = 1 << 2,
    PAGE_GPU_READ  = 1 << 3,
    PAGE_GPU_WRITE = 1 << 4,
    PAGE_RW        = PAGE_READ | PAGE_WRITE,
    PAGE_RWX       = PAGE_READ | PAGE_WRITE | PAGE_EXEC,
    PAGE_UNIFIED   = PAGE_READ | PAGE_WRITE | PAGE_GPU_READ | PAGE_GPU_WRITE
};

// Page Table Entry (PTE)
struct PageTableEntry {
    uint8_t* hostPtr = nullptr;
    uint32_t permissions = PAGE_NONE;
    bool isAllocated = false;
};

// Software TLB Entry
constexpr size_t TLB_SIZE = 4096;
constexpr size_t TLB_MASK = TLB_SIZE - 1;

struct TLBEntry {
    uint64_t vpn = UINT64_MAX; // Virtual Page Number
    uint8_t* hostPtr = nullptr;
    uint32_t permissions = PAGE_NONE;
};

// Statistics
struct MmuStats {
    std::atomic<uint64_t> tlbHits{0};
    std::atomic<uint64_t> tlbMisses{0};
    std::atomic<uint64_t> pageFaults{0};
    std::atomic<size_t> allocatedPages{0};
};

/**
 * 4GB Unified Memory Management Unit for Tegra X1 / Switch Architecture.
 * Handles 36-bit Virtual Address space with Sparse multi-level Page Tables
 * and high-speed Software TLB translation.
 */
class UnifiedMemoryManager {
private:
    // Multi-level / Sparse Page Table
    // Level 1: maps upper address bits (VPN >> 10) to L2 table (1024 entries per table)
    static constexpr size_t L2_ENTRIES = 1024;
    std::unordered_map<uint64_t, std::unique_ptr<PageTableEntry[]>> l1Directory;
    mutable std::mutex pageTableMutex;

    // Fast Direct-Mapped Software TLB
    TLBEntry tlbTable[TLB_SIZE];

    // Stats
    MmuStats stats;

    PageTableEntry* lookupPte(uint64_t vaddr, bool createIfMissing = false);

public:
    UnifiedMemoryManager();
    ~UnifiedMemoryManager();

    // Map virtual address range to backing memory with specified permissions
    bool map(uint64_t vaddr, size_t size, uint32_t perms);

    // Unmap virtual address range and free allocated pages
    bool unmap(uint64_t vaddr, size_t size);

    // Protect virtual memory region
    bool protect(uint64_t vaddr, size_t size, uint32_t perms);

    // TLB operations
    void flushTlb();
    void invalidateTlbPage(uint64_t vaddr);

    // Translate Virtual Address to Host Pointer using TLB fast-path
    inline uint8_t* translate(uint64_t vaddr, uint32_t requiredPerms) {
        uint64_t vpn = vaddr >> SWTC_PAGE_SHIFT;
        size_t tlbIndex = vpn & TLB_MASK;
        const TLBEntry& entry = tlbTable[tlbIndex];

        // Fast TLB Hit
        if (entry.vpn == vpn && (entry.permissions & requiredPerms) == requiredPerms) {
            stats.tlbHits.fetch_add(1, std::memory_order_relaxed);
            return entry.hostPtr + (vaddr & SWTC_PAGE_MASK);
        }

        // Slow Path: Page Table Walk
        return translateSlow(vaddr, requiredPerms, tlbIndex);
    }

    uint8_t* translateSlow(uint64_t vaddr, uint32_t requiredPerms, size_t tlbIndex);

    // Fast Memory Access Primitives (CPU & GPU)
    uint8_t  read8(uint64_t vaddr);
    uint16_t read16(uint64_t vaddr);
    uint32_t read32(uint64_t vaddr);
    uint64_t read64(uint64_t vaddr);

    void write8(uint64_t vaddr, uint8_t value);
    void write16(uint64_t vaddr, uint16_t value);
    void write32(uint64_t vaddr, uint32_t value);
    void write64(uint64_t vaddr, uint64_t value);

    bool readBlock(uint64_t vaddr, uint8_t* dest, size_t length);
    bool writeBlock(uint64_t vaddr, const uint8_t* src, size_t length);

    // Reset all memory state
    void reset();

    // Diagnostics
    size_t getAllocatedPages() const { return stats.allocatedPages.load(std::memory_order_relaxed); }
    size_t getAllocatedBytes() const { return getAllocatedPages() * SWTC_PAGE_SIZE; }
    uint64_t getTlbHits() const { return stats.tlbHits.load(std::memory_order_relaxed); }
    uint64_t getTlbMisses() const { return stats.tlbMisses.load(std::memory_order_relaxed); }
    uint64_t getPageFaults() const { return stats.pageFaults.load(std::memory_order_relaxed); }
};

UnifiedMemoryManager& getGlobalMmu();

} // namespace SwtcMmu
