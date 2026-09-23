package org.derilh.run

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.core.target.TargetFactory
import org.derilh.exceptions.ProblemLevel
import org.derilh.ir.LLVMIRBuilder
import org.derilh.lexer.Lexer
import org.derilh.util.Printer
import run.EmitFormat
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.absolutePathString

enum class OptLevel(val ir: String) {
    O0("O0"),
    O1("O1"),
    O2("O2"),
    O3("O3")
}


class CompileCommand : CliktCommand(
    name = "compile",
    help = "Run all pipeline steps to compile a source file"
) {
    val emitFormat: EmitFormat by option().switch(
        "--emit-IR" to EmitFormat.IR,
        "--emit-ASM" to EmitFormat.ASM,
        "--emit-BIN" to EmitFormat.BINARY
    ).default(EmitFormat.BINARY)

    val optLevel: OptLevel by option(help = "Optimization level").switch(
        "-O0" to OptLevel.O0,
        "-O1" to OptLevel.O1,
        "-O2" to OptLevel.O2,
        "-O3" to OptLevel.O3
    ).default(OptLevel.O0)

    val options: Options by requireObject()
    override fun run() {
        val code = Files.readString(options.inputFile)
        val out = RunHelper.analyze(code, options.inputFile, options)
        val printer = out.createPrinter()
        if (!out.isValidForCodegen()) {
            out.parseResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
            out.analyzeResult!!.problems.flatMap { it.value }.forEach { printer.printException(it) }
            return;
        }

        printer.printSuccess()


        val module = LLVMIRBuilder(options).generate(out.analyzeResult!!.ast);
        val outMethod = out.options.outputMethod;
        val outputPath = if (outMethod is OutputMethod.Terminal) "/proc/self/fd/1" else (outMethod as OutputMethod.File).file.absolutePathString()

        module.optimize(optLevel.ir);
        module.compileTo(outputPath, emitFormat)
    }
}

