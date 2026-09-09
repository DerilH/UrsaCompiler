package org.derilh.core.preprocessor

import org.derilh.core.CharPrefix
import org.derilh.core.Operator
import org.derilh.core.Options
import org.derilh.core.Precedence
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.Radix
import org.derilh.core.SourceLocation
import org.derilh.core.Symbol
import org.derilh.core.target.TargetArchitecture
import org.derilh.core.target.TargetInfo
import org.derilh.lexer.EofToken
import org.derilh.lexer.IdToken
import org.derilh.lexer.IntToken
import org.derilh.lexer.KeywordToken
import org.derilh.lexer.Lexer
import org.derilh.lexer.Macro
import org.derilh.lexer.OperatorToken
import org.derilh.lexer.StringLiteralToken
import org.derilh.lexer.SymbolToken
import org.derilh.lexer.Token
import org.derilh.util.Util
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.text.isWhitespace

class Preprocessor {

    companion object {
        fun parseIncludeHeader(tokens: List<Token>): Pair<String, Boolean> {
            val includeTokens = tokens.drop(1)
            if (includeTokens.isEmpty()) {
                throw IllegalStateException("Expected header name after #include")
            }
            val firstToken = includeTokens.first()

            if (firstToken is StringLiteralToken) {
                return Util.codePointsToUtf16Unescaped(firstToken.value) to false
            }
            if ((firstToken is OperatorToken && firstToken.value == Operator.LESS)) {
                val pathBuilder = StringBuilder()
                var foundClosing = false

                for (token in includeTokens.drop(1)) {
                    if (token is OperatorToken && token.value == Operator.GREATER) {
                        foundClosing = true
                        break
                    }
                    pathBuilder.append(token.stringValue)
                }

                if (!foundClosing) {
                    throw IllegalStateException("Missing closing '>' in #include directive")
                }

                val headerPath = pathBuilder.toString().trim()
                if (headerPath.isEmpty()) {
                    throw IllegalStateException("Empty header path inside <>")
                }

                return headerPath to true
            }

            throw IllegalStateException("Expected string literal or '<' after #include, got: ${firstToken.stringValue}")
        }

        fun resolveHeader(
            includeName: String,
            isAngled: Boolean,
            currentFile: Path?,
            options: Options
        ): Path? {
            // Преобразуем имя инклуда в Path, чтобы файловая система корректно обработала "./"
            val relativeIncludePath = Path.of(includeName)

            if (!isAngled) {
                // 1. Ищем относительно папки ТЕКУЩЕГО файла
                if (currentFile != null) {
                    val absCurrentFile = currentFile.toAbsolutePath().normalize()
                    val currentDir = absCurrentFile.parent ?: absCurrentFile.fileSystem.getPath(".")

                    val candidate = currentDir.resolve(relativeIncludePath).normalize()
                    if (Files.exists(candidate)) return candidate
                }

                // 2. Ищем в локальных include-директориях (-iquote / корень проекта)
                for (quotePath in options.includes.quoteIncludePaths) {
                    val candidate = quotePath.toAbsolutePath().normalize()
                        .resolve(relativeIncludePath).normalize()
                    if (Files.exists(candidate)) return candidate
                }
            }

            // 3. Ищем в системных include-директориях (-I)
            for (sysPath in options.includes.systemIncludePaths) {
                val candidate = sysPath.toAbsolutePath().normalize()
                    .resolve(relativeIncludePath).normalize()
                if (Files.exists(candidate)) return candidate
            }

            return null
        }
        fun buildTargetMacros(target: TargetInfo): List<Macro> {
            //TODO: Add proper __cplusplus value
            val additionalMacros = mutableListOf<Macro>(
                createValueMacro("__cplusplus", "199711L")
            )
            if(target.architecture == TargetArchitecture.X86_64) {
                additionalMacros += createValueMacro("__x86_64__", "1")
            }

            return buildTypeMacros(target) + buildLimitMacros(target) + additionalMacros
        }

        fun buildTypeMacros(target: TargetInfo): List<Macro> {
            val types = target.types

            val typeMappings = listOf(
                "__SIZE_TYPE__" to types.sizeType,
                "__PTRDIFF_TYPE__" to types.ptrDiffType,
                "__WCHAR_TYPE__" to types.wCharType,
                "__WINT_TYPE__" to types.wIntType,
                "__INTMAX_TYPE__" to types.intMaxType,
                "__UINTMAX_TYPE__" to types.uIntMaxType,
                "__SIG_ATOMIC_TYPE__" to types.sigAtomicType,
                "__INTPTR_TYPE__" to types.intPtrType,
                "__UINTPTR_TYPE__" to types.uIntPtrType,

                "__INT8_TYPE__" to types.int8Type,
                "__INT16_TYPE__" to types.int16Type,
                "__INT32_TYPE__" to types.int32Type,
                "__INT64_TYPE__" to types.int64Type,
                "__UINT8_TYPE__" to types.int8Type.toUnsigned(),
                "__UINT16_TYPE__" to types.int16Type.toUnsigned(),
                "__UINT32_TYPE__" to types.int32Type.toUnsigned(),
                "__UINT64_TYPE__" to types.int64Type.toUnsigned(),

                "__INT_LEAST8_TYPE__" to types.int8Type,
                "__INT_LEAST16_TYPE__" to types.int16Type,
                "__INT_LEAST32_TYPE__" to types.int32Type,
                "__INT_LEAST64_TYPE__" to types.int64Type,
                "__UINT_LEAST8_TYPE__" to types.int8Type.toUnsigned(),
                "__UINT_LEAST16_TYPE__" to types.int16Type.toUnsigned(),
                "__UINT_LEAST32_TYPE__" to types.int32Type.toUnsigned(),
                "__UINT_LEAST64_TYPE__" to types.int64Type.toUnsigned(),

                "__INT_FAST8_TYPE__" to types.int8Type,
                "__INT_FAST16_TYPE__" to if (target.getBitWidth(PrimitiveTypeKind.INT) >= 32) PrimitiveTypeKind.INT else PrimitiveTypeKind.LONG,
                "__INT_FAST32_TYPE__" to PrimitiveTypeKind.INT,
                "__INT_FAST64_TYPE__" to PrimitiveTypeKind.LONG_LONG,
                "__UINT_FAST8_TYPE__" to types.int8Type.toUnsigned(),
                "__UINT_FAST16_TYPE__" to (if (target.getBitWidth(PrimitiveTypeKind.INT) >= 32) PrimitiveTypeKind.INT else PrimitiveTypeKind.LONG).toUnsigned(),
                "__UINT_FAST32_TYPE__" to PrimitiveTypeKind.UNSIGNED_INT,
                "__UINT_FAST64_TYPE__" to PrimitiveTypeKind.UNSIGNED_LONG_LONG
            )

            return typeMappings.map { (name, kind) -> createTypeMacro(name, kind) }
        }

        fun buildLimitMacros(target: TargetInfo): List<Macro> {
            val types = target.types

            val limitMappings = listOf(
                "__SCHAR_MAX__" to getSignedMax(target, PrimitiveTypeKind.SIGNED_CHAR),
                "__SHRT_MAX__" to getSignedMax(target, PrimitiveTypeKind.SHORT),
                "__INT_MAX__" to formatBigInt(types.maxInt, PrimitiveTypeKind.INT),
                "__LONG_MAX__" to formatBigInt(types.maxLong, PrimitiveTypeKind.LONG),
                "__LONG_LONG_MAX__" to formatBigInt(types.maxLongLong, PrimitiveTypeKind.LONG_LONG),

                "__WCHAR_MAX__" to getMaxForKind(target, types.wCharType),
                "__WINT_MAX__" to getMaxForKind(target, types.wIntType),
                "__SIZE_MAX__" to getMaxForKind(target, types.sizeType),
                "__PTRDIFF_MAX__" to getMaxForKind(target, types.ptrDiffType),
                "__INTMAX_MAX__" to getMaxForKind(target, types.intMaxType),
                "__UINTMAX_MAX__" to getMaxForKind(target, types.uIntMaxType),
                "__SIG_ATOMIC_MAX__" to getMaxForKind(target, types.sigAtomicType),
                "__INTPTR_MAX__" to getMaxForKind(target, types.intPtrType),
                "__UINTPTR_MAX__" to getMaxForKind(target, types.uIntPtrType),

                "__INT8_MAX__" to getMaxForKind(target, types.int8Type),
                "__INT16_MAX__" to getMaxForKind(target, types.int16Type),
                "__INT32_MAX__" to getMaxForKind(target, types.int32Type),
                "__INT64_MAX__" to getMaxForKind(target, types.int64Type),
                "__UINT8_MAX__" to getMaxForKind(target, types.int8Type.toUnsigned()),
                "__UINT16_MAX__" to getMaxForKind(target, types.int16Type.toUnsigned()),
                "__UINT32_MAX__" to getMaxForKind(target, types.int32Type.toUnsigned()),
                "__UINT64_MAX__" to getMaxForKind(target, types.int64Type.toUnsigned()),

                "__INT_LEAST8_MAX__" to getMaxForKind(target, types.int8Type),
                "__INT_LEAST16_MAX__" to getMaxForKind(target, types.int16Type),
                "__INT_LEAST32_MAX__" to getMaxForKind(target, types.int32Type),
                "__INT_LEAST64_MAX__" to getMaxForKind(target, types.int64Type),
                "__UINT_LEAST8_MAX__" to getMaxForKind(target, types.int8Type.toUnsigned()),
                "__UINT_LEAST16_MAX__" to getMaxForKind(target, types.int16Type.toUnsigned()),
                "__UINT_LEAST32_MAX__" to getMaxForKind(target, types.int32Type.toUnsigned()),
                "__UINT_LEAST64_MAX__" to getMaxForKind(target, types.int64Type.toUnsigned()),

                "__INT_FAST8_MAX__" to getMaxForKind(target, types.int8Type),
                "__INT_FAST16_MAX__" to getMaxForKind(target, if (target.getBitWidth(PrimitiveTypeKind.INT) >= 32) PrimitiveTypeKind.INT else PrimitiveTypeKind.LONG),
                "__INT_FAST32_MAX__" to getMaxForKind(target, PrimitiveTypeKind.INT),
                "__INT_FAST64_MAX__" to getMaxForKind(target, PrimitiveTypeKind.LONG_LONG),
                "__UINT_FAST8_MAX__" to getMaxForKind(target, types.int8Type.toUnsigned()),
                "__UINT_FAST16_MAX__" to getMaxForKind(target, (if (target.getBitWidth(PrimitiveTypeKind.INT) >= 32) PrimitiveTypeKind.INT else PrimitiveTypeKind.LONG).toUnsigned()),
                "__UINT_FAST32_MAX__" to getMaxForKind(target, PrimitiveTypeKind.UNSIGNED_INT),
                "__UINT_FAST64_MAX__" to getMaxForKind(target, PrimitiveTypeKind.UNSIGNED_LONG_LONG),

                "__WCHAR_MIN__" to getMinForKind(target, types.wCharType),
                "__WINT_MIN__" to getMinForKind(target, types.wIntType),
                "__SIG_ATOMIC_MIN__" to getMinForKind(target, types.sigAtomicType)
            )

            return limitMappings.map { (name, value) -> createValueMacro(name, value) }
        }
        private fun getMaxForKind(target: TargetInfo, kind: PrimitiveTypeKind): String {
            val underlying = target.getUnderlyingType(kind)
            return if (underlying.isUnsigned) getUnsignedMax(target, underlying) else getSignedMax(target, underlying)
        }

        private fun getMinForKind(target: TargetInfo, kind: PrimitiveTypeKind): String {
            val underlying = target.getUnderlyingType(kind)
            if (underlying.isUnsigned) return "0"

            val bits = target.getBitWidth(underlying)
            val minVal = -(BigInteger.TWO.pow(bits.toInt() - 1))
            return formatBigInt(minVal, underlying)
        }

        private fun getSignedMax(target: TargetInfo, kind: PrimitiveTypeKind): String {
            val bits = target.getBitWidth(kind)
            val maxVal = BigInteger.TWO.pow(bits.toInt() - 1) - BigInteger.ONE
            return formatBigInt(maxVal, kind)
        }

        private fun getUnsignedMax(target: TargetInfo, kind: PrimitiveTypeKind): String {
            val bits = target.getBitWidth(kind)
            val maxVal = BigInteger.TWO.pow(bits.toInt()) - BigInteger.ONE
            return formatBigInt(maxVal, kind)
        }

        private fun formatBigInt(value: BigInteger, kind: PrimitiveTypeKind): String {
            val suffix = when (kind) {
                PrimitiveTypeKind.LONG -> "L"
                PrimitiveTypeKind.UNSIGNED_LONG -> "UL"
                PrimitiveTypeKind.LONG_LONG -> "LL"
                PrimitiveTypeKind.UNSIGNED_LONG_LONG -> "ULL"
                PrimitiveTypeKind.UNSIGNED_INT -> "U"
                else -> ""
            }
            return "$value$suffix"
        }

        private fun createTypeMacro(name: String, kind: PrimitiveTypeKind): Macro {
            val tokens = kind.cName
                .split(" ")
                .map { KeywordToken.resolve(it, SourceLocation.EXPORTED, true)!! }

            return Macro(name = name, params = null, hasVA = false, tokens = tokens)
        }

        private fun createValueMacro(name: String, value: String): Macro {
            val token = Lexer(enablePreprocessor = false, baseLocation = SourceLocation.EXPORTED)
                .tokenize(value)
                .first { it !is EofToken }

            return Macro(name = name, params = null, hasVA = false, tokens = listOf(token))
        }
    }
}

