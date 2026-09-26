package lumen.compiler

import lumen.CompileError
import lumen.ast.*
import lumen.bytecode.Chunk
import lumen.bytecode.Opcode
import lumen.runtime.BoolValue
import lumen.runtime.DoubleValue
import lumen.runtime.FunctionValue
import lumen.runtime.IntValue
import lumen.runtime.StringValue

private enum class FunctionType { Script, Function }

private class Local(val name: String, var depth: Int, var captured: Boolean, val mutable: Boolean)

private class UpvalueDesc(val index: Int, val isLocal: Boolean, val mutable: Boolean)

private class FunctionScope(
    val enclosing: FunctionScope?,
    val type: FunctionType,
    val name: String
) {
    val chunk = Chunk()
    val locals = ArrayList<Local>()
    val upvalues = ArrayList<UpvalueDesc>()
    var scopeDepth = 0
    var arity = 0

    init {
        locals.add(Local("", 0, false, false))
    }
}

class Compiler(private val globalMutability: MutableMap<String, Boolean> = mutableMapOf()) {
    private lateinit var scope: FunctionScope
    private var currentColumn = 0

    fun compile(program: List<Stmt>, returnLastExpr: Boolean = false): FunctionValue {
        scope = FunctionScope(null, FunctionType.Script, "script")
        if (returnLastExpr && program.isNotEmpty() && program.last() is ExprStmt) {
            for (stmt in program.dropLast(1)) statement(stmt)
            val tail = program.last() as ExprStmt
            expression(tail.expr)
            emit(Opcode.Return, tail.line)
        } else {
            for (stmt in program) statement(stmt)
            emitReturnNil(lastLine(program))
        }
        return FunctionValue("script", 0, scope.chunk, scope.upvalues.size)
    }

    private fun lastLine(program: List<Stmt>): Int = if (program.isEmpty()) 1 else program.last().line

    private fun statement(stmt: Stmt) {
        currentColumn = stmt.column
        when (stmt) {
            is ImportStmt -> if (scope.scopeDepth > 0) {
                throw CompileError("import is only allowed at module top level", stmt.line)
            }
            is LetStmt -> letStatement(stmt)
            is ExprStmt -> {
                expression(stmt.expr)
                emit(Opcode.Pop, stmt.line)
            }
            is BlockStmt -> {
                beginScope()
                stmt.statements.forEach { statement(it) }
                endScope(stmt.line)
            }
            is IfStmt -> ifStatement(stmt)
            is WhileStmt -> whileStatement(stmt)
            is ForStmt -> forStatement(stmt)
            is ReturnStmt -> returnStatement(stmt)
            is FunctionStmt -> functionStatement(stmt)
        }
    }

    private fun letStatement(stmt: LetStmt) {
        if (scope.scopeDepth == 0) {
            expression(stmt.initializer)
            emit(Opcode.DefineGlobal, stmt.line)
            emit(nameConstant(stmt.name), stmt.line)
            globalMutability[stmt.name] = stmt.mutable
        } else {
            declareLocal(stmt.name, stmt.mutable, stmt.line)
            expression(stmt.initializer)
            markInitialized()
        }
    }

    private fun ifStatement(stmt: IfStmt) {
        expression(stmt.condition)
        val thenJump = emitJump(Opcode.JumpIfFalse, stmt.line)
        emit(Opcode.Pop, stmt.line)
        beginScope()
        stmt.thenBranch.forEach { statement(it) }
        endScope(stmt.line)
        val elseJump = emitJump(Opcode.Jump, stmt.line)
        patchJump(thenJump)
        emit(Opcode.Pop, stmt.line)
        if (stmt.elseBranch != null) {
            beginScope()
            stmt.elseBranch.forEach { statement(it) }
            endScope(stmt.line)
        }
        patchJump(elseJump)
    }

