#include "fs_parser.h"
#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "SwtcFsParser"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace SwtcFs {

// Magic constants
constexpr uint32_t PFS0_MAGIC = 0x30534650; // "PFS0"
constexpr uint32_t HFS0_MAGIC = 0x30534648; // "HFS0"
constexpr uint32_t NCA3_MAGIC = 0x3341434E; // "NCA3"
constexpr uint32_t NCA2_MAGIC = 0x3241434E; // "NCA2"
constexpr uint32_t NCA0_MAGIC = 0x3041434E; // "NCA0"

bool Pfs0Reader::parse(const uint8_t* data, size_t totalSize, MountedPartition& outPartition) {
    if (!data || totalSize < sizeof(Pfs0HeaderRaw)) {
        LOGE("PFS0: Data buffer is too small (%zu bytes)", totalSize);
        return false;
    }

    const auto* header = reinterpret_cast<const Pfs0HeaderRaw*>(data);
    if (header->magic != PFS0_MAGIC && header->magic != HFS0_MAGIC) {
        LOGE("PFS0: Invalid magic 0x%08X (Expected PFS0 0x%08X)", header->magic, PFS0_MAGIC);
        return false;
    }

    size_t headerTableSize = sizeof(Pfs0HeaderRaw) + (header->file_count * sizeof(Pfs0FileEntryRaw));
    if (totalSize < headerTableSize + header->string_table_size) {
        LOGE("PFS0: Truncated header/string table");
        return false;
    }

    const auto* entries = reinterpret_cast<const Pfs0FileEntryRaw*>(data + sizeof(Pfs0HeaderRaw));
    const char* stringTable = reinterpret_cast<const char*>(data + headerTableSize);
    uint64_t dataOffset = headerTableSize + header->string_table_size;

    outPartition.files.clear();
    outPartition.fileIndex.clear();
    outPartition.partitionType = "PFS0";

    for (uint32_t i = 0; i < header->file_count; ++i) {
        if (entries[i].name_offset >= header->string_table_size) {
            LOGW("PFS0: Entry %u has invalid string offset %u", i, entries[i].name_offset);
            continue;
        }

        std::string fileName = stringTable + entries[i].name_offset;
        uint64_t fileAbsoluteOffset = dataOffset + entries[i].offset;
        uint64_t fileSize = entries[i].size;

        if (fileAbsoluteOffset + fileSize > totalSize) {
            LOGW("PFS0: File '%s' bounds exceed archive size", fileName.c_str());
            continue;
        }

        MountedFile mFile;
        mFile.name = fileName;
        mFile.offset = fileAbsoluteOffset;
        mFile.size = fileSize;
        mFile.rawData = data + fileAbsoluteOffset;

        outPartition.fileIndex[fileName] = outPartition.files.size();
        outPartition.files.push_back(mFile);

        LOGI("PFS0 Mounted File: %s (Size: %llu bytes, Offset: 0x%llX)", 
             fileName.c_str(), static_cast<unsigned long long>(fileSize), 
             static_cast<unsigned long long>(fileAbsoluteOffset));
    }

    LOGI("PFS0: Successfully mounted partition with %zu files.", outPartition.files.size());
    return !outPartition.files.empty();
}

bool NcaDecoder::decodeNca(const uint8_t* data, size_t totalSize,
                           MountedPartition& outExeFs, MountedPartition& outRomFs) {
    if (!data || totalSize < sizeof(NcaHeaderRaw)) {
        LOGE("NCA: Data buffer is too small for NCA Header (%zu bytes)", totalSize);
        return false;
    }

    const auto* ncaHeader = reinterpret_cast<const NcaHeaderRaw*>(data);
    if (ncaHeader->magic != NCA3_MAGIC && ncaHeader->magic != NCA2_MAGIC && ncaHeader->magic != NCA0_MAGIC) {
        // Some unpacked NCA sections start directly with PFS0 (e.g. decrypted ExeFS/RomFS)
        const auto* directPfs0 = reinterpret_cast<const Pfs0HeaderRaw*>(data);
        if (directPfs0->magic == PFS0_MAGIC || directPfs0->magic == HFS0_MAGIC) {
            LOGI("NCA: Direct PFS0 payload detected, mounting as ExeFS.");
            return Pfs0Reader::parse(data, totalSize, outExeFs);
        }
        LOGE("NCA: Invalid magic 0x%08X", ncaHeader->magic);
        return false;
    }

    LOGI("NCA Content Type: %u, Title ID: 0x%016llX, Media Size: %llu",
         ncaHeader->content_type, 
         static_cast<unsigned long long>(ncaHeader->title_id),
         static_cast<unsigned long long>(ncaHeader->nca_size));

    // Section 0 is typically ExeFS (PFS0 filesystem)
    const auto& sec0 = ncaHeader->section_entries[0];
    if (sec0.media_end_offset > sec0.media_start_offset) {
        uint64_t sec0Start = static_cast<uint64_t>(sec0.media_start_offset) * 0x200;
        uint64_t sec0End = static_cast<uint64_t>(sec0.media_end_offset) * 0x200;
        if (sec0Start < totalSize && sec0End <= totalSize) {
            size_t sec0Size = sec0End - sec0Start;
            LOGI("NCA Section 0 (ExeFS) at 0x%llX (Size: %zu)", static_cast<unsigned long long>(sec0Start), sec0Size);
            Pfs0Reader::parse(data + sec0Start, sec0Size, outExeFs);
            outExeFs.partitionType = "ExeFS";
        }
    }

    // Section 1 is typically RomFS (IVFC / Hierarchical RomFS)
    const auto& sec1 = ncaHeader->section_entries[1];
    if (sec1.media_end_offset > sec1.media_start_offset) {
        uint64_t sec1Start = static_cast<uint64_t>(sec1.media_start_offset) * 0x200;
        uint64_t sec1End = static_cast<uint64_t>(sec1.media_end_offset) * 0x200;
        if (sec1Start < totalSize && sec1End <= totalSize) {
            size_t sec1Size = sec1End - sec1Start;
            LOGI("NCA Section 1 (RomFS) at 0x%llX (Size: %zu)", static_cast<unsigned long long>(sec1Start), sec1Size);
            outRomFs.partitionType = "RomFS";
            MountedFile romFsBlob;
            romFsBlob.name = "romfs.bin";
            romFsBlob.offset = sec1Start;
            romFsBlob.size = sec1Size;
            romFsBlob.rawData = data + sec1Start;
            outRomFs.files.push_back(romFsBlob);
            outRomFs.fileIndex["romfs.bin"] = 0;
        }
    }

    return true;
}

