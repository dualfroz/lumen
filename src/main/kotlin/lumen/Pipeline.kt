package lumen

import lumen.ast.Stmt
import lumen.compiler.Compiler
import lumen.lexer.Lexer
import lumen.lexer.Token
import lumen.module.ModuleLoader
import lumen.parser.Parser
import lumen.runtime.FunctionValue
import lumen.vm.Vm
import java.io.File

object Lumen {
    fun tokenize(source: String): List<Token> = Lexer(source).scan()

    fun parse(source: String): List<Stmt> = Parser(tokenize(source)).parseProgram()

    fun compile(source: String): FunctionValue = Compiler().compile(parse(source))

    fun run(source: String, out: Appendable = System.out): Value {
        val script = compile(source)
        return Vm(out).interpret(script)
    }

    fun runToString(source: String): String {
        val buffer = StringBuilder()
        run(source, buffer)
        return buffer.toString()
    }

    fun runFile(file: File, out: Appendable = System.out): Value {
        val vm = Vm(out)
        return ModuleLoader(vm).load(file)
    }

    fun runFileToString(path: String): String {
        val buffer = StringBuilder()
        runFile(File(path), buffer)
        return buffer.toString()
    }
}

typealias Value = lumen.runtime.Value
