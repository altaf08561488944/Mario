import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

old_props = """    val horizonOs = HorizonServiceManager() // HLE IPC Services
    val audioSubsystem = AudioSubsystem() // PCM Audio Mixing
    
    val gpu = TegraGpuEmulator()
    val keysManager = KeyManager()"""

new_props = """    val horizonOs = HorizonServiceManager() // HLE IPC Services
    val audioSubsystem = AudioSubsystem() // PCM Audio Mixing
    
    val gpu = TegraGpuEmulator()
    val keysManager = KeyManager()
    val firmwareManager = com.example.emulator.hle.FirmwareManager(keysManager) // NAND Firmware Manager"""

content = content.replace(old_props, new_props)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

