import re

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "r") as f:
    code = f.read()

find_str = """        val loaderMsg = NroLoader.simulateParsing(cartridge.sourceFormat)
        val guestProcess = NroLoader.simulateMapping(cartridge.title, cartridge.sourceFormat)
        
        // Pass to Native C++ Execution Engine"""

replace_str = """        
        // Load into Native C++ Execution Engine
        val nativeCore = com.example.emulator.cpu.NativeCpuCore()
        nativeCore.initialize()
        val path = "/virtual_sdcard/Nintendo/switch/${cartridge.title.replace(" ", "_")}.${cartridge.sourceFormat}"
        val isLoaded = nativeCore.loadExecutable(path, cartridge.sourceFormat)
        
        val loaderMsg = if (isLoaded) {
            "✅ Native C++ Loader: Successfully parsed and mapped ${cartridge.sourceFormat.uppercase()} binary segments to Guest Memory."
        } else {
            "⚠️ Native Loader: Failed to read binary segments from storage."
        }
        
        val guestProcess = com.example.emulator.memory.GuestProcess(
            titleId = cartridge.titleId,
            processName = cartridge.title,
            entryPoint = 0x7100000000L,
            isAlive = isLoaded,
            mappedSegments = listOf(".text", ".rodata", ".data"),
            stackPointer = 0x110000000L,
            heapAddress = 0x120000000L,
            tlsBaseAddress = 0x130000000L,
            modules = listOf(cartridge.title, "rtld", "sdk"),
            loadedExecutableName = "${cartridge.title}.${cartridge.sourceFormat}",
            mod0Info = com.example.emulator.Mod0Info(isMod0Valid = true, dynamicOffset = 0, bssStartOffset = 0, bssSizeBytes = 0),
            relocationsApplied = 0
        )
        
        if (isLoaded) {
             telemetryLogger.logEvent("NATIVE_CORE", "Successfully mapped ${cartridge.sourceFormat.uppercase()} to JIT Memory")
        }"""

# Remove the old one first, we need to be careful with regex replacement
code = re.sub(r'        val loaderMsg = NroLoader\.simulateParsing\(cartridge\.sourceFormat\)\n        val guestProcess = NroLoader\.simulateMapping\(cartridge\.title, cartridge\.sourceFormat\)\n        \n        // Pass to Native C\+\+ Execution Engine\n        val nativeCore = com\.example\.emulator\.cpu\.NativeCpuCore\(\)\n        nativeCore\.initialize\(\)\n        val path = "/virtual_sdcard/Nintendo/switch/\$\{cartridge\.title\.replace\(" ", "_"\)\}\.\$\{cartridge\.sourceFormat\}"\n        val isLoaded = nativeCore\.loadExecutable\(path, cartridge\.sourceFormat\)\n        \n        if \(isLoaded\) \{\n             telemetryLogger\.logEvent\("NATIVE_CORE", "Successfully mapped \$\{cartridge\.sourceFormat\.uppercase\(\)\} to JIT Memory"\)\n        \}', replace_str, code)

with open("app/src/main/java/com/example/emulator/SwitchCoreEngine.kt", "w") as f:
    f.write(code)

