import sys

content = open("app/src/main/java/com/example/emulator/hle/FirmwareManager.kt").read()

old_1 = """class FirmwareManager(private val keyManager: KeyManager) {"""
new_1 = """import com.example.emulator.SwitchKeysManager

class FirmwareManager {"""

old_2 = """        // Step 1: Validate Keys
        val isKeysValid = keyManager.validateKeys(keysFilePath)"""
new_2 = """        // Step 1: Validate Keys
        val result = SwitchKeysManager.loadKeysFromFile(File(keysFilePath))
        val isKeysValid = SwitchKeysManager.getKeySet().isLoaded"""

content = content.replace(old_1, new_1)
content = content.replace(old_2, new_2)

with open("app/src/main/java/com/example/emulator/hle/FirmwareManager.kt", "w") as f:
    f.write(content)

