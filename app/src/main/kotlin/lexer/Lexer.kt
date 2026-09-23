package org.derilh.lexer

import org.derilh.core.CharPrefix
import org.derilh.core.Operator
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.core.Radix
import org.derilh.core.SourceLocation
import org.derilh.core.Symbol
import org.derilh.core.preprocessor.PreProcessorMacroExpander
import org.derilh.core.preprocessor.Preprocessor
import org.derilh.core.preprocessor.PreprocessorExpressionParser
import org.derilh.exceptions.LexerException
import org.derilh.run.RunHelper
import org.derilh.util.Util
import java.math.BigInteger
import java.nio.file.Files
import java.util.Stack
import kotlin.collections.mutableMapOf
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

data class Macro(val name: String, val params: List<Token>?, val hasVA: Boolean, val tokens: List<Token>)

class Lexer(val enablePreprocessor: Boolean = true, val baseLocation: SourceLocation? = null, val predefinedMacros: List<Macro> = emptyList(), public val options: Options? = null) {
    lateinit var input: String
    private val macros: MutableMap<String, Macro> = mutableMapOf()
    private val tokens = mutableListOf<Token>()
    private val tokenBuilder = StringBuilder()
    var i = 0
    private var startI = 0
    private var line = baseLocation?.line ?: 1
    private var col = baseLocation?.column ?: 1
    private var startLine = baseLocation?.line ?: 1
    private var startCol = baseLocation?.column ?: 1
    var currentFile = "unknown"
    private var isFloat = false
    private var isDouble = false
    private var radix = Radix.DECIMAL
    private var isUnsigned = false
    private var isLong = false
    private var isLongLong = false
    private var isSizeT = false
    private val include = mutableSetOf<String>()
    var leadingWhiteSpace: Boolean = false
        get() {
            return field.also { leadingWhiteSpace = false }
        }
    private var branchStack = Stack<Boolean>()
    private val isValidBranch get() = branchStack.isEmpty() || branchStack.peek();

    init {
        macros += predefinedMacros.map { it.name to it }
    }

    fun tokenize(code: String, fileName: String = "unknown"): List<Token> {
        input = code
        currentFile = fileName
        tokens.clear()
        tokenBuilder.clear()
        i = 0
        startI = 0
        line = 1
        col = 1
        startLine = 1
        startCol = 1
        while (i < input.length) {
            if (skipComment()) continue
            val ch = input[i]
            if (tryReadPreprocessorDirective()) continue

            if (ch.isWhitespace()) {
                if (buildToken()) continue;
                advance()
                leadingWhiteSpace = true;
                continue
            }

            if (tryReadLiteral()) continue
            if (tokenBuilder.isEmpty() && tryReadNumber()) continue
            val operator = tryReadOperator(i)
            if (operator != null) {
                if (buildToken()) continue;
                markStart()
                append(OperatorToken(operator, startLoc(), leadingWhiteSpace))
                advance(operator.value.length)
                continue
            }

            val symbolToken = tryReadSymbol(i)
            if (symbolToken != null) {
                if (buildToken()) continue;
                markStart()
                append(SymbolToken(symbolToken, startLoc(), leadingWhiteSpace))
                advance()
                continue
            }

            if (tokenBuilder.isEmpty()) markStart()
            tokenBuilder.append(ch)
            advance()
        }
        buildToken()

        if (branchStack.isNotEmpty()) {
            throw IllegalStateException("Unclosed conditional branch")
        }

        return tokens.toList()
    }

