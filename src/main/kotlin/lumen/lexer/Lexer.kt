package lumen.lexer

import lumen.LexError

private val keywords = mapOf(
    "let" to TokenType.Let,
    "var" to TokenType.Var,
    "fn" to TokenType.Fn,
    "return" to TokenType.Return,
    "if" to TokenType.If,
    "else" to TokenType.Else,
    "while" to TokenType.While,
    "for" to TokenType.For,
    "in" to TokenType.In,
    "true" to TokenType.True,
    "false" to TokenType.False,
    "nil" to TokenType.Nil,
    "and" to TokenType.And,
    "or" to TokenType.Or,
    "not" to TokenType.Not,
    "import" to TokenType.Import
)

class Lexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var lineStart = 0
    private var groupingDepth = 0
    private val enclosingGroupingDepths = ArrayDeque<Int>()

    fun scan(): List<Token> {
        while (!atEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.Eof, "", line, current - lineStart + 1))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            ' ', '\t', '\r' -> {}
            '\n' -> {
                if (groupingDepth == 0) add(TokenType.Newline)
                line++
                lineStart = current
            }
            '(' -> {
                groupingDepth++
                add(TokenType.LeftParen)
            }
            ')' -> {
                if (groupingDepth > 0) groupingDepth--
                add(TokenType.RightParen)
            }
            '[' -> {
                groupingDepth++
                add(TokenType.LeftBracket)
            }
            ']' -> {
                if (groupingDepth > 0) groupingDepth--
                add(TokenType.RightBracket)
            }
            '{' -> {
                enclosingGroupingDepths.addLast(groupingDepth)
                groupingDepth = 0
                add(TokenType.LeftBrace)
            }
            '}' -> {
                groupingDepth = enclosingGroupingDepths.removeLastOrNull() ?: 0
                add(TokenType.RightBrace)
            }
            ',' -> add(TokenType.Comma)
            ':' -> add(TokenType.Colon)
            ';' -> add(TokenType.Semicolon)
            '.' -> add(TokenType.Dot)
            '+' -> add(TokenType.Plus)
            '-' -> add(TokenType.Minus)
            '*' -> add(TokenType.Star)
            '%' -> add(TokenType.Percent)
            '/' -> when {
                match('/') -> lineComment()
                match('*') -> blockComment()
                else -> add(TokenType.Slash)
            }
            '=' -> add(if (match('=')) TokenType.EqualEqual else TokenType.Assign)
            '!' -> {
                if (match('=')) add(TokenType.BangEqual)
                else throw LexError("unexpected character '!'", line, columnOf(start))
            }
            '<' -> add(if (match('=')) TokenType.LessEqual else TokenType.Less)
            '>' -> add(if (match('=')) TokenType.GreaterEqual else TokenType.Greater)
            '"' -> string()
            else -> when {
                c.isDigit() -> number()
                isIdentStart(c) -> identifier()
                else -> throw LexError("unexpected character '$c'", line, columnOf(start))
            }
        }
    }

    private fun lineComment() {
        while (!atEnd() && peek() != '\n') advance()
    }

    private fun blockComment() {
        var depth = 1
        while (!atEnd() && depth > 0) {
            val c = advance()
            when {
                c == '\n' -> {
                    line++
                    lineStart = current
                }
                c == '/' && peek() == '*' -> {
                    advance()
                    depth++
                }
                c == '*' && peek() == '/' -> {
                    advance()
                    depth--
                }
            }
        }
        if (depth > 0) throw LexError("unterminated block comment", line, columnOf(start))
    }

    private fun string() {
        val builder = StringBuilder()
        while (!atEnd() && peek() != '"') {
            val c = advance()
            when {
                c == '\n' -> {
                    builder.append('\n')
                    line++
                    lineStart = current
                }
                c == '\\' -> builder.append(escape())
                else -> builder.append(c)
            }
        }
        if (atEnd()) throw LexError("unterminated string", line, columnOf(start))
        advance()
        tokens.add(Token(TokenType.StringLiteral, builder.toString(), line, columnOf(start), stringValue = builder.toString()))
    }

    private fun escape(): Char {
        if (atEnd()) throw LexError("unterminated escape sequence", line, columnOf(start))
        return when (val e = advance()) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            '"' -> '"'
            '\\' -> '\\'
            '0' -> '\u0000'
            else -> throw LexError("invalid escape '\\$e'", line, columnOf(current - 2))
        }
    }

    private fun number() {
        while (peek().isDigit()) advance()
        var isDouble = false
        if (peek() == '.' && peekNext().isDigit()) {
            isDouble = true
            advance()
            while (peek().isDigit()) advance()
        }
        val text = source.substring(start, current)
        if (isDouble) {
            tokens.add(Token(TokenType.DoubleLiteral, text, line, columnOf(start), doubleValue = text.toDouble()))
        } else {
            tokens.add(Token(TokenType.IntLiteral, text, line, columnOf(start), intValue = text.toLong()))
        }
    }

    private fun identifier() {
        while (isIdentPart(peek())) advance()
        val text = source.substring(start, current)
        add(keywords[text] ?: TokenType.Identifier)
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'

    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun add(type: TokenType) {
        tokens.add(Token(type, source.substring(start, current), line, columnOf(start)))
    }

    private fun columnOf(index: Int): Int = index - lineStart + 1

    private fun advance(): Char = source[current++]

    private fun match(expected: Char): Boolean {
        if (atEnd() || source[current] != expected) return false
        current++
        return true
    }

    private fun peek(): Char = if (atEnd()) ' ' else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) ' ' else source[current + 1]

    private fun atEnd(): Boolean = current >= source.length
}
