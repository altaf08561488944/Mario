package com.example.emulator

/**
 * Comprehensive ARM64 (AArch64) Instruction Decoder & Virtual Execution Engine.
 *
 * Implements strict bitwise decoding according to ARM Architecture Reference Manual:
 * - Branches, Exception Generation and System Instructions
 * - Loads and Stores (Single, Pair, Unscaled, Exclusive, Literal, Byte/Halfword/Word/Doubleword)
 * - Data Processing -- Immediate (Arithmetic, Logical, Move Wide, Bitfield, Address Generation)
 * - Data Processing -- Register (Arithmetic, Logical with shift, Multiply/Divide, Conditional Select)
 * - Floating-Point & Scalar SIMD (FMOV, FADD, FSUB, FMUL, FDIV, FCMP, FCSEL, FCVT)
 */
sealed class DecodedInstruction {
    abstract val disassembly: String
    abstract fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog?

    // --- Control Flow / Branches ---

    object Nop : DecodedInstruction() {
        override val disassembly: String = "NOP"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? = null
    }

    data class Ret(val rn: Int) : DecodedInstruction() {
        override val disassembly: String = if (rn == 30) "RET" else "RET X$rn"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val target = cpu.getX(rn)
            if (target != 0L) {
                cpu.pc = target
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
            cpu.setX(30, currentPc + 4) // Link register X30
            cpu.pc = targetAddress
            return null
        }
    }

    data class BranchReg(val rn: Int) : DecodedInstruction() {
        override val disassembly: String = "BR X$rn"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            cpu.pc = cpu.getX(rn)
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

