package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import org.derilh.analyzer.SemanticAnalyzer
import org.derilh.ast.Parser
import org.derilh.lexer.Lexer
import org.derilh.target.X86_64LinuxTargetInfo
import org.derilh.util.Printer
import org.derilh.util.printTree
import java.nio.file.Files
import javax.swing.AbstractAction
import kotlin.io.path.writeText

class SemaCommand : CliktCommand(
    name = "sema",
    help = "Run semantic analyzer on AST tree"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val output by option("-o", "--output", help = "Source file path")
        .path(mustExist = false, canBeFile = true, mustBeWritable = true)


    private val target by option("-t", "--target", help = "Target architecture").choice("x86_64Linux", "x86_32Linux", "x86_64Windows", "x86_32Windows").required()

    override fun run() {
        var code = Files.readString(inputPath)
        val preProcessor = PreProcessor();
        code = preProcessor.preProcess(code, inputPath)

        val lexer = Lexer()
        val tokens = lexer.tokenize(code)

        val parser = Parser(tokens)
        val ast = parser.parse();


        val targetInfo = when(target) {
            "x86_64Linux" -> X86_64LinuxTargetInfo();
            else -> TODO("Target not supported yet")
        }

        val printer = Printer(code, lexer);
        val analyzer = SemanticAnalyzer(ast, targetInfo, printer);
        analyzer.analyze();
    }
}

