package lumen.ast

sealed class Expr {
    abstract val line: Int
    abstract val column: Int
}

class IntLiteral(val value: Long, override val line: Int, override val column: Int = 0) : Expr()
class DoubleLiteral(val value: Double, override val line: Int, override val column: Int = 0) : Expr()
class StringLiteral(val value: String, override val line: Int, override val column: Int = 0) : Expr()
class BoolLiteral(val value: Boolean, override val line: Int, override val column: Int = 0) : Expr()
class NilLiteral(override val line: Int, override val column: Int = 0) : Expr()

class ListLiteral(val elements: List<Expr>, override val line: Int, override val column: Int = 0) : Expr()
class MapLiteral(val entries: List<Pair<Expr, Expr>>, override val line: Int, override val column: Int = 0) : Expr()

class Variable(val name: String, override val line: Int, override val column: Int = 0) : Expr()
class Assign(val name: String, val value: Expr, override val line: Int, override val column: Int = 0) : Expr()

class Index(val target: Expr, val index: Expr, override val line: Int, override val column: Int = 0) : Expr()
class IndexAssign(val target: Expr, val index: Expr, val value: Expr, override val line: Int, override val column: Int = 0) : Expr()

class Unary(val op: UnaryOp, val operand: Expr, override val line: Int, override val column: Int = 0) : Expr()
class Binary(val op: BinaryOp, val left: Expr, val right: Expr, override val line: Int, override val column: Int = 0) : Expr()
class Logical(val op: LogicalOp, val left: Expr, val right: Expr, override val line: Int, override val column: Int = 0) : Expr()

class Call(val callee: Expr, val args: List<Expr>, override val line: Int, override val column: Int = 0) : Expr()
class MethodCall(val receiver: Expr, val method: String, val args: List<Expr>, override val line: Int, override val column: Int = 0) : Expr()
class FunctionExpr(val name: String?, val params: List<String>, val body: List<Stmt>, override val line: Int, override val column: Int = 0) : Expr()

enum class UnaryOp { Negate, Not }
enum class BinaryOp { Add, Sub, Mul, Div, Mod, Equal, NotEqual, Less, LessEqual, Greater, GreaterEqual }
enum class LogicalOp { And, Or }

sealed class Stmt {
    abstract val line: Int
    abstract val column: Int
}

class LetStmt(val name: String, val initializer: Expr, val mutable: Boolean, override val line: Int, override val column: Int = 0) : Stmt()
class ExprStmt(val expr: Expr, override val line: Int, override val column: Int = 0) : Stmt()
class BlockStmt(val statements: List<Stmt>, override val line: Int, override val column: Int = 0) : Stmt()
class IfStmt(val condition: Expr, val thenBranch: List<Stmt>, val elseBranch: List<Stmt>?, override val line: Int, override val column: Int = 0) : Stmt()
class WhileStmt(val condition: Expr, val body: List<Stmt>, override val line: Int, override val column: Int = 0) : Stmt()
class ForStmt(val name: String, val iterable: Expr, val body: List<Stmt>, override val line: Int, override val column: Int = 0) : Stmt()
class ReturnStmt(val value: Expr?, override val line: Int, override val column: Int = 0) : Stmt()
class FunctionStmt(val name: String, val function: FunctionExpr, override val line: Int, override val column: Int = 0) : Stmt()
class ImportStmt(val path: String, override val line: Int, override val column: Int = 0) : Stmt()
