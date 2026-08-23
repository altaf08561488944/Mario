package com.example.emulator

/**
 * Real ARM64 (AArch64) Instruction Decoder.
 * Uses strict bitwise operations (masks, bit shifts, and sign extensions)
 * to parse 32-bit machine opcodes into executable instruction actions.
 */
sealed class DecodedInstruction {
    abstract val disassembly: String
    abstract fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog?

    object Nop : DecodedInstruction() {
        override val disassembly: String = "NOP"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? = null
    }

    data class Ret(val rn: Int) : DecodedInstruction() {
        override val disassembly: String = "RET X$rn"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val retAddr = cpu.getX(rn)
            if (retAddr != 0L) {
                cpu.pc = retAddr
            } else {
                cpu.isHalted = true
            }
            return null
        }
    }

    data class Svc(val svcNumber: Int) : DecodedInstruction() {
        override val disassembly: String = "SVC #0x${svcNumber.toString(16).padStart(2, '0').uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog {
            return HorizonKernelSvc.dispatchSvc(svcNumber, cpu, memory)
        }
    }

    data class Movz(val rd: Int, val imm16: Int, val shift: Int) : DecodedInstruction() {
        val value: Long = imm16.toLong() shl shift
        override val disassembly: String = "MOV X$rd, #0x${value.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            cpu.setX(rd, value)
            return null
        }
    }

    data class Movk(val rd: Int, val imm16: Int, val shift: Int) : DecodedInstruction() {
        override val disassembly: String = "MOVK X$rd, #0x${imm16.toString(16).uppercase()}, LSL #$shift"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val mask = (0xFFFFL shl shift).inv()
            val current = cpu.getX(rd) and mask
            val value = current or (imm16.toLong() shl shift)
            cpu.setX(rd, value)
            return null
        }
    }

    data class AddImm(val rd: Int, val rn: Int, val imm12: Int) : DecodedInstruction() {
        override val disassembly: String = "ADD ${if (rd == 31) "SP" else "X$rd"}, ${if (rn == 31) "SP" else "X$rn"}, #$imm12"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val result = base + imm12
            if (rd == 31) cpu.sp = result else cpu.setX(rd, result)
            return null
        }
    }

    data class SubImm(val rd: Int, val rn: Int, val imm12: Int) : DecodedInstruction() {
        override val disassembly: String = "SUB ${if (rd == 31) "SP" else "X$rd"}, ${if (rn == 31) "SP" else "X$rn"}, #$imm12"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val result = base - imm12
            if (rd == 31) cpu.sp = result else cpu.setX(rd, result)
            return null
        }
    }

    data class AddReg(val rd: Int, val rn: Int, val rm: Int) : DecodedInstruction() {
        override val disassembly: String = "ADD X$rd, X$rn, X$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val result = cpu.getX(rn) + cpu.getX(rm)
            cpu.setX(rd, result)
            return null
        }
    }

    data class SubReg(val rd: Int, val rn: Int, val rm: Int, val isSetFlags: Boolean) : DecodedInstruction() {
        override val disassembly: String = if (isSetFlags && rd == 31) "CMP X$rn, X$rm" else "SUB${if (isSetFlags) "S" else ""} X$rd, X$rn, X$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val valN = cpu.getX(rn)
            val valM = cpu.getX(rm)
            val result = valN - valM
            if (rd != 31) cpu.setX(rd, result)
            if (isSetFlags) {
                cpu.flagN = result < 0
                cpu.flagZ = result == 0L
                cpu.flagC = valN >= valM
                cpu.flagV = ((valN xor valM) < 0) && ((valN xor result) < 0)
            }
            return null
        }
    }

    data class OrrReg(val rd: Int, val rn: Int, val rm: Int) : DecodedInstruction() {
        override val disassembly: String = if (rn == 31) "MOV X$rd, X$rm" else "ORR X$rd, X$rn, X$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val valM = cpu.getX(rm)
            if (rn == 31) {
                cpu.setX(rd, valM)
            } else {
                cpu.setX(rd, cpu.getX(rn) or valM)
            }
            return null
        }
    }

    data class StrImm(val rt: Int, val rn: Int, val offset: Long) : DecodedInstruction() {
        override val disassembly: String = "STR X$rt, [${if (rn == 31) "SP" else "X$rn"}, #$offset]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            memory.write64(base + offset, cpu.getX(rt))
            return null
        }
    }

    data class LdrImm(val rt: Int, val rn: Int, val offset: Long) : DecodedInstruction() {
        override val disassembly: String = "LDR X$rt, [${if (rn == 31) "SP" else "X$rn"}, #$offset]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val loadedVal = memory.read64(base + offset)
            cpu.setX(rt, loadedVal)
            return null
        }
    }

    data class StpImm(val rt1: Int, val rt2: Int, val rn: Int, val signedOffset: Long) : DecodedInstruction() {
        override val disassembly: String = "STP X$rt1, X$rt2, [${if (rn == 31) "SP" else "X$rn"}, #$signedOffset]!"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            var base = if (rn == 31) cpu.sp else cpu.getX(rn)
            base += signedOffset
            if (rn == 31) cpu.sp = base else cpu.setX(rn, base)
            memory.write64(base, cpu.getX(rt1))
            memory.write64(base + 8, cpu.getX(rt2))
            return null
        }
    }

    data class LdpImm(val rt1: Int, val rt2: Int, val rn: Int, val signedOffset: Long) : DecodedInstruction() {
        override val disassembly: String = "LDP X$rt1, X$rt2, [${if (rn == 31) "SP" else "X$rn"}, #$signedOffset]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val val1 = memory.read64(base)
            val val2 = memory.read64(base + 8)
            cpu.setX(rt1, val1)
            cpu.setX(rt2, val2)
            val newBase = base + signedOffset
            if (rn == 31) cpu.sp = newBase else cpu.setX(rn, newBase)
            return null
        }
    }

    data class BranchImm(val targetAddress: Long) : DecodedInstruction() {
        override val disassembly: String = "B 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            cpu.pc = targetAddress
            return null
        }
    }

    data class BranchLinkImm(val targetAddress: Long) : DecodedInstruction() {
        override val disassembly: String = "BL 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            cpu.setX(30, currentPc + 4) // Store return address in Link Register X30
            cpu.pc = targetAddress
            return null
        }
    }

    data class BranchLinkReg(val rn: Int) : DecodedInstruction() {
        override val disassembly: String = "BLR X$rn"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val target = cpu.getX(rn)
            cpu.setX(30, currentPc + 4)
            cpu.pc = target
            return null
        }
    }

    data class Cbz(val rt: Int, val targetAddress: Long, val isCbnz: Boolean) : DecodedInstruction() {
        override val disassembly: String = "${if (isCbnz) "CBNZ" else "CBZ"} X$rt, 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val valT = cpu.getX(rt)
            val condition = if (isCbnz) valT != 0L else valT == 0L
            if (condition) {
                cpu.pc = targetAddress
            }
            return null
        }
    }

    data class BranchCond(val cond: Int, val targetAddress: Long) : DecodedInstruction() {
        val condName: String = when (cond) { 0 -> "EQ"; 1 -> "NE"; 10 -> "GE"; 11 -> "LT"; else -> "COND" }
        override val disassembly: String = "B.$condName 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val conditionMet = when (cond) {
                0 -> cpu.flagZ // EQ
                1 -> !cpu.flagZ // NE
                2 -> cpu.flagC // CS/HS
                3 -> !cpu.flagC // CC/LO
                10 -> cpu.flagN == cpu.flagV // GE
                11 -> cpu.flagN != cpu.flagV // LT
                12 -> !cpu.flagZ && (cpu.flagN == cpu.flagV) // GT
                13 -> cpu.flagZ || (cpu.flagN != cpu.flagV) // LE
                else -> true
            }
            if (conditionMet) {
                cpu.pc = targetAddress
            }
            return null
        }
    }

    data class Unknown(val rawOpcode: Int) : DecodedInstruction() {
        override val disassembly: String = "RAW_ARM64 [0x${rawOpcode.toUInt().toString(16).padStart(8, '0').uppercase()}]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? = null
    }
}

