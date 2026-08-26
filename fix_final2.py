with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

import re
code = re.sub(
    r'return 0; // Return code 0 = Success / Yield to OS\s*#include <vector>',
    r'return 0; // Return code 0 = Success / Yield to OS\n}\n\n#include <vector>',
    code
)
# Just in case #include <vector> is not there directly
code = re.sub(
    r'return 0; // Return code 0 = Success / Yield to OS\s*// Real NRO / NSP',
    r'return 0; // Return code 0 = Success / Yield to OS\n}\n\n// Real NRO / NSP',
    code
)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)

