package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import org.derilh.ast.Parser
import org.derilh.core.Options
import org.derilh.core.target.TargetFactory
import org.derilh.lexer.Lexer
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.util.printTree
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.time.Clock

class ParseCommand : CliktCommand(
    name = "parser",
    help = "Run the parser on a source file to obtain AST tree"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val output by option("-o", "--output", help = "Source file path")
        .path(mustExist = false, canBeFile = true, mustBeWritable = true)

    private val target by option("-tg", "--target", help = "Target architecture").choice(*TargetFactory.getSupportedTargets()).required()
    private val traceErrors by option("--traceErrors", help = "Adds stack trace to errors").flag()
    private val printTerminal by option("-t", "--terminal", help = "Prints result to terminal").flag()

    override fun run() {
        var code = Files.readString(inputPath)
        val preProcessor = PreProcessor();
        code = preProcessor.preProcess(code, inputPath)

        val lexer = Lexer()
        val tokens = lexer.tokenize(code, inputPath.toString())

        val opts = Options(TargetFactory.createTarget(target), traceErrors);
        val parser = Parser(tokens, SemanticAnalyzer(opts), opts)
        val parseResult = parser.parse();

        if (output != null) {
            System.setOut(PrintStream(output!!.toFile()))
            parseResult.root.printTree();
            System.setOut(System.out)
        }

        if (printTerminal) {
            parseResult.root.printTree();
        }

        if (output == null && !printTerminal) error("No output method specified. Use -o or to to specify an output file or terminal.")
    }
}

