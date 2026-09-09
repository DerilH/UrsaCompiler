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
import org.derilh.core.SourceLocation
import org.derilh.core.Symbol
import org.derilh.core.AccessSpecifier
import org.derilh.core.ICVQualifier
import org.derilh.core.INoExceptSpecifier
import org.derilh.core.IRefQualifier
import org.derilh.core.OpResult
import org.derilh.core.Options
import org.derilh.core.getAsOrElse
import org.derilh.core.getOrNull
import org.derilh.core.ifFailure
import org.derilh.core.isSuccess
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
import org.derilh.lexer.Token
import org.derilh.lexer.ValueToken
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.util.ErrorHelper
import org.derilh.util.ErrorHelper.Companion.getStackTrace
import semantic.Declarator
import semantic.DeclaratorChunk
import semantic.TypeId

class Parser(var tokens: List<Token>, val sema: SemanticAnalyzer, val options: Options) {
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
        val root = RootNode(location.file, parseBlock(false).statements, location)
        root.scope = scope;
        sema.leaveRootScope()

        return ParseResult(problems, root)
    }

    private fun error(message: String, location: SourceLocation): RecoveryStatementNode {
        problems[ProblemLevel.ERROR]!!.add(SyntaxProblem(message, ProblemLevel.ERROR, location, getStackTrace(options)))
        return RecoveryStatementNode(location)
    }

    private fun error(message: String, token: Token? = null): RecoveryStatementNode {
        val t = token ?: currentToken()
        problems[ProblemLevel.ERROR]!!.add(SyntaxProblem(message, ProblemLevel.ERROR, t.location, getStackTrace(options)))
        return RecoveryStatementNode(t.location)
    }

    private fun errorExpr(message: String, token: Token? = null): RecoveryExpressionNode {
        val t = token ?: currentToken()
        problems[ProblemLevel.ERROR]!!.add(SyntaxProblem(message, ProblemLevel.ERROR, t.location, getStackTrace(options)))
        return RecoveryExpressionNode(t.location)
    }

    private fun warn(message: String, token: Token? = null) {
        val t = token ?: currentToken()
        problems[ProblemLevel.WARNING]!!.add(SyntaxProblem(message, ProblemLevel.WARNING, t.location, getStackTrace(options)))
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

    private fun tryParseSimpleDeclOrFunctionDef(): StatementNode? {
        val spec = tryParseDeclSpecSeq() ?: return null;
        if (!spec.hasTypeSpec()) return error("Expected type specifier", spec.location);

        val declarators = parseInitDeclaratorList();
        if (declarators.isEmpty()) {
            val el = spec.getElaborated()
            if (el != null) {
                resolveElaborated(el, false);
                return el;
            } else return error("Expected identifier", currentToken())
        }

        if (declarators.size == 1 && declarators.first().isFunctionDeclarator()) {
            return tryParseFunctionDefinition(spec, declarators.first());
        }

        val list = if (spec.isTypedef) {
            parseTypeDefDeclaration(spec, declarators);
        } else {
            buildList {
                for (decl in declarators) {
                    if (decl.isFunctionDeclarator()) {
                        add(parseFunctionDeclaration(spec, decl) ?: continue);
                    } else {
                        add(parseVarDeclaration(spec, decl) ?: continue);
                    }
                }
            }
        }

        return if (list.size == 1) list.first() else DeclarationSequenceNode(spec, list, spec.location).also { parseSeparator() }
    }

    private fun parseVarDeclaration(spec: DeclSpecifierSeq, declarators: Declarator): VariableDeclarationNode? {
        if (declarators.id == null) {
            error("Expected identifier for var declaration", declarators.location)
            return null;
        }
        var decl = sema.resolveSymbolsLocal(declarators.id.name, sema.scope).getOrNull() as? DeclSymbol.VariableDecl
        if (decl == null) {
            decl = DeclSymbol.variable(declarators.id.name, sema.scope.ownerSymbol)
            decl.astNode = declarators.id;
            sema.scope.define(decl).ifFailure(sema::error)
        } else sema.error("Variable ${declarators.id}, already defined in this scope", declarators.id)
        return VariableDeclarationNode(spec, declarators, declarators.id.location).also {
            it.varDecl = decl;
            decl.astNode = it;
        }
    }

    private fun parseFunctionDeclaration(spec: DeclSpecifierSeq, declarators: Declarator): FunctionDeclarationNode? {
        var overloadSet: DeclSymbol.FunctionOverloadSet? = null;
        val classDecl = sema.scope.findCurrentClass();
        val funDeclarator = declarators.chunks.last() as DeclaratorChunk.Function;
        val defaultParamCount = funDeclarator.params.count { it.init != null }
        if (declarators.id == null) {
            error("Expected identifier for function declaration", declarators.location)
            return null;
        }


        val isCtor = declarators.id.name == classDecl?.name;
        val decl = if (isCtor) DeclSymbol.constructorDecl(declarators.id.name, classDecl, funDeclarator.functionQualifiers, defaultParamCount)
        else DeclSymbol.FunctionDecl(declarators.id.name, sema.scope.ownerSymbol, funDeclarator.functionQualifiers, classDecl != null, isBuiltin = false, defaultParamCount)


        decl.astNode = declarators.id;
        overloadSet = sema.scope.define(decl).getAsOrElse { sema.error(it); null }
        val node = if (isCtor) {
            ConstructorDeclarationNode(spec, declarators, declarators.id.location);
        } else {
            FunctionDeclarationNode(spec, declarators, declarators.id.location);
        }


        decl.astNode = node;
        node.functionDecl = decl
        node.overloadSet = overloadSet;
        return node;
    }

    private fun parseTypeDefDeclaration(spec: DeclSpecifierSeq, declarators: List<Declarator>): List<DeclarationNode> {
        validateTypeDef(spec);
        return buildList {
            for (declarator in declarators) {
                if (declarator.isAbstract()) {
                    error("Expected non-abstract declrator in typedef", declarator.location)
                    continue;
                }
                var decl = sema.resolveSymbolsUnqualified(declarator.id!!.name, sema.scope).getOrNull() as? DeclSymbol.TypedefDecl
                if (decl == null) {
                    decl = DeclSymbol.typedef(declarator.id.name, sema.scope.ownerSymbol)
                    sema.scope.define(decl).ifFailure(sema::error)
                } else sema.error("Typedef ${declarator.id.toDisplayString()}, already defined in this scope", declarator.id)
                add(TypeDefStatementNode(spec, declarator, currentToken().location).also {
                    decl.astNode = it;
                })
            }
        }
    }

    private fun tryParseCStyleCast(): TypeCastExpressionNode? {
        val lastPos = position
        val location = currentToken().location
        consume(Symbol.LPAREN)
        val typeId = tryParseTypeId() ?: run {
            position = lastPos;
            return null;
        }
        validateTypeId(typeId);

        consume(Symbol.RPAREN)
        val expression = parseExpression(Precedence.UNARY)
        return TypeCastExpressionNode(CastMethod.CSTYLE, true, typeId, expression, location)
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

        val scope = Scope(sema.scope, null)
        return sema.withScope(scope) {
            val decls = tryParseSimpleDeclOrFunctionDef();
            val init = if (decls != null) {
                listOf(decls);
            } else if (currentToken() isA Symbol.SEPARATOR) {
                parseSeparator()
                listOf()
            } else parseExpressionList()


            val condition = parseExpression()
            parseSeparator()
            val increment = parseExpressionList()
            consume(Symbol.RPAREN)

            val body: StatementNode? = tryParseStmtOrSingleExpression();

            ForStatementNode(init, condition, increment, body, location).also {
                it.scope = scope;
            }
        }
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
        //TODO: Add inplace var decl support

        val scope = Scope(sema.scope, null)
        lateinit var bodyScope: Scope;
        consume(Keyword.WHILE)
        consume(Symbol.LPAREN)
        val condition = parseExpression()
        consume(Symbol.RPAREN)

        val body = sema.withScope(scope) {
            bodyScope = Scope(sema.scope, null)
            sema.withScope(bodyScope) {
                tryParseStmtOrSingleExpression() ?: error("Empty while body", currentToken())
            }
        }
        return WhileStatementNode(condition, body, location).also {
            it.scope = scope;
            it.bodyScope = bodyScope;
        }
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

    //TODO: Add static functions in class
//    baseType: TypeNode, defineInScope: Boolean = true, isTypeDef: Boolean = false
    private fun tryParseNewDeclarator(): Declarator? {
        val loc = currentToken().location
        val (id, chunks) = tryParseDeclaratorChunks(newDeclarator = true) ?: return null;
        return Declarator(id, null, chunks, loc);
    }

    private fun tryParseInitDeclarator(): Declarator? {
        val loc = currentToken().location
        val (id, chunks) = tryParseDeclaratorChunks() ?: return null;
        val init = if (chunks.lastOrNull() is DeclaratorChunk.Function) null else tryParseInitializer();
        return Declarator(id, init, chunks, loc);
    }

    private fun tryParseDeclarator(): Declarator? {
        val loc = currentToken().location
        val (id, chunks) = tryParseDeclaratorChunks() ?: return null;
        return Declarator(id, null, chunks, loc);
    }

//    private fun parseInitializerList(): InitListExpressionNode {
//        val location = currentToken().location
//        val args = parseArguments(true)
//        return InitListExpressionNode(args.arguments, location)
//    }

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

    private fun tryParseDeclaratorChunks(newDeclarator: Boolean = false): Pair<IdentifierNode?, List<DeclaratorChunk>>? {
        val mark = position;
        val outerPrefixes = mutableListOf<DeclaratorChunk>()

        while (true) {
            val ptr = tryParsePtrDeclarator() ?: break
            outerPrefixes.add(ptr)
        }

        var identifier: IdentifierNode? = null
        var innerChunks = mutableListOf<DeclaratorChunk>()

        when {
            currentToken() isA Symbol.LPAREN -> {
                if (newDeclarator) return null;
                val beforeTry = position;
                if (tryParseFunctionDeclarator() != null) {
                    position = beforeTry;
                } else {
                    position = beforeTry;
                    consume(Symbol.LPAREN)

                    val innerResult = tryParseDeclaratorChunks()
                    if (innerResult == null) {
                        position = mark
                        return null
                    }

                    if (!consume(Symbol.RPAREN, false)) {
                        position = mark
                        return null
                    }

                    identifier = innerResult.first
                    innerChunks.addAll(innerResult.second)
                }
            }

            currentToken() is IdToken -> {
                identifier = parseIdentifier()
            }
        }

        val postfixes = mutableListOf<DeclaratorChunk>()
        while (true) {
            when {
                currentToken() isA Symbol.LBRACKET -> {
                    val arrayChunk = parseArrayDeclarator()
                    postfixes.add(arrayChunk)
                }

                currentToken() isA Symbol.LPAREN -> {
                    postfixes += tryParseFunctionDeclarator() ?: break
                }

                else -> break
            }
        }

        val finalChunks = mutableListOf<DeclaratorChunk>()
        finalChunks.addAll(innerChunks)
        finalChunks.addAll(postfixes)
        finalChunks.addAll(outerPrefixes)

        if (identifier == null && finalChunks.isEmpty()) {
            position = mark
            return null
        }

        return identifier to finalChunks;
    }

    private fun parseInitDeclaratorList(): List<Declarator> {
        val declarators = mutableListOf<Declarator>()
        while (true) {
            declarators += tryParseInitDeclarator() ?: break
            if (currentToken() isA Symbol.COMMA) position++
            else if (currentToken() isA Symbol.SEPARATOR || currentToken() isA Symbol.BEGIN) {
                break
            }
        }
        return declarators;
    }


    private fun parseExpressionList(): List<ExpressionNode> {
        val exprs = mutableListOf<ExpressionNode>()
//        while (currentToken() notA Symbol.SEPARATOR && currentToken() notA Symbol.RPAREN && currentToken() notA Symbol.END) {
        do {
            exprs += parseExpression();
        } while (consume(Symbol.COMMA, false))
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
        val index: ExpressionNode = parseExpression()
        consume(Symbol.RBRACKET)
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

    private fun parseNewExpression(): ExpressionNode {
        val location = currentToken().location
        consume(Keyword.NEW)

        val placementArgs = if (consume(Symbol.LPAREN, false)) {
            position++
            val list = parseExpressionList()
            consume(Symbol.RPAREN)
            list
        } else null


        val type = tryParseNewTypeId() ?: return errorExpr("Expected type id in new expression");
        type.declSpecifier.getElaborated()?.let {
            resolveElaborated(it, false);
        }
        val initializerList = tryParseNewInitializer();
        return NewExpressionNode(placementArgs, type, initializerList, location)
    }

    private fun parseSizeOfExpression(): SizeofExpressionNode {
        val location = currentToken().location
        if (currentToken() notA Keyword.SIZEOF) errorExpr("Invalid sizeof expression provided, expected sizeof keyword")
        position++
        if (currentToken() notA Symbol.LPAREN) error("Invalid sizeof expression provided, expected '(' for expression")
        position++

        val typeId = tryParseTypeId()?.also { validateTypeId(it) }
        val expression = if (typeId != null) null else parseExpression()
        if (expression is EmptyExpressionNode) {
            errorExpr("Invalid sizeof expression provided, expected type or expression")
        }
        if (currentToken() notA Symbol.RPAREN) error("Invalid sizeof expression provided, expected ')' for expression")
        position++
        return SizeofExpressionNode(typeId, expression, location)
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
        return if (operator oneOf listOf(Operator.DOT, Operator.ARROW, Operator.DOT_STAR, Operator.ARROW_STAR)) {
            MemberAccessExpressionNode(left, right, operator.value, computeLocationSpan(left, right))
        } else BinaryExpressionNode(left, right, operator.value, computeLocationSpan(left, right))
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

//    private fun isTypeToken(): Boolean {
//        val basePos = position;
//        val baseProblems = problems.mapValues { it.value.size }
//        var isType = false;
//        try {
//            parseDeclSpecifierSeq()
//            isType = problems.all { (level, list) -> list.size == baseProblems[level] }
//        } catch (e: Exception) {
//            isType = false
//        } finally {
//            // Rollback problems added during speculative parsing
//            problems.forEach { (level, list) ->
//                val prevSize = baseProblems[level] ?: 0
//                while (list.size > prevSize) {
//                    list.removeAt(list.size - 1)
//                }
//            }
//        }
//        position = basePos;
//        return isType
//    }

//    private fun tryParseType(): TypeNode? {
//        val basePos = position;
//        val baseProblems = problems.mapValues { it.value.size }
//        try {
//            val type = parseDeclSpecifierSeq()
//            if (problems.all { (level, list) -> list.size == baseProblems[level] }) {
//                return type
//            }
//        } catch (e: Exception) {
//        }
//        // Rollback problems
//        problems.forEach { (level, list) ->
//            val prevSize = baseProblems[level] ?: 0
//            while (list.size > prevSize) {
//                list.removeAt(list.size - 1)
//            }
//        }
//        position = basePos;
//        return null;
//    }

    private fun tryParseStorageClassSpec(): StorageClassSpecifier? {
        val tok = currentToken()
        return when {
            tok isA Keyword.EXTERN -> StorageClassSpecifier.Extern(tok.location)
            tok isA Keyword.STATIC -> StorageClassSpecifier.Static(tok.location)
            tok isA Keyword.THREAD_LOCAL -> StorageClassSpecifier.ThreadLocal(tok.location)
            tok isA Keyword.MUTABLE -> StorageClassSpecifier.Mutable(tok.location)
            else -> null
        }
    }

    private fun tryParseFunctionSpec(): FunctionSpecifier? {
        val tok = currentToken()
        return when {
            tok isA Keyword.VIRTUAL -> FunctionSpecifier.Virtual(tok.location)
            else -> tryParseExplicitSpec();
        }
    }

    private fun tryParseExplicitSpec(): FunctionSpecifier.Explicit? {
        val loc = currentToken().location
        if (currentToken() isA Keyword.EXPLICIT) {
            val expr = if (currentToken() isA Symbol.LPAREN) {
                parseExpression();
            } else null;
            return FunctionSpecifier.Explicit(expr, loc);
        } else return null;
    }

    private fun tryParseElaboratedTypeSpec(): ClassDeclarationNode? {
        return if (currentToken() oneOf getClassKeys()) {
            val node = parseClass()
            if (node is ClassDeclarationNode)
                node
            else {
                error("Expected elaborated specifier, not a class definition", currentToken())
                null
            }
        } else null;
    }

    private fun tryParseSimpleTypeSpec(hasId: Boolean): SimpleTypeSpecifier? {
        val tok = currentToken()
        return if (tok is KeywordToken) {
            when (tok.value) {
                Keyword.CHAR, Keyword.CHAR8_T, Keyword.CHAR16_T, Keyword.CHAR32_T,
                Keyword.WCHAR_T, Keyword.BOOL, Keyword.SHORT, Keyword.INT,
                Keyword.LONG, Keyword.SIGNED, Keyword.UNSIGNED, Keyword.FLOAT,
                Keyword.DOUBLE, Keyword.VOID -> {
                    consume(tok.value);
                    SimpleTypeSpecifier.Keyword(tok.value, tok.location)
                }

                else -> tryParseDecltypeOrPlaceholderSpec()
            }
        } else {
            tryParseIdTypeSpec(hasId)
        }
    }

    private fun tryParseIdTypeSpec(hasId: Boolean): SimpleTypeSpecifier.Id? {
        val mark = position;
        if (currentToken() !is IdToken || hasId) return null;
        val id = parseIdentifier();
        val decls = sema.resolveSymbols(id, sema.scope, false).getOrNull();
        if (decls !is DeclSymbol.ClassDecl && decls !is DeclSymbol.TypedefDecl) {
            position = mark
            return null;
        }
        return SimpleTypeSpecifier.Id(id, id.location!!);
    }

    private fun tryParseTypenameSpec(): TypenameSpecifier? {
        val loc = currentToken().location;
        return if (consume(Keyword.TYPENAME, false)) {
            TypenameSpecifier(parseIdentifier(), loc);
        } else null;
    }

    private fun tryParseTypeSpec(hasId: Boolean): TypeSpecifier? {
        return tryParseSimpleTypeSpec(hasId) ?: tryParseElaboratedTypeSpec() ?: tryParseCVQual() ?: tryParseTypenameSpec();
    }

    private fun tryParseDefTypeSpec(hasId: Boolean): TypeSpecifier? {
        if (currentToken() oneOf getClassKeys()) {
            return parseClass() as TypeSpecifier;
        }
        return tryParseTypeSpec(hasId);
    }

    private fun parseCvQualSeq(): List<CVQualifier> {
        return buildList {
            while (true) {
                add(tryParseCVQual() ?: break);
            }
        }
    }

    private fun tryParseCVQual(): CVQualifier? {
        val loc = currentToken().location
        return when {
            consume(Keyword.CONST, false) -> CVQualifier.Const(loc);
            consume(Keyword.VOLATILE, false) -> CVQualifier.Volatile(loc);
            //TODO: Add support for restrict expansion cv
            else -> null;
        }
    }

    private fun tryParseDecltypeOrPlaceholderSpec(): SimpleTypeSpecifier? {
        val loc = currentToken().location
        return if (consume(Keyword.DECLTYPE, false)) {
            if (tokens[position + 2] isA Keyword.AUTO) {
                consume(Symbol.LPAREN)
                consume(Keyword.AUTO)
                consume(Symbol.RPAREN)
                SimpleTypeSpecifier.Placeholder(true, loc);
            } else {
                SimpleTypeSpecifier.Decltype(parseExpression(), loc);
            }
        } else if (consume(Keyword.AUTO, false)) {
            SimpleTypeSpecifier.Placeholder(false, loc);
        } else null
    }

    private fun tryParseDeclSpec(hasId: Boolean): DeclSpecifier? {
        val loc = currentToken().location
        return tryParseDefTypeSpec(hasId) ?: tryParseStorageClassSpec() ?: tryParseFunctionSpec() ?: when {
            consume(Keyword.FRIEND, false) -> DeclSpecifier.Friend(loc)
            consume(Keyword.TYPEDEF, false) -> DeclSpecifier.Typedef(loc)
            consume(Keyword.CONSTEXPR, false) -> ConstExprSpecifier.Constexpr(loc)
            consume(Keyword.CONSTEVAL, false) -> ConstExprSpecifier.Consteval(loc)
            consume(Keyword.CONSTINIT, false) -> ConstExprSpecifier.Constinit(loc)
            consume(Keyword.INLINE, false) -> DeclSpecifier.Inline(loc)
            else -> null;
        }
    }

    private fun tryParseDeclSpecSeq(base: DeclSpecifierSeq? = null): DeclSpecifierSeq? {
        //TODO: add attribute support
        val loc = currentToken().location
        val spec = tryParseDeclSpec(base?.hasDeclaredId ?: false) ?: return base;

        var base = base ?: DeclSpecifierSeq(location = loc);
        fun checkDuplicate(value: Boolean) {
            if (value) {
                sema.error(ErrorHelper.duplicateSpec(spec, loc))
            }
        }

        base = when (spec) {
            is DeclSpecifier.Friend -> {
                checkDuplicate(base.isFriend)
                base.copy(friendSpec = spec);
            }

            is DeclSpecifier.Typedef -> {
                checkDuplicate(base.isTypedef)
                base.copy(typedefSpec = spec);
            }

            is ConstExprSpecifier -> {
                checkDuplicate(base.constexprSpec != null)
                base.copy(constexprSpec = spec);
            }

            is DeclSpecifier.Inline -> {
                checkDuplicate(base.isInline)
                base.copy(inlineSpec = spec);
            }

            is StorageClassSpecifier -> {
                val s = if (base.storageClassSpec != null) {
                    base.storageClassSpec + spec
                } else listOf(spec);
                if (s.size > 1) {
                    val isThreadLocal = s.any { it is StorageClassSpecifier.ThreadLocal };
                    if (!isThreadLocal || s.none { it is StorageClassSpecifier.Extern || it is StorageClassSpecifier.Static }) {
                        sema.error(ErrorHelper.duplicateSpec(spec, loc))
                    }
                }
                base.copy(storageClassSpec = s);
            }

            is FunctionSpecifier -> {
                checkDuplicate(base.funcSpec != null)
                base.copy(funcSpec = spec);
            }

            is TypeSpecifier -> {
                val s = if (base.typeSpecs != null) {
                    base.typeSpecs + spec
                } else listOf(spec);
                base.copy(typeSpecs = s)
            }
        }

        return tryParseDeclSpecSeq(base);
    }

//    private fun parseDeclSpecifierSeq(): DeclSpecifierSeq {
//        val location = currentToken().location
//        var isConst = false
//        var isVolatile = false
//        var isSigned: Boolean? = null
//        var isShort = false
//        var longCount = 0
//        var typeId: Any? = null
//
//        fun assertFirstId() {
//            if (typeId != null) {
//                error("Cannot use multiple ids in type")
//            }
//        }
//
//        fun hasPrimitive(): Boolean {
//            return isSigned != null || isShort || longCount != 0
//        }
//
//        while (true) {
//            val token = currentToken()
//            var advance = true;
//            when {
//                token isA Keyword.AUTO || token isA Keyword.DECLTYPE -> typeId = assertFirstId().let { token }
//                typeId == null && !hasPrimitive() && (token is IdToken || token isA Operator.NAMESPACE) -> {
//                    val id = parseIdentifier();
//                    advance = false;
//
//                    val decls = sema.resolveSymbols(id, sema.scope, false).getOrNull();
//                    if (decls is DeclSymbol.ClassDecl || decls is DeclSymbol.TypedefDecl) {
//                        typeId = assertFirstId().let { id }
//                    } else throw IllegalStateException("Unknown type provided: $id")
//                }
//
//                token isA Keyword.UNSIGNED -> {
//                    if (isSigned != null) error("Cannot use multiple sign qualifiers")
//                    isSigned = false;
//                }
//
//                token isA Keyword.SIGNED -> {
//                    if (isSigned != null) error("Cannot use multiple sign qualifiers")
//                    isSigned = true;
//                }
//
//                token isA Keyword.SHORT -> isShort = true
//                token isA Keyword.LONG -> longCount++
//                token isA Keyword.INT -> typeId = assertFirstId().let { PrimitiveTypeKind.INT }
//                token isA Keyword.CHAR -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR }
//                token isA Keyword.CHAR8_T -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR8_T }
//                token isA Keyword.CHAR16_T -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR16_T }
//                token isA Keyword.CHAR32_T -> typeId = assertFirstId().let { PrimitiveTypeKind.CHAR32_T }
//                token isA Keyword.WCHAR_T -> typeId = assertFirstId().let { PrimitiveTypeKind.WCHAR_T }
//                token isA Keyword.FLOAT -> typeId = assertFirstId().let { PrimitiveTypeKind.FLOAT }
//                token isA Keyword.DOUBLE -> typeId = assertFirstId().let { PrimitiveTypeKind.DOUBLE }
//                token isA Keyword.BOOL -> typeId = assertFirstId().let { PrimitiveTypeKind.BOOL }
//                token isA Keyword.VOID -> typeId = assertFirstId().let { PrimitiveTypeKind.VOID }
//                token isA Keyword.CONST -> isConst = true
//                token isA Keyword.VOLATILE -> isVolatile = true
//                else -> break
//            }
//
//            if (advance) {
//                position++
//            }
//        }
//
//
//        //Type resolving
//        fun assertNoPrimitives() {
//            if (hasPrimitive()) {
//                error("Cannot use primitive type qualifiers with given type")
//            }
//        }
//
//        if (typeId == null && hasPrimitive()) {
//            typeId = PrimitiveTypeKind.INT;
//        }
//
//        return when (typeId) {
//            is KeywordToken if typeId isA Keyword.AUTO -> AutoTypeNode(
//                isConst,
//                isVolatile,
//                location
//            ).also { assertNoPrimitives() }
//
////            is KeywordToken if typeId isA Keyword.DECLTYPE -> DeclTypeTypeNode(
////                isConst,
////                isVolatile,
////                location
////            ).also { assertNoPrimitives() }
//            is KeywordToken if typeId isA Keyword.DECLTYPE -> TODO("Decltype is not supported yet")
//
//
//            is IdentifierNode -> {
//
//                DeclaredTypeNode(typeId, isConst, isVolatile, location).also { assertNoPrimitives() }
//            }
//
//            is PrimitiveTypeKind -> {
//                checkPrimitiveCombinations(typeId, isSigned, isShort, longCount)
//                val kind = resolvePrimitiveKind(typeId, isSigned, isShort, longCount == 1, longCount == 2)
//                        ?: run {
//                            error("Invalid type provided: $typeId")
//                            PrimitiveTypeKind.INT
//                        }
//                PrimitiveTypeNode(kind, isConst, isVolatile, location)
//            }
//
//            else -> {
//                throw IllegalStateException("Invalid type provided: $typeId")
////                PrimitiveTypeNode(PrimitiveTypeKind.INT, isConst, isVolatile, location)
//            }
//        }
//    }

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

    private fun tryParsePtrDeclarator(): DeclaratorChunk? {
        val location = currentToken().location
        val kind = when {
            consume(Operator.POINTER, false) -> DeclaratorChunk.Ptr.Kind.Pointer
            consume(Operator.AND, false) -> DeclaratorChunk.Ptr.Kind.RRef
            consume(Operator.AMP, false) -> DeclaratorChunk.Ptr.Kind.LRef
            else -> return tryParseMemberPtrDeclarator();
        }

        val cvSeq = parseCvQualSeq();
        return DeclaratorChunk.Ptr(kind, cvSeq, location);
    }

    private fun tryParseMemberPtrDeclarator(): DeclaratorChunk.MemberPointer? {
        val lastPos = position
        val location = currentToken().location
        val namespace = mutableListOf<IdentifierNode>()

        val isGlobal = if (currentToken() isA Operator.NAMESPACE) {
            position++
            true
        } else false

        var isPointer = false
        var cvSeq: List<CVQualifier>? = null;
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

                cvSeq = parseCvQualSeq();

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
            return DeclaratorChunk.MemberPointer(classIdNode, cvSeq ?: emptyList(), location)
        } else {
            position = lastPos
            return null
        }
    }

    private fun parseFunctionQualifiers(): FunctionQualifiers {
        val cv = parseCvQualSeq();
        val refQualifier = tryParseRefQual();
        val noexceptSpec = tryParseNoExceptSpec();

        val token = currentToken()
        if (token isA Keyword.CONST || token isA Keyword.VOLATILE) {
            error("cv-qualifiers must appear BEFORE ref-qualifiers and 'noexcept'", token)
        }
        if (token isA Operator.AMP || token isA Operator.AND) {
            error("ref-qualifiers must appear BEFORE 'noexcept'", token)
        }

        return FunctionQualifiers(cv as List<ICVQualifier>, refQualifier, noexceptSpec)
    }

    private fun tryParseRefQual(): IRefQualifier? {
        val token = currentToken()
        return when {
            token isA Operator.AMP -> RefQualifier.LValue(token.location)
            token isA Operator.AND -> RefQualifier.RValue(token.location)
            else -> null
        }
    }

    private fun tryParseNoExceptSpec(): INoExceptSpecifier? {
        val loc = currentToken().location
        if (consume(Keyword.NOEXCEPT, false)) return null;
        val expr = if (consume(Symbol.LPAREN, false)) {
            parseExpression().also { consume(Symbol.LPAREN) }
        } else null;
        return NoexceptSpecifier(expr, loc);
    }

    private fun tryParseFunctionDeclarator(): DeclaratorChunk.Function? {
        val location = currentToken().location
        val params = tryParseParameters() ?: return null;
        val qualifiers = parseFunctionQualifiers();
        val trailing = if (consume(Operator.ARROW, false)) tryParseTypeId() else null;
        return DeclaratorChunk.Function(params, qualifiers, trailing, location);
    }

    private fun parseArrayDeclarator(): DeclaratorChunk.Array {
        val location = currentToken().location
        consume(Symbol.LBRACKET)
        val size: ExpressionNode? = if (currentToken() isA Symbol.RBRACKET) null else parseExpression()
        consume(Symbol.RBRACKET)
        return DeclaratorChunk.Array(size, location)
    }

    private fun tryParseNewTypeId(): TypeId? {
        return TypeId(tryParseDeclSpecSeq() ?: return null, tryParseNewDeclarator())
    }

    private fun tryParseTypeId(): TypeId? {
        val id = TypeId(tryParseDeclSpecSeq() ?: return null, tryParseDeclarator())
        id.declSpecifier.getElaborated()?.let {
            resolveElaborated(it, false);
        }
        return id;
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

    private fun tryParseFunctionDefinition(
        declSpec: DeclSpecifierSeq,
        declarator: Declarator
    ): StatementNode? {
        val location = currentToken().location

        val declaration = parseFunctionDeclaration(declSpec, declarator) ?: return null
        val isCtor = declaration is ConstructorDeclarationNode;
        val funDeclarator = declarator.chunks.last() as DeclaratorChunk.Function;

        val decl = declaration.functionDecl;
        val overloadSet = declaration.overloadSet;
        decl.scope = Scope(sema.scope, decl)

        val memberInitializer = if (isCtor && currentToken() isA Symbol.COLON) parseMemberInitializerList() else emptyList()

        val body = sema.withScope(decl.scope) {
            for (param in funDeclarator.params) {
                val name = param.declarator?.id?.name ?: continue
                val paramDeclarator = param.declarator;
                val paramDecl: DeclSymbol;
                when {
                    paramDeclarator.isFunctionDeclarator() -> {
                        val decl = paramDeclarator.getFunctionDeclarator()!!;
                        paramDecl = DeclSymbol.functionDecl(name, sema.scope.ownerSymbol, decl.functionQualifiers, decl.defaultParamCount)
                        param.decl = paramDecl;
                    }

                    else -> {
                        paramDecl = DeclSymbol.variable(name, sema.scope.ownerSymbol)
                        param.decl = paramDecl;
                    }
                }
                paramDecl.astNode = param;

                sema.scope.define(paramDecl).ifFailure(sema::error)
            }
            FunctionBodyNode(parseBlock().statements, location);
        }
        val node = if (isCtor) {
            ConstructorDefinitionNode(declaration, memberInitializer, body, location);
        } else {
            FunctionDefinitionNode(declaration, body, location);
        }

        parseSeparator(false);
        decl.astNode = node
        decl.definitionNode = node
        node.functionDecl = decl
        node.overloadSet = overloadSet
        return node;
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
            while (consume(Symbol.SEPARATOR, false));
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
            else {
                parseSeparator(true)
                CompoundStatementNode(listOf(expr), location)
            }
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
            token isA Keyword.ASM -> parseAsmStatement();
            else -> tryParseDeclarationOrDefinition();
        }
    }

    private fun parseAsmStatement(): StatementNode {

        consume(Keyword.ASM);
        val isVolatile = consume(Keyword.VOLATILE, false);

        consume(Symbol.LPAREN)
        val asmString = parseValueExpression();
        if (asmString !is StringLiteralNode && asmString !is StringConcatExpressionNode) {
            return error("Expected string literal constant in asm statement", currentToken())
        }

        val outList = if (consume(Symbol.COLON, false)) {
            if (currentToken() isA Symbol.COLON) {
                emptyList();
            } else parseAsmOperandList(false) ?: return RecoveryStatementNode(currentToken().location);
        } else emptyList();

        val inList = if (consume(Symbol.COLON, false)) {
            if (currentToken() isA Symbol.COLON) {
                emptyList();
            } else parseAsmOperandList(true) ?: return RecoveryStatementNode(currentToken().location);
        } else emptyList();
        val constraintsList = if (consume(Symbol.COLON, false)) {
            if (currentToken() isA Symbol.COLON || currentToken() isA Symbol.RPAREN) {
                emptyList();
            } else parseAsmClobberList() ?: return RecoveryStatementNode(currentToken().location);
        } else emptyList();
        consume(Symbol.RPAREN);
        return AsmStatementNode(asmString, outList, inList, constraintsList, isVolatile, currentToken().location);
    }

    fun parseAsmOperandList(isInput: Boolean): List<AsmOperandNode>? {
        val exprs = mutableListOf<AsmOperandNode>()
        do {
            val constraint = parseValueExpression();
            if (constraint !is StringLiteralNode && constraint !is StringConcatExpressionNode) {
                errorExpr("Expected string constant in asm operand constraint")
                return null;
            }
            val expr = parseExpression();
            exprs += AsmOperandNode(constraint, expr, isInput, currentToken().location);
        } while (consume(Symbol.COMMA, false))
        return exprs;
    }

    fun parseAsmClobberList(): List<ExpressionNode>? {
        val exprs = mutableListOf<ExpressionNode>()
        do {
            val clobber = parseValueExpression();
            if (clobber !is StringLiteralNode && clobber !is StringConcatExpressionNode) {
                errorExpr("Expected string constant in asm clobber")
                return null;
            }
            exprs += clobber;
        } while (consume(Symbol.COMMA, false))
        return exprs;
    }

    private fun tryParseDeclarationOrDefinition(): StatementNode {
        val token = currentToken()
        return tryParseSimpleDeclOrFunctionDef() ?: run {
            when {
                token isA Keyword.NAMESPACE -> parseNamespaceDefinition(token.location)
                else -> EmptyStatementNode
            }
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
        while (currentToken() notA Symbol.END && currentToken() !is EofToken) {
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
//
//                var isCtor = false;
//                if (currentToken() is IdToken) {
//                    val constructor = if (classId != null) tryParseConstructor(classId) else null
//                    if (constructor != null) {
//                        nodes += constructor;
//                        isCtor = true;
//                    }
//                }

//                if (!isCtor) {
                val statement = tryParseStatement();
                if (statement != EmptyStatementNode) {
                    nodes += statement
                    parseSeparator(false)
                } else break
//                }
            }
        }
        consume(Symbol.END)
        return ClassBodyNode(nodes, location)
    }

    private fun tryParseParameters(): List<ParameterNode>? {
        if (!consume(Symbol.LPAREN, false)) return null;

        val startPos = position;
        val list = mutableListOf<ParameterNode>()

        if (consume(Symbol.RPAREN, false)) return emptyList();

        while (true) {
            list += tryParseParameter() ?: break;
            consume(Symbol.COMMA, false);
        }

        if (list.isEmpty()) {
            position = startPos;
            return null;
        }
        consume(Symbol.RPAREN)

        return list;
    }

    private fun tryParseParameter(): ParameterNode? {
        val location = currentToken().location
        val isExplicitObject = consume(Keyword.THIS, false)
        val declSpec = tryParseDeclSpecSeq() ?: return null
        declSpec.getElaborated()?.let {
            resolveElaborated(it, false);
        }
        val declarator = tryParseDeclarator();
        val init = tryParseInitializerClause();
        return ParameterNode(isExplicitObject, declSpec, declarator, init, location)
    }

    private fun tryParseNewInitializer(): ExpressionNode? {
        return tryParseBracedInitList() ?: tryParseInitList();
    }

    private fun tryParseInitializer(): ExpressionNode? {
        return tryParseInitializerClause() ?: tryParseInitList();
    }

    private fun tryParseInitializerClause(): ExpressionNode? {
        return tryParseBracedInitList() ?: run {
            if (!consume(Operator.ASSIGN, false)) null
            else tryParseBracedInitList() ?: run {
                val expr = parseExpression();
                if (expr is EmptyExpressionNode) null else expr;
            }
        }
    }

    private fun tryParseInitList(): InitListExpressionNode? {
        val loc = currentToken().location;
        if (!consume(Symbol.LPAREN, false)) return null;
        val exprs = parseExpressionList();
        consume(Symbol.RPAREN);
        return InitListExpressionNode(exprs, false, loc);
    }

    private fun tryParseBracedInitList(): InitListExpressionNode? {
        val loc = currentToken().location;
        if (!consume(Symbol.BEGIN, false)) return null;
        val exprs = parseExpressionList();
        consume(Symbol.END);
        return InitListExpressionNode(exprs, true, loc);
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
        var res: OpResult<DeclSymbol.NamespaceDecl>
        if (identifier == null) {
            res = findOrCreateNamespaceDef(sema.getAnonNamespaceName(), sema, true, null, processedOnly = false);
            if (res.isSuccess()) {
                sema.scope.addUsingDirective(res.value.scope)
            }
        } else {
            if (identifier is QualifiedIdentifierNode) {
                for (qual in identifier.qualifiers) {
                    val res = findOrCreateNamespaceDef(qual.name, sema, false, qual, processedOnly = false)
                    if (res.isSuccess()) {
                        sema.enterScope(res.value.scope);
                        leaveCount++;
                    }
                }
            }

            res = findOrCreateNamespaceDef(identifier.name, sema, false, identifier, processedOnly = false)
        }


        if (res.isSuccess()) {
            sema.enterScope(res.value.scope)
            leaveCount++
        }


        val body = NamespaceBodyNode(parseBlock().statements, location);
        repeat(leaveCount) { sema.leaveScope() }
        return NamespaceDeclarationNode(identifier, body, location).also {
            parseSeparator(false)
            if (res.isSuccess()) {
                res.value.declarations += it
                it.nsDecl = res.value;
                it.nsDecl.astNode = it;
            }
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

        val outNode: StatementNode;
        if (currentToken() isA Symbol.BEGIN) {
            val decl = declareClass(identifier, classType);
            val body = sema.withScope(decl.scope) {
                parseClassBody(identifier)
            }
            outNode = ClassDefinitionNode(identifier, classType, body, location).also {
                if (decl.definitionNode != null) {
                    sema.error("Class ${decl.name} has already been defined or declared", it)
                } else {
                    decl.astNode = it
                    decl.definitionNode = it
                }
                it.classDecl = decl
            }
            parseSeparator(true);
        } else {
            outNode = ClassDeclarationNode(identifier, classType, location)
        }
        return outNode;
    }

    private fun declareClass(identifier: IdentifierNode?, classType: ClassType): DeclSymbol.ClassDecl {
        val decl: DeclSymbol.ClassDecl;
        if (identifier != null) {
            val found = sema.resolveSymbolsLocal(identifier, sema.scope, false).getOrNull() as? DeclSymbol.ClassDecl;
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
        return decl;
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

    private fun getClassKeys(): List<Keyword> = listOf(Keyword.CLASS, Keyword.STRUCT, Keyword.UNION);
    fun validateFunc(spec: DeclSpecifierSeq) {
        if (spec.isTypedef) {
            error("Function declaration cannot be a 'typedef'", spec.location)
        }
        if (spec.constexprSpec is ConstExprSpecifier.Constinit) {
            error("'constinit' specifier is not allowed on function declarations", spec.constexprSpec.location)
        }
        spec.storageClassSpec?.let { storage ->
            storage.forEach { specifier ->
                if (specifier is StorageClassSpecifier.Mutable || specifier is StorageClassSpecifier.ThreadLocal) {
                    error("Storage class '${specifier}' is not allowed on functions", specifier.location)
                }
            }
        }
        spec.funcSpec?.let { funcSpec ->
            // virtual и explicit разрешены ТОЛЬКО внутри классов/структур
//            if (!spec.isMemberFunction) {
//                if (funcSpec.isVirtual) {
//                    error("'virtual' specifier is only allowed on member function declarations", funcSpec.location)
//                }
//                if (funcSpec.isExplicit) {
//                    error("'explicit' specifier is only allowed on constructors or conversion operators inside a class", funcSpec.location)
//                }
//            }

//            if (funcSpec is FunctionSpecifier.Virtual && spec.) {
//                error("A member function cannot be both 'virtual' and 'static'", funcSpec.location)
//            }

        }
        //TODO: vaildate funcion decl
    }

    fun validateTypeDef(spec: DeclSpecifierSeq) {
        spec.funcSpec?.let { funcSpec ->
            error("Function specifiers ('inline', 'virtual', 'explicit') are not allowed in typedef", funcSpec.location)
        }

        spec.storageClassSpec?.let { storage ->
            error("Cannot combine 'typedef' with storage class specifiers", storage.first().location);
        }

        if (spec.constexprSpec != null) {
            error("Constexpr specifiers ('constexpr', 'consteval', 'constinit') are not allowed in typedef", spec.constexprSpec.location)
        }

        if (spec.friendSpec != null) {
            error("'friend' specifier is not allowed in typedef declaration", spec.friendSpec.location)
        }
    }

    fun validateNewTypeId(typeId: TypeId) {
        if (typeId.declarator != null && !typeId.declarator.isAbstract()) {
            sema.error("Expected abstract declarator", typeId.declarator.id!!)
        }
        if (!typeId.declSpecifier.hasTypeSpec()) {
            sema.error("Expected type specifier", location = typeId.declSpecifier.location)
        }

        if (typeId.declSpecifier.isDefining()) {
            sema.error("Type definition is not allowed in current context", location = typeId.declSpecifier.location)
        }
    }

    fun validateTypeId(typeId: TypeId) {
        if (typeId.declarator != null && !typeId.declarator.isAbstract()) {
            sema.error("Expected abstract declarator", typeId.declarator.id!!)
        }
        if (!typeId.declSpecifier.hasTypeSpec()) {
            sema.error("Expected type specifier", location = typeId.declSpecifier.location)
        }
        if (typeId.declSpecifier.isDefining()) {
            sema.error("Type definition is not allowed in current context", location = typeId.declSpecifier.location)
        }
    }

    fun resolveElaborated(spec: ClassDeclarationNode, hasDeclarator: Boolean) {
        if (spec.name == null) {
            error("Expected class name", spec.location)
            return;
        }
        if (!hasDeclarator) {
            declareClass(spec.name, spec.type).also {
                it.astNode = spec;
                spec.classDecl = it;
            }
            return;
        }

        val classDecl: DeclSymbol.ClassDecl? = sema.resolveSymbols(spec.name, sema.scope, false, tagOnly = true).getAsOrElse { null }
        if (classDecl == null) {
            declareClass(spec.name, spec.type).also {
                it.astNode = spec;
                spec.classDecl = it;
            }
        } else {
            spec.classDecl = classDecl;
            classDecl;
        }
    }
}