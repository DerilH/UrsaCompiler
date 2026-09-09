package org.derilh.util

import org.derilh.ast.ASTNode
import org.derilh.ast.DeclarationNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdentifierNode
import org.derilh.ast.RootNode
import org.derilh.ast.StatementNode
import org.derilh.ast.api.IASTNode
import org.derilh.core.CharPrefix
import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SemanticProblem
import org.derilh.exceptions.SyntaxProblem
import org.derilh.lexer.CharToken
import org.derilh.lexer.EofToken
import org.derilh.lexer.FloatToken
import org.derilh.lexer.IntToken
import org.derilh.lexer.KeywordToken
import org.derilh.lexer.Lexer
import org.derilh.lexer.StringLiteralToken
import org.derilh.lexer.Token
import org.derilh.lexer.ValueToken

class Printer(val source: String) {
    val lines = source.lines();

    companion object {
        private const val ANSI_RESET = "\u001b[0m"
        private const val KEYWORD_COLOR = "\u001b[1;34m"
        private const val LITERAL_COLOR = "\u001b[1;32m"
        private const val ANSI_BOLD = "\u001b[1m"
        private const val ANSI_BOLD_GREEN_COLOR = "\u001b[1;32m"
        private const val ANSI_RED = "\u001B[31m"
        private const val ANSI_GREEN = "\u001B[32m"
        private const val ANSI_YELLOW = "\u001B[33m"
        private const val ANSI_BLUE = "\u001B[34m"
        private const val ANSI_CYAN = "\u001B[36m"
        private const val ANSI_PURPLE = "\u001B[35m"
    }

    fun printSuccess() {
        println("SUCCESS".withColor(ANSI_BOLD_GREEN_COLOR))
    }

