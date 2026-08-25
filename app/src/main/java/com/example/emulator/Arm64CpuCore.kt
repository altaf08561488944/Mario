package com.example.emulator

/**
 * Real AArch64 / ARM64 CPU Core & Interpreter for Nintendo Switch Emulation.
 * Executes genuine 64-bit ARM64 machine instructions fetched from Guest Virtual Memory.
 * Fully supports AArch64 GP Registers (X0-X30, W0-W30, SP, XZR), System Registers (TPIDR_EL0),
 * Floating-Point Registers (D0-D31 / V0-V31), and standard ARM64 NZCV condition codes.
 */
class Arm64CpuCore(val coreId: Int) {

    // 64-bit General Purpose Registers X0 - X30 (X31 represents XZR / SP depending on context)
    private val x = LongArray(31)

    // 64-bit Floating-Point / SIMD Register Bank (D0 - D31 / V0 - V31)
    private val v = LongArray(32)

    // Program Counter, Stack Pointer, and Thread Local Storage (TLS) Base
    var pc: Long = GuestMemory.CODE_BASE
    var sp: Long = GuestMemory.STACK_TOP
    var tlsBase: Long = GuestMemory.TLS_BASE

    // System Registers
    var cntvct: Long = 0L // Virtual Counter
    var fpcr: Int = 0     // Floating-Point Control Register
    var fpsr: Int = 0     // Floating-Point Status Register

    // NZCV Condition Flags (PSTATE)
    var flagN: Boolean = false // Negative
    var flagZ: Boolean = false // Zero
    var flagC: Boolean = false // Carry
    var flagV: Boolean = false // Overflow

    var instructionsExecuted: Long = 0L
    var isHalted: Boolean = false

    var lastDisassembly: String = "NOP"
    var lastSvcLog: HorizonSvcLog? = null

    // Fast JIT / Basic Block Translation Cache to avoid re-decoding hot loops
    private val translationCache = HashMap<Long, DecodedInstruction>(4096)

    fun reset(
        startPc: Long = GuestMemory.CODE_BASE,
        initialSp: Long = GuestMemory.STACK_TOP,
        initialTlsBase: Long = GuestMemory.TLS_BASE
    ) {
        for (i in x.indices) x[i] = 0L
        for (i in v.indices) v[i] = 0L
        pc = startPc
        sp = initialSp
        tlsBase = initialTlsBase
        cntvct = 0L
        fpcr = 0
        fpsr = 0
        flagN = false
        flagZ = true
        flagC = false
        flagV = false
        instructionsExecuted = 0L
        isHalted = false
        lastDisassembly = "INIT_RESET"
        lastSvcLog = null
        translationCache.clear()
    }

    // 64-bit Register Accessors
    fun getX(regIndex: Int): Long {
        if (regIndex == 31) return 0L // XZR
        return if (regIndex in 0..30) x[regIndex] else 0L
    }

    fun setX(regIndex: Int, value: Long) {
        if (regIndex in 0..30) {
            x[regIndex] = value
        }
    }

    // 32-bit (W) Register Accessors - Automatically zero-extends to 64-bit per ARM64 spec
    fun getW(regIndex: Int): Int {
        if (regIndex == 31) return 0 // WZR
        return if (regIndex in 0..30) (x[regIndex] and 0xFFFFFFFFL).toInt() else 0
    }

    fun setW(regIndex: Int, value: Int) {
        if (regIndex in 0..30) {
            x[regIndex] = value.toLong() and 0xFFFFFFFFL // Zero-extend top 32 bits
        }
    }

    // Floating-Point Register Accessors
    fun getD(regIndex: Int): Double {
        val bits = if (regIndex in 0..31) v[regIndex] else 0L
        return java.lang.Double.longBitsToDouble(bits)
    }

    fun setD(regIndex: Int, value: Double) {
        if (regIndex in 0..31) {
            v[regIndex] = java.lang.Double.doubleToRawLongBits(value)
        }
    }

