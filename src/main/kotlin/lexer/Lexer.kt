package org.derilh.lexer

import org.derilh.core.CharPrefix
import org.derilh.core.Operator
import org.derilh.core.Radix
import org.derilh.core.Symbol
import org.derilh.exceptions.LexerException
import org.derilh.exceptions.SyntaxException
import java.text.NumberFormat
import kotlin.math.min

class Lexer {
    private lateinit var input: String;
    fun tokenize(code: String): List<Token> {
        input = code
        val tokens = mutableListOf<Token>()
        val tokenBuilder = StringBuilder()
        var i = 0
        var startI = 0;
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
            startI = i;
        }

        fun currentLoc() = SourceLocation(line, col, i, i - startI, currentFile)
        fun startLoc() = SourceLocation(startLine, startCol, startI, i - startI, currentFile)

        fun buildStringToken(prefix: CharPrefix, value: IntArray) {
            tokens += StringLiteralToken(value, prefix, startLoc())
            tokenBuilder.clear()
        }

        fun buildNumToken(
            isFloat: Boolean,
            isDouble: Boolean,
            radix: Radix,
            isUnsigned: Boolean,
            isLong: Boolean,
            isLongLong: Boolean,
            isSizeT: Boolean
        ) {
            var value = tokenBuilder.toString();
            try {
                val token =
                    if (isFloat || isDouble) {
                        value = if(!value.last().isDigit()) value.dropLast(1) else value
                        FloatToken(value, isDouble, isLong, startLoc())
                    } else {
                        if (radix == Radix.HEXADECIMAL || radix == Radix.BINARY) {
                            value = value.substring(2)
                        }

                        val intVal = value.toBigInteger(radix = radix.base)
                        IntToken(
                            intVal,
                            radix = radix,
                            isUnsigned = isUnsigned,
                            isLong = isLong,
                            isLongLong = isLongLong,
                            isSizeT = isSizeT,
                            startLoc()
                        )
                    }
                tokens += token
                tokenBuilder.clear()
            } catch (e: Exception) {
                println("Cannot parse number token $value at line $startLine, col $startCol")
            }
        }

        fun buildCharToken(prefix: CharPrefix, value: IntArray) {
            tokens += CharToken(value, prefix, startLoc())
            tokenBuilder.clear()
        }

        fun buildToken() {
            if (!tokenBuilder.isBlank()) {
                val string = tokenBuilder.toString()
                tokens += KeywordToken.resolve(string, startLoc()) ?: BooleanToken.resolve(string, startLoc())
                        ?: IdToken(string, startLoc())
                tokenBuilder.clear()
            }
        }

