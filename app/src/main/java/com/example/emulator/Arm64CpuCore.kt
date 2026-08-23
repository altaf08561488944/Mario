package com.example.emulator

/**
 * Real AArch64 / ARM64 CPU Interpreter Engine for Nintendo Switch Emulation.
 * Executes genuine 64-bit ARM64 machine instructions fetched from Guest Virtual Memory.
 */
class Arm64CpuCore(val coreId: Int) {

    // 64-bit General Purpose Registers X0 - X30
    private val x = LongArray(31)

    // Program Counter & Stack Pointer
    var pc: Long = GuestMemory.CODE_BASE
    var sp: Long = GuestMemory.STACK_TOP

    // NZCV Condition Flags
    var flagN: Boolean = false
    var flagZ: Boolean = false
    var flagC: Boolean = false
    var flagV: Boolean = false

    var instructionsExecuted: Long = 0L
    var isHalted: Boolean = false

    var lastDisassembly: String = "NOP"
    var lastSvcLog: HorizonSvcLog? = null

    fun reset(startPc: Long = GuestMemory.CODE_BASE, initialSp: Long = GuestMemory.STACK_TOP) {
        for (i in x.indices) x[i] = 0L
        pc = startPc
        sp = initialSp
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

        lastSvcLog = null

        // Decode ARM64 instruction opcodes
        when {
            // NOP: 0xD503201F
            opcode == 0xD503201F.toInt() -> {
                lastDisassembly = "NOP"
            }

            // RET: 0xD65F03C0 (pc = X30)
            opcode == 0xD65F03C0.toInt() -> {
                val retAddr = getX(30)
                lastDisassembly = "RET X30 (0x${retAddr.toString(16).uppercase()})"
                if (retAddr != 0L) {
                    pc = retAddr
                } else {
                    isHalted = true
                }
            }

            // SVC instruction: 0xD4000001 .. 0xD403FFFF (Bits 31-21 == 11010100000)
            (opcode and 0xFFE0001F.toInt()) == 0xD4000001.toInt() || (opcode and 0xFFE00000.toInt()) == 0xD4000000.toInt() -> {
                val svcNum = (opcode ushr 5) and 0xFFFF
                lastDisassembly = "SVC #0x${svcNum.toString(16).padStart(2, '0').uppercase()}"
                lastSvcLog = HorizonKernelSvc.dispatchSvc(svcNum, this, memory)
            }

            // MOVZ Xd, #imm, LSL #shift (0xD2800000)
            (opcode and 0xFF800000.toInt()) == 0xD2800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                val shift = hw * 16
                val value = imm16.toLong() shl shift
                setX(rd, value)
                lastDisassembly = "MOV X$rd, #0x${value.toString(16).uppercase()}"
            }

            // MOVK Xd, #imm, LSL #shift (0xF2800000)
            (opcode and 0xFF800000.toInt()) == 0xF2800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                val shift = hw * 16
                val mask = (0xFFFFL shl shift).inv()
                val current = getX(rd) and mask
                val value = current or (imm16.toLong() shl shift)
                setX(rd, value)
                lastDisassembly = "MOVK X$rd, #0x${imm16.toString(16).uppercase()}, LSL #$shift"
            }

