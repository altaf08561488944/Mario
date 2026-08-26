import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

find_str = """                                    SwtcTab.CARTRIDGE_LIBRARY -> CartridgeLibraryScreen(
                                        cartridges = cartridges,
                                        onLaunchCartridge = { cartridge -> viewModel.launchCartridge(cartridge) },
                                        onDeleteCartridge = { id -> viewModel.deleteCartridge(id) },
                                        onGoToMyFolder = { viewModel.selectTab(SwtcTab.MY_FOLDER) },
                                        onGoToSaveStates = { viewModel.selectTab(SwtcTab.SAVE_STATES) }
                                    )"""

replace_str = """                                    SwtcTab.CARTRIDGE_LIBRARY -> CartridgeLibraryScreen(
                                        cartridges = cartridges,
                                        onLaunchCartridge = { cartridge -> viewModel.launchCartridge(cartridge) },
                                        onDeleteCartridge = { id -> viewModel.deleteCartridge(id) },
                                        onGoToMyFolder = { viewModel.selectTab(SwtcTab.MY_FOLDER) },
                                        onGoToSaveStates = { viewModel.selectTab(SwtcTab.SAVE_STATES) },
                                        onScanDevice = { viewModel.scanDeviceForCartridges() }
                                    )"""

code = code.replace(find_str, replace_str)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)
