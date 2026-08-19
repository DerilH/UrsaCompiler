package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import org.derilh.ast.Parser
import org.derilh.lexer.Lexer
import org.derilh.util.printTree
import java.nio.file.Files
import kotlin.io.path.writeText

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
        val tokens = lexer.tokenize(code)

        val parser = Parser(tokens)
        val ast = parser.parse();
        if(output != null) {
            TODO("Ast output to file not supported yet")
        } else {
            ast.printTree();
        }
    }
}

