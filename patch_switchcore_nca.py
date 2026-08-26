import re

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "r") as f:
    code = f.read()

find_str = """        val loaderMsg = if (isLoaded) {
            "✅ Native C++ Loader: Successfully parsed and mapped ${cartridge.sourceFormat.uppercase()} binary segments to Guest Memory."
        } else {
            "⚠️ Native Loader: Failed to read binary segments from storage."
        }"""
        
replace_str = """        val loaderMsg = if (isLoaded) {
            "✅ Native C++ Loader: Successfully decrypted NCA and mapped ${cartridge.sourceFormat.uppercase()} ExeFS to Guest Memory."
        } else {
            if (cartridge.sourceFormat.uppercase() == "NSP") {
                "🛑 Native Loader Error: NCA Decryption Failed. Valid 'prod.keys' required in Configs folder to decrypt commercial games."
            } else {
                "⚠️ Native Loader: Failed to read binary segments from storage."
            }
        }"""
        
code = code.replace(find_str, replace_str)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(code)

