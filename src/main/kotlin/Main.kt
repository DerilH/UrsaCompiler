package org.derilh

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.core.IncludePaths
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.core.target.TargetFactory
import org.derilh.run.CompileCommand
import org.derilh.run.LexerCommand
import org.derilh.run.ParserCommand
import org.derilh.run.PreprocessorCommand
import org.derilh.run.SemaCommand
import java.nio.file.Path


class IncludeOptions : OptionGroup(name = "Include Path Options") {
    val quoteIncludePaths by option("-iquote", help = "Add directory to quote include search path").path().multiple()
    private val includePaths by option("-I", help = "Add directory to system include search path").path().multiple()
    private val systemIncludePaths by option("-isystem", help = "Add directory to system include search path").path().multiple()

    fun buildWith(system: List<Path>): IncludePaths {
        val combinedSystem = includePaths + systemIncludePaths + system
        return IncludePaths(quoteIncludePaths = quoteIncludePaths, systemIncludePaths = combinedSystem)
    }
}

class UrsaCCommand : CliktCommand(
    name = "ursac",
    help = "Ursa compilers(c++)"
) {
    private val target by option("-t", "--target", help = "Target architecture").choice(*TargetFactory.getSupportedTargets())
    private val printTerminal by option("--terminal", help = "Prints result to terminal").flag(default = false)
    private val inputPath by option("-i", "--input", help = "Source file path").path(mustExist = true, canBeFile = true, mustBeReadable = true).required()
    private val outputPath by option("-o", "--output", help = "Output file path").path(mustExist = false, canBeFile = true, mustBeWritable = false)
    private val traceErrors by option("--traceErrors", help = "Adds stack trace to errors").flag()

    val includes by IncludeOptions();

    init {
        subcommands(PreprocessorCommand())
        subcommands(LexerCommand())
        subcommands(ParserCommand())
        subcommands(SemaCommand())
        subcommands(CompileCommand())
    }

    override fun run() {
        val target = TargetFactory.createTarget(target)
        val outputMethod = when {
            printTerminal && outputPath != null ->
                throw UsageError("Only one output method can be specified: use --terminal or --output(-o)")

            !printTerminal && outputPath == null ->
                throw UsageError("Output method is not specified: use --terminal or --output(-o)")

            printTerminal ->
                OutputMethod.Terminal

            else ->
                OutputMethod.File(outputPath!!)
        }

        currentContext.findOrSetObject { Options(target, traceErrors, inputPath, outputMethod, includes.buildWith(target.detectIncludes())) }
    }
}


fun main(args: Array<String>) {
    UrsaCCommand().main(args)
}