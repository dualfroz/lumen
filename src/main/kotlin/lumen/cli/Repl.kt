package lumen.cli

import lumen.LumenError
import lumen.compiler.Compiler
import lumen.formatError
import lumen.lexer.Lexer
import lumen.module.ModuleError
import lumen.module.ModuleLoader
import lumen.parser.Parser
import lumen.runtime.NilValue
import lumen.runtime.displayString
import lumen.vm.Vm
import java.io.File

class Repl {
    private val vm = Vm()
    private val globalMutability = mutableMapOf<String, Boolean>()
    private val loader = ModuleLoader(vm, globalMutability)
    private val origin = File("<repl>")

    fun start() {
        println("lumen repl - type 'exit' to quit")
        val reader = System.`in`.bufferedReader()
        while (true) {
            print("lumen> ")
            System.out.flush()
            val line = reader.readLine() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed == "exit" || trimmed == "quit") break
            evaluate(line)
        }
        println()
    }

    private fun evaluate(source: String) {
        try {
            val program = Parser(Lexer(source).scan()).parseProgram()
            loader.loadImports(origin, program)
            val script = Compiler(globalMutability).compile(program, returnLastExpr = true)
            val result = vm.interpret(script)
            if (result !is NilValue) println(displayString(result))
        } catch (error: ModuleError) {
            System.err.println(formatError(error.source, error.error))
        } catch (error: LumenError) {
            System.err.println(formatError(source, error))
        }
    }
}
