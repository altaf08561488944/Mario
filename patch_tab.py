import sys

content = open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt").read()

old_enum = """enum class SwtcTab {
    BOOT_SETUP,
    VIRTUAL_STORAGE,
    MY_FOLDER,
    CARTRIDGE_LIBRARY,
    WEB_ENVIRONMENT,
    HARDWARE_MONITOR
}"""
new_enum = """enum class SwtcTab {
    BOOT_SETUP,
    VIRTUAL_STORAGE,
    MY_FOLDER,
    CARTRIDGE_LIBRARY,
    WEB_ENVIRONMENT,
    HARDWARE_MONITOR,
    SETTINGS
}"""

content = content.replace(old_enum, new_enum)

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.write(content)

