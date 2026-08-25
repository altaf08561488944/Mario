#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <memory>
#include <unordered_map>

namespace SwtcFs {

#pragma pack(push, 1)

struct Pfs0HeaderRaw {
    uint32_t magic;             // "PFS0" (0x30534650)
    uint32_t file_count;
    uint32_t string_table_size;
    uint32_t reserved;
};

struct Pfs0FileEntryRaw {
    uint64_t offset;
    uint64_t size;
    uint32_t name_offset;
    uint32_t reserved;
};

struct NcaHeaderRaw {
    uint8_t fixed_key_sig[0x100];
    uint8_t npdm_sig[0x100];
    uint32_t magic;             // "NCA3" (0x3341434E) or "NCA2"
    uint8_t distribution_type;
    uint8_t content_type;
    uint8_t key_generation;
    uint8_t kaek_index;
    uint64_t nca_size;
    uint64_t title_id;
    uint32_t sdk_version;
    uint8_t crypto_type;
    uint8_t reserved1[0xF];
    uint8_t rights_id[0x10];
    
    struct SectionEntry {
        uint32_t media_start_offset;
        uint32_t media_end_offset;
        uint8_t reserved[0x8];
    } section_entries[4];
};

struct RomFsHeaderRaw {
    uint64_t header_size;
    uint64_t dir_hash_table_offset;
    uint64_t dir_hash_table_size;
    uint64_t dir_meta_table_offset;
    uint64_t dir_meta_table_size;
    uint64_t file_hash_table_offset;
    uint64_t file_hash_table_size;
    uint64_t file_meta_table_offset;
    uint64_t file_meta_table_size;
    uint64_t data_offset;
};

#pragma pack(pop)

struct MountedFile {
    std::string name;
    uint64_t offset;
    uint64_t size;
    const uint8_t* rawData = nullptr;
};

struct MountedPartition {
    std::string partitionType; // "ExeFS", "RomFS", "NCA"
    std::vector<MountedFile> files;
    std::unordered_map<std::string, size_t> fileIndex;
};

class Pfs0Reader {
public:
    static bool parse(const uint8_t* data, size_t totalSize, MountedPartition& outPartition);
};

class NcaDecoder {
public:
    static bool decodeNca(const uint8_t* data, size_t totalSize, 
                          MountedPartition& outExeFs, MountedPartition& outRomFs);
};

class NspMountManager {
public:
    static NspMountManager& getInstance();
    
    bool mountNsp(const uint8_t* nspData, size_t nspSize);
    void unmountAll();
    
    bool hasMountedExeFs() const { return exeFsMounted; }
    bool hasMountedRomFs() const { return romFsMounted; }
    
    const MountedPartition& getExeFs() const { return exeFs; }
    const MountedPartition& getRomFs() const { return romFs; }
    const MountedPartition& getPfs0Root() const { return pfs0Root; }

private:
    NspMountManager() = default;
    MountedPartition pfs0Root;
    MountedPartition exeFs;
    MountedPartition romFs;
    bool exeFsMounted = false;
    bool romFsMounted = false;
};

} // namespace SwtcFs
