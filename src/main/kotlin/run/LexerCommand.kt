package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import org.derilh.lexer.Lexer
import java.nio.file.Files
import kotlin.io.path.writeText

class LexerCommand : CliktCommand(
    name = "lexer",
    help = "Run the lexer on a source file"
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
        if(output != null) {
            Files.createFile(output!!).writeText(tokens.joinToString("\n") { it.toString() })
        } else {
            tokens.forEachIndexed { index, token ->  println("${token.location.line} : $token") }
        }
    }
}

