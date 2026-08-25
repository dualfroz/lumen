package lumen

import lumen.bytecode.Opcode
import lumen.runtime.FunctionValue
import lumen.runtime.IntValue
import lumen.runtime.StringValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompilerTest {
    @Test
    fun emitsExactBytecodeForArithmetic() {
        val script = Lumen.compile("1 + 2")
        assertEquals(
            listOf(
                Opcode.Constant.ordinal, 0,
                Opcode.Constant.ordinal, 1,
                Opcode.Add.ordinal,
                Opcode.Pop.ordinal,
                Opcode.Nil.ordinal,
                Opcode.Return.ordinal
            ),
            script.chunk.code
        )
        assertEquals(listOf<Any>(IntValue(1), IntValue(2)), script.chunk.constants)
    }

    @Test
    fun emitsNegateForUnaryMinus() {
        val script = Lumen.compile("-5")
        assertEquals(
            listOf(
                Opcode.Constant.ordinal, 0,
                Opcode.Negate.ordinal,
                Opcode.Pop.ordinal,
                Opcode.Nil.ordinal,
                Opcode.Return.ordinal
            ),
            script.chunk.code
        )
    }

    @Test
    fun globalDeclarationEmitsDefineGlobal() {
        val script = Lumen.compile("let x = 5")
        assertTrue(script.chunk.code.contains(Opcode.DefineGlobal.ordinal))
        assertTrue(script.chunk.constants.contains(StringValue("x")))
        assertTrue(script.chunk.constants.contains(IntValue(5)))
    }

    @Test
    fun functionCompilesToNestedFunctionConstant() {
        val script = Lumen.compile("fn f() { return 42 }")
        assertTrue(script.chunk.code.contains(Opcode.Closure.ordinal))
        val function = assertIs<FunctionValue>(script.chunk.constants.first { it is FunctionValue })
        assertEquals("f", function.name)
        assertEquals(0, function.arity)
        assertTrue(function.chunk.constants.contains(IntValue(42)))
    }

    @Test
    fun shortCircuitAndEmitsJumpIfFalse() {
        val script = Lumen.compile("true and false")
        assertTrue(script.chunk.code.contains(Opcode.JumpIfFalse.ordinal))
    }

    @Test
    fun forLoopEmitsIteratorOpcodes() {
        val script = Lumen.compile("for (i in items) { print(i) }")
        assertTrue(script.chunk.code.contains(Opcode.GetIter.ordinal))
        assertTrue(script.chunk.code.contains(Opcode.ForIter.ordinal))
    }
}
