with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

code = code.replace(
    'LOGI("C++ CPU Engine Initialized. Guest PC set to 0x%llx", (unsigned long long)cpuState.pc);\nextern "C" JNIEXPORT jint JNICALL',
    'LOGI("C++ CPU Engine Initialized. Guest PC set to 0x%llx", (unsigned long long)cpuState.pc);\n}\n\nextern "C" JNIEXPORT jint JNICALL'
)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)
