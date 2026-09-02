package org.derilh

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.derilh.run.CompileCommand
import org.derilh.run.LexerCommand
import org.derilh.run.ParseCommand
import org.derilh.run.PreprocessorCommand
import org.derilh.run.SemaCommand


class UrsacApp : CliktCommand(
    name = "ursac",
    help = "Ursa compilers(c++)"
) {

    init {
        subcommands(PreprocessorCommand())
        subcommands(LexerCommand())
        subcommands(ParseCommand())
        subcommands(SemaCommand())
        subcommands(CompileCommand())
    }

    override fun run() {
    }
}



fun main(args: Array<String>) {
    UrsacApp().main(args)
}