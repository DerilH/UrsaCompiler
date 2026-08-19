package org.derilh.ast

import org.derilh.core.CastMethod
import org.derilh.core.ClassType
import org.derilh.exceptions.SyntaxException
import org.derilh.core.Keyword
import org.derilh.core.MethodQualifiers
import org.derilh.core.Operator
import org.derilh.core.Precedence
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.RefQualifier
import org.derilh.core.Symbol
import org.derilh.lexer.BooleanToken
import org.derilh.lexer.CharToken
import org.derilh.lexer.EofToken
import org.derilh.lexer.FloatToken
import org.derilh.lexer.IdToken
import org.derilh.lexer.IntToken
import org.derilh.lexer.KeywordToken
import org.derilh.lexer.StringLiteralToken
import org.derilh.lexer.OperatorToken
import org.derilh.lexer.SourceLocation
import org.derilh.lexer.SymbolToken
import org.derilh.lexer.Token
import org.derilh.lexer.ValueToken

class Parser(var tokens: List<Token>) {
    private var position: Int = 0
    private var rootStatements = mutableListOf<ASTNode>()
    private var declarations =
        hashSetOf<IdentifierNode>(
            QualifiedIdentifierNode(
                listOf(IdentifierNode("std", SourceLocation.EXPORTED)),
                "string",
                isGlobal = false,
                SourceLocation.EXPORTED
            )
        )

    fun parse(): RootNode {
        val location = currentToken().location
        return RootNode(parseCompound().statements, location);
    }

    private fun parseCompound(): CompoundStatementNode {
        val location = currentToken().location
        rootStatements = mutableListOf()
        while (position < tokens.size) {
            val statement = tryParseStatement();
            if (statement is EmptyStatementNode) {
                throw SyntaxException("Unknow statement provided", currentToken(), position)
            }
            rootStatements += statement
        }

        return CompoundStatementNode(rootStatements, location)
    }

    private fun currentToken(): Token =
        tokens.getOrElse(position) { EofToken(location = tokens[position - 1].location) }

    private fun nextToken(): Token = tokens[position + 1]

    private fun nextTokenPrecedence(): Precedence {
        val token = currentToken()
        return when {
            token is OperatorToken -> token.value.precedence
            token isA Symbol.LPAREN -> Precedence.POSTFIX
            token isA Symbol.LBRACKET -> Precedence.POSTFIX
            token isA Keyword.NEW -> Precedence.POSTFIX
            token isA Keyword.SIZEOF -> Precedence.POSTFIX
            else -> Precedence.LOWEST
        }
    }

    internal fun parseExpression(precedence: Precedence = Precedence.LOWEST): ExpressionNode {
        val location = currentToken().location
        var current = currentToken();
        var expression = when {
            current is IdToken || current isA Operator.NAMESPACE -> IdExpressionNode(parseIdentifier(), location)
            current isA Keyword.THIS -> ThisExpressionNode(location).also { position++ }
            current isA Keyword.NULLPTR -> NullptrLiteralNode(location).also { position++ }
            current is ValueToken<*> -> parseValueExpression()
            current is OperatorToken && current.value.isUnary -> parsePrefixUnaryExpression()
            current isA Keyword.NEW -> parseNewExpression()
            current isA Keyword.SIZEOF -> parseSizeOfExpression()

            current isA Symbol.LPAREN -> {
                tryParseCStyleCast()
                    ?: run {
                        consume(Symbol.LPAREN)
                        val list = parseExpressionList()
                        consume(Symbol.RPAREN)
                        if (list.size == 1) list.first()
                        else CommaExpressionNode(list, location)
                    }
            }

            else -> EmptyExpressionNode
        }

        while (precedence < nextTokenPrecedence()) {
            current = currentToken();
            if (current is OperatorToken) {
                if (current.value.isBinary) {
                    expression = parseBinaryExpression(expression)
                } else if (current.value.isUnary) {
                    expression = parsePostfixUnaryExpression(expression)
                }
            } else if (currentToken() isA Symbol.LBRACKET) {
                expression = parseArrayAccess(expression)
            } else if (currentToken() isA Symbol.LPAREN) {
                expression = parseCallExpression(expression)
            }
        }
        return expression
    }

    private fun tryParseCStyleCast(): TypeCastExpressionNode? {
        val lastPos = position
        val location = currentToken().location
        try {
            consume(Symbol.LPAREN)
            val type = parseType();
            val declarator = parseDeclarator(type, true)
            consume(Symbol.RPAREN)
            val expression = parseExpression(Precedence.UNARY)
            return TypeCastExpressionNode(CastMethod.CSTYLE, true, declarator, expression, location)
        } catch (e: Exception) {
        }
        position = lastPos
        return null
    }

