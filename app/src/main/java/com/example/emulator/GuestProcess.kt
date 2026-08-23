package com.example.emulator

data class GuestProcess(
    val titleId: String,
    val processName: String,
    val entryPoint: Long,
    val isAlive: Boolean,
    val mappedSegments: List<String>,
    val stackPointer: Long,
    val heapAddress: Long,
    val modules: List<String>,
    val loadedExecutableName: String
)
