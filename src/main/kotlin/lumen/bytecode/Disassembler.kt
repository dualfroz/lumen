package lumen.bytecode

import lumen.runtime.FunctionValue
import lumen.runtime.Value
import lumen.runtime.displayString

object Disassembler {
    fun dump(function: FunctionValue, out: Appendable = System.out) {
        out.append("== ${function.name} (arity ${function.arity}, upvalues ${function.upvalueCount}) ==\n")
        val chunk = function.chunk
        var offset = 0
        while (offset < chunk.code.size) {
            offset = instruction(chunk, offset, out)
        }
        for (constant in chunk.constants) {
            if (constant is FunctionValue) dump(constant, out)
        }
    }

    private fun instruction(chunk: Chunk, offset: Int, out: Appendable): Int {
        out.append(pad(offset))
        out.append("  ")
        val op = Opcode.from(chunk.code[offset])
        return when (op) {
            Opcode.Constant, Opcode.GetGlobal, Opcode.SetGlobal, Opcode.DefineGlobal ->
                constantInstruction(op, chunk, offset, out)
            Opcode.GetLocal, Opcode.SetLocal, Opcode.GetUpvalue, Opcode.SetUpvalue,
            Opcode.Call, Opcode.BuildList, Opcode.BuildMap ->
                operandInstruction(op, chunk, offset, out)
            Opcode.Jump, Opcode.JumpIfFalse, Opcode.Loop ->
                jumpInstruction(op, chunk, offset, out)
            Opcode.ForIter -> forIterInstruction(chunk, offset, out)
            Opcode.Invoke -> invokeInstruction(chunk, offset, out)
            Opcode.Closure -> closureInstruction(chunk, offset, out)
            else -> {
                out.append(op.name).append("\n")
                offset + 1
            }
        }
    }

    private fun constantInstruction(op: Opcode, chunk: Chunk, offset: Int, out: Appendable): Int {
        val index = chunk.code[offset + 1]
        out.append(op.name).append(" ").append(index.toString())
            .append(" (").append(constantText(chunk.constants[index])).append(")\n")
        return offset + 2
    }

    private fun operandInstruction(op: Opcode, chunk: Chunk, offset: Int, out: Appendable): Int {
        out.append(op.name).append(" ").append(chunk.code[offset + 1].toString()).append("\n")
        return offset + 2
    }

    private fun jumpInstruction(op: Opcode, chunk: Chunk, offset: Int, out: Appendable): Int {
        val jump = chunk.code[offset + 1]
        val target = if (op == Opcode.Loop) offset + 2 - jump else offset + 2 + jump
        out.append(op.name).append(" ").append(jump.toString())
            .append(" -> ").append(target.toString()).append("\n")
        return offset + 2
    }

    private fun forIterInstruction(chunk: Chunk, offset: Int, out: Appendable): Int {
        val slot = chunk.code[offset + 1]
        val jump = chunk.code[offset + 2]
        val target = offset + 3 + jump
        out.append("ForIter slot ").append(slot.toString())
            .append(" exit -> ").append(target.toString()).append("\n")
        return offset + 3
    }

    private fun invokeInstruction(chunk: Chunk, offset: Int, out: Appendable): Int {
        val nameIndex = chunk.code[offset + 1]
        val argCount = chunk.code[offset + 2]
        out.append("Invoke ").append(constantText(chunk.constants[nameIndex]))
            .append(" (").append(argCount.toString()).append(" args)\n")
        return offset + 3
    }

    private fun closureInstruction(chunk: Chunk, offset: Int, out: Appendable): Int {
        val index = chunk.code[offset + 1]
        val function = chunk.constants[index] as FunctionValue
        out.append("Closure ").append(index.toString())
            .append(" (").append(function.name).append(")\n")
        return offset + 2 + function.upvalueCount * 2
    }

    private fun constantText(value: Value): String =
        if (value is FunctionValue) "<fn ${value.name}>" else displayString(value)

    private fun pad(offset: Int): String = offset.toString().padStart(4, '0')
}