        fun decodeEscapeChar(quoteChar: Char): Int {
            try {
                return when (val ch = input[i]) {
                    'n' -> {
                        advance(); 0x0A
                    }

                    't' -> {
                        advance(); 0x09
                    }

                    'r' -> {
                        advance(); 0x0D
                    }

                    'a' -> {
                        advance(); 0x07
                    }

                    'b' -> {
                        advance(); 0x08
                    }

                    'f' -> {
                        advance(); 0x0C
                    }

                    'v' -> {
                        advance(); 0x0B
                    }

                    in '0'..'7' -> {
                        var octalStr = ""
                        for (ind in 0..2) {
                            val nextCh = input.getOrNull(i + ind)
                            if (nextCh != null && nextCh in '0'..'7') {
                                octalStr += nextCh
                            } else {
                                break
                            }
                        }
                        advance(octalStr.length)
                        octalStr.toInt(Radix.OCTAL.base)
                    }

                    'x' -> {
                        var hexStr = ""
                        for (ind in 1..8) {
                            val nextCh = input.getOrNull(i + ind)
                            if (nextCh != null && isHexDigit(nextCh)) {
                                hexStr += nextCh
                            } else {
                                break
                            }
                        }

                        if (hexStr.isEmpty()) {
                            throw LexerException("Hex escape sequence used without following hex digits at position $i")
                        }

                        val charAfterHex = input.getOrNull(i + 1 + hexStr.length)
                        if (hexStr.length == 8 && charAfterHex != null && isHexDigit(charAfterHex)) {
                            throw LexerException("Hex escape sequence is out of range at position $i")
                        }

                        advance(hexStr.length + 1)
                        hexStr.toLong(Radix.HEXADECIMAL.base).toInt()
                    }

                    'u' -> {
                        var hexStr = ""
                        for (ind in 1..4) {
                            val nextCh = input.getOrNull(i + ind)
                            if (nextCh != null && isHexDigit(nextCh)) {
                                hexStr += nextCh
                            } else {
                                break
                            }
                        }

                        if (hexStr.length != 4) {
                            throw LexerException("Invalid utf16 sequence size at position $i: $hexStr")
                        }

                        advance(5)
                        hexStr.toInt(Radix.HEXADECIMAL.base)
                    }

                    'U' -> {
                        var hexStr = ""
                        for (ind in 1..8) {
                            val nextCh = input.getOrNull(i + ind)
                            if (nextCh != null && isHexDigit(nextCh)) {
                                hexStr += nextCh
                            } else {
                                break
                            }
                        }

                        if (hexStr.length != 8) {
                            throw LexerException("Invalid utf32 sequence size at position $i: $hexStr")
                        }

                        advance(9)
                        hexStr.toLong(Radix.HEXADECIMAL.base).toInt()
                    }

                    else -> {
                        advance()
                        ch.code
                    }
                }
            }catch (e: NumberFormatException) {
                throw LexerException("Invalid escape sequence format at position: ${i}")
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

            val header = matchLiteralHeader(i)
            if (header != null) {
                if(header.isRaw) TODO("Raw literals are not supported yet")
                buildToken()
                markStart()


                advance(header.prefixLength)
                advance()

                var isTerminated = false

                val charList = mutableListOf<Int>()
                while (i < input.length) {
                    val currentCh = input[i]

                    if (currentCh == header.quoteChar) {
                        advance()
                        isTerminated = true
                        break
                    }

                    if (currentCh == '\\' && !header.isRaw) {
                        advance()
                        val nextCh = input.getOrNull(i)
                        if(nextCh != null) {
                            charList += decodeEscapeChar(header.quoteChar)
                        }
                        continue
                    }

                    val codePoint = input.codePointAt(i);
                    charList.add(codePoint)
                    advance(Character.charCount(codePoint))
                }

//                if (!isTerminated) {
//                }

                if (header.kind == LiteralKind.CHAR) {
                    buildCharToken(prefix = header.prefix, value = charList.toIntArray())
                } else {
                    buildStringToken(prefix = header.prefix, value = charList.apply{add(0)}.toIntArray())
                }
                continue
            } else if (tokenBuilder.isEmpty()) {
                val isDigit = ch.isDigit()
                val isDotNumber = (ch == '.' && input.getOrNull(i + 1)?.isDigit() == true)

                if (isDigit || isDotNumber) {
                    markStart()

                    var isFloat = false
                    var isDouble = isDotNumber
                    var radix = Radix.DECIMAL;
                    var isUnsigned = false
                    var isLong = false
                    var isLongLong = false
                    var isSizeT = false

                    if (ch == '0' && !isDotNumber) {
                        val nextChar = input.getOrNull(i + 1)?.lowercaseChar()
                        when (nextChar) {
                            'x' -> {
                                radix = Radix.HEXADECIMAL;
                                tokenBuilder.append(ch).append(input[i + 1])
                                advance(); advance()

                                if (i >= input.length || !isHexDigit(input[i])) {
                                    throw LexerException("Hexadecimal literal requires at least one hex digit after '0x'")
                                }
                            }

                            'b' -> {
                                radix = Radix.BINARY;
                                tokenBuilder.append(ch).append(input[i + 1])
                                advance(); advance()
                                if (i >= input.length || (input[i] != '0' && input[i] != '1')) {
                                    throw LexerException("Binary literal requires at least one binary digit after '0b'")
                                }
                            }

                            in '0'..'7' -> {
                                radix = Radix.OCTAL;
                                tokenBuilder.append(ch)
                                advance()
                            }

                            else -> {

                                tokenBuilder.append(ch)
                                advance()
                            }
                        }
                    }

                    var hasExponent = false

                    while (i < input.length) {
                        val cur = input[i]
                        val lowerCur = cur.lowercaseChar()

                        if (cur == '\'') {
                            val next = input.getOrNull(i + 1)
                            if (next != null && isValidDigitForBase(next, radix)) {
                                tokenBuilder.append(cur)
                                advance()
                                continue
                            } else {
                                break
                            }
                        }

                        val isValidDigit = when (radix) {
                            Radix.HEXADECIMAL -> isHexDigit(cur)
                            Radix.BINARY -> cur == '0' || cur == '1'
                            Radix.OCTAL -> cur in '0'..'7'
                            Radix.DECIMAL -> cur.isDigit()
                        }

                        if (isValidDigit) {
                            tokenBuilder.append(cur)
                            advance()
                            continue
                        }

                        if (radix == Radix.DECIMAL) {
                            if (cur == '.' && !isDouble && !hasExponent) {
                                isDouble = true
                                tokenBuilder.append(cur)
                                advance()
                                continue
                            }
                            if (lowerCur == 'e' && !hasExponent) {
                                hasExponent = true
                                isDouble = true
                                tokenBuilder.append(cur)
                                advance()

                                if (i < input.length && (input[i] == '+' || input[i] == '-')) {
                                    tokenBuilder.append(input[i])
                                    advance()
                                }
                                continue
                            }
                        }

                        break
                    }

                    while (i < input.length) {
                        val lowerCur = input[i].lowercaseChar()

                        if (lowerCur == 'f' && (isDouble || radix != Radix.HEXADECIMAL)) {
                            isFloat = true
                            isDouble = false
                            tokenBuilder.append(input[i])
                            advance()
                            break
                        } else if (lowerCur == 'u' && !isUnsigned) {
                            isUnsigned = true
                            tokenBuilder.append(input[i])
                            advance()
                        } else if (lowerCur == 'l') {
                            tokenBuilder.append(input[i])
                            advance()
                            if (i < input.length && input[i].lowercaseChar() == 'l') {
                                isLongLong = true
                                tokenBuilder.append(input[i])
                                advance()
                            } else {
                                isLong = true
                            }
                        } else if (lowerCur == 'z' && !isSizeT) {
                            isSizeT = true
                            tokenBuilder.append(input[i])
                            advance()
                        } else {
                            break
                        }
                    }

                    buildNumToken(isFloat, isDouble, radix, isUnsigned, isLong, isLongLong, isSizeT)
                    continue
                }
            }
            val operator = tryReadOperator(i)
            if (operator != null) {
                buildToken()
                markStart()
                tokens += OperatorToken(operator, startLoc())
                advance(operator.value.length)
                continue
            }

            val symbolToken = tryReadSymbol(i)
            if (symbolToken != null) {
                buildToken()
                markStart()
                tokens += SymbolToken(symbolToken, startLoc())
                advance()
                continue
            }

            if (tokenBuilder.isEmpty()) markStart()
            tokenBuilder.append(ch)
            advance()
        }
        return tokens
    }


