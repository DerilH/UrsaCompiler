package org.derilh.ast

import org.derilh.analyzer.ClassScope
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.GlobalScope
import org.derilh.analyzer.Scope
import org.derilh.analyzer.findOrCreateNamespaceDef
import org.derilh.core.CastMethod
import org.derilh.core.ClassType
import org.derilh.core.Keyword
import org.derilh.core.FunctionQualifiers
import org.derilh.core.Operator
import org.derilh.core.Precedence
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.RefQualifier
import org.derilh.core.SourceLocation
import org.derilh.core.Symbol
import org.derilh.core.AccessSpecifier
import org.derilh.core.ifFailure
import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SyntaxProblem
import org.derilh.lexer.BooleanToken
import org.derilh.lexer.CharToken
import org.derilh.lexer.EofToken
import org.derilh.lexer.FloatToken
import org.derilh.lexer.IdToken
import org.derilh.lexer.IntToken
import org.derilh.lexer.KeywordToken
import org.derilh.lexer.StringLiteralToken
import org.derilh.lexer.OperatorToken
import org.derilh.lexer.SymbolToken
import org.derilh.lexer.Token
import org.derilh.lexer.ValueToken
import org.derilh.semantic.analyzer.SemanticAnalyzer

class Parser(var tokens: List<Token>, val sema: SemanticAnalyzer) {
    private var position: Int = 0
    private val problems: Map<ProblemLevel, MutableList<SyntaxProblem>> = buildMap {
        for (level in ProblemLevel.entries) {
            this[level] = mutableListOf()
        }
    }

    fun parse(): ParseResult {
        val location = currentToken().location
        val scope = GlobalScope();
        sema.enterRootScope(scope)
        val root = RootNode(parseBlock(false).statements, location)
        root.scope = scope;
        sema.leaveRootScope()

        return ParseResult(problems, root)
    }

    private fun error(message: String, token: Token? = null): RecoveryStatementNode {
        val t = token ?: currentToken()
        problems[ProblemLevel.ERROR]!!.add(SyntaxProblem(message, ProblemLevel.ERROR, t.location))
        return RecoveryStatementNode(t.location)
    }

    private fun errorExpr(message: String, token: Token? = null): RecoveryExpressionNode {
        val t = token ?: currentToken()
        problems[ProblemLevel.ERROR]!!.add(SyntaxProblem(message, ProblemLevel.ERROR, t.location))
        return RecoveryExpressionNode(t.location)
    }

    private fun warn(message: String, token: Token? = null) {
        val t = token ?: currentToken()
        problems[ProblemLevel.WARNING]!!.add(SyntaxProblem(message, ProblemLevel.WARNING, t.location))
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
            current isA Keyword.TRUE -> BooleanLiteralNode(true, location).also { position++ }
            current isA Keyword.FALSE -> BooleanLiteralNode(false, location).also { position++ }
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

            else -> errorExpr("Invalid expression provided")
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
            val declarator = parseDeclarator(type)
            if (declarator !is AbstractDeclaratorNode) {
                error("Expected abstract declarator for cstyle cast", currentToken())
            }
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
        consume(Keyword.IF)


        //TODO: Add inplace var decl support
        consume(Symbol.LPAREN)
        val condition = parseExpression();
        consume(Symbol.RPAREN)

        val ifScope = Scope(sema.scope, null)
        lateinit var bodyScope: Scope;
        var elseBodyScope: Scope? = null;

        var body: StatementNode? = null;
        var elseBody: StatementNode? = null;

        sema.withScope(ifScope) {
            bodyScope = Scope(sema.scope, null)
            body = sema.withScope(bodyScope) {
                tryParseStmtOrSingleExpression() ?: error("Empty if statement provided", currentToken())
            }

            if (consume(Keyword.ELSE, false)) {

                elseBodyScope = Scope(sema.scope, null)

                elseBody = sema.withScope(elseBodyScope) {
                    tryParseStmtOrSingleExpression() ?: error("Empty else statement provided", currentToken())
                }
            }
        }
        return IfStatementNode(condition, body!!, elseBody, location).also {
            it.scope = ifScope;
            it.bodyScope = bodyScope;
            it.elseBodyScope = elseBodyScope;
        }
    }