class PreProcessorMacroExpander(val lexer: Lexer) {
    val activeMacros: MutableSet<String> = mutableSetOf()

    fun expandForIfDirective(
        tokens: List<Token>,
    ): List<Token> {
        val result = mutableListOf<Token>()
        var i = 0

        while (i < tokens.size) {
            val tok = tokens[i]
            val valName = tok.stringValue

            if (valName == "defined") {
                result.add(tok)
                i++

                var hasParen = false
                if (i < tokens.size && tokens[i] isA Symbol.LPAREN) {
                    hasParen = true
                    result.add(tokens[i])
                    i++
                }

                if (i < tokens.size && tokens[i].isIdentifierLike) {
                    result.add(tokens[i])
                    i++
                }

                if (hasParen && i < tokens.size && tokens[i] isA Symbol.RPAREN) {
                    result.add(tokens[i])
                    i++
                }
                continue
            }

            if (valName == "__has_include" ||
                valName == "__has_cpp_attribute" ||
                valName == "__has_attribute" ||
                valName == "__has_builtin" ||
                valName == "__has_feature" ||
                valName == "__has_extension" ||
                valName == "__has_warning" ||
                valName == "__building_module"
            ) {
                val keywordTok = tok
                i++

                val innerArgs = mutableListOf<Token>()
                var hasParen = false

                if (i < tokens.size && tokens[i] isA Symbol.LPAREN) {
                    hasParen = true
                    i++

                    var parenDepth = 1
                    while (i < tokens.size && parenDepth > 0) {
                        val argTok = tokens[i]
                        if (argTok isA Symbol.LPAREN) parenDepth++
                        else if (argTok isA Symbol.RPAREN) {
                            parenDepth--
                            if (parenDepth == 0) {
                                i++
                                break
                            }
                        }
                        innerArgs.add(argTok)
                        i++
                    }
                }

                val evaluatedToken = when (valName) {
                    "__has_include" -> evalHasInclude(innerArgs)
                    "__has_cpp_attribute" -> evalHasCppAttribute(innerArgs)
                    "__has_attribute" -> evalHasAttribute(innerArgs)
                    "__has_builtin" -> evalHasBuiltin(innerArgs)
                    "__has_feature" -> evalHasFeature(innerArgs)
                    "__has_extension" -> evalHasExtension(innerArgs)
                    "__has_warning" -> evalHasWarning(innerArgs)
                    "__building_module" -> evalBuildingModule(innerArgs)
                    else -> IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, keywordTok.location, false)
                }

                result.add(evaluatedToken)
                continue
            }

            val singleTokenList = listOf(tok)
            val expanded = expandTokens(singleTokenList)
            result.addAll(expanded)
            i++
        }