    private fun parseIfStatement(): IfStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.IF) throw SyntaxException(
            "Invalid if expression provided, expected if keyword",
            currentToken(),
            position
        )
        position++

        val condition = parseExpression();
        val body = tryParseStmtOrSingleExpression();
        var elseStatement: StatementNode = EmptyStatementNode;
        if (currentToken() isA Keyword.ELSE) {
            position++
            elseStatement = tryParseStmtOrSingleExpression()
            if (elseStatement is EmptyStatementNode) throw SyntaxException(
                "Empty else statement provided",
                currentToken(),
                position
            )
        }
        return IfStatementNode(condition, body, elseStatement, location)
    }

    private fun parseContinueStatement(): ContinueStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.CONTINUE) throw SyntaxException(
            "Expected continue keyword",
            currentToken(),
            position
        )
        position++
        return ContinueStatementNode(location).also { parseSeparator(true) }
    }

    private fun parseBreakStatement(): BreakStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.BREAK) throw SyntaxException(
            "Expected break keyword",
            currentToken(),
            position
        )
        position++
        return BreakStatementNode(location).also { parseSeparator(true) }
    }

    private fun parseForStatement(): ForStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.FOR) throw SyntaxException(
            "Invalid if expression provided, expected if keyword",
            currentToken(),
            position
        )
        position++

        if (currentToken() notA Symbol.LPAREN) throw SyntaxException(
            "Invalid for statement provided, expected '(' ",
            currentToken(),
            position
        )
        position++

        val init = if (isTypeToken()) {
            listOf(parseVarDeclaration())
        } else if (currentToken() isA Symbol.SEPARATOR) {
            parseSeparator()
            listOf()
        } else parseExpressionList()


        val condition = parseExpression()
        if (currentToken() notA Symbol.SEPARATOR) throw SyntaxException(
            "Invalid for statement provided, expected separator",
            currentToken(),
            position
        )
        position++

        val increment = parseExpressionList()

        if (currentToken() notA Symbol.RPAREN) throw SyntaxException(
            "Invalid for statement provided, expected ')'",
            currentToken(),
            position
        )
        position++


        val body = tryParseStmtOrSingleExpression();
        return ForStatementNode(init, condition, increment, body, location)
    }

    private fun parseDoStatement(): DoStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.DO) throw SyntaxException(
            "Invalid while statement provided, expected statement keyword", currentToken(), position
        )
        position++
        val body = tryParseStmtOrSingleExpression()
        if (currentToken() notA Keyword.WHILE) throw SyntaxException(
            "Invalid do while statement provided, expected while keyword", currentToken(), position
        )
        position++

        val condition = parseExpression()
        parseSeparator(true)
        return DoStatementNode(body, condition, location)
    }

    private fun parseWhileStatement(): WhileStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.WHILE) throw SyntaxException(
            "Invalid while statement provided, expected statement keyword", currentToken(), position
        )
        position++
        val condition = parseExpression()
        val body = tryParseStmtOrSingleExpression()
        return WhileStatementNode(condition, body, location)
    }

    private fun parseReturnStatement(): ReturnStatementNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.RETURN) throw SyntaxException(
            "Invalid return expression provided, expected return keyword",
            currentToken(),
            position
        )
        position++
        return ReturnStatementNode(parseExpression(), location)
    }

    private fun parseVarDeclaration(
        type: TypeNode? = null,
        declarators: List<DeclaratorNode>? = null
    ): VariableDeclarationNode {
        val location = currentToken().location
        val type = type ?: parseType()
        val decls = declarators ?: parseDeclaratorList(type)
        return VariableDeclarationNode(type, decls, location).also { parseSeparator() }
    }

    private fun parseDeclarator(baseType: TypeNode, abstract: Boolean = false): DeclaratorNode {
        val location = currentToken().location
        val (id, finalType) = parseDeclaratorInternal(baseType)
        if(abstract && id != null) throw SyntaxException("Expected abstract declarator but id provided: $id", currentToken(), position)

        val hasAssign = consume(Operator.ASSIGN, false);

        val initializer = if(!abstract && finalType !is FunctionTypeNode) {
            if(currentToken() isA Symbol.BEGIN) parseInitializerList();
            else if(hasAssign) parseExpression();
            else null
        } else null

        return DeclaratorNode(finalType, id, initializer, location)
    }

    private fun parseInitializerList(): InitializerListExpressionNode {
        val location = currentToken().location
        val args = parseArguments(true)
        return InitializerListExpressionNode(args.arguments, location)
    }

    private fun findPostfixDeclarator(): Int {
        var pos = position;
        if (currentToken() notA Symbol.LPAREN) {
            return -1;
        }
        pos++
        var parCount = 1;


        while (parCount != 0 && tokens[pos] notA Symbol.SEPARATOR) {
            if (tokens[pos] isA Symbol.LPAREN) parCount++
            else if (tokens[pos] isA Symbol.RPAREN) parCount--
            pos++
        }

        return pos
    }

    private fun parseDeclaratorInternal(baseType: TypeNode): Pair<IdentifierNode?, TypeNode> {
        if (currentToken().oneOf(listOf(Symbol.COMMA, Symbol.RPAREN))) {
            return Pair(null, baseType)
        }
        checkValidDeclaratorToken()
        var typeAfterPrefixes = baseType
        while (true) {
            typeAfterPrefixes = when {
                currentToken() isA Operator.POINTER -> parsePointerType(typeAfterPrefixes)

                currentToken() isA Operator.AND -> parseRValueReferenceType(typeAfterPrefixes)

                currentToken() isA Operator.AMP -> parseReferenceType(typeAfterPrefixes)
                currentToken() is IdToken -> {
                    val type = tryParseMemberPointerType(typeAfterPrefixes)
                    type ?: break
                }

                else -> break
            }
        }


        var id: IdentifierNode? = null

        when {
            currentToken() isA Symbol.LPAREN -> {

                var pos = findPostfixDeclarator();
                if (pos != -1) {
                    val lastPos = position;
                    position = pos;
                    while (true) {
                        typeAfterPrefixes = when {
                            currentToken() isA Symbol.LBRACKET -> parseArrayType(typeAfterPrefixes)
                            currentToken() isA Symbol.LPAREN -> parseFunctionType(typeAfterPrefixes)
                            else -> break
                        }
                    }
                    pos = position;
                    position = lastPos;
                }
                position++
                val decl = parseDeclaratorInternal(typeAfterPrefixes)
                consume(Symbol.RPAREN)
//                    .also { consume(Symbol.RPAREN) };
                position = if (pos != -1) pos else position
                return decl
            }

            currentToken() is IdToken -> {
                id = parseIdentifier()
            }

            else -> {
                id = null
            }
        }


        while (true) {
            typeAfterPrefixes = when {
                currentToken() isA Symbol.LBRACKET -> parseArrayType(typeAfterPrefixes)
                currentToken() isA Symbol.LPAREN -> parseFunctionType(typeAfterPrefixes)
                else -> break
            }
        }

        return Pair(id, typeAfterPrefixes)
    }

    private fun checkValidDeclaratorToken() {
        if (!currentToken().oneOf(
                listOf(
                    Operator.POINTER,
                    Operator.AND,
                    Operator.AMP,
                    Symbol.LPAREN
                )
            ) && currentToken() !is IdToken
        ) {
            throw SyntaxException("Invalid declarator token", currentToken(), position)
        }
    }

    private fun parseDeclaratorList(baseType: TypeNode): List<DeclaratorNode> {
        val declarators = mutableListOf<DeclaratorNode>()
        while (true) {
            declarators += parseDeclarator(baseType)
            if (currentToken() isA Symbol.COMMA) position++
            else if (currentToken() isA Symbol.SEPARATOR || currentToken() isA Symbol.BEGIN) {
                break
            } else throw SyntaxException("Invalid token provided after declarator", currentToken(), position)
        }
        return declarators;
    }


    private fun parseExpressionList(): List<ExpressionNode> {
        val exprs = mutableListOf<ExpressionNode>()
        while (currentToken() notA Symbol.SEPARATOR && currentToken() notA Symbol.RPAREN && currentToken() notA Symbol.END) {
            exprs += parseExpression();
            if (currentToken() isA Symbol.COMMA) position++
        }
        return exprs;
    }

    private fun parseArguments(acceptsBraced: Boolean): ArgumentsNode {
        val location = currentToken().location
        val hasL = consume(Symbol.LPAREN, !acceptsBraced)
        if (acceptsBraced && !hasL) {
            consume(Symbol.BEGIN)
        }

        val list = parseExpressionList();

        consume(if (hasL) Symbol.RPAREN else Symbol.END)
        return ArgumentsNode(list, location)
    }

    private fun parseArrayAccess(expression: ExpressionNode): ArrayAccessNode {
        val location = currentToken().location
        if (currentToken() notA Symbol.LBRACKET) throw SyntaxException(
            "Expected '[' for array access",
            currentToken(),
            position
        )
        position++;
        val index: ExpressionNode? =
            if (currentToken() isA Symbol.RBRACKET) {
                null
            } else parseExpression()
        position++
        return ArrayAccessNode(expression, index, location);
    }

    private fun parseCallExpression(id: ExpressionNode? = null): CallExpressionNode {
        val location = currentToken().location
        val id = id ?: parseExpression()
        val arguments = parseArguments(acceptsBraced = false)
        return CallExpressionNode(id, arguments, location)
    }

    private fun parseValueExpression(): ExpressionNode {
        var token = currentToken()
        if (token !is ValueToken<*>) throw SyntaxException(
            "Invalid expression provided, expected value",
            token,
            position
        )
        if (token is StringLiteralToken) {
            val location = token.location
            val literals = mutableListOf<StringLiteralNode>();

            while (token is StringLiteralToken) {
                literals += StringLiteralNode(token.value, token.prefix, token.location);

                position++;
                token = currentToken();
            }
            return if (literals.size == 1) literals[0] else StringConcatExpressionNode(literals, location);
        }
        position++
        val location = token.location
        return when (token) {
            is IntToken -> IntLiteralNode(
                token.value,
                token.radix,
                token.isUnsigned,
                token.isLong,
                token.isLongLong,
                token.isSizeT,
                location
            )

            is BooleanToken -> BooleanLiteralNode(token.value, location)
            is FloatToken -> FloatLiteralNode(token.value, token.isDouble, token.isLong, location)
            is CharToken -> {
                CharLiteralNode(
                    token.value,
                    token.prefix,
                    location = location
                )
            }

            else -> throw SyntaxException("Invalid expression provided, expected value", token, position)
        }
    }

    private fun parseNewExpression(): NewExpressionNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.NEW) throw SyntaxException(
            "Invalid new expression provided, expected new keyword",
            currentToken(),
            position
        )
        position++

        val placementArgs = if (currentToken() isA Symbol.LPAREN) {
            position++
            val list = parseExpressionList()
            if (currentToken() notA Symbol.RPAREN) throw SyntaxException(
                "Invalid new expression provided, expected ')' for placement arguments",
                currentToken(),
                position
            )
            position++
            list
        } else listOf()


        val type = if (currentToken() isA Symbol.LPAREN) {
            parseDeclarator(parseType()).type
        } else parseNewExprTypeId(parseType())

        var initializerList: InitializerListExpressionNode? = null;

        if (currentToken() isA Symbol.LPAREN || currentToken() isA Symbol.BEGIN) {
            val initLocation = currentToken().location
            position++
            initializerList = InitializerListExpressionNode(parseExpressionList(), initLocation);

            if (currentToken() notA Symbol.RPAREN && currentToken() notA Symbol.END) throw SyntaxException(
                "Invalid new expression provided, expected ')' or '}' for initializer list",
                currentToken(),
                position
            )
            position++
        }
        return NewExpressionNode(placementArgs, initializerList, type, location)
    }

    private fun parseNewExprTypeId(baseType: TypeNode? = null): TypeNode {
        var type = baseType ?: parseType()

        while (currentToken() isA Operator.POINTER || currentToken() isA Operator.AMP) {
            type = parsePointerType(type)
        }

        while (currentToken() isA Symbol.LBRACKET) {
            type = parseArrayType(type)
        }

        return type
    }

    private fun parseSizeOfExpression(): SizeofExpressionNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.SIZEOF) throw SyntaxException(
            "Invalid sizeof expression provided, expected sizeof keyword",
            currentToken(), position
        )
        position++
        if (currentToken() notA Symbol.LPAREN) throw SyntaxException(
            "Invalid sizeof expression provided, expected '(' for expression",
            currentToken(),
            position
        )
        position++

        val expression = tryParseType() ?: parseExpression()
        if (expression is EmptyExpressionNode) {
            throw SyntaxException(
                "Invalid sizeof expression provided, expected type or expression",
                currentToken(),
                position
            )
        }
        if (currentToken() notA Symbol.RPAREN) throw SyntaxException(
            "Invalid sizeof expression provided, expected ')' for expression",
            currentToken(), position
        )
        position++
        return SizeofExpressionNode(expression, location)
    }

    private fun parsePrefixUnaryExpression(): UnaryExpressionNode {
        val token = currentToken() as? OperatorToken
            ?: throw SyntaxException("Expected unary operator", currentToken(), position)
        val location = token.location

        if (!token.value.isUnary) {
            throw SyntaxException("Operator '${token.value}' cannot be used as unary operator", token, position)
        }

        position++
        val operand = parseExpression(Precedence.UNARY)

        return UnaryExpressionNode(operand, token.value, true, location)
    }

    private fun parsePostfixUnaryExpression(left: ExpressionNode): UnaryExpressionNode {
        val token = currentToken() as? OperatorToken
            ?: throw SyntaxException("Expected postfix operator", currentToken(), position)
        val location = token.location

        if (!token.value.isUnary) {
            throw SyntaxException("Operator '${token.value}' cannot be used as postfix operator", token, position)
        }

        position++

        return UnaryExpressionNode(left, token.value, false, location)
    }

    private fun parseBinaryExpression(left: ExpressionNode? = null): BinaryExpressionNode {
        val left = left ?: parseExpression();
        if (currentToken() !is OperatorToken) throw SyntaxException(
            "Invalid expression provided, expected operator",
            nextToken(),
            position + 1
        )
        val operator = currentToken() as OperatorToken;
        val location = operator.location
        if (!operator.value.isBinary) throw SyntaxException(
            "Operator '${operator}' cannot be used as binary operator",
            currentToken(),
            position
        )
        position++

        val precedence = if (operator.value.precedence.isRightAssociative()) {
            operator.value.precedence.decrement()
        } else {
            operator.value.precedence
        }

        val expression = parseExpression(precedence);
        return BinaryExpressionNode(left, expression, operator.value, location)
    }


    private fun isTypeToken(): Boolean {
        val basePos = position;
        var isType = false;
        try {
            parseType()
            isType = true
        } catch (e: Exception) {
            isType = false
        }
        position = basePos;
        return isType
    }

    private fun tryParseType(leftType: TypeNode? = null): TypeNode? {
        val basePos = position;
        try {
            return parseType(leftType)
        } catch (e: Exception) {
        }
        position = basePos;
        return null;
    }

    private fun parseType(leftType: TypeNode? = null): TypeNode {
        val location = currentToken().location
        var isConst = false
        var isVolatile = false
        var isSigned: Boolean? = null
        var isShort = false
        var longCount = 0
        var typeId: Any? = null

        fun assertFirstId() {
            if (typeId != null) {
                throw SyntaxException("Cannot use multiple ids in type", currentToken(), position)
            }
        }

        //        val type = leftType ?: (token is IdToken || token isA Operator.NAMESPACE)
        fun hasPrimitive(): Boolean {
            return isSigned != null || isShort || longCount != 0
        }

        while (true) {
            val token = currentToken()
            var advance = true;
            when {
                token isA Keyword.AUTO || token isA Keyword.DECLTYPE -> typeId = assertFirstId().let { token }
                typeId == null && !hasPrimitive() && (token is IdToken || token isA Operator.NAMESPACE) -> {
                    val id = parseIdentifier();
                    advance = false;
                    if (id in declarations) {
                        typeId = assertFirstId().let { id }
                    } else throw SyntaxException("Unknown type provided: $id", currentToken(), position)
                }

                token isA Keyword.UNSIGNED -> {
                    if (isSigned != null) throw SyntaxException(
                        "Cannot use multiple sign qualifiers",
                        currentToken(),
                        position
                    )
                    isSigned = false;
                }

                token isA Keyword.SIGNED -> {
                    if (isSigned != null) throw SyntaxException(
                        "Cannot use multiple sign qualifiers",
                        currentToken(),
                        position
                    )
                    isSigned = true;
                }

                token isA Keyword.SHORT -> isShort = true
                token isA Keyword.LONG -> longCount++
                token isA Keyword.INT -> typeId = assertFirstId().let { PrimitiveTypeKind.INT }
                token isA Keyword.CHAR -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR }
                token isA Keyword.CHAR8_T -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR8_T }
                token isA Keyword.CHAR16_T -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR16_T }
                token isA Keyword.CHAR32_T -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR32_T }
                token isA Keyword.WCHAR_T -> typeId = assertFirstId().let { PrimitiveTypeKind.WCHAR_T }
                token isA Keyword.FLOAT -> typeId = assertFirstId().let { PrimitiveTypeKind.FLOAT }
                token isA Keyword.DOUBLE -> typeId = assertFirstId().let { PrimitiveTypeKind.DOUBLE }
                token isA Keyword.BOOL -> typeId = assertFirstId().let { PrimitiveTypeKind.BOOL }
                token isA Keyword.VOID -> typeId = assertFirstId().let { PrimitiveTypeKind.VOID }
                token isA Keyword.CONST -> isConst = true
                token isA Keyword.VOLATILE -> isVolatile = true
                else -> break
            }

            if (advance) {
                position++
            }
        }


        //Type resolving
        fun assertNoPrimitives() {
            if (hasPrimitive()) {
                throw SyntaxException("Cannot use primitive type qualifiers with given type", currentToken(), position)
            }
        }

        if (typeId == null && hasPrimitive()) {
            typeId = PrimitiveTypeKind.INT;
        }

        return when (typeId) {
            is KeywordToken if typeId isA Keyword.AUTO -> AutoTypeNode(
                isConst,
                isVolatile,
                location
            ).also { assertNoPrimitives() }

//            is KeywordToken if typeId isA Keyword.DECLTYPE -> DeclTypeTypeNode(
//                isConst,
//                isVolatile,
//                location
//            ).also { assertNoPrimitives() }
            is KeywordToken if typeId isA Keyword.DECLTYPE -> TODO("Decltype is not supported yet")


            is IdentifierNode -> DeclaredTypeNode(typeId, isConst, isVolatile, location).also { assertNoPrimitives() }
            is PrimitiveTypeKind -> {
                checkPrimitiveCombinations(typeId, isSigned, isShort, longCount)
                val king = resolvePrimitiveKind(typeId, isSigned, isShort, longCount == 1, longCount == 2)
                    ?: throw SyntaxException("Invalid type provided: $typeId", currentToken(), position)
                PrimitiveTypeNode(king, isConst, isVolatile, location)
            }

            else -> throw SyntaxException("Invalid type provided: $typeId", currentToken(), position)
        }
    }

    fun resolvePrimitiveKind(
        typeId: PrimitiveTypeKind,
        isSigned: Boolean?,
        isShort: Boolean,
        isLong: Boolean,
        isLongLong: Boolean
    ): PrimitiveTypeKind? {
        return when (typeId) {
            PrimitiveTypeKind.INT -> {

                if (isSigned == false) {
                    when {
                        isShort -> PrimitiveTypeKind.UNSIGNED_SHORT
                        isLong -> PrimitiveTypeKind.UNSIGNED_LONG
                        isLongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG
                        else -> PrimitiveTypeKind.UNSIGNED_INT
                    }
                } else {
                    when {
                        isShort -> PrimitiveTypeKind.SHORT
                        isLong -> PrimitiveTypeKind.LONG
                        isLongLong -> PrimitiveTypeKind.LONG
                        else -> PrimitiveTypeKind.INT

                    }
                }
            }

            PrimitiveTypeKind.CHAR -> when (isSigned) {
                true -> PrimitiveTypeKind.SIGNED_CHAR
                false -> PrimitiveTypeKind.UNSIGNED_CHAR
                null -> PrimitiveTypeKind.CHAR
            }

            PrimitiveTypeKind.DOUBLE -> if (isLong) PrimitiveTypeKind.LONG_DOUBLE else PrimitiveTypeKind.DOUBLE
            else -> typeId
        }
    }


    private fun checkPrimitiveCombinations(
        kind: PrimitiveTypeKind,
        isSigned: Boolean?,
        isShort: Boolean,
        longCount: Int
    ) {
        val hasPrimitive = isSigned != null || isShort || longCount != 0
        fun assertNoPrimitives() {
            if (hasPrimitive) {
                throw SyntaxException("Cannot use primitive type qualifiers with given type", currentToken(), position)
            }
        }

        if (kind == PrimitiveTypeKind.FLOAT || kind == PrimitiveTypeKind.BOOL || kind == PrimitiveTypeKind.VOID) {
            assertNoPrimitives()
        } else if (kind == PrimitiveTypeKind.DOUBLE) {
            if (isSigned != null || isShort) {
                throw SyntaxException("Invalid modifiers for double", currentToken(), position)
            } else if (longCount > 1) throw SyntaxException(
                "Too many long keywords for double",
                currentToken(),
                position
            )
        } else if (kind == PrimitiveTypeKind.CHAR) {
            if (isShort || longCount > 0) {
                throw SyntaxException("Invalid size modifier for char", currentToken(), position)
            }
        } else if (kind == PrimitiveTypeKind.INT) {
            if (longCount > 2) {
                throw SyntaxException("Too many long keywords for int", currentToken(), position)
            }
        }
    }

    private fun parsePointerType(baseType: TypeNode): PointerTypeNode {
        val location = currentToken().location
        if (currentToken() notA Operator.POINTER) throw SyntaxException(
            "Expected '*' for pointer type",
            currentToken(),
            position
        )
        position++;

        val constAndVol = findConstAndVolatile()
        return PointerTypeNode(baseType, isConst = constAndVol.first, isVolatile = constAndVol.second, location = location)
    }

    private fun tryParseMemberPointerType(baseType: TypeNode): MemberPointerTypeNode? {
        val lastPos = position
        val location = currentToken().location
        val namespace = mutableListOf<IdentifierNode>()

        val isGlobal = if (currentToken() isA Operator.NAMESPACE) {
            position++
            true
        } else false

        var isPointer = false
        var isConst = false
        var isVolatile = false
        var classIdNode: IdentifierNode? = null

        while (true) {
            if (currentToken() !is IdToken) {
                break
            }

            val node = parseSimpleIdentifier()

            if (currentToken() isA Operator.NAMESPACE) { // ::
                namespace += node
                position++
            }

            if (currentToken() isA Operator.POINTER) { // *
                isPointer = true
                position++

                val (c, v) = findConstAndVolatile()
                isConst = c
                isVolatile = v

                namespace.removeLastOrNull()
                classIdNode = if (namespace.isEmpty() && !isGlobal) {
                    node
                } else {
                    QualifiedIdentifierNode(
                        qualifiers = namespace,
                        name = node.name,
                        isGlobal = isGlobal,
                        location = node.location
                    )
                }
                break
            }
        }

        if (isPointer && classIdNode != null) {
            return MemberPointerTypeNode(classIdNode, baseType, isConst, isVolatile, location)
        } else {
            position = lastPos
            return null
        }
    }

    private fun parseReferenceType(baseType: TypeNode): ReferenceTypeNode {
        val location = currentToken().location
        if (currentToken() notA Operator.AMP) throw SyntaxException(
            "Expected '&' for reference type",
            currentToken(),
            position
        )
        position++;

        val constAndVol = findConstAndVolatile()
        return ReferenceTypeNode(baseType, isConst = constAndVol.first, isVolatile = constAndVol.second, location = location)
    }

    private fun parseRValueReferenceType(baseType: TypeNode): RValueReferenceTypeNode {
        val location = currentToken().location
        if (currentToken() notA Operator.AND) throw SyntaxException(
            "Expected '&' for reference type",
            currentToken(),
            position
        )
        position++;

        if (currentToken() isA Operator.AMP || currentToken() isA Operator.POINTER || currentToken() isA Operator.AND || currentToken() isA Symbol.LBRACKET || currentToken() isA Keyword.CONST) {
            throw SyntaxException(
                "Invalid rvalue reference type provided, must be the last type",
                currentToken(),
                position
            )
        }

        val constAndVol = findConstAndVolatile()
        return RValueReferenceTypeNode(baseType, isConst = constAndVol.first, isVolatile = constAndVol.second, location = location)
    }

    //left -> isConst : right -> isVolatile
    private fun findConstAndVolatile(): Pair<Boolean, Boolean> {

        var isConst = false;
        var isVolatile = false;

        for (i in 0 until 2) {
            if (currentToken() isA Keyword.CONST) {
                if (isConst) throw SyntaxException("Cannot use multiple const qualifiers", currentToken(), position)
                isConst = true;
                position++
            } else if (currentToken() isA Keyword.VOLATILE) {
                if (isVolatile) throw SyntaxException(
                    "Cannot use multiple volatile qualifiers",
                    currentToken(),
                    position
                )
                isVolatile = true;
                position++
            } else break;
        }

        return Pair(isConst, isVolatile)
    }

    private fun parseFunctionQualifiers(): MethodQualifiers {
        var isConst = false
        var isVolatile = false
        var refQualifier = RefQualifier.NONE
        var isNoexcept = false

        while (true) {
            if (currentToken() isA Keyword.CONST) {
                if (isConst) throw SyntaxException("Duplicate 'const' qualifier", currentToken(), position)
                isConst = true
                position++
            } else if (currentToken() isA Keyword.VOLATILE) {
                if (isVolatile) throw SyntaxException("Duplicate 'volatile' qualifier", currentToken(), position)
                isVolatile = true
                position++
            } else break
        }

        if (currentToken() isA Operator.AMP) {
            refQualifier = RefQualifier.LVALUE
            position++
        } else if (currentToken() isA Operator.AND) {
            refQualifier = RefQualifier.RVALUE
            position++
        }

        if (currentToken() isA Keyword.NOEXCEPT) {
            isNoexcept = true
            position++
        }

        val token = currentToken()
        if (token isA Keyword.CONST || token isA Keyword.VOLATILE) {
            throw SyntaxException("cv-qualifiers must appear BEFORE ref-qualifiers and 'noexcept'", token, position)
        }
        if (token isA Operator.AMP || token isA Operator.AND) {
            throw SyntaxException("ref-qualifiers must appear BEFORE 'noexcept'", token, position)
        }

        return MethodQualifiers(isConst, isVolatile, refQualifier, isNoexcept)
    }

    private fun parseFunctionType(type: TypeNode): FunctionTypeNode {
        val location = currentToken().location
        val params = parseParameters();
        val qualifiers = parseFunctionQualifiers();
        return FunctionTypeNode(type, params, qualifiers, location)
    }

    private fun parseArrayType(type: TypeNode): ArrayTypeNode {
        val location = currentToken().location
        if (currentToken() notA Symbol.LBRACKET) throw SyntaxException(
            "Expected '[' for array type",
            currentToken(),
            position
        )
        position++;
        val size: ExpressionNode? =
            if (currentToken() isA Symbol.RBRACKET) null
            else parseExpression()
        position++
        return ArrayTypeNode(type, size, isConst = false, isVolatile = false, location = location)
    }

    private fun parseIdentifier(): IdentifierNode {
        val firstToken = currentToken()
        val namespace = mutableListOf<IdentifierNode>()
        val isGlobal = if (currentToken() isA Operator.NAMESPACE) {
            position++
            true
        } else false

        while (true) {
            val node = parseSimpleIdentifier()
            if (currentToken() isA Operator.NAMESPACE) {
                namespace += node
                position++
            } else {
                return if (namespace.isEmpty() && !isGlobal) {
                    node
                } else {
                    QualifiedIdentifierNode(
                        qualifiers = namespace,
                        name = node.name,
                        isGlobal = isGlobal,
                        location = firstToken.location
                    )
                }
            }
        }
    }

    private fun parseMemberInitializerList(): List<MemberInitializerNode> {
        val location = currentToken().location
        consume(Symbol.COLON)

        val list = mutableListOf<MemberInitializerNode>()
        while (currentToken() notA Symbol.BEGIN) {
            val id = parseIdentifier()
            val args = parseArguments(true)
            consume(Symbol.COMMA, false)

            list += MemberInitializerNode(id, args, false, location)
        }
        return list
    }

    private fun tryParseConstructor(classId: IdentifierNode): ConstructorDeclarationNode? {
        val lastPos = position
        val location = currentToken().location
        val declaratorNode = parseDeclarator(
            PrimitiveTypeNode(
                PrimitiveTypeKind.VOID,
                isConst = false,
                isVolatile = false,
                location = location
            )
        )

        if (classId != declaratorNode.id) {
            position = lastPos
            return null
        }

        val declType = declaratorNode.type;
        if (declType !is FunctionTypeNode) throw SyntaxException(
            "Invalid constructor declaration provided",
            currentToken(),
            position
        )

        val memberInitializers = if (currentToken() isA Symbol.COLON) parseMemberInitializerList() else listOf()
        val body = parseBlock()
        return ConstructorDeclarationNode(declType, memberInitializers, body, location);
    }

    private fun parseFunctionDeclaration(
        type: TypeNode? = null,
        declarator: DeclaratorNode? = null
    ): FunctionDefinitionNode {
        val location = currentToken().location
        val type = type ?: parseType();
        val declarator = declarator ?: parseDeclarator(type)

        if (declarator.type !is FunctionTypeNode) throw SyntaxException(
            "Invalid method declaration provided",
            currentToken(),
            position
        );
        if (declarator.id == null) throw SyntaxException(
            "No identifier provided for method declaration",
            currentToken(),
            position
        );

        val body = FunctionBodyNode(parseBlock().statements, location);
        return FunctionDefinitionNode(declarator, body, location).also { parseSeparator(false) }
    }

    private fun parseBlock(): AnonymousBlock {
        val token = currentToken()
        val location = token.location
        if (token isA Symbol.BEGIN) {
            position++
        } else throw SyntaxException("Begin symbol expected for code block", token, position)

        val nodes = mutableListOf<ASTNode>()
        while (currentToken() notA Symbol.END) {
            val statement = tryParseStatement();

            if (statement != EmptyStatementNode) {
                nodes += statement
                parseSeparator(false)
            } else {
                nodes += parseExpression()
                parseSeparator()
            }
        }
        position++
        return AnonymousBlock(nodes, location)
    }

    private fun tryParseStmtOrSingleExpression(): StatementNode {
        val location = currentToken().location
        val stmt = tryParseStatement();
        if (stmt is EmptyStatementNode) {
            return CompoundStatementNode(listOf(parseExpression()), location)
        }
        return stmt
    }

    private fun tryParseStatement(): StatementNode {
        val token = currentToken()
        return when {
            token isA Symbol.BEGIN -> parseBlock()
            token isA Keyword.RETURN -> parseReturnStatement()
            token isA Keyword.IF -> parseIfStatement()
            token isA Keyword.FOR -> parseForStatement()
            token isA Keyword.BREAK -> parseBreakStatement()
            token isA Keyword.WHILE -> parseWhileStatement()
            token isA Keyword.DO -> parseDoStatement()
            token isA Keyword.CONTINUE -> parseContinueStatement();
            else -> {
                val decl = tryParseDeclaration();
                if (decl is ClassDeclarationNode && decl.name != null) {
                    declarations += decl.name;
                }
                decl
            }
        }
    }

    private fun tryParseDeclaration(): StatementNode {
        val token = currentToken()
        val type = tryParseType();
        return when {
            type != null -> {
                val declarators = parseDeclaratorList(type);
                val decl: DeclarationNode =
                    if (declarators.size == 1 && currentToken() isA Symbol.BEGIN) {
                        parseFunctionDeclaration(type, declarators[0])
                    } else parseVarDeclaration(type, declarators);
//                    } else throw SyntaxException("Invalid declaration provided", tokens[nextTokenPos], position)
                decl
            }

            token isA Keyword.CLASS || token isA Keyword.STRUCT -> parseClassDeclaration()
            token isA Keyword.NAMESPACE -> parseNamespaceDeclaration(token.location)
            else -> EmptyStatementNode
        }
    }

    private fun consume(op: Operator, strict: Boolean = true): Boolean {
        if (strict && currentToken() notA op) throw SyntaxException(
            "Expected operator '$op'",
            currentToken(),
            position
        )
        return if (currentToken() isA op) {
            position++
            true
        } else false
    }

    private fun consume(symbol: Symbol, strict: Boolean = true): Boolean {
        if (strict && currentToken() notA symbol) throw SyntaxException(
            "Expected symbol '$symbol'",
            currentToken(),
            position
        )

        return if (currentToken() isA symbol) {
            position++
            true
        } else false
    }

    private fun consume(keyword: Keyword, strict: Boolean = true): Boolean {
        if (strict && currentToken() notA keyword) throw SyntaxException(
            "Expected keyword '$keyword'",
            currentToken(),
            position
        )

        return if (currentToken() isA keyword) {
            position++
            true
        } else false
    }

    private fun parseSeparator(strict: Boolean = true) {
        if (strict && currentToken() notA Symbol.SEPARATOR) throw SyntaxException(
            "Expected separator",
            currentToken(),
            position
        )
        if (currentToken() isA Symbol.SEPARATOR) position++
    }

    private fun parseClassBody(classId: IdentifierNode?): ClassBodyNode {
        val location = currentToken().location
        consume(Symbol.BEGIN)

        val nodes = mutableListOf<ASTNode>()
        while (currentToken() notA Symbol.END) {
            if (currentToken() oneOf listOf(Keyword.PUBLIC, Keyword.PRIVATE, Keyword.PROTECTED)) {
                val keyword = (currentToken() as KeywordToken).value;
                position++
                if (currentToken() notA Symbol.COLON) throw SyntaxException(
                    "Expected ':' symbol after access modifier",
                    currentToken(),
                    position
                )
                position++
                nodes += AccessDeclarationNode(keyword, currentToken().location);
            } else if (currentToken() is IdToken) {
                val constructor = if (classId != null) tryParseConstructor(classId) else null
                if (constructor == null) {
                    throw SyntaxException("Unknow identifier provided in class body", currentToken(), position)
                }
                nodes += constructor
            } else {
                val statement = tryParseStatement();
                if (statement != EmptyStatementNode) {
                    nodes += statement
                    parseSeparator(false)
                }
            }
        }
        position++
        return ClassBodyNode(nodes, location)
    }

    private fun parseParameters(): List<ParameterNode> {
        var token = currentToken()
        if (token isA Symbol.LPAREN) {
            position++;
        } else throw SyntaxException("Left parenthesis expected for parameters declaration", token, position)

        val list = mutableListOf<ParameterNode>()

        if (currentToken() isA Symbol.RPAREN) {
            position++
        } else {
            token = currentToken()
            while (token !is SymbolToken || token isA Symbol.COMMA) {
                list += parseParameter()
                token = currentToken()
                if (token isA Symbol.COMMA) position++
            }

            token = currentToken()
            if (token isA Symbol.RPAREN) {
                position++;
            } else throw SyntaxException("Right parenthesis expected for parameters declaration", token, position)
        }

        return list;
    }

    private fun parseParameter(): ParameterNode {
        val location = currentToken().location
        val type = parseType();
        val declarator = parseDeclarator(type);
        return ParameterNode(declarator, location)
    }

    private fun parseSimpleIdentifier(): IdentifierNode {
        val token = currentToken()
        if (token is IdToken) {
            position++;
            return IdentifierNode(token.value, token.location)
        }
        throw SyntaxException("Invalid identifier provided ", currentToken(), position)
    }

    private fun parseNamespaceDeclaration(location: SourceLocation): NamespaceDeclarationNode {
        consume(Keyword.NAMESPACE)

        var identifier: IdentifierNode? = null;
        if (currentToken() notA Symbol.BEGIN) {
            identifier = parseIdentifier()
        }
        val body = parseCompound()
        val decl = NamespaceDeclarationNode(identifier, body, location)
        parseSeparator(false)
        return decl;
    }

    private fun parseClassDeclaration(): DeclarationNode {
        val location = currentToken().location
        val classType = resolveClassType(currentToken());
        position++

        var identifier: IdentifierNode? = null;
        if (currentToken() notA Symbol.BEGIN) {
            identifier = parseSimpleIdentifier()
        }
        val body = parseClassBody(identifier)
        val decl = ClassDeclarationNode(identifier, classType, body, location)
        parseSeparator(false)
        return decl;
    }

    private fun resolveClassType(token: Token): ClassType {
        if(token !is KeywordToken) throw SyntaxException("Invalid class type provided", token, position)

        return when(token.value) {
            Keyword.CLASS -> ClassType.CLASS
            Keyword.STRUCT -> ClassType.STRUCT
            else -> throw SyntaxException("Invalid class keyword provided", token, position)
        }
    }
}