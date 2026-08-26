with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

if "#include <fstream>" not in code:
    code = "#include <fstream>\n" + code

# Fix missing brace on nativeInitialize
# It looks like:
# LOGI("C++ CPU Engine Initialized. Guest PC set to 0x%llx", (unsigned long long)cpuState.pc);
# extern "C" JNIEXPORT jint JNICALL
import re
code = re.sub(
    r'LOGI\("C\+\+ CPU Engine Initialized\. Guest PC set to 0x%llx", \(unsigned long long\)cpuState\.pc\);\s*extern "C" JNIEXPORT jint JNICALL',
    r'LOGI("C++ CPU Engine Initialized. Guest PC set to 0x%llx", (unsigned long long)cpuState.pc);\n}\n\nextern "C" JNIEXPORT jint JNICALL',
    code
)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)

