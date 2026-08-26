import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

# Fix the newline issue in the string literals
code = code.replace('keyName.erase(keyName.find_last_not_of("\n', 'keyName.erase(keyName.find_last_not_of(" \\n\\r\\t")+1);')
code = code.replace('")+1);', '')
code = code.replace('keyName.erase(0, keyName.find_first_not_of("\n', 'keyName.erase(0, keyName.find_first_not_of(" \\n\\r\\t"));')
code = code.replace('"));\n', '')
code = code.replace('keyHex.erase(keyHex.find_last_not_of("\n', 'keyHex.erase(keyHex.find_last_not_of(" \\n\\r\\t")+1);')
code = code.replace('keyHex.erase(0, keyHex.find_first_not_of("\n', 'keyHex.erase(0, keyHex.find_first_not_of(" \\n\\r\\t"));')

# Fix includes
if "#include <fstream>" not in code:
    code = "#include <fstream>\n" + code

# Fix HorizonOS redefinition
# Let's find all instances of "class HorizonOS {"
horizon_os_blocks = [m.start() for m in re.finditer(r'class HorizonOS \{', code)]
if len(horizon_os_blocks) > 1:
    # Cut out the first one
    start = horizon_os_blocks[0]
    end = code.find("};", start) + 2
    code = code[:start] + code[end:]

# Fix extra closing brace at the end if present
if code.endswith("}\n}") or code.endswith("}}"):
    # This was reported: extraneous closing brace ('}') at line 662
    lines = code.split("\n")
    if lines[-1].strip() == "}":
        lines = lines[:-1]
    elif lines[-2].strip() == "}":
        lines.pop(-2)
    code = "\n".join(lines)

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)