    private fun parseContinueStatement(): ContinueStatementNode {
        val location = currentToken().location
        consume(Keyword.CONTINUE)
        position++
        return ContinueStatementNode(location).also { parseSeparator(true) }
    }

    private fun parseBreakStatement(): BreakStatementNode {
        val location = currentToken().location
        consume(Keyword.BREAK)
        position++
        return BreakStatementNode(location).also { parseSeparator(true) }
    }

    private fun parseForStatement(): ForStatementNode {
        val location = currentToken().location
        consume(Keyword.FOR)
        consume(Symbol.LPAREN)

        val init = if (isTypeToken()) {
            listOf(parseDeclarationSeq())
        } else if (currentToken() isA Symbol.SEPARATOR) {
            parseSeparator()
            listOf()
        } else parseExpressionList()


        val condition = parseExpression()
        parseSeparator()
        val increment = parseExpressionList()
        consume(Symbol.RPAREN)

        var body = tryParseStmtOrSingleExpression();
        if (body == null) {
            body = error("Empty for body", currentToken())
        }
        return ForStatementNode(init, condition, increment, body, location)
    }

    private fun parseDoStatement(): DoStatementNode {
        val location = currentToken().location
        consume(Keyword.DO)
        position++
        var body = tryParseStmtOrSingleExpression()
        if (body == null) {
            body = error("Do statement provided without body", currentToken())
        }
        consume(Keyword.WHILE)
        position++

        val condition = parseExpression()
        parseSeparator(true)
        return DoStatementNode(body, condition, location)
    }

    private fun parseWhileStatement(): WhileStatementNode {
        val location = currentToken().location
        consume(Keyword.WHILE)
        val condition = parseExpression()
        var body = tryParseStmtOrSingleExpression()
        if (body == null) {
            body = error("Empty while body", currentToken())
        }
        return WhileStatementNode(condition, body, location)
    }

    private fun parseReturnStatement(): ReturnStatementNode {
        val location = currentToken().location
        consume(Keyword.RETURN)

        val expr = if (currentToken() isA Symbol.SEPARATOR) {
            null;
        } else parseExpression();

        parseSeparator(true)

        return ReturnStatementNode(expr, location)
    }

    private fun parseDeclarationSeq(
        type: TypeNode? = null,
        declarators: List<DeclaratorNode>? = null
    ): DeclarationSequenceNode {
        val location = currentToken().location
        val type = type ?: parseType()
        val decls = declarators ?: parseDeclaratorList(type)
        return DeclarationSequenceNode(type, decls, location).also { parseSeparator() }
    }

