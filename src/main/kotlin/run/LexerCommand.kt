package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.util.Printer
import java.nio.file.Files
import kotlin.io.path.writeText

class LexerCommand : CliktCommand(
    name = "lexer",
    help = "Run the lexer on a source file"
) {
    val options: Options by requireObject()

    override fun run() {
        val originalCode = Files.readString(options.inputFile)
        val module = RunHelper.runLexer(originalCode, options);

        val outMethod = options.outputMethod
        if(outMethod == OutputMethod.Terminal) {
            print(module.createPrinter().prettyTokens(module.tokens!!))
        } else if(outMethod is OutputMethod.File) {
            Files.createFile(outMethod.file).writeText(module.createPrinter().prettyTokens(module.tokens!!))
        }
    }
}

