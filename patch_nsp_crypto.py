import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

find_nsp = """    // Set mock execution state
    cpuState.pc = 0x7100000000;
    cpuState.sp = 0x110000000;"""
    
replace_nsp = """    // Set mock execution state
    cpuState.pc = 0x7100000000;
    cpuState.sp = 0x110000000;
    
    // Simulate NCA AES-XTS Decryption initialization
    std::vector<uint8_t> mockKeys = {0x00, 0x01, 0x02, 0x03}; 
    CryptoEngine::getInstance().InitializeKeys(mockKeys);
    CryptoEngine::getInstance().DecryptNCASection(nullptr, 0x100000, 2, 0); // 2 = AES-XTS
    
    // Mount basic HLE environment OS hooks
    HorizonOS::getInstance().HandleSVC(0x18); // Initialize sysinfo"""
    
code = code.replace(find_nsp, replace_nsp)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)