    private fun advance() {
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

    private fun advance(n: Int) {
        repeat(n) { advance() }
    }

    private fun markStart() {
        startLine = line
        startCol = col
        startI = i;
    }

    fun currentLoc() = SourceLocation(line, col, i - startI, currentFile)
    fun startLoc() = SourceLocation(startLine, startCol, i - startI, currentFile)


    private fun buildStringToken(prefix: CharPrefix, value: IntArray) {
        append(StringLiteralToken(value, prefix, startLoc(), leadingWhiteSpace))
        tokenBuilder.clear()
    }

    private fun buildNumToken(
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
                    value = value.dropLast(1)
                    FloatToken(value, isDouble, isLong, startLoc(), leadingWhiteSpace)
                } else {
                    if (radix == Radix.HEXADECIMAL || radix == Radix.BINARY) {
                        value = value.substring(2)
                    }

                    val intVal = value.toBigInteger(radix = radix.base)
                    IntToken(intVal, radix = radix, isUnsigned = isUnsigned, isLong = isLong, isLongLong = isLongLong, isSizeT = isSizeT, startLoc(), leadingWhiteSpace)
                }
            append(token)
            tokenBuilder.clear()
        } catch (e: Exception) {
            println("Cannot parse number token $value at line $startLine, col $startCol")
        }
    }

    private fun buildCharToken(prefix: CharPrefix, value: IntArray) {
        append(CharToken(value, prefix, startLoc(), leadingWhiteSpace))
        tokenBuilder.clear()
    }


    /**
     * @return true if macro was used false otherwise
     */
    private fun buildToken(): Boolean {
        if (!tokenBuilder.isBlank()) {
            val string = tokenBuilder.toString()
            val macro = getMacro(string);
            var macroExpanded = false;
            if (macro != null && isValidBranch) {
                val expander = PreProcessorMacroExpander(this);
                val args = expander.parseMacroArgs(macro);
                if (args != null) {
                    macroExpanded = true;
                    append(expander.expandMacro(macro, args.first).map { it.copy(location = startLoc()) })
                }
            }

            if (!macroExpanded) {
                append(
                    KeywordToken.resolve(string, startLoc(), leadingWhiteSpace)
                            ?: BooleanToken.resolve(string, startLoc(), leadingWhiteSpace)
                            ?: IdToken(string, startLoc(), leadingWhiteSpace)
                )
            }
            tokenBuilder.clear()
            return macroExpanded
        }
        return false;
    }


    private fun decodeEscapeChar(quoteChar: Char): Int {
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
        } catch (e: NumberFormatException) {
            throw LexerException("Invalid escape sequence format at position: ${i}")
        }
    }

    private fun tryReadPreprocessorDirective(): Boolean {
        if (!enablePreprocessor) return false;
        val ch = input[i]
        if (ch == '#') {
            advance()
            val startI = i;
            while (true) {
                skipLine();
                if (input[i - 2] != '\\') break;
            }
            val endI = i;
            val tokens = Lexer(enablePreprocessor = false, baseLocation = startLoc(), options = options).tokenize(input.substring(startI, endI), currentFile)
                .filter { it.stringValue != "\\" }
            val directive = tokens.firstOrNull()?.stringValue ?: throw IllegalStateException("Invalid preprocessor directive ${tokens.firstOrNull()}");
            var validDirective = true;
            if (isValidBranch) {
                when (directive) {
                    "define" -> parseDefineMacro(tokens)
                    "undef" -> parseUndefDirective(tokens)
                    "line" -> parseLineDirective(PreProcessorMacroExpander(this).expandTokens(tokens, true));
                    "include" -> applyInclude(PreProcessorMacroExpander(this).expandTokens(tokens, true));
                    "error" -> parseErrorDirective(tokens)
                    else -> validDirective = false
                }
            }

            when (directive) {
                "ifdef" -> parseIfdef(tokens, false)
                "if" -> parseIf(tokens);
                "elif" -> parseElIf(tokens);
                "ifndef" -> parseIfdef(tokens, true)
                "endif" -> parseEndIf(tokens)
                "else" -> parseElse(tokens)
                "elifdef" -> parseElIfDef(tokens, false)
                "elifndef" -> parseElIfDef(tokens, true)
                else -> {
                    if (!validDirective) {
                        throw IllegalStateException("Invalid preprocessor directive ${directive}");
                    }
                }
            }
            return true;
        }
        return false
    }

    private fun parseErrorDirective(tokens: List<Token>) {
        throw RuntimeException("Error directive: ${tokens.drop(1).joinToString(" ") { it.stringValue }}");
    }

    private fun parseElIf(tokens: List<Token>) {
        if (branchStack.isEmpty()) throw IllegalStateException("Elifdef without ifdef");
        branchStack.pop();
        parseIf(tokens);
    }

    private fun parseIf(tokens: List<Token>) {
        val value = isValidBranch && evaluateIfExpr(tokens.drop(1))
        pushIf(value);
    }

    private fun evaluateIfExpr(tokens: List<Token>): Boolean {
        val expander = PreProcessorMacroExpander(this);
        val expandedTokens = expander.expandForIfDirective(tokens)

        val parser = PreprocessorExpressionParser(macros.keys)
        return parser.parsePreprocessorExpr(expandedTokens)
    }

    private fun parseElIfDef(tokens: List<Token>, reverse: Boolean) {
        if (branchStack.isEmpty()) throw IllegalStateException("Elifdef without ifdef");
        branchStack.pop();
        parseIfdef(tokens, reverse)
    }

    private fun parseElse(tokens: List<Token>) {
        if (branchStack.isEmpty()) throw IllegalStateException("Else without ifdef");
        val isPrevValid = isValidBranch;
        branchStack.pop();
        branchStack.push(isValidBranch && !isPrevValid)
    }

    private fun parseEndIf(tokens: List<Token>) {
        if (branchStack.isEmpty()) throw IllegalStateException("Endif without ifdef");
        branchStack.pop();
    }

    private fun parseIfdef(tokens: List<Token>, reverse: Boolean) {
        tokens.getOrNull(1) ?: throw IllegalStateException("Invalid macro");
        if (!tokens[1].isIdentifierLike) throw IllegalStateException("Invalid macro name ${tokens[1]}");
        val name = tokens[1].stringValue;
        pushIfDef(name, reverse)
    }

    private fun pushIf(evaluatedValue: Boolean) {
        val isValid = isValidBranch && evaluatedValue
        branchStack.push(isValid)
    }

    private fun pushIfDef(name: String, reverse: Boolean) {
        var contains = macros.contains(name);
        if (reverse) contains = !contains;
        val isValid = isValidBranch && contains
        branchStack.push(isValid)
    }

    private fun applyInclude(tokens: List<Token>) {
        val header = Preprocessor.parseIncludeHeader(tokens);
        val path = Preprocessor.resolveHeader(header.first, header.second, Path(currentFile), options!!);
        path ?: throw IllegalStateException("Unknown include ${header}")
        include += path.absolutePathString()
        val code = Files.readString(path);
        val module = RunHelper.runLexer(code, options.copy(inputFile = path), macros.values);
        macros += module.lexer!!.macros //TODO: Optimize me
        append(module.tokens!!)
    }

    private fun parseUndefDirective(tokens: List<Token>) {
        val name = tokens.getOrNull(1) ?: throw IllegalStateException("Invalid macro");
        if (!name.isIdentifierLike) throw IllegalStateException("Invalid macro name ${name}");
        macros.remove(name.stringValue);
    }

    private fun parseLineDirective(tokens: List<Token>) {
        val line = tokens.getOrNull(1);
        val lineNum = try {
            (line as IntToken).value.intValueExact()
        } catch (e: Exception) {
            throw IllegalStateException("Line value is to big, expected signed integer")
        }
        this.line = lineNum;

        val file = tokens.getOrNull(2);
        if (file != null) {
            currentFile = file.stringValue
        }
        col = 1
    }

    private fun parseDefineMacro(tokens: List<Token>) {
        val name = tokens.getOrNull(1) ?: throw IllegalStateException("Invalid macro");
        if (!name.isIdentifierLike) throw IllegalStateException("Invalid macro name ${name}");
        if (name.stringValue == "defined") throw IllegalStateException("Invalid macro name ${name}");

        var params: List<Token>? = null;
        var i = 2;
        var hasVA = false;
        if (tokens.getOrNull(2) != null && !tokens[2].leadingWhitespace && tokens[2] isA Symbol.LPAREN) {
            i++
            params = mutableListOf<Token>()
            val tok = tokens.getOrNull(3) ?: throw IllegalStateException("Invalid macro");
            if (tok notA Symbol.RPAREN) {
                while (true) {
                    if (hasVA) {
                        throw IllegalStateException("Invalid macro argument ${tok}, expected id");
                    }
                    val tok = tokens[i];
                    if (!tok.isIdentifierLike) {
                        if ((tok as? OperatorToken)?.value == Operator.TRIPLE_DOT) {
                            hasVA = true;
                            i++
                        } else throw IllegalStateException("Invalid macro argument ${tok}, expected id");
                    } else {
                        val value = tok.stringValue;
                        params += IdToken(value, tok.location, tok.leadingWhitespace);
                        i++;
                    }
                    val next = tokens.getOrNull(i) ?: throw IllegalStateException("Invalid macro");
                    if (next isA Symbol.COMMA) i++;
                    else if (next isA Symbol.RPAREN) {
                        i++;
                        break;
                    }
                }
            } else i++;
        }
        val macro = Macro(name.stringValue, if (hasVA) emptyList() else params, hasVA, tokens.subList(i, tokens.size));
        macros[macro.name] = macro;
    }

    private fun skipComment(): Boolean {
        if (input.getOrNull(i) == '/') {
            val next = input.getOrNull(i + 1);
            if (next == '/') {
                skipLine()
            } else if (next == '*') {
                while (true) {
                    val curr = input.getOrNull(i) ?: break;
                    if (curr == '*' && (input.getOrNull(i + 1) ?: break) == '/') {
                        advance()
                        advance()
                        return true
                    }
                    advance()
                }
            } else return false;
            return true;
        }
        return false
    }

    private fun skipLine() {
        while (true) {
            val curr = input.getOrNull(i) ?: break;
            if (curr == '\n') {
                advance()
                return
            }
            advance()
        }
    }

    private fun tryReadLiteral(): Boolean {
        val header = matchLiteralHeader(i)
        if (header == null) return false
        if (header.isRaw) TODO("Raw literals are not supported yet")
        if (buildToken()) return true;
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
                if (nextCh != null) {
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
            buildStringToken(prefix = header.prefix, value = charList.apply { add(0) }.toIntArray())
        }
        return true
    }

    private fun tryReadNumber(): Boolean {
        val ch = input[i]
        val isDigit = input[i].isDigit()
        val isDotNumber = (input[i] == '.' && input.getOrNull(i + 1)?.isDigit() == true)
        if (!isDigit && !isDotNumber) return false
        markStart()

        isFloat = false
        isDouble = isDotNumber
        radix = Radix.DECIMAL
        isUnsigned = false
        isLong = false
        isLongLong = false
        isSizeT = false

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
                advance()
                break
            } else if (lowerCur == 'u' && !isUnsigned) {
                isUnsigned = true
                advance()
            } else if (lowerCur == 'l') {
                advance()
                if (i < input.length && input[i].lowercaseChar() == 'l') {
                    isLongLong = true
                    advance()
                } else {
                    isLong = true
                }
            } else if (lowerCur == 'z' && !isSizeT) {
                isSizeT = true
                advance()
            } else {
                break
            }
        }

        buildNumToken(isFloat, isDouble, radix, isUnsigned, isLong, isLongLong, isSizeT)
        return true
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
            '?' -> Symbol.QUESTION
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
                '=' -> Operator.ADD_ASSIGN          // +=
                '+' -> Operator.INCREMENT       // ++
                else -> Operator.PLUS            // +
            }

            '-' -> when (next) {
                '=' -> Operator.MINUS_ASSIGN        // -=
                '-' -> Operator.DECREMENT       // --
                '>' -> if (input.getOrNull(i + 2) == '*') Operator.ARROW_STAR else Operator.ARROW // ->* або ->
                else -> Operator.MINUS           // -
            }

            '*' -> when (next) {
                '=' -> Operator.MULT_ASSIGN         // *=
                else -> Operator.POINTER        // *
            }

            '/' -> when (next) {
                '=' -> Operator.DIV_ASSIGN          // /=
                else -> Operator.DIVIDE         // /
            }

            '%' -> when (next) {
                '=' -> Operator.MOD_ASSIGN          // %=
                else -> Operator.MOD            // %
            }

            '<' -> when (next) {
                '=' -> if (input.getOrNull(i + 2) == '>') Operator.SPACESHIP else Operator.LESS_EQUAL // <=> або <=
                '<' -> if (input.getOrNull(i + 2) == '=') Operator.LSHIFT_ASSIGN else Operator.LBITSHIFT // <<= або <<
                else -> Operator.LESS            // <
            }

            '>' -> when (next) {
                '=' -> Operator.GREATER_EQUAL   // >=
                '>' -> if (input.getOrNull(i + 2) == '=') Operator.RSHIFT_ASSIGN else Operator.RBITSHIFT // >>= або >>
                else -> Operator.GREATER         // >
            }

            '&' -> when (next) {
                '&' -> Operator.AND             // &&
                '=' -> Operator.BIT_AND_ASSIGN      // &=
                else -> Operator.AMP            // &
            }

            '|' -> when (next) {
                '|' -> Operator.OR              // ||
                '=' -> Operator.BIT_OR_ASSIGN       // |=
                else -> Operator.BIT_OR         // |
            }

            '^' -> when (next) {
                '=' -> Operator.BIT_XOR_ASSIGN      // ^=
                else -> Operator.BIT_XOR        // ^
            }

            '!' -> when (next) {
                '=' -> Operator.NOT_EQ          // !=
                else -> Operator.NOT            // !
            }

            '~' -> Operator.BIT_NOT             // ~

            '.' -> when (next) {
                '*' -> Operator.DOT_STAR        // .*
                '.' -> when (input.getOrNull(i + 2)) {
                    '.' -> Operator.TRIPLE_DOT
                    else -> Operator.DOT
                }

                else -> Operator.DOT            // .
            }

            ':' -> when (next) {
                ':' -> Operator.NAMESPACE       // ::
                else -> null
            }

            '#' -> when (next) {
                '#' -> Operator.DOUBLE_HASH       // ::
                else -> Operator.HASH
            }

            else -> null
        }
    }

    fun getMacro(name: String): Macro? {
        return when (name) {
            "__LINE__" -> Macro("__LINE__", null, false, listOf(IntToken(BigInteger.valueOf(line.toLong()), Radix.DECIMAL, false, false, false, false, startLoc(), leadingWhitespace = false)))
            "__FILE__" -> Macro("__FILE__", null, false, listOf(StringLiteralToken(Util.utf16ToCodePoints(currentFile), CharPrefix.NONE, startLoc(), leadingWhitespace = false)))
            else -> macros[name]
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

    private fun append(token: Token) {
        if (isValidBranch) {
            tokens += token
        }
    }

    private fun append(tokens: List<Token>) {
        if (isValidBranch) {
            this.tokens += tokens
        }
    }

    fun hasInclude(path: String): Boolean {
        return include.contains(path)
    }
}