    private fun whileStatement(stmt: WhileStmt) {
        val loopStart = scope.chunk.code.size
        expression(stmt.condition)
        val exitJump = emitJump(Opcode.JumpIfFalse, stmt.line)
        emit(Opcode.Pop, stmt.line)
        beginScope()
        stmt.body.forEach { statement(it) }
        endScope(stmt.line)
        emitLoop(loopStart, stmt.line)
        patchJump(exitJump)
        emit(Opcode.Pop, stmt.line)
    }

    private fun forStatement(stmt: ForStmt) {
        beginScope()
        expression(stmt.iterable)
        emit(Opcode.GetIter, stmt.line)
        val iterSlot = addSyntheticLocal("\$iter")
        emit(Opcode.Nil, stmt.line)
        val varSlot = declareLocal(stmt.name, true, stmt.line)
        markInitialized()

        val loopStart = scope.chunk.code.size
        emit(Opcode.ForIter, stmt.line)
        emit(iterSlot, stmt.line)
        val exitPatch = emit(0, stmt.line)
        emit(Opcode.SetLocal, stmt.line)
        emit(varSlot, stmt.line)
        emit(Opcode.Pop, stmt.line)

        beginScope()
        stmt.body.forEach { statement(it) }
        endScope(stmt.line)

        emitLoop(loopStart, stmt.line)
        patchJump(exitPatch)
        endScope(stmt.line)
    }

    private fun returnStatement(stmt: ReturnStmt) {
        if (scope.type == FunctionType.Script) {
            throw CompileError("'return' outside of a function", stmt.line)
        }
        if (stmt.value == null) {
            emitReturnNil(stmt.line)
        } else {
            expression(stmt.value)
            emit(Opcode.Return, stmt.line)
        }
    }

    private fun functionStatement(stmt: FunctionStmt) {
        if (scope.scopeDepth == 0) {
            compileFunction(stmt.function)
            emit(Opcode.DefineGlobal, stmt.line)
            emit(nameConstant(stmt.name), stmt.line)
            globalMutability[stmt.name] = true
        } else {
            declareLocal(stmt.name, true, stmt.line)
            markInitialized()
            compileFunction(stmt.function)
        }
    }

    private fun expression(expr: Expr) {
        currentColumn = expr.column
        when (expr) {
            is IntLiteral -> emitConstant(IntValue(expr.value), expr.line)
            is DoubleLiteral -> emitConstant(DoubleValue(expr.value), expr.line)
            is StringLiteral -> emitConstant(StringValue(expr.value), expr.line)
            is BoolLiteral -> emit(if (expr.value) Opcode.True else Opcode.False, expr.line)
            is NilLiteral -> emit(Opcode.Nil, expr.line)
            is ListLiteral -> {
                expr.elements.forEach { expression(it) }
                emit(Opcode.BuildList, expr.line)
                emit(expr.elements.size, expr.line)
            }
            is MapLiteral -> {
                expr.entries.forEach { (key, value) ->
                    expression(key)
                    expression(value)
                }
                emit(Opcode.BuildMap, expr.line)
                emit(expr.entries.size, expr.line)
            }
            is Variable -> variableAccess(expr.name, expr.line)
            is Assign -> assignment(expr)
            is Index -> {
                expression(expr.target)
                expression(expr.index)
                emit(Opcode.IndexGet, expr.line)
            }
            is IndexAssign -> {
                expression(expr.target)
                expression(expr.index)
                expression(expr.value)
                emit(Opcode.IndexSet, expr.line)
            }
            is Unary -> {
                expression(expr.operand)
                emit(if (expr.op == UnaryOp.Negate) Opcode.Negate else Opcode.Not, expr.line)
            }
            is Binary -> {
                expression(expr.left)
                expression(expr.right)
                emitBinary(expr.op, expr.line)
            }
            is Logical -> logical(expr)
            is Call -> {
                expression(expr.callee)
                expr.args.forEach { expression(it) }
                currentColumn = expr.column
                emit(Opcode.Call, expr.line)
                emit(expr.args.size, expr.line)
            }
            is MethodCall -> {
                expression(expr.receiver)
                expr.args.forEach { expression(it) }
                currentColumn = expr.column
                emit(Opcode.Invoke, expr.line)
                emit(nameConstant(expr.method), expr.line)
                emit(expr.args.size, expr.line)
            }
            is FunctionExpr -> compileFunction(expr)
        }
    }

