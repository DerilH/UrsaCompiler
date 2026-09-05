package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.util.Printer
import java.nio.file.Files
import kotlin.io.path.writeText

class PreprocessorCommand : CliktCommand(
    name = "preprocessor",
    help = "Run the preprocessor on a source file"
) {
    val options: Options by requireObject()

    override fun run() {
        val originalCode = Files.readString(options.inputFile)
        val module = RunHelper.runLexer(originalCode, options);

        val outMethod = options.outputMethod
        if(outMethod == OutputMethod.Terminal) {
            print(module.createPrinter().restoreSource(module.tokens!!))
        } else if(outMethod is OutputMethod.File) {
            Files.createFile(outMethod.file).writeText(module.createPrinter().restoreSource(module.tokens!!))
        }
    }
}

