package lumen.lexer

enum class TokenType {
    IntLiteral,
    DoubleLiteral,
    StringLiteral,
    Identifier,

    Let,
    Var,
    Fn,
    Return,
    If,
    Else,
    While,
    For,
    In,
    True,
    False,
    Nil,
    And,
    Or,
    Not,
    Import,

    Plus,
    Minus,
    Star,
    Slash,
    Percent,
    Assign,
    EqualEqual,
    BangEqual,
    Less,
    LessEqual,
    Greater,
    GreaterEqual,
    LeftParen,
    RightParen,
    LeftBracket,
    RightBracket,
    LeftBrace,
    RightBrace,
    Comma,
    Colon,
    Semicolon,
    Dot,
    Newline,

    Eof
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val line: Int,
    val column: Int = 1,
    val intValue: Long = 0L,
    val doubleValue: Double = 0.0,
    val stringValue: String = ""
)
