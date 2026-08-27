package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.ast.Parser
import org.derilh.core.Options
import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SemanticProblem
import org.derilh.lexer.Lexer
import org.derilh.semantic.AnalyzeResult
import org.derilh.target.X86_64LinuxTargetInfo
import org.derilh.util.Printer
import org.derilh.util.printTree
import java.nio.file.Files
import java.nio.file.Path

class SemaCommand : CliktCommand(
    name = "sema",
    help = "Run semantic analyzer on AST tree"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val printAst by option("-ast", "--printAst", help = "Prints ast to terminal").flag()
    private val traceErrors by option("--traceErrors", help = "Adds stack trace to errors").flag()

    private val output by option("-o", "--output", help = "Source file path")
        .path(mustExist = false, canBeFile = true, mustBeWritable = true)


    private val target by option("-t", "--target", help = "Target architecture").choice("x86_64Linux", "x86_32Linux", "x86_64Windows", "x86_32Windows").required()

    override fun run() {
        val code = Files.readString(inputPath);

        val options = Options(target, printAst, traceErrors)
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
        System.out.flush()
        if(printAst) result.second.ast.printTree()
    }
}

