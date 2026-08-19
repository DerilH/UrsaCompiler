package org.derilh.util

import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SemanticProblem
import org.derilh.lexer.KeywordToken
import org.derilh.lexer.Lexer
import org.derilh.lexer.ValueToken

class Printer(val source: String, val lexer: Lexer) {
    companion object {
        const val BOLD = "\u001b[1m"
        const val RESET = "\u001b[0m"
        const val KEYWORD_COLOR = "\u001b[1;34m"
        const val LITERAL_COLOR = "\u001b[1;32m"
    }

    fun printException(e: SemanticProblem) {
        if (e.node == null || e.node.location == null) {
            println(e.msg)
            return
        }

        val loc = e.node.location!!;
        var line = source.getLineAtCharIndex(loc.index);
        val builder = StringBuilder()
        var currentPos = 0
        for (token in lexer.tokenize(line)) {
            val start = token.location.index
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
        val outStr = "${loc.file}:${loc.line}:${loc.column}: ${e.level.toString().withColor(e.level)}: ${e.msg.withBold()}\n " +
                "  ${loc.line} |$line\n" +
                "  ${" ".repeat(loc.line.toString().length)}  |${" ".repeat(loc.column - 1)}${"^".withColor(LITERAL_COLOR)}"

        println(outStr)
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

    private fun String.withColor(problem: ProblemLevel): String  {
        val color = when(problem) {
            ProblemLevel.WARNING -> "\u001b[1;35m"
            ProblemLevel.ERROR -> "\u001b[1;31m"
        }
        return withColor(color)
    }
}