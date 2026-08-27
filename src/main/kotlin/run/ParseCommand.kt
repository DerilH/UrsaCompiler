package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import org.derilh.ast.Parser
import org.derilh.core.Options
import org.derilh.lexer.Lexer
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.target.TargetInfo
import org.derilh.target.X86_64LinuxTargetInfo
import org.derilh.util.printTree
import java.nio.file.Files

class ParseCommand : CliktCommand(
    name = "parser",
    help = "Run the lexer, preprocessor and parse on source file to obtain AST tree"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val output by option("-o", "--output", help = "Source file path")
        .path(mustExist = false, canBeFile = true, mustBeWritable = true)

    override fun run() {
        var code = Files.readString(inputPath)
        val preProcessor = PreProcessor();
        code = preProcessor.preProcess(code, inputPath)

        val lexer = Lexer()
        val tokens = lexer.tokenize(code, inputPath.toString())

        val opts = Options("x86_64Linux", false, false);
        val parser = Parser(tokens, SemanticAnalyzer(opts), opts)
        val parseResult = parser.parse();
        if(output != null) {
            TODO("Ast output to file not supported yet")
        } else {
            parseResult.root.printTree();
        }
    }
}

