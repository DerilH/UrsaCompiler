package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import java.io.PrintStream
import java.nio.file.Files

class SemaCommand : CliktCommand(
    name = "sema",
    help = "Run the semantic analyzer on input"
) {

    val options: Options by requireObject()

    override fun run() {
        val code = Files.readString(options.inputFile)
        val out = RunHelper.analyze(code, options.inputFile, options)
        val printer = out.createPrinter()
        if (out.hasErrors()) {
            out.parseResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
            out.analyzeResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
        } else {
            val astString = printer.astToString(out.analyzeResult!!.ast);
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


