package org.derilh

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.derilh.run.LexerCommand
import org.derilh.run.ParseCommand
import org.derilh.run.SemaCommand


class UrsacApp : CliktCommand(
    name = "ursac",
    help = "Ursa compilers lexer(c++)"
) {

    init {
        subcommands(LexerCommand())
        subcommands(ParseCommand())
        subcommands(SemaCommand())
    }

    override fun run() {
    }
}



fun main(args: Array<String>) {
    UrsacApp().main(args)
}