    private fun matchLiteralHeader(i: Int): LiteralHeader? {
        val c0 = input.getOrNull(i) ?: return null
        val c1 = input.getOrNull(i + 1)
        val c2 = input.getOrNull(i + 2)
        val c3 = input.getOrNull(i + 3)

        if (c0 == 'u' && c1 == '8') {
            if (c2 == 'R' && c3 == '"') {
                return LiteralHeader(
                    CharPrefix.UTF8,
                    LiteralKind.STRING,
                    quoteChar = '"',
                    prefixLength = 3,
                    isRaw = true
                )
            }
            if (c2 == '\'' || c2 == '"') {
                val kind = if (c2 == '\'') LiteralKind.CHAR else LiteralKind.STRING
                return LiteralHeader(CharPrefix.UTF8, kind, quoteChar = c2, prefixLength = 2)
            }
        }

        if (c0 == 'u' || c0 == 'U' || c0 == 'L') {
            val prefix = when (c0) {
                'u' -> CharPrefix.UTF16
                'U' -> CharPrefix.UTF32
                'L' -> CharPrefix.WIDE
                else -> CharPrefix.NONE
            }

            if (c1 == 'R' && c2 == '"') {
                return LiteralHeader(prefix, LiteralKind.STRING, quoteChar = '"', prefixLength = 2, isRaw = true)
            }
            if (c1 == '\'' || c1 == '"') {
                val kind = if (c1 == '\'') LiteralKind.CHAR else LiteralKind.STRING
                return LiteralHeader(prefix, kind, quoteChar = c1, prefixLength = 1)
            }
        }

        if (c0 == 'R' && c1 == '"') {
            return LiteralHeader(CharPrefix.NONE, LiteralKind.STRING, quoteChar = '"', prefixLength = 1, isRaw = true)
        }

        if (c0 == '\'' || c0 == '"') {
            val kind = if (c0 == '\'') LiteralKind.CHAR else LiteralKind.STRING
            return LiteralHeader(CharPrefix.NONE, kind, quoteChar = c0, prefixLength = 0)
        }

        return null
    }

    private fun isHexDigit(ch: Char?): Boolean {
        return ch != null && ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F'
    }

    private fun isValidDigitForBase(ch: Char, radix: Radix): Boolean {
        return when (radix) {
            Radix.HEXADECIMAL -> isHexDigit(ch)
            Radix.BINARY -> ch == '0' || ch == '1'
            Radix.OCTAL -> ch in '0'..'7'
            Radix.DECIMAL -> ch.isDigit()
        }
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

    enum class LiteralKind { CHAR, STRING }

    data class LiteralHeader(
        val prefix: CharPrefix,
        val kind: LiteralKind,
        val quoteChar: Char,
        val prefixLength: Int,
        val isRaw: Boolean = false,
    )
}