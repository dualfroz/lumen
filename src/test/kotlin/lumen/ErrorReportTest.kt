package lumen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ErrorReportTest {
    @Test
    fun lexErrorCarriesLineAndColumn() {
        val error = assertFailsWith<LexError> { Lumen.tokenize("let x = @") }
        assertEquals(1, error.line)
        assertEquals(9, error.column)
    }

    @Test
    fun parseErrorCarriesLineAndColumn() {
        val error = assertFailsWith<ParseError> { Lumen.parse("println(1 +)") }
        assertEquals(1, error.line)
        assertEquals(12, error.column)
    }

    @Test
    fun runtimeErrorCarriesLineAndColumn() {
        val error = assertFailsWith<RuntimeError> {
            Lumen.run("let xs = [1]\nprintln(xs[5])", StringBuilder())
        }
        assertEquals(2, error.line)
        assertTrue(error.column > 0, "runtime error should report a column")
    }

    @Test
    fun errorOnSecondLineReportsSecondLine() {
        val error = assertFailsWith<LexError> { Lumen.tokenize("let x = 1\nlet y = @") }
        assertEquals(2, error.line)
        assertEquals(9, error.column)
    }

    @Test
    fun formatErrorRendersSourceFragmentWithCaret() {
        val source = "let x = 1\nlet y = @"
        val error = assertFailsWith<LexError> { Lumen.tokenize(source) }
        val report = formatError(source, error)
        assertTrue(report.contains("line 2, column 9"), "report should name the position")
        assertTrue(report.contains("let y = @"), "report should quote the offending source line")
        assertTrue(report.contains("^"), "report should point at the column with a caret")
    }
}
