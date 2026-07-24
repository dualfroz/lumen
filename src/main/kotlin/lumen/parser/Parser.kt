package lumen.parser

import lumen.ParseError
import lumen.ast.*
import lumen.lexer.Token
import lumen.lexer.TokenType

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parseProgram(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        skipSeparators()
        while (!check(TokenType.Eof)) {
            statements.add(statement())
            skipSeparators()
        }
        return statements
    }

    private fun statement(): Stmt = when (peek().type) {
        TokenType.Let -> declaration(mutable = false)
        TokenType.Var -> declaration(mutable = true)
        TokenType.Import -> importStatement()
        TokenType.Fn -> if (peekNext().type == TokenType.Identifier) functionStatement() else expressionStatement()
        TokenType.Return -> returnStatement()
        TokenType.If -> ifStatement()
        TokenType.While -> whileStatement()
        TokenType.For -> forStatement()
        else -> expressionStatement()
    }

    private fun declaration(mutable: Boolean): Stmt {
        val keyword = advance()
        val name = consume(TokenType.Identifier, "expected variable name").lexeme
        consume(TokenType.Assign, "expected '=' in declaration")
        val initializer = expression()
        return LetStmt(name, initializer, mutable, keyword.line, keyword.column)
    }

    private fun importStatement(): Stmt {
        val keyword = advance()
        val path = consume(TokenType.StringLiteral, "expected module path string after 'import'")
        return ImportStmt(path.stringValue, keyword.line, keyword.column)
    }

    private fun functionStatement(): Stmt {
        val keyword = advance()
        val name = consume(TokenType.Identifier, "expected function name").lexeme
        val params = parameters()
        val body = block()
        return FunctionStmt(name, FunctionExpr(name, params, body, keyword.line, keyword.column), keyword.line, keyword.column)
    }

    private fun returnStatement(): Stmt {
        val keyword = advance()
        val value = if (isStatementEnd()) null else expression()
        return ReturnStmt(value, keyword.line, keyword.column)
    }

    private fun ifStatement(): Stmt {
        val keyword = advance()
        consume(TokenType.LeftParen, "expected '(' after 'if'")
        val condition = expression()
        consume(TokenType.RightParen, "expected ')' after condition")
        val thenBranch = block()
        var elseBranch: List<Stmt>? = null
        if (matchElse()) {
            elseBranch = if (check(TokenType.If)) listOf(ifStatement()) else block()
        }
        return IfStmt(condition, thenBranch, elseBranch, keyword.line, keyword.column)
    }

    private fun whileStatement(): Stmt {
        val keyword = advance()
        consume(TokenType.LeftParen, "expected '(' after 'while'")
        val condition = expression()
        consume(TokenType.RightParen, "expected ')' after condition")
        val body = block()
        return WhileStmt(condition, body, keyword.line, keyword.column)
    }

    private fun forStatement(): Stmt {
        val keyword = advance()
        consume(TokenType.LeftParen, "expected '(' after 'for'")
        val name = consume(TokenType.Identifier, "expected loop variable name").lexeme
        consume(TokenType.In, "expected 'in' in for loop")
        val iterable = expression()
        consume(TokenType.RightParen, "expected ')' after for header")
        val body = block()
        return ForStmt(name, iterable, body, keyword.line, keyword.column)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        return ExprStmt(expr, expr.line, expr.column)
    }

    private fun block(): List<Stmt> {
        consume(TokenType.LeftBrace, "expected '{'")
        val statements = mutableListOf<Stmt>()
        skipSeparators()
        while (!check(TokenType.RightBrace) && !check(TokenType.Eof)) {
            statements.add(statement())
            skipSeparators()
        }
        consume(TokenType.RightBrace, "expected '}' to close block")
        return statements
    }

    private fun parameters(): List<String> {
        consume(TokenType.LeftParen, "expected '(' after function name")
        val params = mutableListOf<String>()
        if (!check(TokenType.RightParen)) {
            do {
                params.add(consume(TokenType.Identifier, "expected parameter name").lexeme)
            } while (match(TokenType.Comma))
        }
        consume(TokenType.RightParen, "expected ')' after parameters")
        return params
    }

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val target = or()
        if (check(TokenType.Assign)) {
            val op = advance()
            val value = assignment()
            return when (target) {
                is Variable -> Assign(target.name, value, target.line, target.column)
                is Index -> IndexAssign(target.target, target.index, value, target.line, target.column)
                else -> throw ParseError("invalid assignment target", op.line, op.column)
            }
        }
        return target
    }

    private fun or(): Expr {
        var left = and()
        while (check(TokenType.Or)) {
            val op = advance()
            val right = and()
            left = Logical(LogicalOp.Or, left, right, op.line, op.column)
        }
        return left
    }

    private fun and(): Expr {
        var left = equality()
        while (check(TokenType.And)) {
            val op = advance()
            val right = equality()
            left = Logical(LogicalOp.And, left, right, op.line, op.column)
        }
        return left
    }

    private fun equality(): Expr {
        var left = comparison()
        while (check(TokenType.EqualEqual) || check(TokenType.BangEqual)) {
            val token = advance()
            val op = if (token.type == TokenType.EqualEqual) BinaryOp.Equal else BinaryOp.NotEqual
            val right = comparison()
            left = Binary(op, left, right, token.line, token.column)
        }
        return left
    }

    private fun comparison(): Expr {
        var left = term()
        while (true) {
            val op = when (peek().type) {
                TokenType.Less -> BinaryOp.Less
                TokenType.LessEqual -> BinaryOp.LessEqual
                TokenType.Greater -> BinaryOp.Greater
                TokenType.GreaterEqual -> BinaryOp.GreaterEqual
                else -> break
            }
            val token = advance()
            val right = term()
            left = Binary(op, left, right, token.line, token.column)
        }
        return left
    }

    private fun term(): Expr {
        var left = factor()
        while (true) {
            val op = when (peek().type) {
                TokenType.Plus -> BinaryOp.Add
                TokenType.Minus -> BinaryOp.Sub
                else -> break
            }
            val token = advance()
            val right = factor()
            left = Binary(op, left, right, token.line, token.column)
        }
        return left
    }

    private fun factor(): Expr {
        var left = unary()
        while (true) {
            val op = when (peek().type) {
                TokenType.Star -> BinaryOp.Mul
                TokenType.Slash -> BinaryOp.Div
                TokenType.Percent -> BinaryOp.Mod
                else -> break
            }
            val token = advance()
            val right = unary()
            left = Binary(op, left, right, token.line, token.column)
        }
        return left
    }

    private fun unary(): Expr {
        if (check(TokenType.Minus)) {
            val op = advance()
            return Unary(UnaryOp.Negate, unary(), op.line, op.column)
        }
        if (check(TokenType.Not)) {
            val op = advance()
            return Unary(UnaryOp.Not, unary(), op.line, op.column)
        }
        return callChain()
    }

    private fun callChain(): Expr {
        var expr = primary()
        while (true) {
            when (peek().type) {
                TokenType.LeftParen -> {
                    advance()
                    expr = Call(expr, argumentList(), expr.line, expr.column)
                }
                TokenType.LeftBracket -> {
                    advance()
                    val index = expression()
                    consume(TokenType.RightBracket, "expected ']' after index")
                    expr = Index(expr, index, expr.line, expr.column)
                }
                TokenType.Dot -> {
                    advance()
                    val name = consume(TokenType.Identifier, "expected method name after '.'")
                    consume(TokenType.LeftParen, "expected '(' after method name")
                    expr = MethodCall(expr, name.lexeme, argumentList(), name.line, name.column)
                }
                else -> break
            }
        }
        return expr
    }

    private fun argumentList(): List<Expr> {
        val args = mutableListOf<Expr>()
        skipNewlines()
        if (!check(TokenType.RightParen)) {
            do {
                skipNewlines()
                args.add(expression())
                skipNewlines()
            } while (match(TokenType.Comma))
        }
        skipNewlines()
        consume(TokenType.RightParen, "expected ')' after arguments")
        return args
    }

    private fun primary(): Expr {
        val token = peek()
        return when (token.type) {
            TokenType.IntLiteral -> {
                advance()
                IntLiteral(token.intValue, token.line, token.column)
            }
            TokenType.DoubleLiteral -> {
                advance()
                DoubleLiteral(token.doubleValue, token.line, token.column)
            }
            TokenType.StringLiteral -> {
                advance()
                StringLiteral(token.stringValue, token.line, token.column)
            }
            TokenType.True -> {
                advance()
                BoolLiteral(true, token.line, token.column)
            }
            TokenType.False -> {
                advance()
                BoolLiteral(false, token.line, token.column)
            }
            TokenType.Nil -> {
                advance()
                NilLiteral(token.line, token.column)
            }
            TokenType.Identifier -> {
                advance()
                Variable(token.lexeme, token.line, token.column)
            }
            TokenType.LeftParen -> {
                advance()
                val inner = expression()
                consume(TokenType.RightParen, "expected ')' after expression")
                inner
            }
            TokenType.LeftBracket -> listLiteral()
            TokenType.LeftBrace -> mapLiteral()
            TokenType.Fn -> functionExpression()
            else -> throw ParseError("unexpected ${describe(token)}", token.line, token.column)
        }
    }

    private fun listLiteral(): Expr {
        val open = advance()
        val elements = mutableListOf<Expr>()
        skipNewlines()
        if (!check(TokenType.RightBracket)) {
            do {
                skipNewlines()
                if (check(TokenType.RightBracket)) break
                elements.add(expression())
                skipNewlines()
            } while (match(TokenType.Comma))
        }
        skipNewlines()
        consume(TokenType.RightBracket, "expected ']' after list")
        return ListLiteral(elements, open.line, open.column)
    }

    private fun mapLiteral(): Expr {
        val open = advance()
        val entries = mutableListOf<Pair<Expr, Expr>>()
        skipNewlines()
        if (!check(TokenType.RightBrace)) {
            do {
                skipNewlines()
                if (check(TokenType.RightBrace)) break
                val key = expression()
                consume(TokenType.Colon, "expected ':' in map entry")
                val value = expression()
                entries.add(key to value)
                skipNewlines()
            } while (match(TokenType.Comma))
        }
        skipNewlines()
        consume(TokenType.RightBrace, "expected '}' after map")
        return MapLiteral(entries, open.line, open.column)
    }

    private fun functionExpression(): Expr {
        val keyword = advance()
        val name = if (check(TokenType.Identifier)) advance().lexeme else null
        val params = parameters()
        val body = block()
        return FunctionExpr(name, params, body, keyword.line, keyword.column)
    }

    private fun isStatementEnd(): Boolean =
        check(TokenType.Newline) || check(TokenType.Semicolon) ||
            check(TokenType.RightBrace) || check(TokenType.Eof)

    private fun matchElse(): Boolean {
        val saved = current
        skipNewlines()
        if (check(TokenType.Else)) {
            advance()
            return true
        }
        current = saved
        return false
    }

    private fun skipSeparators() {
        while (check(TokenType.Newline) || check(TokenType.Semicolon)) advance()
    }

    private fun skipNewlines() {
        while (check(TokenType.Newline)) advance()
    }

    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        val found = peek()
        throw ParseError("$message, got ${describe(found)}", found.line, found.column)
    }

    private fun describe(token: Token): String = when (token.type) {
        TokenType.Newline -> "end of line"
        TokenType.Eof -> "end of input"
        else -> "'${token.lexeme.ifEmpty { token.type.name }}'"
    }

    private fun check(type: TokenType): Boolean = peek().type == type

    private fun advance(): Token = tokens[current++]

    private fun peek(): Token = tokens[current]

    private fun peekNext(): Token = if (current + 1 < tokens.size) tokens[current + 1] else tokens.last()
}
