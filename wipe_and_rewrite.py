import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if '")+1);' in line or '"));' in line:
        continue
    new_lines.append(line)

code = "".join(new_lines)
if "#include <fstream>" not in code:
    code = "#include <fstream>\n" + code

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)

