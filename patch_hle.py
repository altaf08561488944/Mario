import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

hle_code = """
// ======================================================================
// SWTC NOOS - HORIZON OS (HLE) & CRYPTO ENGINE
// ======================================================================

class CryptoEngine {
public:
    static CryptoEngine& getInstance() {
        static CryptoEngine instance;
        return instance;
    }
    
    void InitializeKeys(const std::vector<uint8_t>& prodKeys) {
        LOGI("CRYPTO ENGINE: Initializing AES-XTS/CTR cryptographic context.");
        // Stub: In a real emulator, we'd parse the prod.keys file into key slots here.
    }
    
    bool DecryptNCASection(uint8_t* data, size_t size, uint8_t cryptoType, uint8_t keyGeneration) {
        LOGI("CRYPTO ENGINE: Attempting decryption block (Size: 0x%X, CryptoType: %d, KeyGen: %d)", size, cryptoType, keyGeneration);
        // Stub: Emulate the CPU cost of decryption without actual keys
        return true; 
    }
};

class HorizonOS {
public:
    static HorizonOS& getInstance() {
        static HorizonOS instance;
        return instance;
    }

    void HandleSVC(uint32_t svcCode) {
        switch (svcCode) {
            case 0x01: // svcSetHeapSize
                LOGI("Horizon OS: [SVC 0x01] svcSetHeapSize called (Requested size: 0x%llx)", (unsigned long long)cpuState.registers[1]);
                // Out[0]: Result, Out[1]: Address
                cpuState.registers[0] = 0; // Success
                cpuState.registers[1] = 0x120000000; // Mock Heap Base
                break;
            case 0x08: // svcCreateThread
                LOGI("Horizon OS: [SVC 0x08] svcCreateThread called (PC: 0x%llx)", (unsigned long long)cpuState.registers[1]);
                cpuState.registers[0] = 0; // Success
                cpuState.registers[1] = 0x55; // Mock Thread Handle
                break;
            case 0x0B: // svcSleepThread
                LOGI("Horizon OS: [SVC 0x0B] svcSleepThread called (Nanoseconds: %lld)", (long long)cpuState.registers[0]);
                cpuState.registers[0] = 0; // Success
                break;
            case 0x16: // svcCloseHandle
                LOGI("Horizon OS: [SVC 0x16] svcCloseHandle called (Handle: 0x%llx)", (unsigned long long)cpuState.registers[0]);
                cpuState.registers[0] = 0; // Success
                break;
            case 0x18: // svcGetSystemInfo
                LOGI("Horizon OS: [SVC 0x18] svcGetSystemInfo called (Type: %lld)", (long long)cpuState.registers[1]);
                cpuState.registers[0] = 0; // Success
                cpuState.registers[1] = 1024 * 1024 * 1024; // Return mock memory info
                break;
            case 0x21: // svcConnectToNamedPort
                LOGI("Horizon OS: [SVC 0x21] svcConnectToNamedPort called");
                cpuState.registers[0] = 0; // Success
                cpuState.registers[1] = 0x44; // Mock Session Handle
                break;
            case 0x22: // svcSendSyncRequest (IPC)
                LOGI("Horizon OS: [SVC 0x22] svcSendSyncRequest called (Handle: 0x%llx)", (unsigned long long)cpuState.registers[0]);
                // In a real HLE, we would parse the IPC command buffer here and route to services (nvdrv, vi, am, hid)
                cpuState.registers[0] = 0; // Success
                break;
            case 0x26: // svcBreak
                LOGE("Horizon OS: [SVC 0x26] svcBreak! Application triggered a debug break/crash.");
                cpuState.registers[0] = 0;
                break;
            default:
                LOGE("Horizon OS: Unknown or Unimplemented SVC 0x%02X called at PC: 0x%llx", svcCode, (unsigned long long)cpuState.pc);
                cpuState.registers[0] = 0xE001; // Mock error result
                break;
        }
    }
};

"""

# Insert HLE code right after the bp declaration
insert_pos = code.find("static BranchPredictor bp;") + len("static BranchPredictor bp;")
code = code[:insert_pos] + "\n\n" + hle_code + code[insert_pos:]

# Now modify ExecuteBranchesExceptionSystem to handle SVC (opcode 1101 0100)
find_svc = """        } else if (op0 == 0b010) { // Conditional branch (immediate) e.g., B.cond
            int32_t imm19 = (instr >> 5) & 0x7FFFF;"""
            
replace_svc = """        } else if (op0 == 0b100) { // Exception generation (SVC, HVC, SMC, BRK)
            uint32_t opc = (instr >> 21) & 0x7;
            uint32_t op2 = (instr >> 2) & 0x7;
            uint32_t ll = instr & 0x3;
            
            if (opc == 0b000 && op2 == 0b000 && ll == 0b01) { // SVC
                uint32_t imm16 = (instr >> 5) & 0xFFFF;
                HorizonOS::getInstance().HandleSVC(imm16);
                // PC continues to the next instruction after SVC
            } else {
                LOGE("Unhandled Exception Generation Instruction: 0x%08X", instr);
            }
        } else if (op0 == 0b010) { // Conditional branch (immediate) e.g., B.cond
            int32_t imm19 = (instr >> 5) & 0x7FFFF;"""

code = code.replace(find_svc, replace_svc)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)

