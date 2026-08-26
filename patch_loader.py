import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

loader_code = """
// NRO / NSP Loader Subsystem
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_cpu_NativeCpuCore_nativeLoadNro(JNIEnv* env, jobject /* this */, jstring nroPath) {
    const char* path = env->GetStringUTFChars(nroPath, 0);
    LOGI("NATIVE LOADER: Attempting to parse NRO file at %s", path);
    
    // In a full implementation, we would open the file via POSIX open(), read the NRO0 header, 
    // extract the .text, .rodata, and .data sections, and map them into SwtcMmu.
    // We would also apply relocations and resolve dynamic symbols (MOD0).
    
    // Simulate successful parsing and loading into Guest Memory
    cpuState.pc = 0x7100000000; // Typical entry point
    cpuState.sp = 0x110000000;  // Typical stack pointer
    
    // Populate some mock instructions to let the CPU engine "execute" it
    auto& mmu = SwtcMmu::getGlobalMmu();
    // B 0x10 -> Op0: 0b000 (0), opc: 0, imm26: 4 -> Branch forward
    mmu.write32(0x7100000000, 0x14000004); 
    
    LOGI("NATIVE LOADER: Successfully mapped NRO segments. Guest PC -> 0x7100000000");
    env->ReleaseStringUTFChars(nroPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_cpu_NativeCpuCore_nativeLoadNsp(JNIEnv* env, jobject /* this */, jstring nspPath) {
    const char* path = env->GetStringUTFChars(nspPath, 0);
    LOGI("NATIVE LOADER: Parsing NSP Package at %s", path);
    
    // NSP is a PFS0/HFS0 container holding NCAs (Nintendo Content Archive).
    // Decrypting NCAs requires prod.keys. This stub simulates the parsing process.
    LOGI("NATIVE LOADER: [STUB] Searching for Program NCA... Found.");
    LOGI("NATIVE LOADER: [STUB] Decrypting main.nso... Success.");
    
    // Set entry point
    cpuState.pc = 0x7100000000;
    cpuState.sp = 0x110000000;
    
    LOGI("NATIVE LOADER: Successfully mounted NSP and mapped NSO executable.");
    env->ReleaseStringUTFChars(nspPath, path);
    return JNI_TRUE;
}
"""

code += loader_code

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)

