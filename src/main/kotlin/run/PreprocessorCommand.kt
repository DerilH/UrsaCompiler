package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.PreProcessor
import java.nio.file.Files
import kotlin.io.path.writeText

class PreprocessorCommand : CliktCommand(
    name = "preprocessor",
    help = "Run the preprocessor on a source file"
) {
    private val inputPath by option("-i", "--input", help = "Source file path")
        .path(mustExist = true, canBeFile = true, mustBeReadable = true)
        .required()

    private val output by option("-o", "--output", help = "File to output preprocessed code to")
        .path(mustExist = false, canBeFile = true, mustBeWritable = true)


    private val printTerminal by option("-t", "--terminal", help = "Prints result to terminal").flag()


    override fun run() {
        var code = Files.readString(inputPath)
        val preProcessor = PreProcessor();
        code = preProcessor.preProcess(code, inputPath)

        if(output != null) {
            Files.createFile(output!!).writeText(code)
        }
        if(printTerminal) {
            println(code)
        }

        if(output == null && !printTerminal) error("No output method specified. Use -o or -t to specify an output file or terminal.")
    }
}