    private fun parseDeclarator(baseType: TypeNode, defineInScope: Boolean = true): DeclaratorNode {
        val location = currentToken().location
        val (id, finalType) = parseDeclaratorInternal(baseType)
        if (id == null) return AbstractDeclaratorNode(finalType, location);

        if (finalType is FunctionTypeNode) {
            var decl: DeclSymbol.FunctionDecl? = null;
            if (defineInScope) {
                decl = sema.resolveSymbols(id, sema.scope, false).filterIsInstance<DeclSymbol.FunctionDecl>().firstOrNull()
                if (decl == null) {
                    val classDecl = sema.scope.findCurrentClass();
                    val defaultParamCount = finalType.params.count { (it.declarator as? VariableDeclaratorNode)?.initializer != null }
                    decl = DeclSymbol.FunctionDecl(id.name, sema.scope.ownerSymbol, finalType.qualifiers, classDecl != null, isBuiltin = false, defaultParamCount)
                    decl.astNode = id;
                    sema.scope.define(decl).ifFailure(sema::error)
                    decl.scope = Scope(sema.scope, decl)
                }
            } else decl = null;
            return FunctionDeclaratorNode(id, finalType, location).also {
                if (decl != null) {
                    decl.astNode = it
                    it.functionDecl = decl
                }
            }
        }

        val hasAssign = consume(Operator.ASSIGN, false);

        val initializer = if (currentToken() isA Symbol.BEGIN) parseInitializerList();
        else if (hasAssign) parseExpression()
        else null


        var decl: DeclSymbol.VariableDecl? = null;
        if (defineInScope) {
            decl = sema.resolveSymbolsLocal(id, sema.scope).filterIsInstance<DeclSymbol.VariableDecl>().firstOrNull()
            if (decl == null) {
                decl = DeclSymbol.variable(id.name, sema.scope.ownerSymbol)
                decl.astNode = id;
                sema.scope.define(decl).ifFailure(sema::error)
            } else sema.error("Variable ${id}, already defined in this scope", id)
        }
        return VariableDeclaratorNode(id, finalType, initializer, location).also {
            if (decl != null) {
                it.varDecl = decl;
                decl.astNode = it;
            }
        }
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
            error("Invalid declarator token", currentToken())
        }
    }

    private fun parseDeclaratorList(baseType: TypeNode): List<DeclaratorNode> {
        val declarators = mutableListOf<DeclaratorNode>()
        while (true) {
            declarators += parseDeclarator(baseType)
            if (currentToken() isA Symbol.COMMA) position++
            else if (currentToken() isA Symbol.SEPARATOR || currentToken() isA Symbol.BEGIN) {
                break
            } else {
                error("Invalid token provided after declarator", currentToken())
                break;
            }
        }
        return declarators;
    }


    private fun parseExpressionList(): List<ExpressionNode> {
        val exprs = mutableListOf<ExpressionNode>()
        while (currentToken() notA Symbol.SEPARATOR && currentToken() notA Symbol.RPAREN && currentToken() notA Symbol.END) {
            exprs += parseExpression();
            consume(Symbol.COMMA, false)
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
        consume(Symbol.LBRACKET)
        val index: ExpressionNode? =
            if (consume(Symbol.RBRACKET, false)) {
                null
            } else parseExpression()
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
        if (token !is ValueToken<*>) errorExpr("Invalid expression provided, expected value")
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

            else -> errorExpr("Invalid expression provided, expected value", token)
        }
    }

    private fun parseNewExpression(): NewExpressionNode {
        val location = currentToken().location
        consume(Keyword.NEW)

        val placementArgs = if (consume(Symbol.LPAREN, false)) {
            position++
            val list = parseExpressionList()
            consume(Symbol.RPAREN, false)
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

            if (currentToken() notA Symbol.RPAREN && currentToken() notA Symbol.END) error("Expected ')' after new type")
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
        if (currentToken() notA Keyword.SIZEOF) errorExpr("Invalid sizeof expression provided, expected sizeof keyword")
        position++
        if (currentToken() notA Symbol.LPAREN) error("Invalid sizeof expression provided, expected '(' for expression")
        position++

        val expression = tryParseType() ?: parseExpression()
        if (expression is EmptyExpressionNode) {
            errorExpr("Invalid sizeof expression provided, expected type or expression")
        }
        if (currentToken() notA Symbol.RPAREN) error("Invalid sizeof expression provided, expected ')' for expression")
        position++
        return SizeofExpressionNode(expression, location)
    }

    private fun parsePrefixUnaryExpression(): UnaryExpressionNode {
        val token = currentToken() as? OperatorToken
                ?: throw Exception("Expected unary operator")
        val location = token.location

        if (!token.value.isUnary) {
            errorExpr("Operator '${token.value}' cannot be used as unary operator", token)
        }

        position++
        val operand = parseExpression(Precedence.UNARY)

        return UnaryExpressionNode(operand, token.value, true, location)
    }

    private fun parsePostfixUnaryExpression(left: ExpressionNode): UnaryExpressionNode {
        val token = currentToken() as? OperatorToken
                ?: throw Exception("Expected postfix operator")
        val location = token.location

        if (!token.value.isUnary) {
            errorExpr("Operator '${token.value}' cannot be used as postfix operator", token)
        }

        position++

        return UnaryExpressionNode(left, token.value, false, location)
    }

    private fun parseBinaryExpression(left: ExpressionNode? = null): BinaryExpressionNode {
        val left = left ?: parseExpression();
        if (currentToken() !is OperatorToken) {
            errorExpr("Invalid expression provided, expected operator")
            return BinaryExpressionNode(
                left,
                RecoveryExpressionNode(currentToken().location),
                Operator.PLUS,
                left.location!!
            ) // dummy
        }
        val operator = currentToken() as OperatorToken;
        if (!operator.value.isBinary) {
            errorExpr("Operator '${operator}' cannot be used as binary operator")
        }
        position++

        val precedence = if (operator.value.precedence.isRightAssociative()) {
            operator.value.precedence.decrement()
        } else {
            operator.value.precedence
        }

        val right = parseExpression(precedence);
        return BinaryExpressionNode(left, right, operator.value, computeLocationSpan(left, right))
    }

    private fun computeLocationSpan(vararg expressions: ExpressionNode?): SourceLocation {
        val baseLine = expressions[0]!!.location!!.line
        var latestLoc: SourceLocation = expressions[0]!!.location!!;
        for (expr in expressions) {
            if (expr == null || baseLine != expr.location!!.line) continue;
            latestLoc = expr.location!!;
        }

        var length = latestLoc.column - expressions[0]!!.location!!.column + latestLoc.length
        if (length == 0) length = 1;
        return expressions[0]!!.location!!.copy(length = length)
    }

    private fun isTypeToken(): Boolean {
        val basePos = position;
        val baseProblems = problems.mapValues { it.value.size }
        var isType = false;
        try {
            parseType()
            isType = problems.all { (level, list) -> list.size == baseProblems[level] }
        } catch (e: Exception) {
            isType = false
        } finally {
            // Rollback problems added during speculative parsing
            problems.forEach { (level, list) ->
                val prevSize = baseProblems[level] ?: 0
                while (list.size > prevSize) {
                    list.removeAt(list.size - 1)
                }
            }
        }
        position = basePos;
        return isType
    }

    private fun tryParseType(): TypeNode? {
        val basePos = position;
        val baseProblems = problems.mapValues { it.value.size }
        try {
            val type = parseType()
            if (problems.all { (level, list) -> list.size == baseProblems[level] }) {
                return type
            }
        } catch (e: Exception) {
        }
        // Rollback problems
        problems.forEach { (level, list) ->
            val prevSize = baseProblems[level] ?: 0
            while (list.size > prevSize) {
                list.removeAt(list.size - 1)
            }
        }
        position = basePos;
        return null;
    }

    private fun parseType(): TypeNode {
        val location = currentToken().location
        var isConst = false
        var isVolatile = false
        var isSigned: Boolean? = null
        var isShort = false
        var longCount = 0
        var typeId: Any? = null

        fun assertFirstId() {
            if (typeId != null) {
                error("Cannot use multiple ids in type")
            }
        }

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

                    val decls = sema.resolveSymbols(id, sema.scope, false).filterIsInstance<DeclSymbol.ClassDecl>();
                    if (decls.isNotEmpty()) {
                        typeId = assertFirstId().let { id }
                    } else error("Unknown type provided: $id")
                }

                token isA Keyword.UNSIGNED -> {
                    if (isSigned != null) error("Cannot use multiple sign qualifiers")
                    isSigned = false;
                }

                token isA Keyword.SIGNED -> {
                    if (isSigned != null) error("Cannot use multiple sign qualifiers")
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
                error("Cannot use primitive type qualifiers with given type")
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
                        ?: run {
                            error("Invalid type provided: $typeId")
                            PrimitiveTypeKind.INT
                        }
                PrimitiveTypeNode(king, isConst, isVolatile, location)
            }

            else -> {
                error("Invalid type provided: $typeId")
                PrimitiveTypeNode(PrimitiveTypeKind.INT, isConst, isVolatile, location)
            }
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
                error("Cannot use primitive type qualifiers with given type")
            }
        }

        if (kind == PrimitiveTypeKind.FLOAT || kind == PrimitiveTypeKind.BOOL || kind == PrimitiveTypeKind.VOID) {
            assertNoPrimitives()
        } else if (kind == PrimitiveTypeKind.DOUBLE) {
            if (isSigned != null || isShort) {
                error("Invalid modifiers for double")
            } else if (longCount > 1) error("Too many long keywords for double")
        } else if (kind == PrimitiveTypeKind.CHAR) {
            if (isShort || longCount > 0) {
                error("Invalid size modifier for char")
            }
        } else if (kind == PrimitiveTypeKind.INT) {
            if (longCount > 2) {
                error("Too many long keywords for int")
            }
        }
    }

    private fun parsePointerType(baseType: TypeNode): PointerTypeNode {
        val location = currentToken().location
        if (currentToken() notA Operator.POINTER) error("Expected '*' for pointer type")
        position++;

        val constAndVol = findConstAndVolatile()
        return PointerTypeNode(
            baseType,
            isConst = constAndVol.first,
            isVolatile = constAndVol.second,
            location = location
        )
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
        if (currentToken() notA Operator.AMP) error("Expected '&' for reference type")
        position++;

        val constAndVol = findConstAndVolatile()
        return ReferenceTypeNode(
            baseType,
            isConst = constAndVol.first,
            isVolatile = constAndVol.second,
            location = location
        )
    }

    private fun parseRValueReferenceType(baseType: TypeNode): RValueReferenceTypeNode {
        val location = currentToken().location
        if (currentToken() notA Operator.AND) error("Expected '&' for reference type")
        position++;

        if (currentToken() isA Operator.AMP || currentToken() isA Operator.POINTER || currentToken() isA Operator.AND || currentToken() isA Symbol.LBRACKET || currentToken() isA Keyword.CONST) {
            error("Invalid rvalue reference type provided, must be the last type")
        }

        val constAndVol = findConstAndVolatile()
        return RValueReferenceTypeNode(
            baseType,
            isConst = constAndVol.first,
            isVolatile = constAndVol.second,
            location = location
        )
    }

    //left -> isConst : right -> isVolatile
    private fun findConstAndVolatile(): Pair<Boolean, Boolean> {

        var isConst = false;
        var isVolatile = false;

        for (i in 0 until 2) {
            if (currentToken() isA Keyword.CONST) {
                if (isConst) error("Cannot use multiple const qualifiers")
                isConst = true;
                position++
            } else if (currentToken() isA Keyword.VOLATILE) {
                if (isVolatile) error("Cannot use multiple volatile qualifiers")
                isVolatile = true;
                position++
            } else break;
        }

        return Pair(isConst, isVolatile)
    }

    private fun parseFunctionQualifiers(): FunctionQualifiers {
        var isConst = false
        var isVolatile = false
        var refQualifier = RefQualifier.NONE
        var isNoexcept = false

        while (true) {
            if (currentToken() isA Keyword.CONST) {
                if (isConst) error("Duplicate 'const' qualifier")
                isConst = true
                position++
            } else if (currentToken() isA Keyword.VOLATILE) {
                if (isVolatile) error("Duplicate 'volatile' qualifier")
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
            error("cv-qualifiers must appear BEFORE ref-qualifiers and 'noexcept'", token)
        }
        if (token isA Operator.AMP || token isA Operator.AND) {
            error("ref-qualifiers must appear BEFORE 'noexcept'", token)
        }

        return FunctionQualifiers(isConst, isVolatile, refQualifier, isNoexcept)
    }

    private fun parseFunctionType(type: TypeNode): FunctionTypeNode {
        val location = currentToken().location
        val params = parseParameters();
        val qualifiers = parseFunctionQualifiers();
        return FunctionTypeNode(type, params, qualifiers, location)
    }

    private fun parseArrayType(type: TypeNode): ArrayTypeNode {
        val location = currentToken().location
        if (currentToken() notA Symbol.LBRACKET) error("Expected '[' for array type")
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

    private fun tryParseConstructor(classId: IdentifierNode): ASTNode? {
        val lastPos = position
        val location = currentToken().location
        val declaratorNode = parseDeclarator(PrimitiveTypeNode(PrimitiveTypeKind.VOID, isConst = false, isVolatile = false, location = location), false)

        if (declaratorNode !is FunctionDeclaratorNode) {
            return null
        }

        if (classId != declaratorNode.id) {
            position = lastPos
            return null
        }

        val declType = declaratorNode.type;
        if (declType !is FunctionTypeNode) {
            return error("Invalid constructor declaration provided", currentToken())
        }


        var decl = sema.resolveSymbols(classId, sema.scope, false).filterIsInstance<DeclSymbol.ConstructorDecl>().firstOrNull()
        if (decl == null) {
            val defaultParamCount = declType.params.count { (it.declarator as? VariableDeclaratorNode)?.initializer != null }
            //TODO: add explicit support
            decl = DeclSymbol.constructorDecl(classId.name, sema.scope.ownerSymbol, declType.qualifiers, isExplicit = false, defaultParamCount)
            decl.astNode = declaratorNode;
            sema.scope.define(decl).ifFailure(sema::error)
            decl.scope = Scope(sema.scope, decl)
        }

        if (currentToken() isA Symbol.SEPARATOR) {
            return ConstructorDeclarationNode(declType, location).also {
                decl.astNode = it
                it.ctorDecl = decl
            }
        } else {
            val memberInitializers = if (currentToken() isA Symbol.COLON) parseMemberInitializerList() else listOf()
            val body = sema.withScope(decl.scope) {
                parseBlock()
            }
            return ConstructorDefinitionNode(declType, memberInitializers, body, location).also {
                decl.astNode = it
                decl.definitionNode = it;
                it.ctorDecl = decl
            };
        }
    }

    private fun parseFunctionDefinition(
        type: TypeNode? = null,
        declarator: DeclaratorNode? = null
    ): StatementNode {
        val location = currentToken().location
        val type = type ?: parseType();
        val declarator = declarator ?: parseDeclarator(type)


        val funcType = declarator.type;
        if (declarator !is FunctionDeclaratorNode || funcType !is FunctionTypeNode) {
            return error("Invalid function definition provided", currentToken())
        }

        var decl = sema.resolveSymbols(declarator.id, sema.scope, false).filterIsInstance<DeclSymbol.FunctionDecl>().firstOrNull()
        if (decl == null) {
            val classDecl = sema.scope.findCurrentClass();

            decl = DeclSymbol.FunctionDecl(declarator.id.name, sema.scope.ownerSymbol, funcType.qualifiers, classDecl != null, isBuiltin = false, declarator.defaultParamCount)
            decl.astNode = declarator;
            sema.scope.define(decl).ifFailure(sema::error)
            decl.scope = Scope(sema.scope, decl)
        }

        val body = sema.withScope(decl.scope) {
            for (param in funcType.params) {
                val name = param.name?.name ?: continue
                val decl = DeclSymbol.param(name, sema.scope.ownerSymbol)
                decl.astNode = declarator;
                sema.scope.define(decl).ifFailure(sema::error)
            }
            FunctionBodyNode(parseBlock().statements, location);
        }
        return FunctionDefinitionNode(declarator, body, location).also {
            parseSeparator(false);
            decl.astNode = it
            decl.definitionNode = it
            it.functionDecl = decl
        }
    }

    private fun parseBlock(withBraces: Boolean = true): CompoundStatementNode {
        val token = currentToken()
        val location = token.location
        if (withBraces) {
            consume(Symbol.BEGIN)
        }
        val nodes = mutableListOf<ASTNode>()
        while (currentToken() notA Symbol.END && currentToken() !is EofToken) {
            val statement = tryParseStatement();
            if (statement is RecoveryStatementNode) {
                skipUntilNextStmt()
            } else if (statement != EmptyStatementNode) {
                nodes += statement
                parseSeparator(false)
            } else {
                val expr = parseExpression()
                if (expr is RecoveryExpressionNode) {
                    position++
                } else {
                    nodes += expr
                    parseSeparator()
                }
            }
        }
        if (withBraces) {
            consume(Symbol.END)
        }
        return CompoundStatementNode(nodes, location)
    }

    private fun skipUntilNextStmt() {
        while (currentToken() notA Symbol.SEPARATOR && currentToken() notA Symbol.END && currentToken() !is EofToken) {
            position++
        }

        if (currentToken() !is EofToken) {
            position++
        }
    }

    private fun tryParseStmtOrSingleExpression(): StatementNode? {
        val location = currentToken().location
        val stmt = tryParseStatement();
        if (stmt is EmptyStatementNode) {
            val expr = parseExpression();
            return if (expr is EmptyExpressionNode) null
            else CompoundStatementNode(listOf(parseExpression()), location)
        }
        return stmt as? CompoundStatementNode ?: CompoundStatementNode(listOf(stmt), location)
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
                val decl = tryParseDeclarationOrDefinition();
                val className = (decl as? ClassDeclarationNode)?.name ?: (decl as? ClassDefinitionNode)?.name;
                if (className != null) {
//                    declarations += className;
                }
                decl
            }
        }
    }

    private fun tryParseDeclarationOrDefinition(): StatementNode {
        val token = currentToken()
        val type = tryParseType();
        return when {
            type != null -> {
                val declarators = parseDeclaratorList(type);
                val decl: StatementNode =
                    if (declarators.size == 1 && currentToken() isA Symbol.BEGIN) {
                        parseFunctionDefinition(type, declarators[0])
                    } else parseDeclarationSeq(type, declarators);
//                    } else throw SyntaxException("Invalid declaration provided", tokens[nextTokenPos], position)
                decl
            }

            token isA Keyword.CLASS || token isA Keyword.STRUCT -> parseClass()
            token isA Keyword.NAMESPACE -> parseNamespaceDefinition(token.location)
            else -> EmptyStatementNode
        }
    }

    private fun consume(op: Operator, strict: Boolean = true): Boolean {
        if (strict && currentToken() notA op) {
            error("Expected operator '$op'")
        }
        return if (currentToken() isA op) {
            position++
            true
        } else false
    }

    private fun consume(symbol: Symbol, strict: Boolean = true): Boolean {
        if (strict && currentToken() notA symbol) {
            error("Expected symbol '$symbol'", currentToken())
        }

        return if (currentToken() isA symbol) {
            position++
            true
        } else false
    }

    private fun consume(keyword: Keyword, strict: Boolean = true): Boolean {
        if (strict && currentToken() notA keyword) {
            error("Expected keyword '$keyword'", currentToken())
        }

        return if (currentToken() isA keyword) {
            position++
            true
        } else false
    }

    private fun parseSeparator(strict: Boolean = true) {
        if (strict && currentToken() notA Symbol.SEPARATOR) {
            error("Expected separator", currentToken())
        }
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
                consume(Symbol.COLON)

                val specifier = when (keyword) {
                    Keyword.PUBLIC -> AccessSpecifier.PUBLIC
                    Keyword.PRIVATE -> AccessSpecifier.PRIVATE
                    Keyword.PROTECTED -> AccessSpecifier.PROTECTED
                    else -> throw IllegalArgumentException("Invalid keyword acquired")
                }

                nodes += AccessSpecifierNode(specifier, currentToken().location);
            } else {

                var isCtor = false;
                if (currentToken() is IdToken) {
                    val constructor = if (classId != null) tryParseConstructor(classId) else null
                    if (constructor != null) {
                        nodes += constructor;
                        isCtor = true;
                    }
                }

                if (!isCtor) {
                    val statement = tryParseStatement();
                    if (statement != EmptyStatementNode) {
                        nodes += statement
                        parseSeparator(false)
                    }
                }
            }
        }
        consume(Symbol.END)
        return ClassBodyNode(nodes, location)
    }

    private fun parseParameters(): List<ParameterNode> {
        var token: Token
        consume(Symbol.LPAREN)

        val list = mutableListOf<ParameterNode>()

        if (!consume(Symbol.RPAREN, false)) {
            token = currentToken()
            while (token !is SymbolToken || token isA Symbol.COMMA) {
                list += parseParameter()
                token = currentToken()
                if (token isA Symbol.COMMA) position++
            }
            consume(Symbol.RPAREN)
        }

        return list;
    }

    private fun parseParameter(): ParameterNode {
        val location = currentToken().location
        val type = parseType();
        val declarator = parseDeclarator(type, false);

        if (declarator !is AbstractDeclaratorNode && declarator !is VariableDeclaratorNode) {
            error("Invalid parameter declaration provided", currentToken())
        }

        return ParameterNode(declarator, location)
    }

    private fun parseSimpleIdentifier(): IdentifierNode {
        val token = currentToken()
        if (token is IdToken) {
            position++;
            return IdentifierNode(token.value, token.location)
        }
        error("Invalid identifier provided ")
        return IdentifierNode("", token.location)
    }

    private fun parseNamespaceDefinition(location: SourceLocation): NamespaceDeclarationNode {
        consume(Keyword.NAMESPACE)

        var identifier: IdentifierNode? = null;
        if (currentToken() notA Symbol.BEGIN) {
            identifier = parseIdentifier()
        }


        var leaveCount: Int = 0;
        var decl: DeclSymbol.NamespaceDecl? = null;
        if (identifier == null) {
            decl = findOrCreateNamespaceDef(sema.getAnonNamespaceName(), sema, true);
            sema.scope.addUsingDirective(decl.scope)
        } else {
            if (identifier is QualifiedIdentifierNode) {
                for (qual in identifier.qualifiers) {
                    val ns = findOrCreateNamespaceDef(qual.name, sema, false)
                    sema.enterScope(ns.scope);
                    leaveCount++;
                }
            }

            decl = findOrCreateNamespaceDef(identifier.name, sema, false)
        }


        sema.enterScope(decl.scope)
        leaveCount++

        val body = NamespaceBodyNode(parseBlock().statements, location);
        repeat(leaveCount) { sema.leaveScope() }
        return NamespaceDeclarationNode(identifier, body, location).also {
            parseSeparator(false)
            decl.declarations += it
            it.nsDecl = decl;
        }
    }

    private fun parseClass(): StatementNode {
        val location = currentToken().location
        val classType = resolveClassType(currentToken());
        position++

        var identifier: IdentifierNode? = null;
        if (currentToken() notA Symbol.BEGIN) {
            identifier = parseSimpleIdentifier()
        }
        var decl: DeclSymbol.ClassDecl
        if (identifier != null) {
            val found = sema.resolveSymbols(identifier, sema.scope, false).filterIsInstance<DeclSymbol.ClassDecl>().firstOrNull()
            if (found == null) {
                decl = DeclSymbol.classDecl(identifier.name, classType, sema.scope.ownerSymbol)
                decl.astNode = identifier;
                sema.scope.define(decl).ifFailure(sema::error)
                decl.scope = ClassScope(sema.scope, decl)
            } else decl = found;
        } else {
            decl = DeclSymbol.classDecl(sema.getAnonClassName(), classType, sema.scope.ownerSymbol)
            sema.scope.define(decl).ifFailure(sema::error)
            decl.scope = ClassScope(sema.scope, decl)
        }


        if (currentToken() notA Symbol.SEPARATOR) {
            val body = sema.withScope(decl.scope) {
                parseClassBody(identifier)
            }

            return ClassDefinitionNode(identifier, classType, body, location).also {
                parseSeparator(true)
                decl.astNode = it
                decl.definitionNode = it
                it.classDecl = decl
            }

        } else {
            return ClassDeclarationNode(identifier, classType, location).also { node ->
                parseSeparator(true)
                decl.astNode = node
                node.classDecl = decl
            }
        }
    }

    private fun resolveClassType(token: Token): ClassType {
        if (token !is KeywordToken) {
            error("Invalid class type provided")
            return ClassType.CLASS
        }

        return when (token.value) {
            Keyword.CLASS -> ClassType.CLASS
            Keyword.STRUCT -> ClassType.STRUCT
            else -> {
                error("Invalid class keyword provided")
                ClassType.CLASS
            }
        }
    }
}