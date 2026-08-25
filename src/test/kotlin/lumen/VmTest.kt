package lumen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VmTest {
    private fun output(source: String) = Lumen.runToString(source)

    @Test
    fun arithmeticAndPrecedence() {
        assertEquals("14\n", output("println(2 + 3 * 4)"))
        assertEquals("20\n", output("println((2 + 3) * 4)"))
        assertEquals("1\n", output("println(7 % 3)"))
    }

    @Test
    fun integerAndDoubleDivision() {
        assertEquals("3\n", output("println(7 / 2)"))
        assertEquals("3.5\n", output("println(7.0 / 2)"))
    }

    @Test
    fun comparisonsAndBooleans() {
        assertEquals("true\n", output("println(3 < 5)"))
        assertEquals("false\n", output("println(5 <= 4)"))
        assertEquals("true\n", output("println(2 == 2 and 3 != 4)"))
    }

    @Test
    fun stringConcatenation() {
        assertEquals("hello world\n", output("println(\"hello\" + \" \" + \"world\")"))
    }

    @Test
    fun shortCircuitAvoidsEvaluation() {
        val program = """
            fn boom() { return 1 / 0 }
            println(false and boom())
            println(true or boom())
        """.trimIndent()
        assertEquals("false\ntrue\n", output(program))
    }

    @Test
    fun shortCircuitCounterfactualWouldThrow() {
        assertFailsWith<RuntimeError> {
            output("fn boom() { return 1 / 0 }\nprintln(true and boom())")
        }
    }

    @Test
    fun ifElseBranches() {
        val program = """
            fn classify(n) {
                if (n < 0) { return "negative" } else if (n == 0) { return "zero" } else { return "positive" }
            }
            println(classify(-3))
            println(classify(0))
            println(classify(7))
        """.trimIndent()
        assertEquals("negative\nzero\npositive\n", output(program))
    }

    @Test
    fun whileLoopAccumulates() {
        val program = """
            var sum = 0
            var i = 1
            while (i <= 5) {
                sum = sum + i
                i = i + 1
            }
            println(sum)
        """.trimIndent()
        assertEquals("15\n", output(program))
    }

    @Test
    fun forOverRange() {
        val program = """
            var total = 0
            for (n in range(1, 5)) { total = total + n }
            println(total)
        """.trimIndent()
        assertEquals("10\n", output(program))
    }

    @Test
    fun forOverListAndMap() {
        assertEquals("6\n", output("var s = 0\nfor (x in [1, 2, 3]) { s = s + x }\nprintln(s)"))
        val mapProgram = """
            let ages = {"a": 1, "b": 2}
            var sum = 0
            for (key in ages) { sum = sum + ages[key] }
            println(sum)
        """.trimIndent()
        assertEquals("3\n", output(mapProgram))
    }

    @Test
    fun recursionComputesFibonacci() {
        val program = """
            fn fib(n) {
                if (n < 2) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            println(fib(10))
        """.trimIndent()
        assertEquals("55\n", output(program))
    }

    @Test
    fun closureCapturesMutableState() {
        val program = """
            fn makeCounter() {
                var count = 0
                fn tick() { count = count + 1 return count }
                return tick
            }
            let c = makeCounter()
            println(c())
            println(c())
            println(c())
        """.trimIndent()
        assertEquals("1\n2\n3\n", output(program))
    }

    @Test
    fun closuresAreIndependent() {
        val program = """
            fn adder(amount) {
                fn add(x) { return x + amount }
                return add
            }
            let add2 = adder(2)
            let add10 = adder(10)
            println(add2(5))
            println(add10(5))
        """.trimIndent()
        assertEquals("7\n15\n", output(program))
    }

    @Test
    fun listIndexingAndMutation() {
        val program = """
            let xs = [10, 20, 30]
            xs[1] = 99
            println(xs[0] + xs[1] + xs[2])
        """.trimIndent()
        assertEquals("139\n", output(program))
    }

    @Test
    fun mapAccessAndInsertion() {
        val program = """
            let m = {"one": 1}
            m["two"] = 2
            println(m["one"] + m["two"])
            println(len(keys(m)))
        """.trimIndent()
        assertEquals("3\n2\n", output(program))
    }

    @Test
    fun builtinsPushPopLenType() {
        val program = """
            let xs = [1, 2]
            push(xs, 3)
            println(len(xs))
            println(pop(xs))
            println(len(xs))
            println(type(xs))
            println(type("s"))
            println(type(1))
        """.trimIndent()
        assertEquals("3\n3\n2\nlist\nstring\nint\n", output(program))
    }

    @Test
    fun higherOrderFunctions() {
        val program = """
            let nums = [1, 2, 3, 4]
            println(str(map(fn(x) { return x * x }, nums)))
            println(str(filter(fn(x) { return x % 2 == 0 }, nums)))
            println(reduce(fn(a, b) { return a + b }, nums, 0))
        """.trimIndent()
        assertEquals("[1, 4, 9, 16]\n[2, 4]\n10\n", output(program))
    }

    @Test
    fun stringAndIntConversions() {
        assertEquals("42\n", output("println(int(\"42\") + 0)"))
        assertEquals("n=7\n", output("println(\"n=\" + str(7))"))
        assertEquals("3\n", output("println(int(3.9))"))
    }

    @Test
    fun undefinedVariableFails() {
        assertFailsWith<RuntimeError> { output("println(missing)") }
    }

    @Test
    fun callingNonFunctionFails() {
        assertFailsWith<RuntimeError> { output("let x = 5\nx()") }
    }

    @Test
    fun divisionByZeroFails() {
        assertFailsWith<RuntimeError> { output("println(1 / 0)") }
    }

    @Test
    fun listIndexOutOfRangeFails() {
        assertFailsWith<RuntimeError> { output("let xs = [1]\nprintln(xs[5])") }
    }

    @Test
    fun assigningImmutableFails() {
        assertFailsWith<CompileError> { output("let x = 1\nx = 2") }
    }

    @Test
    fun wrongArgumentCountFails() {
        assertFailsWith<RuntimeError> { output("fn f(a, b) { return a }\nf(1)") }
    }
}
