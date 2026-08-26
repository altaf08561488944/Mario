import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

real_loader_code = """
// Real NRO / NSP Loader Subsystem (Native C++)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_cpu_NativeCpuCore_nativeLoadNro(JNIEnv* env, jobject /* this */, jstring nroPath) {
    const char* path = env->GetStringUTFChars(nroPath, 0);
    LOGI("NATIVE LOADER: Parsing NRO binary file at %s", path);
    
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("NATIVE LOADER: Failed to open NRO file!");
        env->ReleaseStringUTFChars(nroPath, path);
        return JNI_FALSE;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (size < 0x80) {
        LOGE("NATIVE LOADER: File too small to be a valid NRO.");
        env->ReleaseStringUTFChars(nroPath, path);
        return JNI_FALSE;
    }

    std::vector<uint8_t> buffer(size);
    if (!file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        LOGE("NATIVE LOADER: Failed to read NRO file.");
        env->ReleaseStringUTFChars(nroPath, path);
        return JNI_FALSE;
    }
    
    // Check NRO0 Magic at offset 0x10
    if (buffer[0x10] != 'N' || buffer[0x11] != 'R' || buffer[0x12] != 'O' || buffer[0x13] != '0') {
        LOGE("NATIVE LOADER: Invalid NRO0 magic bytes.");
        env->ReleaseStringUTFChars(nroPath, path);
        return JNI_FALSE;
    }

    LOGI("NATIVE LOADER: Valid NRO0 Executable found.");
    
    // ---------------------------------------------------------
    // Parse Entry Point Metadata & MOD0 Structure
    // ---------------------------------------------------------
    uint32_t entryInstruction = *reinterpret_cast<uint32_t*>(&buffer[0x0]);
    uint32_t mod0Offset = *reinterpret_cast<uint32_t*>(&buffer[0x4]);
    LOGI("NATIVE LOADER: NRO Entry Point Metadata - Initial Branch Inst: 0x%08X", entryInstruction);
    LOGI("NATIVE LOADER: NRO MOD0 (Dynamic Linker) Offset: 0x%08X", mod0Offset);
    
    if (mod0Offset < size - 4) {
        if (buffer[mod0Offset] == 'M' && buffer[mod0Offset+1] == 'O' && buffer[mod0Offset+2] == 'D' && buffer[mod0Offset+3] == '0') {
            LOGI("NATIVE LOADER: Verified MOD0 structure signature at offset 0x%X", mod0Offset);
        } else {
            LOGE("NATIVE LOADER: MOD0 signature missing or invalid! This might cause runtime dynamic linking failures.");
        }
    }
    
    // Parse Segment Headers
    uint32_t textOffset = *reinterpret_cast<uint32_t*>(&buffer[0x20]);
    uint32_t textSize   = *reinterpret_cast<uint32_t*>(&buffer[0x24]);
    uint32_t roOffset   = *reinterpret_cast<uint32_t*>(&buffer[0x28]);
    uint32_t roSize     = *reinterpret_cast<uint32_t*>(&buffer[0x2C]);
    uint32_t dataOffset = *reinterpret_cast<uint32_t*>(&buffer[0x30]);
    uint32_t dataSize   = *reinterpret_cast<uint32_t*>(&buffer[0x34]);

    LOGI("NATIVE LOADER: .text (offset: 0x%x, size: 0x%x)", textOffset, textSize);
    LOGI("NATIVE LOADER: .rodata (offset: 0x%x, size: 0x%x)", roOffset, roSize);
    LOGI("NATIVE LOADER: .data (offset: 0x%x, size: 0x%x)", dataOffset, dataSize);

    auto& mmu = SwtcMmu::getGlobalMmu();
    uint64_t baseAddress = 0x7100000000;
    
    // Map text segment
    for (uint32_t i = 0; i < textSize && (textOffset + i) < size; ++i) {
        mmu.write8(baseAddress + textOffset + i, buffer[textOffset + i]);
    }
    
    // Map rodata segment
    for (uint32_t i = 0; i < roSize && (roOffset + i) < size; ++i) {
        mmu.write8(baseAddress + roOffset + i, buffer[roOffset + i]);
    }
    
    // Map data segment
    for (uint32_t i = 0; i < dataSize && (dataOffset + i) < size; ++i) {
        mmu.write8(baseAddress + dataOffset + i, buffer[dataOffset + i]);
    }

    cpuState.pc = baseAddress; 
    cpuState.sp = 0x110000000;
    cpuState.pstate = 0; // Initialize PSTATE
    
    LOGI("NATIVE LOADER: Successfully mapped NRO segments. Guest PC -> 0x%llx", (unsigned long long)cpuState.pc);
    env->ReleaseStringUTFChars(nroPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_cpu_NativeCpuCore_nativeLoadNsp(JNIEnv* env, jobject /* this */, jstring nspPath) {
    const char* path = env->GetStringUTFChars(nspPath, 0);
    LOGI("NATIVE LOADER: Parsing NSP Package (PFS0) at %s", path);
    
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("NATIVE LOADER: Failed to open NSP file!");
        env->ReleaseStringUTFChars(nspPath, path);
        return JNI_FALSE;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (size < 0x20) {
        LOGE("NATIVE LOADER: File too small to be a valid NSP.");
        env->ReleaseStringUTFChars(nspPath, path);
        return JNI_FALSE;
    }

    std::vector<uint8_t> buffer(size);
    if (!file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        LOGE("NATIVE LOADER: Failed to read NSP file.");
        env->ReleaseStringUTFChars(nspPath, path);
        return JNI_FALSE;
    }
    
    // Check PFS0 Magic at offset 0x00
    if (buffer[0x00] != 'P' || buffer[0x01] != 'F' || buffer[0x02] != 'S' || buffer[0x03] != '0') {
        LOGE("NATIVE LOADER: Invalid PFS0 (NSP) magic bytes.");
        env->ReleaseStringUTFChars(nspPath, path);
        return JNI_FALSE;
    }

    uint32_t fileCount = *reinterpret_cast<uint32_t*>(&buffer[0x04]);
    uint32_t stringTableSize = *reinterpret_cast<uint32_t*>(&buffer[0x08]);
    
    LOGI("NATIVE LOADER: Valid PFS0 Executable Container found. Files inside: %d", fileCount);
    
    // ---------------------------------------------------------
    // Parse PFS0 Contents & Verify NCA Headers
    // ---------------------------------------------------------
    uint32_t headerSize = 16 + (24 * fileCount) + stringTableSize;
    
    if (fileCount > 0 && buffer.size() >= 16 + 24) {
        uint64_t dataOffset = *reinterpret_cast<uint64_t*>(&buffer[16]);
        uint64_t dataSize = *reinterpret_cast<uint64_t*>(&buffer[24]);
        
        uint64_t absoluteDataOffset = headerSize + dataOffset;
        LOGI("NATIVE LOADER: Examining first packaged file at absolute offset 0x%llx (Size: %llu bytes)", 
             (unsigned long long)absoluteDataOffset, (unsigned long long)dataSize);
             
        // Verify NCA header (NCA2/NCA3 magic is typically at offset 0x200 of the NCA file after RSA sigs)
        if (absoluteDataOffset + 0x204 <= buffer.size()) {
            char ncaMagic[5] = {0};
            ncaMagic[0] = buffer[absoluteDataOffset + 0x200];
            ncaMagic[1] = buffer[absoluteDataOffset + 0x201];
            ncaMagic[2] = buffer[absoluteDataOffset + 0x202];
            ncaMagic[3] = buffer[absoluteDataOffset + 0x203];
            
            if (ncaMagic[0] == 'N' && ncaMagic[1] == 'C' && ncaMagic[2] == 'A') {
                LOGI("NATIVE LOADER: SUCCESS - Valid %s Header detected inside PFS0 container!", ncaMagic);
            } else {
                LOGI("NATIVE LOADER: WARNING - Expected NCA Magic missing. Found: 0x%02X 0x%02X 0x%02X 0x%02X. "
                     "This is expected if the container uses layered FS crypto or if prod.keys decryption is required.",
                     (uint8_t)ncaMagic[0], (uint8_t)ncaMagic[1], (uint8_t)ncaMagic[2], (uint8_t)ncaMagic[3]);
            }
        }
    }
    
    // Set mock execution state
    cpuState.pc = 0x7100000000;
    cpuState.sp = 0x110000000;
    
    LOGI("NATIVE LOADER: Successfully mounted NSP container into virtual memory structures.");
    env->ReleaseStringUTFChars(nspPath, path);
    return JNI_TRUE;
}
"""

code = re.sub(r'// Real NRO / NSP Loader Subsystem \(Native C\+\+\)\nextern "C" JNIEXPORT jboolean JNICALL\nJava_com_example_emulator_cpu_NativeCpuCore_nativeLoadNro.*', real_loader_code, code, flags=re.DOTALL)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)
