package lumen

open class LumenError(
    val rawMessage: String,
    val line: Int,
    val column: Int = 0
) : RuntimeException(
    buildString {
        if (line > 0) {
            append("[line ").append(line)
            if (column > 0) append(", column ").append(column)
            append("] ")
        }
        append(rawMessage)
    }
)

class LexError(message: String, line: Int, column: Int = 0) : LumenError(message, line, column)

class ParseError(message: String, line: Int, column: Int = 0) : LumenError(message, line, column)

class CompileError(message: String, line: Int, column: Int = 0) : LumenError(message, line, column)

class RuntimeError(message: String, line: Int, column: Int = 0) : LumenError(message, line, column)

fun formatError(source: String, error: LumenError): String {
    val header = "error: ${error.rawMessage}"
    if (error.line <= 0) return header
    val lines = source.split("\n")
    if (error.line > lines.size) {
        return "$header\n --> line ${error.line}"
    }
    val sourceLine = lines[error.line - 1]
    val gutter = error.line.toString()
    val pad = " ".repeat(gutter.length)
    val builder = StringBuilder()
    builder.append(header).append("\n")
    val position = if (error.column > 0) "line ${error.line}, column ${error.column}" else "line ${error.line}"
    builder.append(pad).append(" --> ").append(position).append("\n")
    builder.append(pad).append(" |\n")
    builder.append(gutter).append(" | ").append(sourceLine).append("\n")
    if (error.column > 0) {
        val caretPad = " ".repeat((error.column - 1).coerceAtLeast(0))
        builder.append(pad).append(" | ").append(caretPad).append("^")
    } else {
        builder.append(pad).append(" |")
    }
    return builder.toString()
}