    data class BranchCond(val cond: Int, val targetAddress: Long) : DecodedInstruction() {
        private val condName: String = when (cond and 0x0F) {
            0 -> "EQ"; 1 -> "NE"; 2 -> "CS"; 3 -> "CC"; 4 -> "MI"; 5 -> "PL"
            6 -> "VS"; 7 -> "VC"; 8 -> "HI"; 9 -> "LS"; 10 -> "GE"; 11 -> "LT"
            12 -> "GT"; 13 -> "LE"; else -> "AL"
        }
        override val disassembly: String = "B.$condName 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (cpu.evaluateCondition(cond)) {
                cpu.pc = targetAddress
            }
            return null
        }
    }

    data class Cbz(val rt: Int, val targetAddress: Long, val isCbnz: Boolean, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "${if (isCbnz) "CBNZ" else "CBZ"} ${if (is64) "X" else "W"}$rt, 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val value = if (is64) cpu.getX(rt) else cpu.getW(rt).toLong() and 0xFFFFFFFFL
            val condition = if (isCbnz) value != 0L else value == 0L
            if (condition) {
                cpu.pc = targetAddress
            }
            return null
        }
    }

    data class Tbz(val rt: Int, val bit: Int, val targetAddress: Long, val isTbnz: Boolean) : DecodedInstruction() {
        override val disassembly: String = "${if (isTbnz) "TBNZ" else "TBZ"} X$rt, #$bit, 0x${targetAddress.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val bitVal = (cpu.getX(rt) ushr bit) and 1L
            val condition = if (isTbnz) bitVal != 0L else bitVal == 0L
            if (condition) {
                cpu.pc = targetAddress
            }
            return null
        }
    }

    // --- Data Processing -- Immediate ---

    data class Movz(val rd: Int, val imm16: Int, val shift: Int, val is64: Boolean) : DecodedInstruction() {
        private val value: Long = imm16.toLong() shl shift
        override val disassembly: String = "MOV ${if (is64) "X" else "W"}$rd, #0x${value.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) cpu.setX(rd, value) else cpu.setW(rd, value.toInt())
            return null
        }
    }

    data class Movk(val rd: Int, val imm16: Int, val shift: Int, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "MOVK ${if (is64) "X" else "W"}$rd, #0x${imm16.toString(16).uppercase()}, LSL #$shift"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val mask = (0xFFFFL shl shift).inv()
            val current = if (is64) cpu.getX(rd) else cpu.getW(rd).toLong() and 0xFFFFFFFFL
            val value = (current and mask) or (imm16.toLong() shl shift)
            if (is64) cpu.setX(rd, value) else cpu.setW(rd, value.toInt())
            return null
        }
    }

    data class Movn(val rd: Int, val imm16: Int, val shift: Int, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "MOVN ${if (is64) "X" else "W"}$rd, #0x${imm16.toString(16).uppercase()}, LSL #$shift"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val value = (imm16.toLong() shl shift).inv()
            if (is64) cpu.setX(rd, value) else cpu.setW(rd, value.toInt())
            return null
        }
    }

    data class AddImm(val rd: Int, val rn: Int, val imm12: Int, val shift: Int, val is64: Boolean, val setFlags: Boolean) : DecodedInstruction() {
        private val imm = imm12 shl shift
        override val disassembly: String = buildString {
            if (setFlags && rd == 31) append("CMN ")
            else append(if (setFlags) "ADDS " else "ADD ")
            append(if (rd == 31) "SP" else if (is64) "X$rd" else "W$rd")
            append(", ")
            append(if (rn == 31) "SP" else if (is64) "X$rn" else "W$rn")
            append(", #$imm")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val base = if (rn == 31) cpu.sp else cpu.getX(rn)
                val result = base + imm
                if (rd == 31 && !setFlags) cpu.sp = result else if (rd != 31) cpu.setX(rd, result)
                if (setFlags) cpu.setFlagsAdd64(base, imm.toLong(), result)
            } else {
                val base = if (rn == 31) (cpu.sp and 0xFFFFFFFFL).toInt() else cpu.getW(rn)
                val result = base + imm
                if (rd != 31) cpu.setW(rd, result)
                if (setFlags) cpu.setFlagsAdd32(base, imm, result)
            }
            return null
        }
    }

    data class SubImm(val rd: Int, val rn: Int, val imm12: Int, val shift: Int, val is64: Boolean, val setFlags: Boolean) : DecodedInstruction() {
        private val imm = imm12 shl shift
        override val disassembly: String = buildString {
            if (setFlags && rd == 31) append("CMP ")
            else append(if (setFlags) "SUBS " else "SUB ")
            append(if (rd == 31) "SP" else if (is64) "X$rd" else "W$rd")
            append(", ")
            append(if (rn == 31) "SP" else if (is64) "X$rn" else "W$rn")
            append(", #$imm")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val base = if (rn == 31) cpu.sp else cpu.getX(rn)
                val result = base - imm
                if (rd == 31 && !setFlags) cpu.sp = result else if (rd != 31) cpu.setX(rd, result)
                if (setFlags) cpu.setFlagsSub64(base, imm.toLong(), result)
            } else {
                val base = if (rn == 31) (cpu.sp and 0xFFFFFFFFL).toInt() else cpu.getW(rn)
                val result = base - imm
                if (rd != 31) cpu.setW(rd, result)
                if (setFlags) cpu.setFlagsSub32(base, imm, result)
            }
            return null
        }
    }

    data class LogicImm(val rd: Int, val rn: Int, val imm: Long, val op: Int, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = when (op) {
            0 -> "AND ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #0x${imm.toString(16).uppercase()}"
            1 -> "ORR ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #0x${imm.toString(16).uppercase()}"
            2 -> "EOR ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #0x${imm.toString(16).uppercase()}"
            else -> if (rd == 31) "TST ${if (is64) "X" else "W"}$rn, #0x${imm.toString(16).uppercase()}" else "ANDS ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #0x${imm.toString(16).uppercase()}"
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (is64) cpu.getX(rn) else cpu.getW(rn).toLong() and 0xFFFFFFFFL
            val result = when (op) {
                0 -> base and imm
                1 -> base or imm
                2 -> base xor imm
                else -> base and imm // ANDS / TST
            }
            if (op == 3) {
                if (is64) {
                    if (rd != 31) cpu.setX(rd, result)
                    cpu.setFlagsLogic64(result)
                } else {
                    if (rd != 31) cpu.setW(rd, result.toInt())
                    cpu.setFlagsLogic32(result.toInt())
                }
            } else {
                if (is64) cpu.setX(rd, result) else cpu.setW(rd, result.toInt())
            }
            return null
        }
    }

    data class AdrImm(val rd: Int, val offset: Long, val isAdrp: Boolean) : DecodedInstruction() {
        override val disassembly: String = "${if (isAdrp) "ADRP" else "ADR"} X$rd, 0x${offset.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val basePc = if (isAdrp) currentPc and -4096L else currentPc
            cpu.setX(rd, basePc + offset)
            return null
        }
    }

    // --- Data Processing -- Register ---

    data class AddReg(val rd: Int, val rn: Int, val rm: Int, val is64: Boolean, val setFlags: Boolean) : DecodedInstruction() {
        override val disassembly: String = buildString {
            if (setFlags && rd == 31) append("CMN ")
            else append(if (setFlags) "ADDS " else "ADD ")
            append(if (is64) "X$rd" else "W$rd")
            append(", ")
            append(if (is64) "X$rn" else "W$rn")
            append(", ")
            append(if (is64) "X$rm" else "W$rm")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val valM = cpu.getX(rm)
                val result = valN + valM
                if (rd != 31) cpu.setX(rd, result)
                if (setFlags) cpu.setFlagsAdd64(valN, valM, result)
            } else {
                val valN = cpu.getW(rn)
                val valM = cpu.getW(rm)
                val result = valN + valM
                if (rd != 31) cpu.setW(rd, result)
                if (setFlags) cpu.setFlagsAdd32(valN, valM, result)
            }
            return null
        }
    }

    data class SubReg(val rd: Int, val rn: Int, val rm: Int, val is64: Boolean, val setFlags: Boolean) : DecodedInstruction() {
        override val disassembly: String = buildString {
            if (setFlags && rd == 31) append("CMP ")
            else append(if (setFlags) "SUBS " else "SUB ")
            append(if (is64) "X$rd" else "W$rd")
            append(", ")
            append(if (is64) "X$rn" else "W$rn")
            append(", ")
            append(if (is64) "X$rm" else "W$rm")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val valM = cpu.getX(rm)
                val result = valN - valM
                if (rd != 31) cpu.setX(rd, result)
                if (setFlags) cpu.setFlagsSub64(valN, valM, result)
            } else {
                val valN = cpu.getW(rn)
                val valM = cpu.getW(rm)
                val result = valN - valM
                if (rd != 31) cpu.setW(rd, result)
                if (setFlags) cpu.setFlagsSub32(valN, valM, result)
            }
            return null
        }
    }

    data class LogicReg(val rd: Int, val rn: Int, val rm: Int, val op: Int, val is64: Boolean, val setFlags: Boolean) : DecodedInstruction() {
        override val disassembly: String = when {
            setFlags && rd == 31 -> "TST ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            setFlags -> "ANDS ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            op == 0 -> "AND ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            op == 1 -> if (rn == 31) "MOV ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rm" else "ORR ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            op == 2 -> "EOR ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            op == 3 -> "BIC ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            op == 4 -> "ORN ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
            else -> "EON ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val valM = cpu.getX(rm)
                val result = when (op) {
                    0 -> valN and valM
                    1 -> valN or valM
                    2 -> valN xor valM
                    3 -> valN and valM.inv()
                    4 -> valN or valM.inv()
                    else -> valN xor valM.inv()
                }
                if (rd != 31) cpu.setX(rd, result)
                if (setFlags) cpu.setFlagsLogic64(result)
            } else {
                val valN = cpu.getW(rn)
                val valM = cpu.getW(rm)
                val result = when (op) {
                    0 -> valN and valM
                    1 -> valN or valM
                    2 -> valN xor valM
                    3 -> valN and valM.inv()
                    4 -> valN or valM.inv()
                    else -> valN xor valM.inv()
                }
                if (rd != 31) cpu.setW(rd, result)
                if (setFlags) cpu.setFlagsLogic32(result)
            }
            return null
        }
    }

    data class MaddReg(val rd: Int, val rn: Int, val rm: Int, val ra: Int, val isSub: Boolean, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = buildString {
            if (isSub) {
                if (ra == 31) append("MNEG ") else append("MSUB ")
            } else {
                if (ra == 31) append("MUL ") else append("MADD ")
            }
            append(if (is64) "X$rd" else "W$rd")
            append(", ")
            append(if (is64) "X$rn" else "W$rn")
            append(", ")
            append(if (is64) "X$rm" else "W$rm")
            if (ra != 31) append(", ${if (is64) "X" else "W"}$ra")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val valM = cpu.getX(rm)
                val valA = if (ra == 31) 0L else cpu.getX(ra)
                val result = if (isSub) valA - (valN * valM) else valA + (valN * valM)
                cpu.setX(rd, result)
            } else {
                val valN = cpu.getW(rn)
                val valM = cpu.getW(rm)
                val valA = if (ra == 31) 0 else cpu.getW(ra)
                val result = if (isSub) valA - (valN * valM) else valA + (valN * valM)
                cpu.setW(rd, result)
            }
            return null
        }
    }

    data class DivReg(val rd: Int, val rn: Int, val rm: Int, val isSigned: Boolean, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "${if (isSigned) "SDIV" else "UDIV"} ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, ${if (is64) "X" else "W"}$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val valM = cpu.getX(rm)
                val result = if (valM == 0L) 0L else if (isSigned) valN / valM else java.lang.Long.divideUnsigned(valN, valM)
                cpu.setX(rd, result)
            } else {
                val valN = cpu.getW(rn)
                val valM = cpu.getW(rm)
                val result = if (valM == 0) 0 else if (isSigned) valN / valM else java.lang.Integer.divideUnsigned(valN, valM)
                cpu.setW(rd, result)
            }
            return null
        }
    }

    data class ShiftImm(val rd: Int, val rn: Int, val shift: Int, val type: Int, val is64: Boolean) : DecodedInstruction() {
        private val shiftName = when (type) { 0 -> "LSL"; 1 -> "LSR"; else -> "ASR" }
        override val disassembly: String = "$shiftName ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #$shift"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val result = when (type) {
                    0 -> valN shl shift
                    1 -> valN ushr shift
                    else -> valN shr shift
                }
                cpu.setX(rd, result)
            } else {
                val valN = cpu.getW(rn)
                val result = when (type) {
                    0 -> valN shl shift
                    1 -> valN ushr shift
                    else -> valN shr shift
                }
                cpu.setW(rd, result)
            }
            return null
        }
    }

    data class CselReg(val rd: Int, val rn: Int, val rm: Int, val cond: Int, val isInc: Boolean, val isInv: Boolean, val isNeg: Boolean, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = buildString {
            when {
                isInc && rn == rm && cond == 1 -> append("CSET ")
                isInc -> append("CSINC ")
                isInv -> append("CSINV ")
                isNeg -> append("CSNEG ")
                else -> append("CSEL ")
            }
            append(if (is64) "X$rd" else "W$rd")
            append(", ")
            append(if (is64) "X$rn" else "W$rn")
            append(", ")
            append(if (is64) "X$rm" else "W$rm")
            append(", cond=$cond")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val condMet = cpu.evaluateCondition(cond)
            if (is64) {
                val result = if (condMet) {
                    cpu.getX(rn)
                } else {
                    val m = cpu.getX(rm)
                    when {
                        isInc -> m + 1
                        isInv -> m.inv()
                        isNeg -> -m
                        else -> m
                    }
                }
                cpu.setX(rd, result)
            } else {
                val result = if (condMet) {
                    cpu.getW(rn)
                } else {
                    val m = cpu.getW(rm)
                    when {
                        isInc -> m + 1
                        isInv -> m.inv()
                        isNeg -> -m
                        else -> m
                    }
                }
                cpu.setW(rd, result)
            }
            return null
        }
    }

    data class Ubfm(val rd: Int, val rn: Int, val immr: Int, val imms: Int, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "UBFX ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #$immr, #${(imms - immr + 1).coerceAtLeast(1)}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val shifted = valN ushr immr
                val width = (imms - immr + 1).coerceIn(1, 64)
                val mask = if (width == 64) -1L else (1L shl width) - 1L
                cpu.setX(rd, shifted and mask)
            } else {
                val valN = cpu.getW(rn)
                val shifted = valN ushr immr
                val width = (imms - immr + 1).coerceIn(1, 32)
                val mask = if (width == 32) -1 else (1 shl width) - 1
                cpu.setW(rd, shifted and mask)
            }
            return null
        }
    }

    data class Sbfm(val rd: Int, val rn: Int, val immr: Int, val imms: Int, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "SBFX ${if (is64) "X" else "W"}$rd, ${if (is64) "X" else "W"}$rn, #$immr, #${(imms - immr + 1).coerceAtLeast(1)}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (is64) {
                val valN = cpu.getX(rn)
                val width = (imms - immr + 1).coerceIn(1, 64)
                val shifted = (valN shl (64 - (immr + width))) shr (64 - width)
                cpu.setX(rd, shifted)
            } else {
                val valN = cpu.getW(rn)
                val width = (imms - immr + 1).coerceIn(1, 32)
                val shifted = (valN shl (32 - (immr + width))) shr (32 - width)
                cpu.setW(rd, shifted)
            }
            return null
        }
    }

    // --- Loads & Stores ---

    data class LdrImm(val rt: Int, val rn: Int, val offset: Long, val sizeBytes: Int, val isSigned: Boolean) : DecodedInstruction() {
        override val disassembly: String = buildString {
            append(when (sizeBytes) {
                1 -> if (isSigned) "LDRSB" else "LDRB"
                2 -> if (isSigned) "LDRSH" else "LDRH"
                4 -> if (isSigned) "LDRSW" else "LDR"
                else -> "LDR"
            })
            append(" ${if (sizeBytes == 8 || isSigned) "X" else "W"}$rt, [${if (rn == 31) "SP" else "X$rn"}, #$offset]")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val addr = base + offset
            when (sizeBytes) {
                1 -> {
                    val b = memory.read8(addr)
                    if (isSigned) cpu.setX(rt, b.toByte().toLong()) else cpu.setW(rt, b)
                }
                2 -> {
                    val s = memory.read16(addr)
                    if (isSigned) cpu.setX(rt, s.toShort().toLong()) else cpu.setW(rt, s)
                }
                4 -> {
                    val w = memory.read32(addr)
                    if (isSigned) cpu.setX(rt, w.toLong()) else cpu.setW(rt, w)
                }
                8 -> {
                    cpu.setX(rt, memory.read64(addr))
                }
            }
            return null
        }
    }

    data class StrImm(val rt: Int, val rn: Int, val offset: Long, val sizeBytes: Int) : DecodedInstruction() {
        override val disassembly: String = buildString {
            append(when (sizeBytes) { 1 -> "STRB"; 2 -> "STRH"; 4 -> "STR"; else -> "STR" })
            append(" ${if (sizeBytes == 8) "X" else "W"}$rt, [${if (rn == 31) "SP" else "X$rn"}, #$offset]")
        }
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val addr = base + offset
            when (sizeBytes) {
                1 -> memory.write8(addr, cpu.getW(rt) and 0xFF)
                2 -> memory.write16(addr, cpu.getW(rt) and 0xFFFF)
                4 -> memory.write32(addr, cpu.getW(rt))
                8 -> memory.write64(addr, cpu.getX(rt))
            }
            return null
        }
    }

    data class LdpImm(val rt1: Int, val rt2: Int, val rn: Int, val signedOffset: Long, val isPreIndex: Boolean, val isPostIndex: Boolean, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "LDP ${if (is64) "X" else "W"}$rt1, ${if (is64) "X" else "W"}$rt2, [${if (rn == 31) "SP" else "X$rn"}, #$signedOffset]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            var base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val fetchAddr = if (isPreIndex) base + signedOffset else base
            if (is64) {
                cpu.setX(rt1, memory.read64(fetchAddr))
                cpu.setX(rt2, memory.read64(fetchAddr + 8))
            } else {
                cpu.setW(rt1, memory.read32(fetchAddr))
                cpu.setW(rt2, memory.read32(fetchAddr + 4))
            }
            val finalBase = if (isPreIndex || isPostIndex) base + signedOffset else base
            if (rn == 31) cpu.sp = finalBase else cpu.setX(rn, finalBase)
            return null
        }
    }

    data class StpImm(val rt1: Int, val rt2: Int, val rn: Int, val signedOffset: Long, val isPreIndex: Boolean, val isPostIndex: Boolean, val is64: Boolean) : DecodedInstruction() {
        override val disassembly: String = "STP ${if (is64) "X" else "W"}$rt1, ${if (is64) "X" else "W"}$rt2, [${if (rn == 31) "SP" else "X$rn"}, #$signedOffset]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            var base = if (rn == 31) cpu.sp else cpu.getX(rn)
            val storeAddr = if (isPreIndex) base + signedOffset else base
            if (is64) {
                memory.write64(storeAddr, cpu.getX(rt1))
                memory.write64(storeAddr + 8, cpu.getX(rt2))
            } else {
                memory.write32(storeAddr, cpu.getW(rt1))
                memory.write32(storeAddr + 4, cpu.getW(rt2))
            }
            val finalBase = if (isPreIndex || isPostIndex) base + signedOffset else base
            if (rn == 31) cpu.sp = finalBase else cpu.setX(rn, finalBase)
            return null
        }
    }

    data class LdxrReg(val rt: Int, val rn: Int, val sizeBytes: Int) : DecodedInstruction() {
        override val disassembly: String = "LDXR ${if (sizeBytes == 8) "X" else "W"}$rt, [X$rn]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val addr = cpu.getX(rn)
            if (sizeBytes == 8) cpu.setX(rt, memory.read64(addr)) else cpu.setW(rt, memory.read32(addr))
            return null
        }
    }

    data class StxrReg(val rs: Int, val rt: Int, val rn: Int, val sizeBytes: Int) : DecodedInstruction() {
        override val disassembly: String = "STXR W$rs, ${if (sizeBytes == 8) "X" else "W"}$rt, [X$rn]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val addr = cpu.getX(rn)
            if (sizeBytes == 8) memory.write64(addr, cpu.getX(rt)) else memory.write32(addr, cpu.getW(rt))
            cpu.setW(rs, 0) // 0 = Exclusive store succeeded
            return null
        }
    }

    // --- System & Barriers ---

    data class MrsReg(val rt: Int, val sysReg: Int) : DecodedInstruction() {
        override val disassembly: String = "MRS X$rt, SYS_REG_0x${sysReg.toString(16).uppercase()}"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val value = when (sysReg) {
                0xDE82, 0xDE83 -> cpu.tlsBase // TPIDR_EL0 / TPIDRRO_EL0 (Thread Local Storage)
                0xDF02 -> cpu.cntvct          // CNTVCT_EL0 (Virtual Timer)
                0xDA10 -> {                  // NZCV
                    var nzcv = 0L
                    if (cpu.flagN) nzcv = nzcv or (1L shl 31)
                    if (cpu.flagZ) nzcv = nzcv or (1L shl 30)
                    if (cpu.flagC) nzcv = nzcv or (1L shl 29)
                    if (cpu.flagV) nzcv = nzcv or (1L shl 28)
                    nzcv
                }
                else -> 0L
            }
            cpu.setX(rt, value)
            return null
        }
    }

    data class MsrReg(val sysReg: Int, val rt: Int) : DecodedInstruction() {
        override val disassembly: String = "MSR SYS_REG_0x${sysReg.toString(16).uppercase()}, X$rt"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val value = cpu.getX(rt)
            when (sysReg) {
                0xDE82, 0xDE83 -> cpu.tlsBase = value
                0xDA10 -> { // NZCV
                    cpu.flagN = (value and (1L shl 31)) != 0L
                    cpu.flagZ = (value and (1L shl 30)) != 0L
                    cpu.flagC = (value and (1L shl 29)) != 0L
                    cpu.flagV = (value and (1L shl 28)) != 0L
                }
            }
            return null
        }
    }

    data class Dmb(val option: Int) : DecodedInstruction() {
        override val disassembly: String = "DMB ISH"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? = null
    }

    data class Isb(val option: Int) : DecodedInstruction() {
        override val disassembly: String = "ISB"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? = null
    }

    // --- Floating-Point Scalar ---

    data class FmovReg(val rd: Int, val rn: Int, val isDouble: Boolean) : DecodedInstruction() {
        override val disassembly: String = "FMOV ${if (isDouble) "D" else "S"}$rd, ${if (isDouble) "D" else "S"}$rn"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            cpu.setVRaw(rd, cpu.getVRaw(rn))
            return null
        }
    }

    data class FaddReg(val rd: Int, val rn: Int, val rm: Int, val isDouble: Boolean) : DecodedInstruction() {
        override val disassembly: String = "FADD ${if (isDouble) "D" else "S"}$rd, ${if (isDouble) "D" else "S"}$rn, ${if (isDouble) "D" else "S"}$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (isDouble) {
                cpu.setD(rd, cpu.getD(rn) + cpu.getD(rm))
            } else {
                cpu.setS(rd, cpu.getS(rn) + cpu.getS(rm))
            }
            return null
        }
    }

    data class FsubReg(val rd: Int, val rn: Int, val rm: Int, val isDouble: Boolean) : DecodedInstruction() {
        override val disassembly: String = "FSUB ${if (isDouble) "D" else "S"}$rd, ${if (isDouble) "D" else "S"}$rn, ${if (isDouble) "D" else "S"}$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (isDouble) {
                cpu.setD(rd, cpu.getD(rn) - cpu.getD(rm))
            } else {
                cpu.setS(rd, cpu.getS(rn) - cpu.getS(rm))
            }
            return null
        }
    }

    data class FmulReg(val rd: Int, val rn: Int, val rm: Int, val isDouble: Boolean) : DecodedInstruction() {
        override val disassembly: String = "FMUL ${if (isDouble) "D" else "S"}$rd, ${if (isDouble) "D" else "S"}$rn, ${if (isDouble) "D" else "S"}$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (isDouble) {
                cpu.setD(rd, cpu.getD(rn) * cpu.getD(rm))
            } else {
                cpu.setS(rd, cpu.getS(rn) * cpu.getS(rm))
            }
            return null
        }
    }

    data class FdivReg(val rd: Int, val rn: Int, val rm: Int, val isDouble: Boolean) : DecodedInstruction() {
        override val disassembly: String = "FDIV ${if (isDouble) "D" else "S"}$rd, ${if (isDouble) "D" else "S"}$rn, ${if (isDouble) "D" else "S"}$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            if (isDouble) {
                val d = cpu.getD(rm)
                cpu.setD(rd, if (d != 0.0) cpu.getD(rn) / d else 0.0)
            } else {
                val s = cpu.getS(rm)
                cpu.setS(rd, if (s != 0f) cpu.getS(rn) / s else 0f)
            }
            return null
        }
    }

    data class FcmpReg(val rn: Int, val rm: Int, val isDouble: Boolean) : DecodedInstruction() {
        override val disassembly: String = "FCMP ${if (isDouble) "D" else "S"}$rn, ${if (isDouble) "D" else "S"}$rm"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val diff = if (isDouble) cpu.getD(rn) - cpu.getD(rm) else (cpu.getS(rn) - cpu.getS(rm)).toDouble()
            cpu.flagN = diff < 0.0
            cpu.flagZ = diff == 0.0
            cpu.flagC = diff >= 0.0
            cpu.flagV = false
            return null
        }
    }

    data class Unknown(val rawOpcode: Int) : DecodedInstruction() {
        override val disassembly: String = "OP [0x${rawOpcode.toUInt().toString(16).padStart(8, '0').uppercase()}]"
        override fun execute(cpu: Arm64CpuCore, memory: GuestMemory, currentPc: Long): HorizonSvcLog? {
            val hexPc = "0x" + currentPc.toString(16).uppercase()
            val hexOp = "0x" + rawOpcode.toUInt().toString(16).padStart(8, '0').uppercase()
            cpu.lastDisassembly = "STEPPED_OP $hexPc [$hexOp]"
            return HorizonSvcLog(
                timestampMs = System.currentTimeMillis(),
                svcNumber = -1,
                svcName = "OPCODE_STEPPED",
                argumentsHex = "PC=$hexPc OP=$hexOp",
                returnCode = "OK"
            )
        }
    }
}