        return result
    }

    fun evalHasInclude(args: List<Token>): Token {
        val include = Preprocessor.parseIncludeHeader(args);
        val header = Preprocessor.resolveHeader(include.first,include.second, Path(lexer.currentFile), lexer.options!!)

        val res = if(header == null) "0" else if(lexer.hasInclude(header.absolutePathString())) "1" else "0"
        return IntToken(BigInteger(res), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun evalHasCppAttribute(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun evalHasAttribute(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun evalHasBuiltin(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun evalHasFeature(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun evalHasExtension(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun evalHasWarning(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }
    fun evalBuildingModule(args: List<Token>): Token {
        return IntToken(BigInteger("0"), Radix.DECIMAL,false,false,false,false, lexer.startLoc(), false)
    }

    fun expandMacro(macro: Macro, args: List<List<Token>>): List<Token> {
        if (activeMacros.contains(macro.name)) {
            return listOf(IdToken(macro.name, lexer.startLoc(), lexer.leadingWhiteSpace))
        }

        activeMacros.add(macro.name)

        val macroParams = macro.params ?: emptyList()
        val preExpandedArgs = args.mapIndexed { idx, argTokens ->
            val paramName = macroParams.getOrNull(idx)?.stringValue
            if (paramName != null && !isParamUsedWithHashOrDoubleHash(macro, paramName)) {
                expandTokens(argTokens)
            } else {
                argTokens
            }
        }

        val bodyTokens = resolveMacroBody(macro, preExpandedArgs)

        val expanded = expandTokens(bodyTokens)

        activeMacros.remove(macro.name)
        return expanded
    }

    private fun resolveMacroBody(macro: Macro, args: List<List<Token>>): List<Token> {
        val macroParams = macro.params ?: emptyList()

        if (macro.params != null) {
            val argsSize = args.size
            if (!macro.hasVA && macroParams.size != argsSize) {
                throw IllegalStateException("Invalid macro args count: expected ${macroParams.size}, got $argsSize")
            }
            if (macro.hasVA && argsSize < macroParams.size) {
                throw IllegalStateException("Variadic macro requires at least ${macroParams.size} arguments, got $argsSize")
            }
        }

        val paramToArgMap = mutableMapOf<String, List<Token>>()
        if (args != null && (macroParams.isNotEmpty() || macro.hasVA)) {
            for (idx in macroParams.indices) {
                val paramName = macroParams[idx].stringValue
                paramToArgMap[paramName] = args.getOrElse(idx) { emptyList() }
            }

            if (macro.hasVA) {
                val vaArgs = args.drop(macroParams.size)
                val vaTokens = mutableListOf<Token>()
                for ((vIdx, argTokens) in vaArgs.withIndex()) {
                    if (vIdx > 0) {
                        vaTokens.add(SymbolToken(Symbol.COMMA, lexer.startLoc(), leadingWhitespace = false))
                    }
                    vaTokens.addAll(argTokens)
                }
                paramToArgMap["__VA_ARGS__"] = vaTokens
            }
        }

        val result = mutableListOf<Token>()
        val body = macro.tokens
        var idx = 0

        while (idx < body.size) {
            val tok = body[idx]
            if (tok is OperatorToken && tok.value == Operator.HASH) {
                val nextTok = body.getOrNull(idx + 1)
                val argToConvert = nextTok?.stringValue?.let { paramToArgMap[it] }
                if (argToConvert != null) {
                    val strText = tokensToString(argToConvert)

                    val strToken = StringLiteralToken(
                        value = Util.utf16ToCodePoints(strText),
                        prefix = CharPrefix.NONE,
                        location = tok.location,
                        leadingWhitespace = tok.leadingWhitespace
                    )
                    result.add(strToken)
                    idx += 2
                    continue
                }
            }

            if (idx + 1 < body.size && body[idx + 1] is OperatorToken && (body[idx + 1] as OperatorToken).value == Operator.DOUBLE_HASH) {
                val leftTok = substituteToken(tok, paramToArgMap).firstOrNull() ?: tok
                val rightTokRaw = body.getOrNull(idx + 2)
                        ?: throw IllegalStateException("'##' operator requires right operand")
                val rightTok = substituteToken(rightTokRaw, paramToArgMap).firstOrNull() ?: rightTokRaw
                val pastedText = leftTok.stringValue + rightTok.stringValue
                val pastedTokens = Lexer(enablePreprocessor = false, baseLocation = leftTok.location, options = lexer.options).tokenize(pastedText)
                    .filter { it !is EofToken }
                if (pastedTokens.isNotEmpty()) {
                    val mergedToken = pastedTokens[0].copy(leadingWhitespace = leftTok.leadingWhitespace)
                    result.add(mergedToken)
                }
                idx += 3
                continue
            }

            val substituted = substituteToken(tok, paramToArgMap)
            result.addAll(substituted)
            idx++
        }
        return result
    }

    fun expandTokens(tokens: List<Token>, skipFirst: Boolean = false): List<Token> {
        val result = mutableListOf<Token>()
        var i = if(skipFirst) {
            tokens.getOrNull(0)?.let {
                result += it;
            }
            1;
        } else 0


        while (i < tokens.size) {
            val tok = tokens[i]
            val m = lexer.getMacro(tok.stringValue)

            if (m == null) {
                result += tok
                i++
                continue
            }

            if (m.params == null) {
                result += expandMacro(m, emptyList())
                i++
                continue
            }

            val parsed = parseMacroArgs(m, tokens.subList(i + 1, tokens.size))
            if (parsed == null) {
                result += tok
                i++
                continue
            }

            val (macroArgs, tokensConsumed) = parsed
            result += expandMacro(m, macroArgs)

            i += 1 + tokensConsumed
        }

        return result
    }
    fun parseMacroArgs(macro: Macro, tokens: List<Token>? = null): Pair<List<List<Token>>, Int>? {
        if (activeMacros.contains(macro.name)) {
            return null;
        }
        if (macro.params == null) {
            return emptyList<List<Token>>() to 0
        }

        val rawTokens: List<Token> = tokens ?: run {
            var saveI = lexer.i
            while (saveI < lexer.input.length && lexer.input[saveI].isWhitespace()) {
                saveI++
            }

            if (lexer.input.getOrNull(saveI) == '(') {
                val endRparenIndex = extractArgsSubstringEnd(lexer.input, lparenIndex = saveI)
                lexer.i = endRparenIndex + 1
                val argsSubstring = lexer.input.substring(saveI, endRparenIndex + 1)
                Lexer(enablePreprocessor = false, baseLocation = lexer.startLoc(), options = lexer.options).tokenize(argsSubstring).filter { it !is EofToken }
            } else {
                return null
            }
        }

        val firstNonWsIdx = rawTokens.indexOfFirst { it !is EofToken }
        if (firstNonWsIdx == -1 || rawTokens[firstNonWsIdx] !is SymbolToken || (rawTokens[firstNonWsIdx] as SymbolToken).value != Symbol.LPAREN) {
            return null
        }

        val resultArgs = mutableListOf<List<Token>>()
        val currentArg = mutableListOf<Token>()
        var parenDepth = 0
        var tokensConsumed = 0

        for (tok in rawTokens) {
            tokensConsumed++

            if (tok is SymbolToken) {
                when (tok.value) {
                    Symbol.LPAREN -> {
                        parenDepth++
                        if (parenDepth > 1) {
                            currentArg.add(tok)
                        }
                    }

                    Symbol.RPAREN -> {
                        parenDepth--
                        if (parenDepth > 0) {
                            currentArg.add(tok)
                        } else {
                            resultArgs.add(trimLeadingWhitespace(currentArg.toMutableList()))
                            return resultArgs to tokensConsumed
                        }
                    }

                    Symbol.COMMA -> {
                        if (parenDepth == 1) {
                            resultArgs.add(trimLeadingWhitespace(currentArg.toMutableList()))
                            currentArg.clear()
                        } else {
                            currentArg.add(tok)
                        }
                    }

                    else -> currentArg.add(tok)
                }
            } else {
                currentArg.add(tok)
            }
        }

        return resultArgs to tokensConsumed
    }

    private fun isParamUsedWithHashOrDoubleHash(macro: Macro, paramName: String): Boolean {
        val body = macro.tokens
        for (idx in body.indices) {
            if (body[idx].stringValue == paramName) {
                if (idx > 0 && body[idx - 1] is OperatorToken && (body[idx - 1] as OperatorToken).value == Operator.HASH) return true
                if (idx > 0 && body[idx - 1] is OperatorToken && (body[idx - 1] as OperatorToken).value == Operator.DOUBLE_HASH) return true
                if (idx + 1 < body.size && body[idx + 1] is OperatorToken && (body[idx + 1] as OperatorToken).value == Operator.DOUBLE_HASH) return true
            }
        }
        return false
    }

    fun extractArgsSubstringEnd(code: String, lparenIndex: Int): Int {
        require(lparenIndex < code.length && code[lparenIndex] == '(') {
            "lparenIndex must point to '('"
        }

        var i = lparenIndex + 1
        var parenDepth = 0
        var inString = false
        var inChar = false
        var isEscaped = false

        while (i < code.length) {
            val ch = code[i]

            if (isEscaped) {
                isEscaped = false
                i++
                continue
            }

            if (ch == '\\' && (inString || inChar)) {
                isEscaped = true
                i++
                continue
            }

            if (ch == '"' && !inChar) {
                inString = !inString
                i++
                continue
            }

            if (ch == '\'' && !inString) {
                inChar = !inChar
                i++
                continue
            }

            if (inString || inChar) {
                i++
                continue
            }

            when (ch) {
                '(' -> parenDepth++
                ')' -> {
                    if (parenDepth == 0) {
                        return i
                    }
                    parenDepth--
                }
            }
            i++
        }

        throw IllegalStateException("Unmatched '(' in macro call at index $lparenIndex")
    }

    private fun tokensToString(tokens: List<Token>): String {
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        for ((index, tok) in tokens.withIndex()) {
            if (index > 0 && tok.leadingWhitespace) {
                sb.append(' ')
            }
            sb.append(tok.stringValue)
        }
        return sb.toString()
    }

    private fun substituteToken(
        tok: Token,
        paramMap: Map<String, List<Token>>
    ): List<Token> {
        val name = tok.stringValue
        val argTokens = paramMap[name] ?: return listOf(tok)

        if (argTokens.isEmpty()) return emptyList()

        val res = argTokens.toMutableList()

        val first = res[0]
        if (first.leadingWhitespace != tok.leadingWhitespace) {
            res[0] = first.copy(leadingWhitespace = tok.leadingWhitespace)
        }

        return res
    }

    private fun trimLeadingWhitespace(tokens: List<Token>): List<Token> {
        if (tokens.isEmpty()) return tokens

        val result = tokens.toMutableList()
        val first = result[0]

        if (first.leadingWhitespace) {
            result[0] = first.copy(leadingWhitespace = false)
        }

        return result
    }
}

class PreprocessorExpressionParser(private val definedMacros: Set<String>) {

    private var index = 0
    private var currentTokens: List<Token> = emptyList()

    fun parsePreprocessorExpr(tokens: List<Token>): Boolean {
        this.currentTokens = tokens
        this.index = 0

        if (tokens.isEmpty()) return false

        val value = parseExpression(Precedence.LOWEST)
        return value != 0L
    }

    private fun parseExpression(precedence: Precedence): Long {
        var left = parsePrimary()

        while (index < currentTokens.size) {
            val token = currentTokens[index]

            if (token isA Symbol.RPAREN) break

            if (token isA Symbol.QUESTION) {
                if (Precedence.CONDITIONAL < precedence) break
                index++

                val whenTrue = parseExpression(Precedence.LOWEST)
                val p = peek();
                if (p != null && p notA Symbol.COLON) {
                    throw IllegalStateException("Expected ':' in conditional preprocessor expression")
                }
                index++

                val whenFalse = parseExpression(Precedence.CONDITIONAL)
                left = if (left != 0L) whenTrue else whenFalse
                continue
            }

            val op = extractBinaryOperator(token) ?: break

            if (op.precedence < precedence) break

            index++

            if (op == Operator.AND) {
                val right = parseExpression(op.precedence)
                left = if (left != 0L && right != 0L) 1L else 0L
                continue
            }

            if (op == Operator.OR) {
                val right = parseExpression(op.precedence)
                left = if (left != 0L || right != 0L) 1L else 0L
                continue
            }

            val nextPrecedence = if (op.precedence.isRightAssociative()) {
                op.precedence
            } else {
                val nextOrdinal = op.precedence.ordinal + 1
                if (nextOrdinal < Precedence.entries.size) Precedence.entries[nextOrdinal] else op.precedence
            }

            val right = parseExpression(nextPrecedence)
            left = applyBinaryOperator(op, left, right)
        }

        return left
    }

    private fun parsePrimary(): Long {
        val token = peek() ?: throw IllegalStateException("Unexpected end of expression")

        if (token is OperatorToken) {
            val op = token.value
            if (op.isUnary) {
                index++
                val operand = parseExpression(Precedence.UNARY)
                return when (op) {
                    Operator.NOT -> if (operand == 0L) 1L else 0L
                    Operator.BIT_NOT -> operand.inv()
                    Operator.MINUS -> -operand
                    Operator.PLUS -> operand
                    else -> throw IllegalStateException("Unsupported unary operator: ${op.value}")
                }
            }
        }

        if (token isA Symbol.LPAREN) {
            index++
            val valInside = parseExpression(Precedence.LOWEST)
            val closing = peek()
            if (closing != null && closing isA Symbol.RPAREN) {
                index++
            } else {
                throw IllegalStateException("Expected closing ')'")
            }
            return valInside
        }

        if (token.stringValue == "defined") {
            index++
            val hasParen = peek().let { it != null && it isA Symbol.LPAREN }
            if (hasParen) index++

            val macroToken = peek();
            if(macroToken == null || !macroToken.isIdentifierLike) {
                throw IllegalStateException("Expected identifier after defined")
            }

            index++

            if (hasParen) {
                val closing = peek()
                if (closing != null && closing isA Symbol.RPAREN) {
                    index++
                } else {
                    throw IllegalStateException("Expected closing ')' in defined()")
                }
            }

            return if (definedMacros.contains(macroToken.stringValue)) 1L else 0L
        }

        if (token is IntToken) {
            index++
            return token.value.toLong()
        }


        if (token.isIdentifierLike) {
            index++
            return when (token.stringValue) {
                "true" -> 1L
                "false" -> 0L
                else -> 0L
            }
        }

        throw IllegalStateException("Unexpected token in preprocessor expression: ${token.stringValue}")
    }

    private fun applyBinaryOperator(op: Operator, left: Long, right: Long): Long {
        return when (op) {
            Operator.OR -> if (left != 0L || right != 0L) 1L else 0L
            Operator.AND -> if (left != 0L && right != 0L) 1L else 0L
            Operator.BIT_OR -> left or right
            Operator.BIT_XOR -> left xor right
            Operator.AMP -> left and right
            Operator.EQUAL -> if (left == right) 1L else 0L
            Operator.NOT_EQ -> if (left != right) 1L else 0L
            Operator.LESS -> if (left < right) 1L else 0L
            Operator.LESS_EQUAL -> if (left <= right) 1L else 0L
            Operator.GREATER -> if (left > right) 1L else 0L
            Operator.GREATER_EQUAL -> if (left >= right) 1L else 0L
            Operator.LBITSHIFT -> left shl right.toInt()
            Operator.RBITSHIFT -> left shr right.toInt()
            Operator.PLUS -> left + right
            Operator.MINUS -> left - right
            Operator.POINTER -> left * right
            Operator.DIVIDE -> if (right != 0L) left / right else throw ArithmeticException("Division by zero in preprocessor")
            Operator.MOD -> if (right != 0L) left % right else throw ArithmeticException("Modulo by zero in preprocessor")
            else -> throw IllegalStateException("Operator ${op.value} cannot be used as binary in preprocessor")
        }
    }

    private fun extractBinaryOperator(token: Token): Operator? {
        if (token is OperatorToken && token.value.isBinary) {
            return token.value
        }
        return null
    }

    private fun peek(): Token? = currentTokens.getOrNull(index)
}