NspMountManager& NspMountManager::getInstance() {
    static NspMountManager instance;
    return instance;
}

void NspMountManager::unmountAll() {
    pfs0Root.files.clear();
    pfs0Root.fileIndex.clear();
    exeFs.files.clear();
    exeFs.fileIndex.clear();
    romFs.files.clear();
    romFs.fileIndex.clear();
    exeFsMounted = false;
    romFsMounted = false;
    LOGI("NSP Mount Manager: All partitions unmounted.");
}

bool NspMountManager::mountNsp(const uint8_t* nspData, size_t nspSize) {
    unmountAll();
    LOGI("NSP Mount Manager: Mounting NSP archive (%zu bytes)...", nspSize);

    if (!Pfs0Reader::parse(nspData, nspSize, pfs0Root)) {
        LOGE("NSP Mount Manager: Failed to parse root PFS0 archive.");
        return false;
    }

    // Look for Program NCA (ends in .nca or largest .nca payload)
    const MountedFile* programNca = nullptr;
    uint64_t maxNcaSize = 0;

    for (const auto& file : pfs0Root.files) {
        if (file.name.find(".nca") != std::string::npos || file.name.find(".NCA") != std::string::npos) {
            if (file.name.find(".cnmt.nca") == std::string::npos && file.size > maxNcaSize) {
                maxNcaSize = file.size;
                programNca = &file;
            }
        }
    }

    if (programNca && programNca->rawData) {
        LOGI("NSP Mount Manager: Selected Main Program NCA '%s' (%llu bytes)", 
             programNca->name.c_str(), static_cast<unsigned long long>(programNca->size));
        if (NcaDecoder::decodeNca(programNca->rawData, programNca->size, exeFs, romFs)) {
            exeFsMounted = !exeFs.files.empty();
            romFsMounted = !romFs.files.empty();
            LOGI("NSP Mount Manager: ExeFS Mounted: %s, RomFS Mounted: %s", 
                 exeFsMounted ? "YES" : "NO", romFsMounted ? "YES" : "NO");
            return true;
        }
    } else {
        LOGW("NSP Mount Manager: No separate NCA found, checking if archive is raw ExeFS.");
        exeFs = pfs0Root;
        exeFs.partitionType = "ExeFS";
        exeFsMounted = true;
        return true;
    }

    return false;
}

} // namespace SwtcFs

// ======================================================================
// JNI BINDINGS FOR KOTLIN INTEGRATION
// ======================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_NspParser_nativeMountNsp(JNIEnv* env, jobject /* this */, jbyteArray nspByteArray) {
    if (!nspByteArray) return JNI_FALSE;

    jsize len = env->GetArrayLength(nspByteArray);
    jbyte* bytes = env->GetByteArrayElements(nspByteArray, nullptr);
    if (!bytes) return JNI_FALSE;

    bool success = SwtcFs::NspMountManager::getInstance().mountNsp(
        reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));

    env->ReleaseByteArrayElements(nspByteArray, bytes, JNI_ABORT);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_NspParser_nativeGetExeFsFileCount(JNIEnv* env, jobject /* this */) {
    const auto& exeFs = SwtcFs::NspMountManager::getInstance().getExeFs();
    return static_cast<jint>(exeFs.files.size());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_NspParser_nativeGetExeFsFileName(JNIEnv* env, jobject /* this */, jint index) {
    const auto& exeFs = SwtcFs::NspMountManager::getInstance().getExeFs();
    if (index >= 0 && index < static_cast<jint>(exeFs.files.size())) {
        return env->NewStringUTF(exeFs.files[index].name.c_str());
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_emulator_NspParser_nativeGetExeFsFileSize(JNIEnv* env, jobject /* this */, jint index) {
    const auto& exeFs = SwtcFs::NspMountManager::getInstance().getExeFs();
    if (index >= 0 && index < static_cast<jint>(exeFs.files.size())) {
        return static_cast<jlong>(exeFs.files[index].size);
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_NspParser_nativeUnmountNsp(JNIEnv* env, jobject /* this */) {
    SwtcFs::NspMountManager::getInstance().unmountAll();
}