            // ADD Xd, Xn, #imm (0x91000000)
            (opcode and 0xFF000000.toInt()) == 0x91000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                val base = if (rn == 31) sp else getX(rn)
                val result = base + imm12
                if (rd == 31) sp = result else setX(rd, result)
                lastDisassembly = "ADD ${if (rd == 31) "SP" else "X$rd"}, ${if (rn == 31) "SP" else "X$rn"}, #$imm12"
            }

            // SUB Xd, Xn, #imm (0xD1000000)
            (opcode and 0xFF000000.toInt()) == 0xD1000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                val base = if (rn == 31) sp else getX(rn)
                val result = base - imm12
                if (rd == 31) sp = result else setX(rd, result)
                lastDisassembly = "SUB ${if (rd == 31) "SP" else "X$rd"}, ${if (rn == 31) "SP" else "X$rn"}, #$imm12"
            }

            // ADD Xd, Xn, Xm (0x8B000000)
            (opcode and 0xFF200000.toInt()) == 0x8B000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                val result = getX(rn) + getX(rm)
                setX(rd, result)
                lastDisassembly = "ADD X$rd, X$rn, X$rm"
            }

            // SUB / SUBS Xd, Xn, Xm (0xCB000000 / 0xEB000000)
            (opcode and 0xFF000000.toInt()) == 0xCB000000.toInt() || (opcode and 0xFF000000.toInt()) == 0xEB000000.toInt() -> {
                val isS = (opcode ushr 29) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                val valN = getX(rn)
                val valM = getX(rm)
                val result = valN - valM
                if (rd != 31) setX(rd, result)
                if (isS) {
                    flagN = result < 0
                    flagZ = result == 0L
                    flagC = valN >= valM
                    flagV = ((valN xor valM) < 0) && ((valN xor result) < 0)
                }
                lastDisassembly = if (isS && rd == 31) "CMP X$rn, X$rm" else "SUB${if (isS) "S" else ""} X$rd, X$rn, X$rm"
            }

            // ORR Xd, Xn, Xm (0xAA000000)
            (opcode and 0xFF200000.toInt()) == 0xAA000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                val valM = getX(rm)
                if (rn == 31) {
                    setX(rd, valM)
                    lastDisassembly = "MOV X$rd, X$rm"
                } else {
                    val result = getX(rn) or valM
                    setX(rd, result)
                    lastDisassembly = "ORR X$rd, X$rn, X$rm"
                }
            }

            // STR Xt, [Xn, #imm] (0xF9000000)
            (opcode and 0xFFC00000.toInt()) == 0xF9000000.toInt() -> {
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val pimm = (opcode ushr 10) and 0xFFF
                val offset = pimm * 8L
                val base = if (rn == 31) sp else getX(rn)
                val targetAddr = base + offset
                memory.write64(targetAddr, getX(rt))
                lastDisassembly = "STR X$rt, [${if (rn == 31) "SP" else "X$rn"}, #$offset]"
            }

            // LDR Xt, [Xn, #imm] (0xF9400000)
            (opcode and 0xFFC00000.toInt()) == 0xF9400000.toInt() -> {
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val pimm = (opcode ushr 10) and 0xFFF
                val offset = pimm * 8L
                val base = if (rn == 31) sp else getX(rn)
                val targetAddr = base + offset
                val loadedVal = memory.read64(targetAddr)
                setX(rt, loadedVal)
                lastDisassembly = "LDR X$rt, [${if (rn == 31) "SP" else "X$rn"}, #$offset]"
            }

            // STP X1, X2, [Xn, #imm]! (0xA9000000 / 0xA9800000)
            (opcode and 0xFFC00000.toInt()) == 0xA9000000.toInt() || (opcode and 0xFFC00000.toInt()) == 0xA9800000.toInt() -> {
                val rt1 = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rt2 = (opcode ushr 10) and 0x1F
                val imm7 = (opcode ushr 15) and 0x7F
                val signedOffset = (if (imm7 >= 64) imm7 - 128 else imm7) * 8L
                var base = if (rn == 31) sp else getX(rn)
                base += signedOffset
                if (rn == 31) sp = base else setX(rn, base)
                memory.write64(base, getX(rt1))
                memory.write64(base + 8, getX(rt2))
                lastDisassembly = "STP X$rt1, X$rt2, [${if (rn == 31) "SP" else "X$rn"}, #$signedOffset]!"
            }

            // LDP X1, X2, [Xn, #imm]! (0xA8C00000 / 0xA8400000)
            (opcode and 0xFFC00000.toInt()) == 0xA8C00000.toInt() || (opcode and 0xFFC00000.toInt()) == 0xA8400000.toInt() -> {
                val rt1 = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rt2 = (opcode ushr 10) and 0x1F
                val imm7 = (opcode ushr 15) and 0x7F
                val signedOffset = (if (imm7 >= 64) imm7 - 128 else imm7) * 8L
                val base = if (rn == 31) sp else getX(rn)
                val val1 = memory.read64(base)
                val val2 = memory.read64(base + 8)
                setX(rt1, val1)
                setX(rt2, val2)
                val newBase = base + signedOffset
                if (rn == 31) sp = newBase else setX(rn, newBase)
                lastDisassembly = "LDP X$rt1, X$rt2, [${if (rn == 31) "SP" else "X$rn"}, #$signedOffset]"
            }

            // B imm26 (0x14000000)
            (opcode and 0xFC000000.toInt()) == 0x14000000.toInt() -> {
                val imm26 = opcode and 0x03FFFFFF
                val offset = (if (imm26 >= 0x02000000) imm26 - 0x04000000 else imm26) * 4L
                val target = currentPc + offset
                pc = target
                lastDisassembly = "B 0x${target.toString(16).uppercase()}"
            }

            // BL imm26 (0x94000000)
            (opcode and 0xFC000000.toInt()) == 0x94000000.toInt() -> {
                val imm26 = opcode and 0x03FFFFFF
                val offset = (if (imm26 >= 0x02000000) imm26 - 0x04000000 else imm26) * 4L
                setX(30, currentPc + 4) // Link Register X30
                val target = currentPc + offset
                pc = target
                lastDisassembly = "BL 0x${target.toString(16).uppercase()}"
            }

            // BLR Xn (0xD63F0000)
            (opcode and 0xFFFFFC1F.toInt()) == 0xD63F0000.toInt() -> {
                val rn = (opcode ushr 5) and 0x1F
                val target = getX(rn)
                setX(30, currentPc + 4)
                pc = target
                lastDisassembly = "BLR X$rn (0x${target.toString(16).uppercase()})"
            }

            // CBZ / CBNZ Xt, label (0xB4000000 / 0xB5000000)
            (opcode and 0x7E000000.toInt()) == 0x34000000.toInt() -> {
                val isCbnz = (opcode ushr 24) and 0x01 == 1
                val rt = opcode and 0x1F
                val imm19 = (opcode ushr 5) and 0x7FFFF
                val offset = (if (imm19 >= 0x40000) imm19 - 0x80000 else imm19) * 4L
                val valT = getX(rt)
                val condition = if (isCbnz) valT != 0L else valT == 0L
                if (condition) {
                    pc = currentPc + offset
                }
                lastDisassembly = "${if (isCbnz) "CBNZ" else "CBZ"} X$rt, 0x${(currentPc + offset).toString(16).uppercase()}"
            }

            // B.cond label (0x54000000)
            (opcode and 0xFF000010.toInt()) == 0x54000000.toInt() -> {
                val cond = opcode and 0x0F
                val imm19 = (opcode ushr 5) and 0x7FFFF
                val offset = (if (imm19 >= 0x40000) imm19 - 0x80000 else imm19) * 4L
                val conditionMet = when (cond) {
                    0 -> flagZ // EQ
                    1 -> !flagZ // NE
                    2 -> flagC // CS/HS
                    3 -> !flagC // CC/LO
                    10 -> flagN == flagV // GE
                    11 -> flagN != flagV // LT
                    12 -> !flagZ && (flagN == flagV) // GT
                    13 -> flagZ || (flagN != flagV) // LE
                    else -> true
                }
                if (conditionMet) {
                    pc = currentPc + offset
                }
                val condName = when (cond) { 0 -> "EQ"; 1 -> "NE"; 10 -> "GE"; 11 -> "LT"; else -> "COND" }
                lastDisassembly = "B.$condName 0x${(currentPc + offset).toString(16).uppercase()}"
            }

            else -> {
                // Fallback / Unknown opcode: Log instruction and increment PC
                lastDisassembly = "RAW_ARM64 [0x${opcode.toUInt().toString(16).padStart(8, '0').uppercase()}]"
            }
        }

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
