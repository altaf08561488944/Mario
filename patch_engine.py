import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

old_engine = """    val horizonOs = com.example.emulator.hle.HorizonServiceManager() // HLE IPC Services"""

new_engine = """    val horizonOs = com.example.emulator.hle.HorizonServiceManager() // HLE IPC Services
    
    init {
        horizonOs.initialize(mmu)
    }"""

content = content.replace(old_engine, new_engine)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

