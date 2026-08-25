#include "memory_mmu.h"
#include <jni.h>
#include <android/log.h>
#include <cstdlib>

#define LOG_TAG "SwtcMMU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace SwtcMmu {

UnifiedMemoryManager::UnifiedMemoryManager() {
    flushTlb();
    LOGI("Switch 4GB Unified Memory Manager initialized with %zu TLB slots.", TLB_SIZE);
}

UnifiedMemoryManager::~UnifiedMemoryManager() {
    reset();
}

void UnifiedMemoryManager::flushTlb() {
    for (size_t i = 0; i < TLB_SIZE; ++i) {
        tlbTable[i].vpn = UINT64_MAX;
        tlbTable[i].hostPtr = nullptr;
        tlbTable[i].permissions = PAGE_NONE;
    }
}

void UnifiedMemoryManager::invalidateTlbPage(uint64_t vaddr) {
    uint64_t vpn = vaddr >> SWTC_PAGE_SHIFT;
    size_t index = vpn & TLB_MASK;
    if (tlbTable[index].vpn == vpn) {
        tlbTable[index].vpn = UINT64_MAX;
        tlbTable[index].hostPtr = nullptr;
        tlbTable[index].permissions = PAGE_NONE;
    }
}

PageTableEntry* UnifiedMemoryManager::lookupPte(uint64_t vaddr, bool createIfMissing) {
    uint64_t vpn = vaddr >> SWTC_PAGE_SHIFT;
    uint64_t l1Index = vpn / L2_ENTRIES;
    uint64_t l2Index = vpn % L2_ENTRIES;

    std::unique_lock<std::mutex> lock(pageTableMutex);
    auto it = l1Directory.find(l1Index);
    if (it == l1Directory.end()) {
        if (!createIfMissing) return nullptr;
        auto newL2 = std::make_unique<PageTableEntry[]>(L2_ENTRIES);
        PageTableEntry* rawPtr = newL2.get();
        l1Directory[l1Index] = std::move(newL2);
        return &rawPtr[l2Index];
    }

    return &(it->second[l2Index]);
}

bool UnifiedMemoryManager::map(uint64_t vaddr, size_t size, uint32_t perms) {
    if (size == 0) return false;

    uint64_t startVpn = vaddr >> SWTC_PAGE_SHIFT;
    uint64_t endVpn = (vaddr + size + SWTC_PAGE_MASK) >> SWTC_PAGE_SHIFT;
    size_t newPages = 0;

    for (uint64_t vpn = startVpn; vpn < endVpn; ++vpn) {
        uint64_t pageVaddr = vpn << SWTC_PAGE_SHIFT;
        PageTableEntry* pte = lookupPte(pageVaddr, true);
        if (!pte) continue;

        if (!pte->isAllocated) {
            pte->hostPtr = static_cast<uint8_t*>(std::calloc(1, SWTC_PAGE_SIZE));
            pte->isAllocated = true;
            newPages++;
        }
        pte->permissions = perms;
        invalidateTlbPage(pageVaddr);
    }

    stats.allocatedPages.fetch_add(newPages, std::memory_order_relaxed);
    LOGI("MMU Mapped range [0x%llX - 0x%llX] (%zu KB, Perms: 0x%X). Total Pages: %zu",
         static_cast<unsigned long long>(vaddr),
         static_cast<unsigned long long>(vaddr + size),
         size / 1024, perms, stats.allocatedPages.load());
    return true;
}

bool UnifiedMemoryManager::unmap(uint64_t vaddr, size_t size) {
    if (size == 0) return false;

    uint64_t startVpn = vaddr >> SWTC_PAGE_SHIFT;
    uint64_t endVpn = (vaddr + size + SWTC_PAGE_MASK) >> SWTC_PAGE_SHIFT;
    size_t freedPages = 0;

    for (uint64_t vpn = startVpn; vpn < endVpn; ++vpn) {
        uint64_t pageVaddr = vpn << SWTC_PAGE_SHIFT;
        PageTableEntry* pte = lookupPte(pageVaddr, false);
        if (pte && pte->isAllocated) {
            if (pte->hostPtr) {
                std::free(pte->hostPtr);
                pte->hostPtr = nullptr;
            }
            pte->isAllocated = false;
            pte->permissions = PAGE_NONE;
            freedPages++;
        }
        invalidateTlbPage(pageVaddr);
    }

    stats.allocatedPages.fetch_sub(freedPages, std::memory_order_relaxed);
    LOGI("MMU Unmapped range [0x%llX - 0x%llX] (Freed: %zu pages)",
         static_cast<unsigned long long>(vaddr),
         static_cast<unsigned long long>(vaddr + size), freedPages);
    return true;
}

