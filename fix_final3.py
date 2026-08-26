with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

import re
code = re.sub(
    r'return JNI_TRUE;\s*extern "C" JNIEXPORT jboolean JNICALL\s*Java_com_example_emulator_cpu_NativeCpuCore_nativeLoadNsp',
    r'return JNI_TRUE;\n}\n\nextern "C" JNIEXPORT jboolean JNICALL\nJava_com_example_emulator_cpu_NativeCpuCore_nativeLoadNsp',
    code
)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)