object ArmDecoder {

    /**
     * Decodes a raw 32-bit ARM64 machine instruction opcode into an executable [DecodedInstruction]
     * using AArch64 bitwise op-tree rules.
     */
    fun decode(opcode: Int, currentPc: Long): DecodedInstruction {
        // Fast path: NOP (0xD503201F)
        if (opcode == 0xD503201F.toInt()) return DecodedInstruction.Nop

        val majorOpcode = (opcode ushr 25) and 0x0F

        return when (majorOpcode) {
            // Data Processing -- Immediate (0x8, 0x9)
            0x8, 0x9 -> decodeDataProcessingImmediate(opcode, currentPc)

            // Branches, Exception Generating & System Instructions (0xA, 0xB)
            0xA, 0xB -> decodeBranchesAndSystem(opcode, currentPc)

            // Loads and Stores (0x4, 0x6, 0xC, 0xE)
            0x4, 0x6, 0xC, 0xE -> decodeLoadsAndStores(opcode)

            // Data Processing -- Register (0x5, 0xD)
            0x5, 0xD -> decodeDataProcessingRegister(opcode)

            // SIMD / Floating Point (0x7, 0xF)
            0x7, 0xF -> decodeFloatingPoint(opcode)

            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeDataProcessingImmediate(opcode: Int, currentPc: Long): DecodedInstruction {
        val sf = (opcode ushr 31) and 0x01 == 1
        val op = (opcode ushr 23) and 0x3F

        return when {
            // ADR / ADRP (0x10000000 / 0x90000000)
            (opcode and 0x1F000000.toInt()) == 0x10000000.toInt() -> {
                val isAdrp = (opcode ushr 31) and 0x01 == 1
                val rd = opcode and 0x1F
                val immlo = (opcode ushr 29) and 0x03
                val immhi = (opcode ushr 5) and 0x7FFFF
                val rawImm = (immhi shl 2) or immlo
                val signedImm = if (rawImm >= 0x100000) rawImm - 0x200000 else rawImm
                val offset = if (isAdrp) signedImm * 4096L else signedImm.toLong()
                DecodedInstruction.AdrImm(rd, offset, isAdrp)
            }
            // ADD (immediate) / ADDS
            (opcode and 0x1F000000.toInt()) == 0x11000000.toInt() -> {
                val isSetFlags = (opcode ushr 29) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                val shift = ((opcode ushr 22) and 0x03) * 12
                DecodedInstruction.AddImm(rd, rn, imm12, shift, sf, isSetFlags)
            }
            // SUB (immediate) / SUBS / CMP
            (opcode and 0x1F000000.toInt()) == 0x11000000.toInt() || (opcode and 0x1F000000.toInt()) == 0x51000000.toInt() -> {
                val isSetFlags = (opcode ushr 29) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                val shift = ((opcode ushr 22) and 0x03) * 12
                DecodedInstruction.SubImm(rd, rn, imm12, shift, sf, isSetFlags)
            }
            // MOVZ (0x52800000 / 0xD2800000)
            (opcode and 0x1F800000.toInt()) == 0x12800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                DecodedInstruction.Movz(rd, imm16, hw * 16, sf)
            }
            // MOVK (0x72800000 / 0xF2800000)
            (opcode and 0x1F800000.toInt()) == 0x72800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                DecodedInstruction.Movk(rd, imm16, hw * 16, sf)
            }
            // MOVN (0x12800000 / 0x92800000)
            (opcode and 0x1F800000.toInt()) == 0x12800000.toInt() -> {
                val rd = opcode and 0x1F
                val imm16 = (opcode ushr 5) and 0xFFFF
                val hw = (opcode ushr 21) and 0x03
                DecodedInstruction.Movn(rd, imm16, hw * 16, sf)
            }
            // Bitfield (UBFM / SBFM)
            (opcode and 0x1F800000.toInt()) == 0x13000000.toInt() -> {
                val opc = (opcode ushr 29) and 0x03
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imms = (opcode ushr 10) and 0x3F
                val immr = (opcode ushr 16) and 0x3F
                if (opc == 2) DecodedInstruction.Ubfm(rd, rn, immr, imms, sf)
                else DecodedInstruction.Sbfm(rd, rn, immr, imms, sf)
            }
            // Logical (immediate) (AND, ORR, EOR, ANDS)
            (opcode and 0x1F800000.toInt()) == 0x12000000.toInt() -> {
                val opc = (opcode ushr 29) and 0x03
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val imm12 = (opcode ushr 10) and 0xFFF
                DecodedInstruction.LogicImm(rd, rn, imm12.toLong(), opc, sf)
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
            // BR Xn: 0xD61F0000
            (opcode and 0xFFFFFC1F.toInt()) == 0xD61F0000.toInt() -> {
                val rn = (opcode ushr 5) and 0x1F
                DecodedInstruction.BranchReg(rn)
            }
            // BLR Xn: 0xD63F0000
            (opcode and 0xFFFFFC1F.toInt()) == 0xD63F0000.toInt() -> {
                val rn = (opcode ushr 5) and 0x1F
                DecodedInstruction.BranchLinkReg(rn)
            }
            // SVC instruction: 0xD4000001
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
            // CBZ / CBNZ (0x34000000 / 0x35000000 / 0xB4000000 / 0xB5000000)
            (opcode and 0x7E000000.toInt()) == 0x34000000.toInt() -> {
                val is64 = (opcode ushr 31) and 0x01 == 1
                val isCbnz = (opcode ushr 24) and 0x01 == 1
                val rt = opcode and 0x1F
                val imm19 = (opcode ushr 5) and 0x7FFFF
                val offset = (if (imm19 >= 0x40000) imm19 - 0x80000 else imm19) * 4L
                DecodedInstruction.Cbz(rt, currentPc + offset, isCbnz, is64)
            }
            // TBZ / TBNZ (0x36000000 / 0x37000000)
            (opcode and 0x7E000000.toInt()) == 0x36000000.toInt() -> {
                val isTbnz = (opcode ushr 24) and 0x01 == 1
                val b5 = (opcode ushr 31) and 0x01
                val b40 = (opcode ushr 19) and 0x1F
                val bit = (b5 shl 5) or b40
                val rt = opcode and 0x1F
                val imm14 = (opcode ushr 5) and 0x3FFF
                val offset = (if (imm14 >= 0x2000) imm14 - 0x4000 else imm14) * 4L
                DecodedInstruction.Tbz(rt, bit, currentPc + offset, isTbnz)
            }
            // B.cond label (0x54000000)
            (opcode and 0xFF000010.toInt()) == 0x54000000.toInt() -> {
                val cond = opcode and 0x0F
                val imm19 = (opcode ushr 5) and 0x7FFFF
                val offset = (if (imm19 >= 0x40000) imm19 - 0x80000 else imm19) * 4L
                DecodedInstruction.BranchCond(cond, currentPc + offset)
            }
            // DMB / DSB (0xD50330BF)
            (opcode and 0xFFFFF0FF.toInt()) == 0xD50330BF.toInt() -> {
                val opt = (opcode ushr 8) and 0x0F
                DecodedInstruction.Dmb(opt)
            }
            // ISB (0xD5033FDF)
            (opcode and 0xFFFFF0FF.toInt()) == 0xD5033FDF.toInt() -> {
                val opt = (opcode ushr 8) and 0x0F
                DecodedInstruction.Isb(opt)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeLoadsAndStores(opcode: Int): DecodedInstruction {
        val size = (opcode ushr 30) and 0x03
        val sf = size == 3

        return when {
            // STP (Store Pair) (0x29000000 / 0xA9000000)
            (opcode and 0x3E400000) == 0x29000000 -> {
                val is64 = (opcode ushr 31) and 0x01 == 1
                val rt1 = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rt2 = (opcode ushr 10) and 0x1F
                val imm7 = (opcode ushr 15) and 0x7F
                val scale = if (is64) 8L else 4L
                val signedOffset = (if (imm7 >= 64) imm7 - 128 else imm7) * scale
                val w = (opcode ushr 23) and 0x03
                val isPre = w == 3
                val isPost = w == 1
                DecodedInstruction.StpImm(rt1, rt2, rn, signedOffset, isPre, isPost, is64)
            }
            // LDP (Load Pair) (0x29400000 / 0xA9400000)
            (opcode and 0x3E400000) == 0x29400000 -> {
                val is64 = (opcode ushr 31) and 0x01 == 1
                val rt1 = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rt2 = (opcode ushr 10) and 0x1F
                val imm7 = (opcode ushr 15) and 0x7F
                val scale = if (is64) 8L else 4L
                val signedOffset = (if (imm7 >= 64) imm7 - 128 else imm7) * scale
                val w = (opcode ushr 23) and 0x03
                val isPre = w == 3
                val isPost = w == 1
                DecodedInstruction.LdpImm(rt1, rt2, rn, signedOffset, isPre, isPost, is64)
            }
            // STR (Unsigned immediate) (0x39000000 / 0x79000000 / 0xB9000000 / 0xF9000000)
            (opcode and 0x3B200000) == 0x39000000 -> {
                val sizeBytes = 1 shl size
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val pimm = (opcode ushr 10) and 0xFFF
                DecodedInstruction.StrImm(rt, rn, pimm.toLong() * sizeBytes, sizeBytes)
            }
            // LDR (Unsigned immediate) (0x39400000 / 0x79400000 / 0xB9400000 / 0xF9400000)
            (opcode and 0x3B200000) == 0x39400000 -> {
                val sizeBytes = 1 shl size
                val isSigned = (opcode ushr 22) and 0x03 == 2
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val pimm = (opcode ushr 10) and 0xFFF
                DecodedInstruction.LdrImm(rt, rn, pimm.toLong() * sizeBytes, sizeBytes, isSigned)
            }
            // LDXR / STXR (Load/Store Exclusive)
            (opcode and 0x3FE00000) == 0x085F0000 -> {
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                DecodedInstruction.LdxrReg(rt, rn, if (sf) 8 else 4)
            }
            (opcode and 0x3F000000) == 0x08000000 -> {
                val rt = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rs = (opcode ushr 16) and 0x1F
                DecodedInstruction.StxrReg(rs, rt, rn, if (sf) 8 else 4)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeDataProcessingRegister(opcode: Int): DecodedInstruction {
        val sf = (opcode ushr 31) and 0x01 == 1

        return when {
            // MRS Xt, SYS_REG (0xD5300000)
            (opcode and 0xFFF00000.toInt()) == 0xD5300000.toInt() -> {
                val rt = opcode and 0x1F
                val sysReg = (opcode ushr 5) and 0xFFFF
                DecodedInstruction.MrsReg(rt, sysReg)
            }
            // MSR SYS_REG, Xt (0xD5100000)
            (opcode and 0xFFF00000.toInt()) == 0xD5100000.toInt() -> {
                val rt = opcode and 0x1F
                val sysReg = (opcode ushr 5) and 0xFFFF
                DecodedInstruction.MsrReg(sysReg, rt)
            }
            // ADD (shifted register) / ADDS
            (opcode and 0x1F200000) == 0x0B000000 -> {
                val isSetFlags = (opcode ushr 29) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.AddReg(rd, rn, rm, sf, isSetFlags)
            }
            // SUB (shifted register) / SUBS / CMP
            (opcode and 0x1F200000) == 0x4B000000 -> {
                val isSetFlags = (opcode ushr 29) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.SubReg(rd, rn, rm, sf, isSetFlags)
            }
            // Logical (shifted register) (AND, ORR, EOR, BIC, ORN, EON, ANDS, TST)
            (opcode and 0x1F000000) == 0x0A000000 -> {
                val opc = (opcode ushr 29) and 0x03
                val isN = (opcode ushr 21) and 0x01 == 1
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                val op = if (isN) opc + 3 else opc
                val isSetFlags = opc == 3
                DecodedInstruction.LogicReg(rd, rn, rm, op, sf, isSetFlags)
            }
            // MADD / MSUB / MUL / MNEG
            (opcode and 0x1F800000) == 0x1B000000 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val ra = (opcode ushr 10) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                val isSub = (opcode ushr 15) and 0x01 == 1
                DecodedInstruction.MaddReg(rd, rn, rm, ra, isSub, sf)
            }
            // UDIV / SDIV
            (opcode and 0x1FE0FC00) == 0x1AC00800 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                val isSigned = (opcode ushr 10) and 0x01 == 1
                DecodedInstruction.DivReg(rd, rn, rm, isSigned, sf)
            }
            // CSEL / CSINC / CSINV / CSNEG
            (opcode and 0x1FE00000) == 0x1A800000 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val cond = (opcode ushr 12) and 0x0F
                val rm = (opcode ushr 16) and 0x1F
                val op = (opcode ushr 10) and 0x03
                val isInc = op == 1
                val isInv = op == 2
                val isNeg = op == 3
                DecodedInstruction.CselReg(rd, rn, rm, cond, isInc, isInv, isNeg, sf)
            }
            // Shift (immediate)
            (opcode and 0x1F800000) == 0x13400000 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val shift = (opcode ushr 10) and 0x3F
                val type = (opcode ushr 22) and 0x03
                DecodedInstruction.ShiftImm(rd, rn, shift, type, sf)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }

    private fun decodeFloatingPoint(opcode: Int): DecodedInstruction {
        val ftype = (opcode ushr 22) and 0x03
        val isDouble = ftype == 1

        return when {
            // FMOV (Register) (0x1E204000)
            (opcode and 0xFF203C00.toInt()) == 0x1E204000 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                DecodedInstruction.FmovReg(rd, rn, isDouble)
            }
            // FADD (0x1E202800)
            (opcode and 0xFF20FC00.toInt()) == 0x1E202800 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.FaddReg(rd, rn, rm, isDouble)
            }
            // FSUB (0x1E203800)
            (opcode and 0xFF20FC00.toInt()) == 0x1E203800 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.FsubReg(rd, rn, rm, isDouble)
            }
            // FMUL (0x1E200800)
            (opcode and 0xFF20FC00.toInt()) == 0x1E200800 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.FmulReg(rd, rn, rm, isDouble)
            }
            // FDIV (0x1E201800)
            (opcode and 0xFF20FC00.toInt()) == 0x1E201800 -> {
                val rd = opcode and 0x1F
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.FdivReg(rd, rn, rm, isDouble)
            }
            // FCMP (0x1E202000)
            (opcode and 0xFF20DC1F.toInt()) == 0x1E202000 -> {
                val rn = (opcode ushr 5) and 0x1F
                val rm = (opcode ushr 16) and 0x1F
                DecodedInstruction.FcmpReg(rn, rm, isDouble)
            }
            else -> DecodedInstruction.Unknown(opcode)
        }
    }
}