bool UnifiedMemoryManager::protect(uint64_t vaddr, size_t size, uint32_t perms) {
    uint64_t startVpn = vaddr >> SWTC_PAGE_SHIFT;
    uint64_t endVpn = (vaddr + size + SWTC_PAGE_MASK) >> SWTC_PAGE_SHIFT;

    for (uint64_t vpn = startVpn; vpn < endVpn; ++vpn) {
        uint64_t pageVaddr = vpn << SWTC_PAGE_SHIFT;
        PageTableEntry* pte = lookupPte(pageVaddr, false);
        if (pte && pte->isAllocated) {
            pte->permissions = perms;
        }
        invalidateTlbPage(pageVaddr);
    }
    return true;
}

uint8_t* UnifiedMemoryManager::translateSlow(uint64_t vaddr, uint32_t requiredPerms, size_t tlbIndex) {
    stats.tlbMisses.fetch_add(1, std::memory_order_relaxed);

    PageTableEntry* pte = lookupPte(vaddr, false);
    if (!pte || !pte->isAllocated || (pte->permissions & requiredPerms) != requiredPerms) {
        stats.pageFaults.fetch_add(1, std::memory_order_relaxed);
        // Page fault
        return nullptr;
    }

    uint64_t vpn = vaddr >> SWTC_PAGE_SHIFT;
    // Update TLB entry
    tlbTable[tlbIndex].vpn = vpn;
    tlbTable[tlbIndex].hostPtr = pte->hostPtr;
    tlbTable[tlbIndex].permissions = pte->permissions;

    return pte->hostPtr + (vaddr & SWTC_PAGE_MASK);
}

uint8_t UnifiedMemoryManager::read8(uint64_t vaddr) {
    uint8_t* ptr = translate(vaddr, PAGE_READ);
    return ptr ? *ptr : 0;
}

uint16_t UnifiedMemoryManager::read16(uint64_t vaddr) {
    if ((vaddr & SWTC_PAGE_MASK) <= SWTC_PAGE_SIZE - 2) {
        uint8_t* ptr = translate(vaddr, PAGE_READ);
        if (ptr) return *reinterpret_cast<const uint16_t*>(ptr);
    }
    // Cross-page boundary safe read
    return static_cast<uint16_t>(read8(vaddr)) | (static_cast<uint16_t>(read8(vaddr + 1)) << 8);
}

uint32_t UnifiedMemoryManager::read32(uint64_t vaddr) {
    if ((vaddr & SWTC_PAGE_MASK) <= SWTC_PAGE_SIZE - 4) {
        uint8_t* ptr = translate(vaddr, PAGE_READ);
        if (ptr) return *reinterpret_cast<const uint32_t*>(ptr);
    }
    // Cross-page boundary safe read
    return static_cast<uint32_t>(read16(vaddr)) | (static_cast<uint32_t>(read16(vaddr + 2)) << 16);
}

uint64_t UnifiedMemoryManager::read64(uint64_t vaddr) {
    if ((vaddr & SWTC_PAGE_MASK) <= SWTC_PAGE_SIZE - 8) {
        uint8_t* ptr = translate(vaddr, PAGE_READ);
        if (ptr) return *reinterpret_cast<const uint64_t*>(ptr);
    }
    // Cross-page boundary safe read
    return static_cast<uint64_t>(read32(vaddr)) | (static_cast<uint64_t>(read32(vaddr + 4)) << 32);
}

void UnifiedMemoryManager::write8(uint64_t vaddr, uint8_t value) {
    uint8_t* ptr = translate(vaddr, PAGE_WRITE);
    if (ptr) *ptr = value;
}

void UnifiedMemoryManager::write16(uint64_t vaddr, uint16_t value) {
    if ((vaddr & SWTC_PAGE_MASK) <= SWTC_PAGE_SIZE - 2) {
        uint8_t* ptr = translate(vaddr, PAGE_WRITE);
        if (ptr) {
            *reinterpret_cast<uint16_t*>(ptr) = value;
            return;
        }
    }
    write8(vaddr, value & 0xFF);
    write8(vaddr + 1, (value >> 8) & 0xFF);
}

void UnifiedMemoryManager::write32(uint64_t vaddr, uint32_t value) {
    if ((vaddr & SWTC_PAGE_MASK) <= SWTC_PAGE_SIZE - 4) {
        uint8_t* ptr = translate(vaddr, PAGE_WRITE);
        if (ptr) {
            *reinterpret_cast<uint32_t*>(ptr) = value;
            return;
        }
    }
    write16(vaddr, value & 0xFFFF);
    write16(vaddr + 2, (value >> 16) & 0xFFFF);
}

