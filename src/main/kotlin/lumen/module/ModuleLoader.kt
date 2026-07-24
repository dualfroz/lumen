package lumen.module

import lumen.LumenError
import lumen.RuntimeError
import lumen.ast.ImportStmt
import lumen.ast.Stmt
import lumen.compiler.Compiler
import lumen.lexer.Lexer
import lumen.parser.Parser
import lumen.runtime.NilValue
import lumen.runtime.Value
import lumen.vm.Vm
import java.io.File

class ModuleError(val path: String, val source: String, val error: LumenError) :
    RuntimeException(error.message)

class ModuleLoader(
    private val vm: Vm,
    private val globalMutability: MutableMap<String, Boolean> = mutableMapOf()
) {
    private val loaded = HashSet<String>()
    private val loading = HashSet<String>()

    fun load(file: File): Value {
        val key = file.canonicalPath
        if (key in loaded || key in loading) return NilValue
        loading.add(key)
        val source = file.readText()
        try {
            val program = Parser(Lexer(source).scan()).parseProgram()
            loadImports(file, program)
            val script = Compiler(globalMutability).compile(program)
            val result = vm.interpret(script)
            loading.remove(key)
            loaded.add(key)
            return result
        } catch (error: LumenError) {
            throw ModuleError(file.path, source, error)
        }
    }

    fun loadImports(origin: File, program: List<Stmt>) {
        for (stmt in program) {
            if (stmt !is ImportStmt) continue
            val target = resolve(origin, stmt.path)
            if (!target.exists()) {
                throw RuntimeError("cannot import '${stmt.path}': file not found", stmt.line, stmt.column)
            }
            load(target)
        }
    }

    private fun resolve(origin: File, path: String): File {
        val direct = File(path)
        if (direct.isAbsolute) return direct
        val base = origin.absoluteFile.parentFile ?: File(".")
        return File(base, path)
    }
}
