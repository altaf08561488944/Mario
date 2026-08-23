package com.example.emulator

/**
 * Real AArch64 / ARM64 CPU Interpreter Engine for Nintendo Switch Emulation.
 * Executes genuine 64-bit ARM64 machine instructions fetched from Guest Virtual Memory.
 */
class Arm64CpuCore(val coreId: Int) {

    // 64-bit General Purpose Registers X0 - X30
    private val x = LongArray(31)

    // Program Counter, Stack Pointer, and TLS Base
    var pc: Long = GuestMemory.CODE_BASE
    var sp: Long = GuestMemory.STACK_TOP
    var tlsBase: Long = GuestMemory.TLS_BASE

    // NZCV Condition Flags
    var flagN: Boolean = false
    var flagZ: Boolean = false
    var flagC: Boolean = false
    var flagV: Boolean = false

    var instructionsExecuted: Long = 0L
    var isHalted: Boolean = false

    var lastDisassembly: String = "NOP"
    var lastSvcLog: HorizonSvcLog? = null

    fun reset(startPc: Long = GuestMemory.CODE_BASE, initialSp: Long = GuestMemory.STACK_TOP, initialTlsBase: Long = GuestMemory.TLS_BASE) {
        for (i in x.indices) x[i] = 0L
        pc = startPc
        sp = initialSp
        tlsBase = initialTlsBase
        flagN = false
        flagZ = true
        flagC = false
        flagV = false
        instructionsExecuted = 0L
        isHalted = false
        lastDisassembly = "INIT_RESET"
        lastSvcLog = null
    }

    fun getX(regIndex: Int): Long {
        if (regIndex == 31) return 0L // XZR
        return if (regIndex in 0..30) x[regIndex] else 0L
    }

    fun setX(regIndex: Int, value: Long) {
        if (regIndex in 0..30) {
            x[regIndex] = value
        }
    }

    fun getNzcvString(): String {
        return "N:${if (flagN) 1 else 0} Z:${if (flagZ) 1 else 0} C:${if (flagC) 1 else 0} V:${if (flagV) 1 else 0}"
    }

    /**
     * Executes a single ARM64 instruction cycle.
     * Fetches 32-bit opcode from GuestMemory at PC, decodes, executes, and advances PC.
     */
    fun executeStep(memory: GuestMemory): HorizonSvcLog? {
        if (isHalted) return null

        val opcode = memory.read32(pc)
        val currentPc = pc
        pc += 4 // Default next instruction
        instructionsExecuted++

        val decoded = ArmDecoder.decode(opcode, currentPc)
        lastDisassembly = decoded.disassembly
        lastSvcLog = decoded.execute(this, memory, currentPc)

        return lastSvcLog
    }

    fun toCpuRegisterState(): CpuRegisterState {
        return CpuRegisterState(
            coreId = coreId,
            x0 = getX(0),
            x1 = getX(1),
            x2 = getX(2),
            x3 = getX(3),
            pc = pc,
            sp = sp,
            nzcv = getNzcvString(),
            instructionsExecuted = instructionsExecuted
        )
    }
}