void UnifiedMemoryManager::write64(uint64_t vaddr, uint64_t value) {
    if ((vaddr & SWTC_PAGE_MASK) <= SWTC_PAGE_SIZE - 8) {
        uint8_t* ptr = translate(vaddr, PAGE_WRITE);
        if (ptr) {
            *reinterpret_cast<uint64_t*>(ptr) = value;
            return;
        }
    }
    write32(vaddr, static_cast<uint32_t>(value & 0xFFFFFFFF));
    write32(vaddr + 4, static_cast<uint32_t>((value >> 32) & 0xFFFFFFFF));
}

bool UnifiedMemoryManager::readBlock(uint64_t vaddr, uint8_t* dest, size_t length) {
    if (!dest || length == 0) return false;
    for (size_t i = 0; i < length; ++i) {
        dest[i] = read8(vaddr + i);
    }
    return true;
}

bool UnifiedMemoryManager::writeBlock(uint64_t vaddr, const uint8_t* src, size_t length) {
    if (!src || length == 0) return false;
    for (size_t i = 0; i < length; ++i) {
        write8(vaddr + i, src[i]);
    }
    return true;
}

void UnifiedMemoryManager::reset() {
    std::unique_lock<std::mutex> lock(pageTableMutex);
    for (auto& pair : l1Directory) {
        for (size_t i = 0; i < L2_ENTRIES; ++i) {
            if (pair.second[i].isAllocated && pair.second[i].hostPtr) {
                std::free(pair.second[i].hostPtr);
            }
        }
    }
    l1Directory.clear();
    stats.allocatedPages.store(0);
    flushTlb();
    LOGI("MMU reset complete. All memory unmapped and freed.");
}

UnifiedMemoryManager& getGlobalMmu() {
    static UnifiedMemoryManager instance;
    return instance;
}

} // namespace SwtcMmu

// ======================================================================
// JNI BINDINGS FOR KOTLIN INTEGRATION
// ======================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeMapMemory(
    JNIEnv* env, jobject /* this */, jlong vaddr, jlong size, jint perms) {
    return SwtcMmu::getGlobalMmu().map(static_cast<uint64_t>(vaddr), static_cast<size_t>(size), static_cast<uint32_t>(perms)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeUnmapMemory(
    JNIEnv* env, jobject /* this */, jlong vaddr, jlong size) {
    return SwtcMmu::getGlobalMmu().unmap(static_cast<uint64_t>(vaddr), static_cast<size_t>(size)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeRead8(
    JNIEnv* env, jobject /* this */, jlong vaddr) {
    return static_cast<jbyte>(SwtcMmu::getGlobalMmu().read8(static_cast<uint64_t>(vaddr)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeWrite8(
    JNIEnv* env, jobject /* this */, jlong vaddr, jbyte value) {
    SwtcMmu::getGlobalMmu().write8(static_cast<uint64_t>(vaddr), static_cast<uint8_t>(value));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeRead32(
    JNIEnv* env, jobject /* this */, jlong vaddr) {
    return static_cast<jint>(SwtcMmu::getGlobalMmu().read32(static_cast<uint64_t>(vaddr)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeWrite32(
    JNIEnv* env, jobject /* this */, jlong vaddr, jint value) {
    SwtcMmu::getGlobalMmu().write32(static_cast<uint64_t>(vaddr), static_cast<uint32_t>(value));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeRead64(
    JNIEnv* env, jobject /* this */, jlong vaddr) {
    return static_cast<jlong>(SwtcMmu::getGlobalMmu().read64(static_cast<uint64_t>(vaddr)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeWrite64(
    JNIEnv* env, jobject /* this */, jlong vaddr, jlong value) {
    SwtcMmu::getGlobalMmu().write64(static_cast<uint64_t>(vaddr), static_cast<uint64_t>(value));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeGetAllocatedBytes(
    JNIEnv* env, jobject /* this */) {
    return static_cast<jlong>(SwtcMmu::getGlobalMmu().getAllocatedBytes());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeGetTlbHits(
    JNIEnv* env, jobject /* this */) {
    return static_cast<jlong>(SwtcMmu::getGlobalMmu().getTlbHits());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeGetTlbMisses(
    JNIEnv* env, jobject /* this */) {
    return static_cast<jlong>(SwtcMmu::getGlobalMmu().getTlbMisses());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_memory_MemoryManagementUnit_nativeReset(
    JNIEnv* env, jobject /* this */) {
    SwtcMmu::getGlobalMmu().reset();
}