    fun printException(e: SyntaxProblem) {
        val loc = e.location;
        var line = lines.getOrNull(loc.line - 1) ?: run {
            println("${e.msg} at ${loc}}")
            return;
        }
        val builder = StringBuilder()
        var currentPos = 0
        val lexer = Lexer()
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
                else -> ANSI_RESET;
            }
            builder.append("$color$tokenText\u001b[0m")
            currentPos = end
        }
        line = builder.toString();
        val outStr = "${loc.file}:${loc.line}:${loc.column}: ${e.level.toString().withLevelColor(e.level)}: ${e.msg.withBold()}\n " +
                "  ${loc.line} |$line\n" +
                "  ${" ".repeat(loc.line.toString().length)}  |${" ".repeat(loc.column - 1)}${"^".withColor(LITERAL_COLOR)}"

        println(outStr)
        if(e.trace != null) {
            for (elem in e.trace) {
                println("${ANSI_RED}${elem}${ANSI_RESET}")
            }
        }
    }

    fun printException(e: SemanticProblem) {
        val loc = e.node?.location ?: e.location
        if (loc != null) {
            var line = lines[loc.line - 1]
            val builder = StringBuilder()
            var currentPos = 0
            val lexer = Lexer()
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
                    else -> ANSI_RESET;
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
                println("${ANSI_RED}${elem}${ANSI_RESET}")
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
        return "$ANSI_BOLD$this$ANSI_RESET"
    }

    private fun String.withColor(color: String): String  {
        return "$color$this$ANSI_RESET"
    }

    private fun String.withLevelColor(problem: org.derilh.exceptions.ProblemLevel): String  {
        val color = when(problem) {
            ProblemLevel.WARNING -> "\u001b[1;35m"
            ProblemLevel.ERROR -> "\u001b[1;31m"
        }
        return withColor(color)
    }

    fun prettyTokens(tokens: List<Token>): String {
        val builder = StringBuilder()
        for (token in tokens) {
            builder.append(token.toString())
            builder.append('\n')
        }
        return builder.toString()
    }

    fun restoreSource(tokens: List<Token>): String {
        if (tokens.isEmpty()) return ""

        val builder = StringBuilder()
        var lastLine = -1
        var printedColsOnCurrentLine = 0

        for (token in tokens) {
            if (token is EofToken) continue

            val currentLine = token.location.line
            val currentColumn = token.location.column

            if (lastLine != -1 && currentLine > lastLine) {
                val lineDiff = currentLine - lastLine
                repeat(lineDiff) {
                    builder.append('\n')
                }

                val indent = (currentColumn - 1).coerceAtLeast(0)
                repeat(indent) {
                    builder.append(' ')
                }
                printedColsOnCurrentLine = indent
            } else if (lastLine != -1) {
                val expectedCol = currentColumn
                val actualCol = printedColsOnCurrentLine + 1

                if (expectedCol > actualCol) {
                    val spacesNeeded = expectedCol - actualCol
                    repeat(spacesNeeded) {
                        builder.append(' ')
                    }
                    printedColsOnCurrentLine += spacesNeeded
                } else if (token.leadingWhitespace && builder.isNotEmpty() && !builder.endsWith(" ")) {
                    builder.append(' ')
                    printedColsOnCurrentLine += 1
                }
            } else {
                val initialIndent = (currentColumn - 1).coerceAtLeast(0)
                repeat(initialIndent) {
                    builder.append(' ')
                }
                printedColsOnCurrentLine = initialIndent
            }

            lastLine = currentLine

            val tokenText = renderTokenText(token)
            builder.append(tokenText)

            printedColsOnCurrentLine += tokenText.length
        }

        return builder.toString()
    }

    private fun renderTokenText(token: Token): String {
        val sb = StringBuilder()
        when (token) {
            is StringLiteralToken -> {
                if (token.prefix != CharPrefix.NONE) {
                    sb.append(token.prefix.name.lowercase())
                }
                sb.append('"').append(escapeString(token.stringValue)).append('"')
            }

            is CharToken -> {
                if (token.prefix != CharPrefix.NONE) {
                    sb.append(token.prefix.name.lowercase())
                }
                sb.append('\'').append(escapeChar(token.stringValue)).append('\'')
            }

            is IntToken -> {
                sb.append(token.stringValue)
                if (token.isUnsigned) sb.append('U')
                if (token.isLongLong) sb.append("LL")
                else if (token.isLong) sb.append('L')
            }

            is FloatToken -> {
                sb.append(token.stringValue)
                if (!token.isDouble) sb.append('f')
                else if (token.isLong) sb.append('L')
            }

            else -> sb.append(token.stringValue)
        }
        return sb.toString()
    }

    private fun escapeString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun escapeChar(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun astToString(ast: ASTNode): String {
        val builder = StringBuilder();
        ast.printAst(stringBuilder = builder);
        return builder.toString()
    }

    private fun IASTNode.color(): String =
        when (this) {
            is RootNode -> ANSI_PURPLE
            is DeclarationNode -> ANSI_BLUE
            is StatementNode -> ANSI_CYAN
            is ExpressionNode -> ANSI_YELLOW
//            is TypeNode -> ANSI_GREEN
            is IdentifierNode -> ANSI_RED
            else -> ANSI_RESET
        }

    private fun IASTNode.printAst(prefix: String = "", isLast: Boolean = true, stringBuilder: StringBuilder) {
        val connector = if (isLast) "└── " else "├── "
        val nodeColor = color()
        var nodeString = this.toString();
        if(this is ExpressionNode) {
            nodeString += " ${ANSI_RED}RESOLVED_TYPE${ANSI_RESET}: ${ANSI_BLUE}${this.resolvedType}${ANSI_RESET}"
        }
        stringBuilder.appendLine(prefix + connector + nodeColor + nodeString + ANSI_RESET)

        val childPrefix = prefix + if (isLast) "    " else "│   "
        children.forEachIndexed { index, child ->
            child.printAst(prefix = childPrefix, isLast = index == children.lastIndex, stringBuilder)
        }
    }
}