package lumen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MethodTest {
    private fun output(source: String) = Lumen.runToString(source)

    @Test
    fun methodOnLiteralReceiver() {
        assertEquals("ABC\n", output("println(\"abc\".upper())"))
        assertEquals("[1, 2, 3]\n", output("println([3, 2, 1].sort())"))
    }

    @Test
    fun chainedMethodCalls() {
        assertEquals("CBA\n", output("println(\"abc\".upper().chars().reverse().join(\"\"))"))
    }

    @Test
    fun methodMutatesReceiverInPlace() {
        val program = """
            let xs = [1, 2]
            xs.push(3)
            println(xs)
            println(xs.len())
        """.trimIndent()
        assertEquals("[1, 2, 3]\n3\n", output(program))
    }

    @Test
    fun dispatchDependsOnReceiverType() {
        assertEquals("true\n", output("println([1, 2, 3].contains(2))"))
        assertEquals("true\n", output("println(\"abc\".contains(\"b\"))"))
    }

    @Test
    fun higherOrderMethods() {
        assertEquals("[2, 4, 6]\n", output("println([1, 2, 3].map(fn(x) { return x * 2 }))"))
        assertEquals("[2, 4]\n", output("println([1, 2, 3, 4].filter(fn(x) { return x % 2 == 0 }))"))
        assertEquals("10\n", output("println([1, 2, 3, 4].reduce(fn(a, b) { return a + b }, 0))"))
    }

    @Test
    fun unknownMethodFails() {
        assertFailsWith<RuntimeError> { output("println([1].frobnicate())") }
    }

    @Test
    fun methodWrongArityFails() {
        assertFailsWith<RuntimeError> { output("println(\"a\".upper(1))") }
    }

    @Test
    fun methodOnWrongTypeFails() {
        assertFailsWith<RuntimeError> { output("println(42.upper())") }
    }
}
