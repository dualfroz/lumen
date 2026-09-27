package lumen

import lumen.lexer.Lexer
import lumen.lexer.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LexerTest {
    private fun types(source: String) = Lexer(source).scan().map { it.type }

    @Test
    fun scansDeclaration() {
        assertEquals(
            listOf(TokenType.Let, TokenType.Identifier, TokenType.Assign, TokenType.IntLiteral, TokenType.Eof),
            types("let x = 42")
        )
    }

    @Test
    fun distinguishesIntAndDouble() {
        val tokens = Lexer("3 3.14").scan()
        assertEquals(TokenType.IntLiteral, tokens[0].type)
        assertEquals(3L, tokens[0].intValue)
        assertEquals(TokenType.DoubleLiteral, tokens[1].type)
        assertEquals(3.14, tokens[1].doubleValue)
    }

    @Test
    fun decodesStringEscapes() {
        val token = Lexer("\"a\\nb\\t\\\"c\"").scan().first()
        assertEquals(TokenType.StringLiteral, token.type)
        assertEquals("a\nb\t\"c", token.stringValue)
    }

    @Test
    fun skipsLineAndBlockComments() {
        assertEquals(
            listOf(TokenType.IntLiteral, TokenType.Newline, TokenType.IntLiteral, TokenType.Eof),
            types("1 // trailing\n2")
        )
        assertEquals(
            listOf(TokenType.IntLiteral, TokenType.IntLiteral, TokenType.Eof),
            types("1 /* nested /* block */ still */ 2")
        )
    }

    @Test
    fun emitsNewlineTokensButNotInsideGrouping() {
        assertEquals(
            listOf(TokenType.IntLiteral, TokenType.Newline, TokenType.IntLiteral, TokenType.Eof),
            types("1\n2")
        )
        assertEquals(
            listOf(TokenType.LeftParen, TokenType.IntLiteral, TokenType.RightParen, TokenType.Eof),
            types("(\n1\n)")
        )
    }

    @Test
    fun bracesInsideGroupingRestoreNewlines() {
        assertEquals(
            listOf(
                TokenType.LeftParen, TokenType.LeftBrace, TokenType.IntLiteral, TokenType.Newline,
                TokenType.IntLiteral, TokenType.RightBrace, TokenType.IntLiteral, TokenType.RightParen,
                TokenType.Newline, TokenType.IntLiteral, TokenType.Eof
            ),
            types("({1\n2}\n3)\n4")
        )
    }

    @Test
    fun decodesNulEscape() {
        val token = Lexer("\"a\\0b\"").scan().first()
        assertEquals("a\u0000b", token.stringValue)
        assertEquals(3, token.stringValue.length)
    }

    @Test
    fun scansOperators() {
        assertEquals(
            listOf(
                TokenType.EqualEqual, TokenType.BangEqual, TokenType.LessEqual, TokenType.GreaterEqual,
                TokenType.Less, TokenType.Greater, TokenType.Plus, TokenType.Minus,
                TokenType.Star, TokenType.Slash, TokenType.Percent, TokenType.Eof
            ),
            types("== != <= >= < > + - * / %")
        )
    }

    @Test
    fun rejectsUnterminatedString() {
        assertFailsWith<LexError> { Lexer("\"open").scan() }
    }

    @Test
    fun recognizesKeywords() {
        val keywordTypes = types("fn if else while for in return and or not true false nil")
        assertTrue(keywordTypes.contains(TokenType.Fn))
        assertTrue(keywordTypes.contains(TokenType.While))
        assertTrue(keywordTypes.contains(TokenType.And))
        assertTrue(keywordTypes.contains(TokenType.Nil))
    }
}
