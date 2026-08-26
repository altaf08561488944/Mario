import re

with open("app/src/main/java/com/example/data/repository/SwtcRepository.kt", "r") as f:
    code = f.read()

import_str = "import com.example.storage.VirtualStorageStats\nimport com.example.storage.LibraryScannerService"
code = code.replace("import com.example.storage.VirtualStorageStats", import_str)

scanner_inst = "private val supProcessor = SupContainerProcessor(context)\n    private val libraryScanner = LibraryScannerService(context, dao)"
code = code.replace("private val supProcessor = SupContainerProcessor(context)", scanner_inst)

scan_method = """    suspend fun scanAndPopulateLibrary(): Int = withContext(Dispatchers.IO) {
        libraryScanner.scanAndPopulateLibrary()
    }

    fun getVirtualStorageStats"""
code = code.replace("    fun getVirtualStorageStats", scan_method)

with open("app/src/main/java/com/example/data/repository/SwtcRepository.kt", "w") as f:
    f.write(code)
