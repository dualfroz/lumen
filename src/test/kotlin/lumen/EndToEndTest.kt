package lumen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndToEndTest {
    private fun exampleFile(name: String): File {
        val candidates = listOf(File("examples/$name"), File("../examples/$name"))
        return candidates.firstOrNull { it.exists() } ?: error("example not found: $name")
    }

    private fun runExample(name: String): String = Lumen.runToString(exampleFile(name).readText())

    private fun runFileExample(name: String): String {
        val buffer = StringBuilder()
        Lumen.runFile(exampleFile(name), buffer)
        return buffer.toString()
    }

    @Test
    fun fibonacciExample() {
        val expected = buildString {
            val values = listOf(0, 1, 1, 2, 3, 5, 8, 13, 21, 34)
            values.forEachIndexed { index, value -> append("fib($index) = $value\n") }
        }
        assertEquals(expected, runExample("fib.lumen"))
    }

    @Test
    fun fizzBuzzExample() {
        val expected = buildString {
            for (n in 1..15) {
                when {
                    n % 15 == 0 -> append("FizzBuzz\n")
                    n % 3 == 0 -> append("Fizz\n")
                    n % 5 == 0 -> append("Buzz\n")
                    else -> append("$n\n")
                }
            }
        }
        assertEquals(expected, runExample("fizzbuzz.lumen"))
    }

    @Test
    fun quicksortExample() {
        val expected = "input:  [5, 3, 8, 1, 9, 2, 7, 4, 6, 0]\nsorted: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]\n"
        assertEquals(expected, runExample("sort.lumen"))
    }

    @Test
    fun closuresExample() {
        val expected = buildString {
            append("counter: 1 2 3\n")
            append("add10(5) = 15\n")
            append("add100(5) = 105\n")
            append("doubled: [2, 4, 6, 8, 10]\n")
            append("evens: [2, 4]\n")
            append("sum: 15\n")
        }
        assertEquals(expected, runExample("closures.lumen"))
    }

    @Test
    fun stdlibDemoExample() {
        val output = runExample("stdlib_demo.lumen")
        assertTrue(output.contains("sorted:    [1, 2, 3, 5, 8, 9]"), output)
        assertTrue(output.contains("entries:   [[\"alice\", 90], [\"bob\", 75]]"), output)
        assertTrue(output.contains("all assertions passed"), output)
    }

    @Test
    fun calculatorExampleUsesImportAndStdlib() {
        val expected = buildString {
            append("3 4 + 5 * 2 - = 33\n")
            append("2 3 4 * + = 14\n")
            append("10 2 / 3 - = 2\n")
            append("6! = 720\n")
            append("gcd(48, 36) = 12\n")
            append("calc checks passed\n")
        }
        assertEquals(expected, runFileExample("calc.lumen"))
    }

    @Test
    fun csvExampleUsesImportAndStdlib() {
        val expected = buildString {
            append("columns: name | score\n")
            append("Alice scored 90\n")
            append("Bob scored 75\n")
            append("Carol scored 88\n")
            append("total: 253\n")
            append("best:  90\n")
            append("csv checks passed\n")
        }
        assertEquals(expected, runFileExample("csv.lumen"))
    }

    @Test
    fun allExampleFilesExist() {
        listOf("fib.lumen", "fizzbuzz.lumen", "sort.lumen", "closures.lumen").forEach {
            assertTrue(runExample(it).isNotEmpty(), "example $it produced no output")
        }
    }
}