object ArmDecoder {

    /**
     * Decodes a raw 32-bit ARM64 machine instruction opcode into a structured [DecodedInstruction]
     * using bitwise masking (`and`) and logical bit-shifting (`ushr`, `shl`).
     *
     * ARM64 main instruction encoding classification uses bits 25-28 (`majorOpcode`):
     * - 0x8, 0x9: Data Processing -- Immediate (ADD imm, SUB imm, MOVZ, MOVK)
     * - 0xA, 0xB: Branches & System Calls (B, BL, BLR, RET, CBZ, B.cond, SVC, NOP)
     * - 0x4, 0x6, 0xC, 0xE: Loads and Stores (LDR, STR, LDP, STP)
     * - 0x5, 0xD: Data Processing -- Register (ADD reg, SUB reg, ORR reg)
     */
    fun decode(opcode: Int, currentPc: Long): DecodedInstruction {
        if (opcode == 0xD503201F.toInt()) return DecodedInstruction.Nop

        val majorOpcode = (opcode ushr 25) and 0x0F

        return when (majorOpcode) {
            // Data Processing -- Immediate (0x8, 0x9)
            0x8, 0x9 -> decodeDataProcessingImmediate(opcode)

            // Branches, Exception Generating & System Instructions (0xA, 0xB)
            0xA, 0xB -> decodeBranchesAndSystem(opcode, currentPc)

            // Loads and Stores (0x4, 0x6, 0xC, 0xE)
            0x4, 0x6, 0xC, 0xE -> decodeLoadsAndStores(opcode)

            // Data Processing -- Register (0x5, 0xD)
            0x5, 0xD -> decodeDataProcessingRegister(opcode)

            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeDataProcessingImmediate(opcode: Int): DecodedInstruction {
        return when {
            // MOVZ Xd, #imm, LSL #shift (0xD2800000)
            (opcode and 0xFF800000.toInt()) == 0xD2800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                DecodedInstruction.Movz(rd, imm16, hw * 16)
            }
            // MOVK Xd, #imm, LSL #shift (0xF2800000)
            (opcode and 0xFF800000.toInt()) == 0xF2800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                DecodedInstruction.Movk(rd, imm16, hw * 16)
            }
            // ADD Xd, Xn, #imm (0x91000000)
            (opcode and 0xFF000000.toInt()) == 0x91000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                DecodedInstruction.AddImm(rd, rn, imm12)
            }
            // SUB Xd, Xn, #imm (0xD1000000)
            (opcode and 0xFF000000.toInt()) == 0xD1000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                DecodedInstruction.SubImm(rd, rn, imm12)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeBranchesAndSystem(opcode: Int, currentPc: Long): DecodedInstruction {
        return when {
            // RET: 0xD65F0000
            (opcode and 0xFFFFFC1F.toInt()) == 0xD65F0000.toInt() -> {
                val rn = (opcode ushr 5) and 0x1F
                DecodedInstruction.Ret(if (rn == 0) 30 else rn)
            }
            // SVC instruction: 0xD4000001 .. 0xD403FFFF
            (opcode and 0xFFE0001F.toInt()) == 0xD4000001.toInt() || (opcode and 0xFFE00000.toInt()) == 0xD4000000.toInt() -> {
                val svcNum = (opcode ushr 5) and 0xFFFF
                DecodedInstruction.Svc(svcNum)
            }
            // B imm26 (0x14000000)
            (opcode and 0xFC000000.toInt()) == 0x14000000.toInt() -> {
                val imm26 = opcode and 0x03FFFFFF
                val offset = (if (imm26 >= 0x02000000) imm26 - 0x04000000 else imm26) * 4L
                DecodedInstruction.BranchImm(currentPc + offset)
            }
            // BL imm26 (0x94000000)
            (opcode and 0xFC000000.toInt()) == 0x94000000.toInt() -> {
                val imm26 = opcode and 0x03FFFFFF
                val offset = (if (imm26 >= 0x02000000) imm26 - 0x04000000 else imm26) * 4L
                DecodedInstruction.BranchLinkImm(currentPc + offset)
            }
            // BLR Xn (0xD63F0000)
            (opcode and 0xFFFFFC1F.toInt()) == 0xD63F0000.toInt() -> {
                val rn = (opcode ushr 5) and 0x1F
                DecodedInstruction.BranchLinkReg(rn)
            }
            // CBZ / CBNZ Xt, label (0x34000000)
            (opcode and 0x7E000000.toInt()) == 0x34000000.toInt() -> {
                val isCbnz = (opcode ushr 24) and 0x01 == 1
                val rt = opcode and 0x1F
                val imm19 = (opcode ushr 5) and 0x7FFFF
                val offset = (if (imm19 >= 0x40000) imm19 - 0x80000 else imm19) * 4L
                DecodedInstruction.Cbz(rt, currentPc + offset, isCbnz)
            }
            // B.cond label (0x54000000)
            (opcode and 0xFF000010.toInt()) == 0x54000000.toInt() -> {
                val cond = opcode and 0x0F
                val imm19 = (opcode ushr 5) and 0x7FFFF
                val offset = (if (imm19 >= 0x40000) imm19 - 0x80000 else imm19) * 4L
                DecodedInstruction.BranchCond(cond, currentPc + offset)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeLoadsAndStores(opcode: Int): DecodedInstruction {
        return when {
            // STR Xt, [Xn, #imm] (0xF9000000)
            (opcode and 0xFFC00000.toInt()) == 0xF9000000.toInt() -> {
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val pimm = (opcode ushr 10) and 0xFFF
                DecodedInstruction.StrImm(rt, rn, pimm * 8L)
            }
            // LDR Xt, [Xn, #imm] (0xF9400000)
            (opcode and 0xFFC00000.toInt()) == 0xF9400000.toInt() -> {
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val pimm = (opcode ushr 10) and 0xFFF
                DecodedInstruction.LdrImm(rt, rn, pimm * 8L)
            }
            // STP X1, X2, [Xn, #imm]!
            (opcode and 0xFFC00000.toInt()) == 0xA9000000.toInt() || (opcode and 0xFFC00000.toInt()) == 0xA9800000.toInt() -> {
                val rt1 = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rt2 = (opcode ushr 10) and 0x1F
                val imm7 = (opcode ushr 15) and 0x7F
                val signedOffset = (if (imm7 >= 64) imm7 - 128 else imm7) * 8L
                DecodedInstruction.StpImm(rt1, rt2, rn, signedOffset)
            }
            // LDP X1, X2, [Xn, #imm]!
            (opcode and 0xFFC00000.toInt()) == 0xA8C00000.toInt() || (opcode and 0xFFC00000.toInt()) == 0xA8400000.toInt() -> {
                val rt1 = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rt2 = (opcode ushr 10) and 0x1F
                val imm7 = (opcode ushr 15) and 0x7F
                val signedOffset = (if (imm7 >= 64) imm7 - 128 else imm7) * 8L
                DecodedInstruction.LdpImm(rt1, rt2, rn, signedOffset)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeDataProcessingRegister(opcode: Int): DecodedInstruction {
        return when {
            // ADD Xd, Xn, Xm (0x8B000000)
            (opcode and 0xFF200000.toInt()) == 0x8B000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.AddReg(rd, rn, rm)
            }
            // SUB / SUBS Xd, Xn, Xm (0xCB000000 / 0xEB000000)
            (opcode and 0xFF000000.toInt()) == 0xCB000000.toInt() || (opcode and 0xFF000000.toInt()) == 0xEB000000.toInt() -> {
                val isS = (opcode ushr 29) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.SubReg(rd, rn, rm, isS)
            }
            // ORR Xd, Xn, Xm (0xAA000000)
            (opcode and 0xFF200000.toInt()) == 0xAA000000.toInt() -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.OrrReg(rd, rn, rm)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }
}
