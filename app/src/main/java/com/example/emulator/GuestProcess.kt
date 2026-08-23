package com.example.emulator

data class Mod0Info(
    val isMod0Valid: Boolean = false,
    val dynamicOffset: Int = 0,
    val bssStartOffset: Int = 0,
    val bssEndOffset: Int = 0,
    val bssSizeBytes: Int = 0,
    val ehFrameHdrStart: Int = 0,
    val ehFrameHdrEnd: Int = 0,
    val moduleObjectOffset: Int = 0
)

data class GuestProcess(
    val titleId: String,
    val processName: String,
    val entryPoint: Long,
    val isAlive: Boolean,
    val mappedSegments: List<String>,
    val stackPointer: Long,
    val heapAddress: Long,
    val tlsBaseAddress: Long = 0x7100080000L,
    val modules: List<String>,
    val loadedExecutableName: String,
    val mod0Info: Mod0Info = Mod0Info(),
    val relocationsApplied: Int = 0
)

