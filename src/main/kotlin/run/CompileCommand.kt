package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.core.Options
import org.derilh.core.target.TargetFactory
import org.derilh.exceptions.ProblemLevel
import org.derilh.ir.LLVMIRBuilder
import org.derilh.lexer.Lexer
import org.derilh.util.Printer
import java.nio.file.Files

class CompileCommand : CliktCommand(
    name = "compile",
    help = "Run ursac compiler"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val printTerminal by option("--terminal", help = "Prints ast to terminal").flag()
    private val traceErrors by option("--traceErrors", help = "Adds stack trace to errors").flag()
    private val emitIR by option("--emit-IR", help = "Adds stack trace to errors").flag()

    private val output by option("-o", "--output", help = "Source file path")
        .path(mustExist = false, canBeFile = true)


    private val target by option("-t", "--target", help = "Target architecture").choice(*TargetFactory.getSupportedTargets()).required()

    override fun run() {
        val code = Files.readString(inputPath);

        val options = Options(TargetFactory.createTarget(target), traceErrors)
        val result = RunHelper.analyze(code, inputPath, options)
        val printer = Printer(code, Lexer())

        result.second.problems.values.forEach { it ->
            it.forEach {
                printer.printException(it)
            }
        }
        result.first.problems.values.forEach { it ->
            it.forEach {
                printer.printException(it)
            }
        }

        if(result.second.problems[ProblemLevel.ERROR]!!.isEmpty() && result.first.problems[ProblemLevel.ERROR]!!.isEmpty()) {
            printer.printSuccess()
        }

        val module = LLVMIRBuilder(options).generate(result.second.ast);
        if (output != null) {
            if(emitIR) {
                module.emitIRTo(output!!.toAbsolutePath().toString())
            } else {
                module.compileTo(output!!.toAbsolutePath().toString())
            }
        }

        if (printTerminal) {
            if(emitIR) {
                module.emitIRTo("/proc/self/fd/1")
            } else {
                module.compileTo("/proc/self/fd/1")
            }
        }

        if (output == null && !printTerminal) error("No output method specified. Use -o or -t to specify an output file or terminal.")

    }
}

