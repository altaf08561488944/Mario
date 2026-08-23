package com.example.emulator

enum class GameLifecycleState(val displayName: String) {
    FILE_SELECTED("File Selected"),
    VALIDATING("Validating File Structure & Signatures"),
    PARSING_CONTAINER("Parsing Cartridge Container (XCI/NSP)"),
    LOCATING_CONTENT("Locating Program Content & NCA Entries"),
    LOADING_EXECUTABLE("Decrypting & Mapping Executable (NSO/NRO)"),
    CREATING_PROCESS("Creating Guest Process & Address Space"),
    INITIALIZING_RUNTIME("Initializing Horizon Kernel & IPC Services"),
    EXECUTING("ARM64 Core Executing Guest Code"),
    FIRST_FRAME("First Guest Frame Submitted"),
    PLAYABLE("Game Executing & Playable"),
    FAILED("Game Load Failed")
}
