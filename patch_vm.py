import re

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "r") as f:
    code = f.read()

# Add a function to trigger the scan
scan_func = """
    fun scanDeviceForCartridges() {
        viewModelScope.launch {
            val count = repository.scanAndPopulateLibrary()
            if (count > 0) {
                showUserMessage("Scan complete: Found and added $count new cartridges.")
            } else {
                showUserMessage("Scan complete: No new games found.")
            }
        }
    }

    fun launchCartridge(cartridge: VirtualCartridgeEntity) {"""
    
code = code.replace("    fun launchCartridge(cartridge: VirtualCartridgeEntity) {", scan_func)

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.write(code)
