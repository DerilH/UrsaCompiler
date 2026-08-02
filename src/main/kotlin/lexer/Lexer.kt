package org.derilh.lexer

import org.derilh.core.Operator
import org.derilh.core.Symbol

class Lexer {
    private lateinit var input: String;
    fun tokenize(code: String): List<Token> {
        input = code
        val tokens = mutableListOf<Token>()
        val tokenBuilder = StringBuilder()
        var i = 0
        var line = 1
        var col = 1
        var startLine = 1
        var startCol = 1
        var currentFile: String? = null

        fun advance() {
            if (i < input.length) {
                if (input[i] == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
                i++
            }
        }

        fun advance(n: Int) {
            repeat(n) { advance() }
        }

        fun markStart() {
            startLine = line
            startCol = col
        }

        fun currentLoc() = SourceLocation(line, col, currentFile)
        fun startLoc() = SourceLocation(startLine, startCol, currentFile)

        fun buildStringToken() {
            tokens += LiteralToken(tokenBuilder.toString(), startLoc())
            tokenBuilder.clear()
        }

        fun buildNumToken(isFloat: Boolean, isDouble: Boolean, isBinary: Boolean, isHex: Boolean) {
            val value = tokenBuilder.toString();

            try {
                val token =
                    if (isHex) {
                        IntToken(value.substring(2).toInt(radix = 16), startLoc())
                    } else if (isBinary) {
                        IntToken(value.substring(2).toInt(radix = 2), startLoc())
                    } else if (isDouble) {
                        DoubleToken(value.toDouble(), startLoc())
                    } else if (isFloat) {
                        FloatToken(value.toFloat(), startLoc())
                    } else IntToken(value.toInt(), startLoc())
                tokens += token
                tokenBuilder.clear()
            } catch (e: Exception) {
                println("Cannot parse number token $value at line $startLine, col $startCol")
            }
        }

        fun buildToken() {
            if (!tokenBuilder.isBlank()) {
                val string = tokenBuilder.toString()
                tokens += KeywordToken.resolve(string, startLoc()) ?: BooleanToken.resolve(string, startLoc())
                        ?: IdToken(string, startLoc())
                tokenBuilder.clear()
            }
        }


        while (i < input.length) {
            val ch = input[i]

            if (ch == '#') {
                val lineDirective = "^#line\\s+(\\d+)(?:\\s+\"([^\"]+)\")?.*".toRegex()
                val remaining = input.substring(i)
                val eol = remaining.indexOf('\n')
                val lineText = if (eol == -1) remaining else remaining.substring(0, eol)
                val match = lineDirective.matchEntire(lineText)
                if (match != null) {
                    line = match.groupValues[1].toInt()
                    if (match.groupValues[2].isNotEmpty()) {
                        currentFile = match.groupValues[2]
                    }
                    col = 1
                    i += lineText.length
                    if (eol != -1) i++ // Skip newline
                    line++
                    col = 1
                    continue
                }
            }

            if (ch.isWhitespace()) {
                buildToken()
                advance()
                continue
            }

            if (ch == '"') {
                buildToken()
                markStart()
                advance()

                while (i < input.length) {
                    val ch = input[i]
                    if (ch == '\\') {
                        when (val nextCh = input.getOrNull(i + 1)) {
                            '\'' -> {
                                tokenBuilder.append('\''); advance(2)
                            }

                            '"' -> {
                                tokenBuilder.append('"'); advance(2)
                            }

                            '\\' -> {
                                tokenBuilder.append('\\'); advance(2)
                            }

                            else -> {
                                tokenBuilder.append(ch); tokenBuilder.append(nextCh); advance(2)
                            }
                        }
                        continue
                    }
                    if (ch == '"') {
                        advance()
                        break
                    }
                    tokenBuilder.append(ch)
                    advance()
                }
                buildStringToken()
                continue
            } else if (tokenBuilder.isEmpty()) {
                val isDigit = ch.isDigit()
                var isFloat = false
                var isDouble = (ch == '.' && input.getOrNull(i + 1)?.isDigit() == true)
                var isBinary = false;
                var isHex = false;

                if (isDigit || isDouble) {
                    markStart()
                    while (i < input.length) {
                        val ch = input[i]
                        if (ch == '.') isDouble = true;
                        if (ch == 'e') {
                            isDouble = true
                        } else if (ch == 'f') {
                            isDouble = false
                            isFloat = true
                            break
                        } else if (ch == 'x') {
                            isHex = true
                        } else if (ch == 'b') {
                            isBinary = true
                        }

                        if (ch.isWhitespace() || (ch != 'b' && ch != 'x' && ch != '.' && ch != 'e' && ch != '-' && ch != '+') && !ch.isDigit()) {
                            break
                        }
                        tokenBuilder.append(ch)
                        advance()
                    }
                    buildNumToken(isFloat, isDouble, isBinary, isHex)
                    continue
                }
            }
            val operator = tryReadOperator(i)
            if (operator != null) {
                buildToken()
                tokens += OperatorToken(operator, currentLoc())
                advance(operator.value.length)
                continue
            }

            val symbolToken = tryReadSymbol(i)
            if (symbolToken != null) {
                buildToken()
                tokens += SymbolToken(symbolToken, currentLoc())
                advance()
                continue
            }

            if (tokenBuilder.isEmpty()) markStart()
            tokenBuilder.append(ch)
            advance()
        }
        return tokens
    }

    private fun tryReadSymbol(i: Int): Symbol? {
        return when (input[i]) {
            '(' -> Symbol.LPAREN
            ')' -> Symbol.RPAREN
            '[' -> Symbol.LBRACKET
            ']' -> Symbol.RBRACKET
            '{' -> Symbol.BEGIN
            '}' -> Symbol.END
            ';' -> Symbol.SEPARATOR
            ':' -> Symbol.COLON
            ',' -> Symbol.COMMA
            else -> null
        }
    }

    private fun tryReadOperator(i: Int): Operator? {
        val ch = input[i]
        val next = input.getOrNull(i + 1)

        return when (ch) {
            '=' -> when (next) {
                '=' -> Operator.EQUAL           // ==
                else -> Operator.ASSIGN          // =
            }

            '+' -> when (next) {
                '=' -> Operator.ADD_EQ          // +=
                '+' -> Operator.INCREMENT       // ++
                else -> Operator.PLUS            // +
            }

            '-' -> when (next) {
                '=' -> Operator.MINUS_EQ        // -=
                '-' -> Operator.DECREMENT       // --
                '>' -> if (input.getOrNull(i + 2) == '*') Operator.ARROW_STAR else Operator.ARROW // ->* або ->
                else -> Operator.MINUS           // -
            }

            '*' -> when (next) {
                '=' -> Operator.MULT_EQ         // *=
                else -> Operator.POINTER        // *
            }

            '/' -> when (next) {
                '=' -> Operator.DIV_EQ          // /=
                else -> Operator.DIVIDE         // /
            }

            '%' -> when (next) {
                '=' -> Operator.MOD_EQ          // %=
                else -> Operator.MOD            // %
            }

            '<' -> when (next) {
                '=' -> if (input.getOrNull(i + 2) == '>') Operator.SPACESHIP else Operator.LESS_EQUAL // <=> або <=
                '<' -> if (input.getOrNull(i + 2) == '=') Operator.LSHIFT_EQ else Operator.LBITSHIFT // <<= або <<
                else -> Operator.LESS            // <
            }

            '>' -> when (next) {
                '=' -> Operator.GREATER_EQUAL   // >=
                '>' -> if (input.getOrNull(i + 2) == '=') Operator.RSHIFT_EQ else Operator.RBITSHIFT // >>= або >>
                else -> Operator.GREATER         // >
            }

            '&' -> when (next) {
                '&' -> Operator.AND             // &&
                '=' -> Operator.BIT_AND_EQ      // &=
                else -> Operator.AMP            // &
            }

            '|' -> when (next) {
                '|' -> Operator.OR              // ||
                '=' -> Operator.BIT_OR_EQ       // |=
                else -> Operator.BIT_OR         // |
            }

            '^' -> when (next) {
                '=' -> Operator.BIT_XOR_EQ      // ^=
                else -> Operator.BIT_XOR        // ^
            }

            '!' -> when (next) {
                '=' -> Operator.NOT_EQ          // !=
                else -> Operator.NOT            // !
            }

            '~' -> Operator.BIT_NOT             // ~

            '.' -> when (next) {
                '*' -> Operator.DOT_STAR        // .*
                else -> Operator.DOT            // .
            }

            ':' -> when (next) {
                ':' -> Operator.NAMESPACE       // ::
                else -> null
            }

            else -> null
        }
    }
}