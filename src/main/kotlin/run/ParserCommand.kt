package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import java.io.PrintStream
import java.nio.file.Files

class ParserCommand : CliktCommand(
    name = "parser",
    help = "Run the parser on a source file to obtain AST tree"
) {

    val options by requireObject<Options>()

    override fun run() {
        val code = Files.readString(options.inputFile)
        val out = RunHelper.parse(code, options.inputFile, options)
        val printer = out.createPrinter()
        if (!out.parseResult!!.isSuccess()) {
            out.parseResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
        } else {
            printer.printSuccess()
            val astString = printer.astToString(out.parseResult!!.ast);
            val outMethod = out.options.outputMethod
            if (outMethod is OutputMethod.Terminal) {
                print(astString)
            } else if (outMethod is OutputMethod.File) {
                System.setOut(PrintStream(outMethod.file.toFile()))
                print(astString)
                System.setOut(System.out)
            }
            printer.printSuccess()
        }
    }
}

