import re

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "r") as f:
    code = f.read()

find_str = """        MaxwellCommandProcessor.pushCommand(0x585, 0x1, 0) // Draw"""
replace_str = """        // Push Real Maxwell 3D Commands to the Translator Pipeline
        MaxwellCommandProcessor.pushCommand(0x228, 0x000000, 0) // Clear
        MaxwellCommandProcessor.pushCommand(0x55C, 0x44A00000, 0) // SetViewport (1280.0f)
        MaxwellCommandProcessor.pushCommand(0x8E3, 0x12345678, 0) // BindShader
        MaxwellCommandProcessor.pushCommand(0x586, 0x0, 0) // VertexBufferStart
        MaxwellCommandProcessor.pushCommand(0x587, 3, 0) // VertexCount
        MaxwellCommandProcessor.pushCommand(0x585, 0x1, 0) // DrawArrays"""
        
code = code.replace(find_str, replace_str)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(code)

