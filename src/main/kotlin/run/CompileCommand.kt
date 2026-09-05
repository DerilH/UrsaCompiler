package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.core.target.TargetFactory
import org.derilh.exceptions.ProblemLevel
import org.derilh.ir.LLVMIRBuilder
import org.derilh.lexer.Lexer
import org.derilh.util.Printer
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.absolutePathString

class CompileCommand : CliktCommand(
    name = "compile",
    help = "Run all pipeline steps to compile a source file"
) {
    private val emitIR by option("--emit-IR", help = "Adds stack trace to errors").flag()

    val options: Options by requireObject()
    override fun run() {
        val code = Files.readString(options.inputFile)
        val out = RunHelper.analyze(code, options.inputFile, options)
        val printer = out.createPrinter()
        if (!out.isValidForCodegen()) {
            out.parseResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
            out.analyzeResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
            return;
        }

        printer.printSuccess()


        val module = LLVMIRBuilder(options).generate(out.analyzeResult!!.ast);
        val outMethod = out.options.outputMethod;
        val outputPath = if (outMethod is OutputMethod.Terminal) "/proc/self/fd/1" else (outMethod as OutputMethod.File).file.absolutePathString()
        if (emitIR) {
            module.emitIRTo(outputPath)
        } else {
            module.compileTo(outputPath)
        }
    }
}

