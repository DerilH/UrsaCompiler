package org.derilh.util

import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SemanticProblem
import org.derilh.exceptions.SyntaxProblem
import org.derilh.lexer.KeywordToken
import org.derilh.lexer.Lexer
import org.derilh.lexer.ValueToken
import java.awt.Color

class Printer(val source: String, val lexer: Lexer) {
    val lines = source.lines();

    companion object {
        const val BOLD = "\u001b[1m"
        const val RESET = "\u001b[0m"
        const val KEYWORD_COLOR = "\u001b[1;34m"
        const val LITERAL_COLOR = "\u001b[1;32m"
        const val RED_COLOR = "\u001b[0;31m"
        const val BOLD_GREEN_COLOR = "\u001b[1;32m"
    }

    fun printSuccess() {
        println("SUCCESS".withColor(BOLD_GREEN_COLOR))
    }

    fun printException(e: SyntaxProblem) {
        val loc = e.location;
        var line = lines[loc.line - 1]
        val builder = StringBuilder()
        var currentPos = 0
        for (token in lexer.tokenize(line, loc.file)) {
            val start = token.location.column - 1
            val end = start + token.location.length

            if (start > currentPos) {
                builder.append(line.substring(currentPos, start))
            }

            val tokenText = line.substring(start, end)
            val color = when(token) {
                is KeywordToken -> KEYWORD_COLOR
                is ValueToken<*> -> LITERAL_COLOR
                else -> RESET;
            }
            builder.append("$color$tokenText\u001b[0m")
            currentPos = end
        }
        line = builder.toString();
        val outStr = "${loc.file}:${loc.line}:${loc.column}: ${e.level.toString().withLevelColor(e.level)}: ${e.msg.withBold()}\n " +
                "  ${loc.line} |$line\n" +
                "  ${" ".repeat(loc.line.toString().length)}  |${" ".repeat(loc.column - 1)}${"^".withColor(LITERAL_COLOR)}"

        if(e.trace != null) {
            for (elem in e.trace) {
                println("${RED_COLOR}${elem}${RESET}")
            }
        }
        println(outStr)

    }

    fun printException(e: SemanticProblem) {
        val loc = e.node?.location ?: e.location
        if (loc != null) {
            var line = lines[loc.line - 1]
            val builder = StringBuilder()
            var currentPos = 0
            for (token in lexer.tokenize(line, loc.file)) {
                val start = token.location.column - 1
                val end = start + token.location.length

                if (start > currentPos) {
                    builder.append(line.substring(currentPos, start))
                }

                val tokenText = line.substring(start, end)
                val color = when (token) {
                    is KeywordToken -> KEYWORD_COLOR
                    is ValueToken<*> -> LITERAL_COLOR
                    else -> RESET;
                }
                builder.append("$color$tokenText\u001b[0m")
                currentPos = end
            }
            line = builder.toString();
            val outStr = "${loc.file}:${loc.line}:${loc.column}: ${e.level.toString().withLevelColor(e.level)}: ${e.msg.withBold()}\n " +
                    "  ${loc.line} |$line\n" +
                    "  ${" ".repeat(loc.line.toString().length)}  |${" ".repeat(loc.column - 1)}${"^".withColor(LITERAL_COLOR)}"

            println(outStr)
        }
        else {
            println("${e.level}: ${e.msg}")
        }

        if(e.trace != null) {
            for (elem in e.trace) {
                println("${RED_COLOR}${elem}${RESET}")
            }
        }
    }
    fun String.getLineAtCharIndex(index: Int): String {
        if (index !in indices) throw IndexOutOfBoundsException("Index $index out of bounds")

        val start = this.lastIndexOf('\n', index).let { if (it == -1) 0 else it + 1 }
        val end = this.indexOf('\n', index).let { if (it == -1) this.length else it }

        return this.substring(start, end)
    }

    private fun String.withBold(): String  {
        return "$BOLD$this$RESET"
    }

    private fun String.withColor(color: String): String  {
        return "$color$this$RESET"
    }

    private fun String.withLevelColor(problem: org.derilh.exceptions.ProblemLevel): String  {
        val color = when(problem) {
            ProblemLevel.WARNING -> "\u001b[1;35m"
            ProblemLevel.ERROR -> "\u001b[1;31m"
        }
        return withColor(color)
    }
}