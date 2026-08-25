import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

old_engine = """    val jitEngine = com.example.emulator.cpu.JitExecutionEngine()"""
new_engine = """    val jitEngine = com.example.emulator.cpu.JitExecutionEngine(mmu)"""

content = content.replace(old_engine, new_engine)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

