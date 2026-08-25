import sys

content = open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt").read()

old_audio = "val audioSubsystem = com.example.emulator.audio.AudioSubsystem() // PCM Audio Mixing"

new_audio = """val audioSubsystem = com.example.emulator.audio.AudioSubsystem() // PCM Audio Mixing
    
    init {
        horizonOs.initialize(mmu)
        audioSubsystem.initialize()
    }"""

content = content.replace("""    init {
        horizonOs.initialize(mmu)
    }""", "")
content = content.replace(old_audio, new_audio)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(content)

