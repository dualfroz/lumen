package lumen.bytecode

import lumen.runtime.Value

class Chunk {
    val code = mutableListOf<Int>()
    val lines = mutableListOf<Int>()
    val columns = mutableListOf<Int>()
    val constants = mutableListOf<Value>()

    fun emit(byte: Int, line: Int, column: Int): Int {
        code.add(byte)
        lines.add(line)
        columns.add(column)
        return code.size - 1
    }

    fun emit(op: Opcode, line: Int, column: Int): Int = emit(op.ordinal, line, column)

    fun addConstant(value: Value): Int {
        constants.add(value)
        return constants.size - 1
    }

    fun patch(offset: Int, value: Int) {
        code[offset] = value
    }
}
