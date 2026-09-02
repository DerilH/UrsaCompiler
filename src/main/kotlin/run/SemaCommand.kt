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
import org.derilh.util.printTree
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.absolutePathString

class SemaCommand : CliktCommand(
    name = "sema",
    help = "Run the semantic analyzer on input"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val printTerminal by option("--terminal", help = "Prints ast to terminal").flag()
    private val traceErrors by option("--traceErrors", help = "Adds stack trace to errors").flag()

    private val output by option("-o", "--output", help = "Source file path")
        .path(mustExist = false, canBeFile = true, mustBeWritable = true)

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

        if (output != null) {
            System.setOut(PrintStream(output!!.toFile()))
            result.second.ast.printTree();
            System.setOut(System.out)
        }

        if (printTerminal) {
            result.second.ast.printTree();
        }

        if (output == null && !printTerminal) error("No output method specified. Use -o or to to specify an output file or terminal.")

    }
}

