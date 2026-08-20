package lumen

import lumen.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun respectsArithmeticPrecedence() {
        val program = Lumen.parse("1 + 2 * 3")
        val expr = (program.single() as ExprStmt).expr
        val add = assertIs<Binary>(expr)
        assertEquals(BinaryOp.Add, add.op)
        assertIs<IntLiteral>(add.left)
        val mul = assertIs<Binary>(add.right)
        assertEquals(BinaryOp.Mul, mul.op)
    }

    @Test
    fun comparisonBindsLooserThanArithmetic() {
        val expr = (Lumen.parse("a + b < c").single() as ExprStmt).expr
        val comparison = assertIs<Binary>(expr)
        assertEquals(BinaryOp.Less, comparison.op)
        assertIs<Binary>(comparison.left)
        assertIs<Variable>(comparison.right)
    }

    @Test
    fun parsesLetAndVar() {
        val program = Lumen.parse("let a = 1\nvar b = 2")
        val letStmt = assertIs<LetStmt>(program[0])
        assertEquals("a", letStmt.name)
        assertEquals(false, letStmt.mutable)
        val varStmt = assertIs<LetStmt>(program[1])
        assertEquals(true, varStmt.mutable)
    }

    @Test
    fun parsesIfElseChain() {
        val program = Lumen.parse("if (x) { a } else if (y) { b } else { c }")
        val ifStmt = assertIs<IfStmt>(program.single())
        assertEquals(1, ifStmt.thenBranch.size)
        val elseBranch = ifStmt.elseBranch
        assertTrue(elseBranch != null)
        assertIs<IfStmt>(elseBranch.single())
    }

    @Test
    fun parsesFunctionDefinition() {
        val program = Lumen.parse("fn add(a, b) { return a + b }")
        val fn = assertIs<FunctionStmt>(program.single())
        assertEquals("add", fn.name)
        assertEquals(listOf("a", "b"), fn.function.params)
        assertIs<ReturnStmt>(fn.function.body.single())
    }

    @Test
    fun parsesListAndMapAndIndex() {
        val list = (Lumen.parse("[1, 2, 3]").single() as ExprStmt).expr
        assertEquals(3, assertIs<ListLiteral>(list).elements.size)
        val map = (Lumen.parse("{\"a\": 1}").single() as ExprStmt).expr
        assertEquals(1, assertIs<MapLiteral>(map).entries.size)
        val index = (Lumen.parse("xs[0]").single() as ExprStmt).expr
        assertIs<Index>(index)
    }

    @Test
    fun parsesIndexAssignment() {
        val expr = (Lumen.parse("xs[0] = 9").single() as ExprStmt).expr
        assertIs<IndexAssign>(expr)
    }

    @Test
    fun parsesForOverIterable() {
        val program = Lumen.parse("for (x in items) { print(x) }")
        val forStmt = assertIs<ForStmt>(program.single())
        assertEquals("x", forStmt.name)
        assertIs<Variable>(forStmt.iterable)
    }

    @Test
    fun logicalShortCircuitOperators() {
        val expr = (Lumen.parse("a and b or c").single() as ExprStmt).expr
        val or = assertIs<Logical>(expr)
        assertEquals(LogicalOp.Or, or.op)
        assertEquals(LogicalOp.And, assertIs<Logical>(or.left).op)
    }

    @Test
    fun rejectsInvalidAssignmentTarget() {
        assertFailsWith<ParseError> { Lumen.parse("1 + 2 = 3") }
    }

    @Test
    fun rejectsUnterminatedBlock() {
        assertFailsWith<ParseError> { Lumen.parse("if (x) { a") }
    }
}
