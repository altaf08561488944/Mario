import sys

content = open("app/src/main/java/com/example/ui/components/SwtcNavBar.kt").read()

content = content.replace("import androidx.compose.material.icons.filled.SdStorage", "import androidx.compose.material.icons.filled.SdStorage\nimport androidx.compose.material.icons.filled.Settings")

old_items = """        NavTabItem(SwtcTab.WEB_ENVIRONMENT, "Web", Icons.Default.Public),
        NavTabItem(SwtcTab.HARDWARE_MONITOR, "Hardware", Icons.Default.Hardware)
    )"""
new_items = """        NavTabItem(SwtcTab.WEB_ENVIRONMENT, "Web", Icons.Default.Public),
        NavTabItem(SwtcTab.HARDWARE_MONITOR, "Hardware", Icons.Default.Hardware),
        NavTabItem(SwtcTab.SETTINGS, "Settings", Icons.Default.Settings)
    )"""
content = content.replace(old_items, new_items)

with open("app/src/main/java/com/example/ui/components/SwtcNavBar.kt", "w") as f:
    f.write(content)