    private fun assignment(expr: Assign) {
        expression(expr.value)
        val local = resolveLocal(scope, expr.name, expr.line)
        if (local != -1) {
            if (!scope.locals[local].mutable) rejectImmutable(expr)
            emit(Opcode.SetLocal, expr.line)
            emit(local, expr.line)
            return
        }
        val upvalue = resolveUpvalue(scope, expr.name, expr.line)
        if (upvalue != -1) {
            if (!scope.upvalues[upvalue].mutable) rejectImmutable(expr)
            emit(Opcode.SetUpvalue, expr.line)
            emit(upvalue, expr.line)
            return
        }
        if (globalMutability[expr.name] == false) rejectImmutable(expr)
        emit(Opcode.SetGlobal, expr.line)
        emit(nameConstant(expr.name), expr.line)
    }

    private fun rejectImmutable(expr: Assign): Nothing =
        throw CompileError("cannot assign to immutable '${expr.name}'", expr.line)

    private fun variableAccess(name: String, line: Int) {
        val local = resolveLocal(scope, name, line)
        if (local != -1) {
            emit(Opcode.GetLocal, line)
            emit(local, line)
            return
        }
        val upvalue = resolveUpvalue(scope, name, line)
        if (upvalue != -1) {
            emit(Opcode.GetUpvalue, line)
            emit(upvalue, line)
            return
        }
        emit(Opcode.GetGlobal, line)
        emit(nameConstant(name), line)
    }

    private fun logical(expr: Logical) {
        when (expr.op) {
            LogicalOp.And -> {
                expression(expr.left)
                val endJump = emitJump(Opcode.JumpIfFalse, expr.line)
                emit(Opcode.Pop, expr.line)
                expression(expr.right)
                patchJump(endJump)
            }
            LogicalOp.Or -> {
                expression(expr.left)
                val elseJump = emitJump(Opcode.JumpIfFalse, expr.line)
                val endJump = emitJump(Opcode.Jump, expr.line)
                patchJump(elseJump)
                emit(Opcode.Pop, expr.line)
                expression(expr.right)
                patchJump(endJump)
            }
        }
    }

    private fun emitBinary(op: BinaryOp, line: Int) {
        val opcode = when (op) {
            BinaryOp.Add -> Opcode.Add
            BinaryOp.Sub -> Opcode.Sub
            BinaryOp.Mul -> Opcode.Mul
            BinaryOp.Div -> Opcode.Div
            BinaryOp.Mod -> Opcode.Mod
            BinaryOp.Equal -> Opcode.Equal
            BinaryOp.NotEqual -> Opcode.NotEqual
            BinaryOp.Less -> Opcode.Less
            BinaryOp.LessEqual -> Opcode.LessEqual
            BinaryOp.Greater -> Opcode.Greater
            BinaryOp.GreaterEqual -> Opcode.GreaterEqual
        }
        emit(opcode, line)
    }

    private fun compileFunction(fnExpr: FunctionExpr) {
        val enclosing = scope
        scope = FunctionScope(enclosing, FunctionType.Function, fnExpr.name ?: "anonymous")
        scope.arity = fnExpr.params.size
        beginScope()
        for (param in fnExpr.params) {
            declareLocal(param, true, fnExpr.line)
            markInitialized()
        }
        fnExpr.body.forEach { statement(it) }
        emitReturnNil(fnExpr.line)

        val compiled = scope
        val function = FunctionValue(compiled.name, compiled.arity, compiled.chunk, compiled.upvalues.size)
        scope = enclosing

        emit(Opcode.Closure, fnExpr.line)
        emit(addConstant(function), fnExpr.line)
        for (upvalue in compiled.upvalues) {
            emit(if (upvalue.isLocal) 1 else 0, fnExpr.line)
            emit(upvalue.index, fnExpr.line)
        }
    }

