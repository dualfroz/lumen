package lumen.cli

import lumen.Lumen
import lumen.LumenError
import lumen.bytecode.Disassembler
import lumen.formatError
import lumen.module.ModuleError
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        Repl().start()
        return
    }
    if (args[0] == "--dump" && args.size > 1) {
        Disassembler.dump(Lumen.compile(File(args[1]).readText()))
        return
    }
    val path = args[0]
    val file = File(path)
    if (!file.exists()) {
        System.err.println("error: file not found: $path")
        exitProcess(1)
    }
    try {
        Lumen.runFile(file)
    } catch (error: ModuleError) {
        System.err.println(formatError(error.source, error.error))
        exitProcess(1)
    } catch (error: LumenError) {
        System.err.println(formatError(file.readText(), error))
        exitProcess(1)
    }
}
