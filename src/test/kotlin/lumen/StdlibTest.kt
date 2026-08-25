package lumen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StdlibTest {
    private fun output(source: String) = Lumen.runToString(source)

    @Test
    fun mathFunctions() {
        assertEquals("5\n", output("println(abs(-5))"))
        assertEquals("5.0\n", output("println(abs(-5.0))"))
        assertEquals("4.0\n", output("println(sqrt(16))"))
        assertEquals("8.0\n", output("println(pow(2, 3))"))
        assertEquals("3\n", output("println(floor(3.9))"))
        assertEquals("4\n", output("println(ceil(3.1))"))
        assertEquals("3\n", output("println(round(2.5))"))
        assertEquals("2\n", output("println(min(2, 7))"))
        assertEquals("7\n", output("println(max(2, 7))"))
    }

    @Test
    fun piConstantIsAvailable() {
        assertEquals("true\n", output("println(PI > 3.14 and PI < 3.15)"))
    }

    @Test
    fun stringOperations() {
        assertEquals("HELLO\n", output("println(\"hello\".upper())"))
        assertEquals("hi\n", output("println(\"HI\".lower())"))
        assertEquals("trimmed\n", output("println(\"  trimmed  \".trim())"))
        assertEquals("[\"a\", \"b\", \"c\"]\n", output("println(\"a,b,c\".split(\",\"))"))
        assertEquals("hexxo\n", output("println(\"hello\".replace(\"l\", \"x\"))"))
        assertEquals("true\n", output("println(\"hello\".contains(\"ell\"))"))
        assertEquals("1\n", output("println(\"hello\".indexOf(\"ell\"))"))
        assertEquals("ell\n", output("println(\"hello\".substring(1, 4))"))
        assertEquals("[\"h\", \"i\"]\n", output("println(\"hi\".chars())"))
        assertEquals("true\n", output("println(\"lumen\".startsWith(\"lum\"))"))
        assertEquals("true\n", output("println(\"lumen\".endsWith(\"men\"))"))
    }

    @Test
    fun listOperations() {
        assertEquals("[1, 2, 3]\n", output("println([3, 1, 2].sort())"))
        assertEquals("[3, 2, 1]\n", output("println([1, 2, 3].reverse())"))
        assertEquals("[2, 3]\n", output("println([1, 2, 3, 4].slice(1, 3))"))
        assertEquals("6\n", output("println([1, 2, 3].sum())"))
        assertEquals("1\n", output("println([3, 1, 2].min())"))
        assertEquals("3\n", output("println([3, 1, 2].max())"))
        assertEquals("2\n", output("println([1, 2, 3].indexOf(3))"))
        assertEquals("true\n", output("println([1, 2, 3].contains(2))"))
        assertEquals("a-b-c\n", output("println([\"a\", \"b\", \"c\"].join(\"-\"))"))
    }

    @Test
    fun mapOperations() {
        assertEquals("true\n", output("println({\"a\": 1}.has(\"a\"))"))
        assertEquals("false\n", output("let m = {\"a\": 1}\nm.remove(\"a\")\nprintln(m.has(\"a\"))"))
        assertEquals("[\"a\", \"b\"]\n", output("println({\"a\": 1, \"b\": 2}.keys())"))
        assertEquals("[1, 2]\n", output("println({\"a\": 1, \"b\": 2}.values())"))
        assertEquals("[[\"a\", 1]]\n", output("println({\"a\": 1}.entries())"))
    }

    @Test
    fun sortHandlesStrings() {
        assertEquals("[\"apple\", \"cherry\", \"pear\"]\n", output("println([\"pear\", \"apple\", \"cherry\"].sort())"))
    }

    @Test
    fun assertPassesSilentlyAndFailsLoudly() {
        assertEquals("ok\n", output("assert(1 == 1)\nprintln(\"ok\")"))
        val error = assertFailsWith<RuntimeError> { output("assert(1 == 2, \"nope\")") }
        assertTrue(error.message!!.contains("nope"), "expected message to carry the assertion text")
    }
}
