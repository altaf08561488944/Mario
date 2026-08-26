import re

with open("app/src/main/java/com/example/ui/screens/CartridgeLibraryScreen.kt", "r") as f:
    code = f.read()

# Add imports
imports = """import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.PaddingValues"""
code = code.replace("import androidx.compose.material.icons.filled.Save", "import androidx.compose.material.icons.filled.Save\n" + imports)

# Add onScanDevice param properly
code = code.replace("    onGoToSaveStates: (() -> Unit)? = null\n) {", "    onGoToSaveStates: (() -> Unit)? = null,\n    onScanDevice: () -> Unit = {}\n) {")

with open("app/src/main/java/com/example/ui/screens/CartridgeLibraryScreen.kt", "w") as f:
    f.write(code)