    private fun beginScope() {
        scope.scopeDepth++
    }

    private fun endScope(line: Int) {
        scope.scopeDepth--
        while (scope.locals.isNotEmpty() && scope.locals.last().depth > scope.scopeDepth) {
            if (scope.locals.last().captured) {
                emit(Opcode.CloseUpvalue, line)
            } else {
                emit(Opcode.Pop, line)
            }
            scope.locals.removeAt(scope.locals.size - 1)
        }
    }

    private fun declareLocal(name: String, mutable: Boolean, line: Int): Int {
        for (i in scope.locals.indices.reversed()) {
            val local = scope.locals[i]
            if (local.depth != -1 && local.depth < scope.scopeDepth) break
            if (local.name == name) {
                throw CompileError("'$name' is already declared in this scope", line)
            }
        }
        scope.locals.add(Local(name, -1, false, mutable))
        return scope.locals.size - 1
    }

    private fun addSyntheticLocal(name: String): Int {
        scope.locals.add(Local(name, scope.scopeDepth, false, false))
        return scope.locals.size - 1
    }

    private fun markInitialized() {
        if (scope.scopeDepth == 0) return
        scope.locals.last().depth = scope.scopeDepth
    }

    private fun resolveLocal(target: FunctionScope, name: String, line: Int): Int {
        for (i in target.locals.indices.reversed()) {
            val local = target.locals[i]
            if (local.name == name) {
                if (local.depth == -1) {
                    throw CompileError("cannot read '$name' in its own initializer", line)
                }
                return i
            }
        }
        return -1
    }

    private fun resolveUpvalue(target: FunctionScope, name: String, line: Int): Int {
        val enclosing = target.enclosing ?: return -1
        val local = resolveLocal(enclosing, name, line)
        if (local != -1) {
            enclosing.locals[local].captured = true
            return addUpvalue(target, local, true, enclosing.locals[local].mutable)
        }
        val upvalue = resolveUpvalue(enclosing, name, line)
        if (upvalue != -1) {
            return addUpvalue(target, upvalue, false, enclosing.upvalues[upvalue].mutable)
        }
        return -1
    }

    private fun addUpvalue(target: FunctionScope, index: Int, isLocal: Boolean, mutable: Boolean): Int {
        for (i in target.upvalues.indices) {
            val existing = target.upvalues[i]
            if (existing.index == index && existing.isLocal == isLocal) return i
        }
        target.upvalues.add(UpvalueDesc(index, isLocal, mutable))
        return target.upvalues.size - 1
    }

    private fun nameConstant(name: String): Int = addConstant(StringValue(name))

    private fun addConstant(value: lumen.runtime.Value): Int = scope.chunk.addConstant(value)

    private fun emitConstant(value: lumen.runtime.Value, line: Int) {
        emit(Opcode.Constant, line)
        emit(addConstant(value), line)
    }

    private fun emit(op: Opcode, line: Int): Int = scope.chunk.emit(op, line, currentColumn)

    private fun emit(byte: Int, line: Int): Int = scope.chunk.emit(byte, line, currentColumn)

    private fun emitReturnNil(line: Int) {
        emit(Opcode.Nil, line)
        emit(Opcode.Return, line)
    }

    private fun emitJump(op: Opcode, line: Int): Int {
        emit(op, line)
        return emit(0, line)
    }

    private fun patchJump(offsetIndex: Int) {
        val jump = scope.chunk.code.size - (offsetIndex + 1)
        scope.chunk.patch(offsetIndex, jump)
    }

    private fun emitLoop(loopStart: Int, line: Int) {
        emit(Opcode.Loop, line)
        val offset = scope.chunk.code.size + 1 - loopStart
        emit(offset, line)
    }
}