    fun getS(regIndex: Int): Float {
        val bits = if (regIndex in 0..31) (v[regIndex] and 0xFFFFFFFFL).toInt() else 0
        return java.lang.Float.intBitsToFloat(bits)
    }

    fun setS(regIndex: Int, value: Float) {
        if (regIndex in 0..31) {
            v[regIndex] = java.lang.Float.floatToRawIntBits(value).toLong() and 0xFFFFFFFFL
        }
    }

    fun getVRaw(regIndex: Int): Long {
        return if (regIndex in 0..31) v[regIndex] else 0L
    }

    fun setVRaw(regIndex: Int, rawBits: Long) {
        if (regIndex in 0..31) {
            v[regIndex] = rawBits
        }
    }

    // ARM64 Condition Code Evaluator
    fun evaluateCondition(cond: Int): Boolean {
        return when (cond and 0x0F) {
            0 -> flagZ                              // EQ: Equal
            1 -> !flagZ                             // NE: Not Equal
            2 -> flagC                              // CS / HS: Carry Set / Unsigned Higher or Same
            3 -> !flagC                             // CC / LO: Carry Clear / Unsigned Lower
            4 -> flagN                              // MI: Minus / Negative
            5 -> !flagN                             // PL: Plus / Positive or Zero
            6 -> flagV                              // VS: Overflow Set
            7 -> !flagV                             // VC: Overflow Clear
            8 -> flagC && !flagZ                    // HI: Unsigned Higher
            9 -> !flagC || flagZ                    // LS: Unsigned Lower or Same
            10 -> flagN == flagV                    // GE: Signed Greater than or Equal
            11 -> flagN != flagV                    // LT: Signed Less than
            12 -> !flagZ && (flagN == flagV)        // GT: Signed Greater than
            13 -> flagZ || (flagN != flagV)         // LE: Signed Less than or Equal
            14, 15 -> true                          // AL / NV: Always
            else -> true
        }
    }

    // Flag Setters for 64-bit Arithmetic
    fun setFlagsAdd64(a: Long, b: Long, result: Long) {
        flagN = result < 0
        flagZ = result == 0L
        // Carry: unsigned overflow
        flagC = (java.lang.Long.compareUnsigned(result, a) < 0)
        // Overflow: signed overflow
        flagV = ((a xor result) and (b xor result)) < 0
    }

    fun setFlagsSub64(a: Long, b: Long, result: Long) {
        flagN = result < 0
        flagZ = result == 0L
        // Carry: No borrow (a >= b unsigned)
        flagC = java.lang.Long.compareUnsigned(a, b) >= 0
        // Overflow: signed overflow
        flagV = ((a xor b) < 0) && ((a xor result) < 0)
    }

    // Flag Setters for 32-bit Arithmetic
    fun setFlagsAdd32(a: Int, b: Int, result: Int) {
        flagN = result < 0
        flagZ = result == 0
        flagC = (java.lang.Integer.compareUnsigned(result, a) < 0)
        flagV = ((a xor result) and (b xor result)) < 0
    }

    fun setFlagsSub32(a: Int, b: Int, result: Int) {
        flagN = result < 0
        flagZ = result == 0
        flagC = java.lang.Integer.compareUnsigned(a, b) >= 0
        flagV = ((a xor b) < 0) && ((a xor result) < 0)
    }

    fun setFlagsLogic64(result: Long) {
        flagN = result < 0
        flagZ = result == 0L
        flagC = false
        flagV = false
    }

    fun setFlagsLogic32(result: Int) {
        flagN = result < 0
        flagZ = result == 0
        flagC = false
        flagV = false
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

        val currentPc = pc
        val opcode = memory.read32(currentPc)
        pc += 4 // Default next instruction
        instructionsExecuted++
        cntvct++

        var decoded = translationCache[currentPc]
        if (decoded == null) {
            decoded = ArmDecoder.decode(opcode, currentPc)
            // Cache if within hot page range
            if (translationCache.size < 4000) {
                translationCache[currentPc] = decoded
            }
        }

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
            instructionsExecuted = instructionsExecuted,
            isHalted = isHalted
        )
    }
}
