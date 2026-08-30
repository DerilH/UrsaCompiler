package org.derilh.semantic.analyzer

import org.derilh.analyzer.AbstractDeclaratorAnalyzer
import org.derilh.analyzer.AccessSpecifierAnalyzer
import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.BinaryExprAnalyzer
import org.derilh.analyzer.BoolLiteralAnalyzer
import org.derilh.analyzer.CharLiteralAnalyzer
import org.derilh.analyzer.ClassBodyAnalyzer
import org.derilh.analyzer.ClassDeclAnalyzer
import org.derilh.analyzer.ClassDefAnalyzer
import org.derilh.analyzer.CompoundStatementAnalyzer
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.DeclarationSeqAnalyzer
import org.derilh.analyzer.FloatLiteralAnalyzer
import org.derilh.analyzer.GlobalScope
import org.derilh.analyzer.IfStatementAnalyzer
import org.derilh.analyzer.IntLiteralAnalyzer
import org.derilh.analyzer.MemberAccessExprAnalyzer
import org.derilh.analyzer.NamespaceDeclAnalyzer
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.analyzer.NullptrLiteralAnalyzer
import org.derilh.analyzer.ParameterNodeAnalyzer
import org.derilh.analyzer.ReturnStmtAnalyzer
import org.derilh.analyzer.Scope
import org.derilh.analyzer.StringConcatAnalyzer
import org.derilh.analyzer.StringLiteralAnalyzer
import org.derilh.analyzer.TypeCastExprAnalyzer
import org.derilh.analyzer.UnaryExprAnalyzer
import org.derilh.analyzer.VarDeclaratorAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.AccessSpecifierNode
import org.derilh.ast.ArgumentsNode
import org.derilh.ast.ArrayTypeNode
import org.derilh.ast.AutoTypeNode
import org.derilh.ast.BinaryExpressionNode
import org.derilh.ast.BooleanLiteralNode
import org.derilh.ast.CallExpressionNode
import org.derilh.ast.CharLiteralNode
import org.derilh.ast.ClassBodyNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.ClassDefinitionNode
import org.derilh.ast.CompoundStatementNode
import org.derilh.ast.ConstructorDeclarationNode
import org.derilh.ast.ConstructorDefinitionNode
import org.derilh.ast.DeclaredTypeNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.FloatLiteralNode
import org.derilh.ast.FunctionBodyNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.FunctionTypeNode
import org.derilh.ast.IdExpressionNode
import org.derilh.ast.IdentifierNode
import org.derilh.ast.ImplicitCastExpressionNode
import org.derilh.ast.IntLiteralNode
import org.derilh.ast.MemberPointerTypeNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.ast.NullptrLiteralNode
import org.derilh.ast.PointerTypeNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.ast.RValueReferenceTypeNode
import org.derilh.ast.ReferenceTypeNode
import org.derilh.ast.RootNode
import org.derilh.ast.StringConcatExpressionNode
import org.derilh.ast.StringLiteralNode
import org.derilh.ast.TypeCastExpressionNode
import org.derilh.ast.TypeNode
import org.derilh.ast.UnaryExpressionNode
import org.derilh.ast.DeclarationSequenceNode
import org.derilh.ast.FunctionDeclaratorNode
import org.derilh.ast.IfStatementNode
import org.derilh.ast.MemberAccessExpressionNode
import org.derilh.ast.ParameterNode
import org.derilh.ast.RecoveryExpressionNode
import org.derilh.ast.RecoveryStatementNode
import org.derilh.ast.ErrorTypeNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.ast.VariableDeclaratorNode
import org.derilh.core.ConversionKind
import org.derilh.core.FunctionQualifiers
import org.derilh.core.OpResult
import org.derilh.core.Operator
import org.derilh.core.Options
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.SourceLocation
import org.derilh.core.ValueCategory
import org.derilh.core.getAsOrNull
import org.derilh.core.getOrElse
import org.derilh.core.getOrNull
import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SemanticProblem
import org.derilh.semantic.AnalyzeResult
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.TypeContext
import org.derilh.semantic.isFunctionPointer
import org.derilh.target.TargetInfo
import org.derilh.target.X86_64LinuxTargetInfo
import org.derilh.util.ErrorHelper
import org.derilh.util.ErrorHelper.Companion.getStackTrace
import java.math.BigInteger
import kotlin.collections.mapNotNullTo
import kotlin.collections.plusAssign

class SemanticAnalyzer(val options: Options) : AnalyzeContext {
    override var target: TargetInfo;
    override var anonymousIdCounter: Int = 0;
    override val scope: Scope get() = innerScope ?: innerRootScope ?: throw IllegalStateException("Not in any scope")
    override val rootScope: Scope get() = innerRootScope ?: throw IllegalStateException("Not in any scope")
    override var types: TypeContext;
    override var idContext: IdContext = IdContext.NONE;
    private var innerRootScope: Scope? = null;
    private var innerScope: Scope? = null
    private val problems: Map<ProblemLevel, MutableList<SemanticProblem>> = buildMap {
        for (level in ProblemLevel.entries) {
            this[level] = mutableListOf()
        }
    }

    private val analyzers = hashMapOf(
        NullptrLiteralNode::class to NullptrLiteralAnalyzer(),
        FloatLiteralNode::class to FloatLiteralAnalyzer(),
        IntLiteralNode::class to IntLiteralAnalyzer(),
        BooleanLiteralNode::class to BoolLiteralAnalyzer(),
        CharLiteralNode::class to CharLiteralAnalyzer(),
        StringLiteralNode::class to StringLiteralAnalyzer(),
        StringConcatExpressionNode::class to StringConcatAnalyzer(),
        FunctionDefinitionNode::class to FunctionDefAnalyzer(),
        FunctionBodyNode::class to CompoundStatementAnalyzer(),
        CompoundStatementNode::class to CompoundStatementAnalyzer(),

        NamespaceDeclarationNode::class to NamespaceDeclAnalyzer(),
        DeclarationSequenceNode::class to DeclarationSeqAnalyzer(),

        VariableDeclaratorNode::class to VarDeclaratorAnalyzer(),
        AbstractDeclaratorNode::class to AbstractDeclaratorAnalyzer(),

        FunctionDeclaratorNode::class to FunctionDeclAnalyzer(),
        FunctionDefinitionNode::class to FunctionDefAnalyzer(),
        ReturnStatementNode::class to ReturnStmtAnalyzer(),
        IfStatementNode::class to IfStatementAnalyzer(),
        ParameterNode::class to ParameterNodeAnalyzer(),

        AccessSpecifierNode::class to AccessSpecifierAnalyzer(),
        ClassDeclarationNode::class to ClassDeclAnalyzer(),
        ClassDefinitionNode::class to ClassDefAnalyzer(),
        ClassBodyNode::class to ClassBodyAnalyzer(),
        ConstructorDeclarationNode::class to ConstructorDeclAnalyzer(),
        ConstructorDefinitionNode::class to ConstructorDefAnalyzer(),

        IdExpressionNode::class to IdExpressionAnalyzer(),
        TypeCastExpressionNode::class to TypeCastExprAnalyzer(),
        UnaryExpressionNode::class to UnaryExprAnalyzer(),
        BinaryExpressionNode::class to BinaryExprAnalyzer(),
        MemberAccessExpressionNode::class to MemberAccessExprAnalyzer(),
        CallExpressionNode::class to CallExprAnalyzer(),

        RecoveryExpressionNode::class to RecoveryAnalyzer(),
        RecoveryStatementNode::class to RecoveryAnalyzer()
    )

    companion object {
        const val OPERATOR_FUN_PREFIX = ".op_"
        const val CONVERSION_OP_FUN_PREFIX = ".op_conv"

        private fun addBuiltinOverloads(scope: Scope, types: TypeContext) {

            fun add(name: String, returnType: SemanticType, vararg params: SemanticType) {
                val t = types.getFunction(returnType, params.toList(), FunctionQualifiers())
                scope.define(DeclSymbol.builtinOpFunction("$OPERATOR_FUN_PREFIX$name", t))
            }

            fun add(op: Operator, returnType: SemanticType, vararg params: SemanticType) {
                add(op.value, returnType, *params)
            }

            val integers = listOf(types.int, types.uInt, types.long, types.uLong, types.long, types.uLongLong)
            val floats = listOf(types.float, types.double)
            val arithmetics = integers + floats

            for (type in arithmetics) {
                add(Operator.PLUS, type, type, type)
                add(Operator.MINUS, type, type, type)
                add(Operator.POINTER, type, type, type)
                add(Operator.DIVIDE, type, type, type)
            }

            for (type in integers) {
                add(Operator.MOD.name, type, type, type)
            }

            for (type in arithmetics) {
                add(Operator.PLUS, type, type) // Unary +
                add(Operator.MINUS, type, type) // Unary -
                add(Operator.BIT_NOT, type, type) // Unary Bitwise not
            }

            for (type in integers) {
                add(Operator.AMP, type, type, type)
                add(Operator.BIT_OR, type, type, type)
                add(Operator.BIT_XOR, type, type, type)
                add(Operator.LBITSHIFT, type, type, types.int)
                add(Operator.RBITSHIFT, type, type, types.int)
            }

            val comparisonOps = listOf(
                Operator.EQUAL,
                Operator.NOT_EQ,
                Operator.LESS,
                Operator.GREATER,
                Operator.LESS_EQUAL,
                Operator.GREATER_EQUAL
            )
            for (type in arithmetics) {
                for (op in comparisonOps) {
                    add(op, types.bool, type, type)
                }
                //TODO: C++20 add bidirectional comparison
//                add("<=>", types.stdPartialOrdering, type, type)
            }

            add(Operator.AND, types.bool, types.bool, types.bool)
            add(Operator.OR, types.bool, types.bool, types.bool)
            add(Operator.NOT, types.bool, types.bool)
//
            for (type in arithmetics) {
                val refType = types.getReference(type)

                //Prefix
                add(Operator.INCREMENT, refType, refType)
                add(Operator.DECREMENT, refType, refType)

                //Postfix
                add(Operator.INCREMENT, type, refType, types.int)
                add(Operator.DECREMENT, type, refType, types.int)
            }

            for (type in arithmetics) {
                val refType = types.getReference(type)
                add(Operator.ADD_ASSIGN, refType, refType, type)
                add(Operator.MINUS_ASSIGN, refType, refType, type)
                add(Operator.MULT_ASSIGN, refType, refType, type)
                add(Operator.DIV_ASSIGN, refType, refType, type)
            }

            for (type in integers) {
                val refType = types.getReference(type)
                add(Operator.MOD_ASSIGN, refType, refType, type)
                add(Operator.BIT_AND_ASSIGN, refType, refType, type)
                add(Operator.BIT_OR_ASSIGN, refType, refType, type)
                add(Operator.BIT_XOR_ASSIGN, refType, refType, type)
                add(Operator.LSHIFT_ASSIGN, refType, refType, types.int)
                add(Operator.RSHIFT_ASSIGN, refType, refType, types.int)
            }
        }
    }

    init {
        target = when (options.target) {
            "x86_64Linux" -> X86_64LinuxTargetInfo;
            else -> throw IllegalArgumentException("Unknown target: ${options.target}")
        }
        types = TypeContext(target.types.sizeType, target.types.ptrDiffType)
    }

    fun analyze(ast: RootNode): AnalyzeResult {

        enterRootScope(ast.scope)
        for (child in ast.declarations) {
            findAnalyzer(child).analyze(child, this)
        }
        leaveRootScope()

        return AnalyzeResult(problems, ast)
    }

    //
    override fun enterScope() {
        enterScope(Scope(innerScope, null))
    }

    //
    override fun enterScope(owner: Scope) {
        innerScope = owner
    }

    override fun enterRootScope(scope: GlobalScope) {
        innerRootScope = scope
        innerScope = innerRootScope;
    }

    override fun leaveRootScope() {
        innerRootScope = null;
        innerScope = null;
    }


    override fun leaveScope() {
        val current = innerScope ?: throw IllegalStateException("Already outside of any scope")
        innerScope = if (current === innerRootScope) {
            null
        } else {
            current.parent
        }
    }

    override fun <T> withScope(scope: Scope, block: () -> T): T {
        enterScope(scope)
        try {
            return block()
        } finally {
            leaveScope()
        }
    }
//    override fun <T> withScope(block: () -> T): T {
//        return withScope(Scope(innerScope, null), block)
//    }

    override fun <T : ASTNode> findAnalyzer(node: T): NodeAnalyzer<T> {
        require(node !is TypeNode) { "Expected a non-type node, got ${node::class}. Use resolveType() instead." }

        val analyzer = analyzers[node::class]

        if (analyzer == null) {
            error("No analyzer found for ${node::class}", node)
        }
        @Suppress("UNCHECKED_CAST")
        return (analyzer ?: RecoveryAnalyzer()) as NodeAnalyzer<T>;
    }

    override fun isSameType(first: SemanticType, second: SemanticType): Boolean = first == second
    override fun isSameOverloadFun(first: SemanticType.Function, second: SemanticType.Function): Boolean {
        if (first === second) return true
        if (first.params.size != second.params.size) return false

        for (i in first.params.indices) {
            if (first.params[i] !== second.params[i]) return false
        }

        if (first.qualifiers.isConst != second.qualifiers.isConst) return false
        if (first.qualifiers.isVolatile != second.qualifiers.isVolatile) return false
        if (first.qualifiers.refQualifier != second.qualifiers.refQualifier) return false
        return true
    }

    override fun findBinaryOverload(
        firstOp: TypeNode,
        secondOp: TypeNode,
        operator: Operator
    ): DeclSymbol.FunctionDecl? {
        return null
    }

    override fun resolveSymbols(node: IdentifierNode, currentScope: Scope, processedOnly: Boolean): OpResult<DeclSymbol> {
        return when (node) {
            is QualifiedIdentifierNode -> resolveQualified(node, currentScope, processedOnly)
            else -> currentScope.lookupUnqualified(node.name, processedOnly)
        }
    }

    override fun resolveSymbolsLocal(node: IdentifierNode, currentScope: Scope, processedOnly: Boolean): OpResult<DeclSymbol> {
        return when (node) {
            is QualifiedIdentifierNode -> OpResult.failure("Cannot resolve local identifier: ${node.name}", node)
            else -> currentScope.lookupLocal(node.name, processedOnly)
        }
    }

    override fun resolveSymbolsLocal(name: String, currentScope: Scope, processedOnly: Boolean): OpResult<DeclSymbol> {
        return currentScope.lookupLocal(name, processedOnly)
    }


    override fun resolveSymbolsUnqualified(node: String, currentScope: Scope, processedOnly: Boolean): OpResult<DeclSymbol> {
        return currentScope.lookupUnqualified(node, processedOnly)
    }

    private fun resolveQualified(node: QualifiedIdentifierNode, currentScope: Scope, processedOnly: Boolean): OpResult<DeclSymbol> {
        var targetScope: Scope

        if (node.isGlobal) {
            targetScope = currentScope.getRootScope()
        } else {
            val firstQualifier = node.qualifiers.first().name
            val firstFound = resolveSymbolsUnqualified(firstQualifier, currentScope, processedOnly)
                .getOrElse { return it; }

            targetScope = getScopeFromSymbol(firstFound) ?: return OpResult.failure("Symbol '$firstQualifier' is not a class or namespace", node)
        }
        val startIndex = if (node.isGlobal) 0 else 1

        for (i in startIndex until node.qualifiers.size) {
            val qualifierName = node.qualifiers[i].name

            val symbol = targetScope.lookupLocal(qualifierName, processedOnly).getOrElse { return it; }
            targetScope = getScopeFromSymbol(symbol) ?: return OpResult.failure("'$qualifierName' is not a class or namespace", node)
        }

        val finalSymbol = targetScope.lookupLocal(node.name, processedOnly).getOrElse { return it; }
        return OpResult.success(finalSymbol)
    }

    private fun getScopeFromSymbol(symbol: DeclSymbol): Scope? = when (symbol) {
        is DeclSymbol.NamespaceDecl -> symbol.scope
        is DeclSymbol.ClassDecl -> symbol.scope
        else -> null
    }

    override fun resolveType(
        typeNode: TypeNode,
        currentScope: Scope,
        deduceType: SemanticType?,
        isByValue: Boolean
    ): OpResult<SemanticType> {

        val nonRefDeduce = if (deduceType != null) types.removeRef(deduceType) else deduceType

        val semanticType: SemanticType = when (typeNode) {

            is AutoTypeNode -> {
                val finalType = if (nonRefDeduce == null) {
                    types.getAuto(typeNode.isConst, typeNode.isVolatile)
                } else {
                    val actualDeduce = if (isByValue) types.dropCV(types.decay(nonRefDeduce)) else nonRefDeduce
                    types.addCV(actualDeduce, typeNode.isConst, typeNode.isVolatile)
                }

                typeNode.resolvedType = finalType
                return OpResult.success(finalType)
            }

            is PrimitiveTypeNode -> {
                types.getPrimitive(kind = typeNode.kind, isConst = typeNode.isConst, isVolatile = typeNode.isVolatile)
            }

            is PointerTypeNode -> {
                if (typeNode.type is ReferenceTypeNode || typeNode.type is RValueReferenceTypeNode) {
                    return OpResult.failure("Pointer cannot point to a reference type", typeNode)
                }

                val unpackedDeduce = nonRefDeduce?.let { types.dropCV(it) }
                val innerDeduce = (unpackedDeduce as? SemanticType.Pointer)?.pointee

                val pointeeType = resolveType(
                    typeNode = typeNode.type,
                    currentScope = currentScope,
                    deduceType = innerDeduce,
                    isByValue = false
                ).getOrElse { return it }

                types.getPointer(pointee = pointeeType, isConst = typeNode.isConst, isVolatile = typeNode.isVolatile)
            }

            is ReferenceTypeNode -> {
                ensureReferenceBase(typeNode.type, typeNode)

                val innerDeduce = (nonRefDeduce as? SemanticType.Reference)?.pointee ?: nonRefDeduce

                val pointeeType = resolveType(
                    typeNode = typeNode.type,
                    currentScope = currentScope,
                    deduceType = innerDeduce,
                    isByValue = false
                ).getOrElse { return it }

                types.getReference(pointee = pointeeType)
            }

            is RValueReferenceTypeNode -> {
                ensureReferenceBase(typeNode.type, typeNode)

                val innerDeduce = (nonRefDeduce as? SemanticType.RValueReference)?.pointee ?: nonRefDeduce

                val pointeeType = resolveType(
                    typeNode = typeNode.type,
                    currentScope = currentScope,
                    deduceType = innerDeduce,
                    isByValue = false
                ).getOrElse { return it }

                types.getRValueReference(pointee = pointeeType)
            }

            is ArrayTypeNode -> {
                var arraySize: Long? = null

                if (typeNode.sizeExpression != null) {
                    findAnalyzer(typeNode.sizeExpression).analyze(typeNode.sizeExpression, this)
                    val evalResult = typeNode.sizeExpression.evaluated
                            ?: return OpResult.failure(
                                "Array size expression must be constant. Variable length arrays are not supported by standard.",
                                typeNode.sizeExpression
                            )

                    val sizeT = typeNode.sizeExpression.resolvedType as? SemanticType.Primitive
                    val bigIntVal = evalResult as? BigInteger

                    if (sizeT?.kind?.isInt != true || bigIntVal == null || bigIntVal.signum() < 0) {
                        return OpResult.failure("Array size must be a non-negative integer", typeNode.sizeExpression)
                    }

                    arraySize = bigIntVal.longValueExact()
                }

                val innerDeduce = (nonRefDeduce as? SemanticType.Array)?.elementType
                val elementType =
                    resolveType(typeNode.elementType, currentScope, innerDeduce, isByValue).getOrElse { return it }

                types.getArray(elementType = elementType, size = arraySize)
            }

            is DeclaredTypeNode -> {
                val decl = resolveSymbols(typeNode.typeName, currentScope, true).getOrElse { return it; }
                if (decl !is DeclSymbol.ClassDecl) {
                    return OpResult.failure("Invalid type name: ${typeNode.typeName.name}", typeNode.typeName)
                }

                types.getDeclared(classDecl = decl, isConst = typeNode.isConst, isVolatile = typeNode.isVolatile)
            }

            is FunctionTypeNode -> {
                val functionDeduce = nonRefDeduce as? SemanticType.Function

                val returnType = resolveType(
                    typeNode.returnType,
                    currentScope,
                    functionDeduce?.returnType,
                    isByValue = true
                ).getOrElse { return it }

                val resolvedParams = mutableListOf<SemanticType>()
                for ((index, paramNode) in typeNode.params.withIndex()) {
                    val paramDeduce = functionDeduce?.params?.getOrNull(index)

                    val paramType =
                        resolveType(paramNode.type, currentScope, paramDeduce, isByValue = true).getOrElse { return it }
                    resolvedParams.add(paramType)
                }

                types.getFunction(returnType = returnType, params = resolvedParams, qualifiers = typeNode.qualifiers)
            }

            is MemberPointerTypeNode -> {
                val decl = resolveSymbols(typeNode.parentId, currentScope, true).getOrElse { return it }
                if (decl !is DeclSymbol.ClassDecl) {
                    return OpResult.failure("Invalid class name for member pointer: ${typeNode.parentId}", typeNode)
                }

                val innerDeduce = (nonRefDeduce as? SemanticType.MemberPointer)?.pointee
                val memberType =
                    resolveType(typeNode.type, currentScope, innerDeduce, isByValue = false).getOrElse { return it }

                types.getMemberPointer(
                    classDecl = decl,
                    pointee = memberType,
                    isConst = typeNode.isConst,
                    isVolatile = typeNode.isVolatile
                )
            }

            is ErrorTypeNode -> {
                types.getError();
            }
        }

        typeNode.resolvedType = semanticType
        return OpResult.success(semanticType)
    }

    private fun ensureReferenceBase(base: TypeNode, node: TypeNode) {
        if (base is RValueReferenceTypeNode || base is ReferenceTypeNode) {
            error("Reference to reference is forbidden", node)
        } else if (base is PrimitiveTypeNode && base.kind == PrimitiveTypeKind.VOID) {
            error("Reference to void type is forbidden", node)
        }
    }


    override fun resolveOpOverloads(scope: Scope, op: Operator, isBinary: Boolean, leftOperand: ExpressionInfo, rightOperand: ExpressionInfo?): Set<ViableCandidate<DeclSymbol.FunctionDecl>> {
        return resolveOpOverloads(scope, op.value, isBinary, leftOperand, rightOperand)
    }

    override fun resolveOpOverloads(
        scope: Scope,
        op: String,
        isBinary: Boolean,
        leftOperand: ExpressionInfo,
        rightOperand: ExpressionInfo?
    ): Set<ViableCandidate<DeclSymbol.FunctionDecl>> {
        //TODO: checking for visibility modifiers
        val name = "${OPERATOR_FUN_PREFIX}${op}"
//        val scope = resolveSymbols(IdentifierNode(name, SourceLocation.EXPORTED), scope).filterIsInstance<DeclSymbol.OperatorFunctionDecl>().toMutableList()
        val matches = mutableListOf<ViableCandidate<DeclSymbol.FunctionDecl>>()
        val nonRefLeft = types.removeRef(leftOperand.type)

        if (isBinary) {
            if (rightOperand == null) throw IllegalArgumentException("Right operand is null for binary expression: $leftOperand $op")

            if (nonRefLeft is SemanticType.Declared) {
                matches += findBestLocal(name, nonRefLeft.decl.scope, rightOperand)
            }
            val freeCandidates = mutableSetOf<DeclSymbol.FunctionDecl>()
            freeCandidates.addAll(collectADLOverloads(name, leftOperand, rightOperand))

            val unqualifiedSet = resolveSymbolsUnqualified(name, scope).getAsOrNull<DeclSymbol.FunctionOverloadSet>()
            unqualifiedSet?.let { freeCandidates.addAll(it.overloads) }

            if (freeCandidates.isNotEmpty()) {
                val leftRef = leftOperand.copy(type = types.getReference(nonRefLeft))
                matches += findBestMatch(freeCandidates.toList(), listOf(leftRef, rightOperand))
            }
        } else {
            if (nonRefLeft is SemanticType.Declared) {
                matches += findBestLocal(name, nonRefLeft.decl.scope, leftOperand)
            }

            val freeCandidates = mutableSetOf<DeclSymbol.FunctionDecl>()

            freeCandidates.addAll(collectADLOverloads(name, leftOperand))

            val unqualifiedSet = resolveSymbolsUnqualified(name, scope).getAsOrNull<DeclSymbol.FunctionOverloadSet>()
            unqualifiedSet?.let { freeCandidates.addAll(it.overloads) }

            if (op == Operator.AMP.value && leftOperand.valueCategory == ValueCategory.LVALUE) {
                val builtinOp = DeclSymbol.builtinOpFunction(
                    name,
                    types.getFunction(types.getPointer(nonRefLeft), listOf(types.getReference(nonRefLeft)), FunctionQualifiers())
                ).apply { processed = true }

                freeCandidates.add(builtinOp)
            }

            if (freeCandidates.isNotEmpty()) {
                val leftRef = leftOperand.copy(type = types.getReference(nonRefLeft))
                matches += findBestMatch(freeCandidates.toList(), listOf(leftRef))
            }
        }
        return matches.distinctBy { System.identityHashCode(it.decl) }.toSet();
    }

    fun findBestLocal(name: String, scope: Scope, op: ExpressionInfo): Set<ViableCandidate<DeclSymbol.FunctionDecl>> {
        val current: DeclSymbol.FunctionOverloadSet? = scope.lookupLocal(name, true).getAsOrNull()
        return current?.let {
            findBestMatch(current.overloads, listOf(op))
        } ?: emptySet()
    }

    fun findBestWithRef(name: String, scope: Scope, leftOperand: ExpressionInfo, rightOperand: ExpressionInfo?): Set<ViableCandidate<DeclSymbol.FunctionDecl>> {
        val nonRefLeft = types.removeRef(leftOperand.type)
        val inParent: DeclSymbol.FunctionOverloadSet? = resolveSymbolsUnqualified(name, scope).getAsOrNull()
        return inParent?.let {
            findBestMatch(inParent.overloads, listOfNotNull(leftOperand.copy(type = types.getReference(nonRefLeft)), rightOperand))
        } ?: emptySet()
    }


    private fun resolveConstructorOverloads(
        classDecl: DeclSymbol.ClassDecl,
        params: List<ExpressionInfo>,
        onlyImplicit: Boolean
    ): Set<ViableCandidate<DeclSymbol.ConstructorDecl>> {
        var ctor = classDecl.scope.lookupLocal(".ctor", true).getOrNull() as? DeclSymbol.ConstructorDecl
        if (!(!onlyImplicit || ctor?.isExplicit == true)) {
            ctor = null;
        }
        return findBestMatch(listOfNotNull(ctor), params)
    }

    override fun <T : DeclSymbol.FunctionDecl> findBestMatch(
        decls: Collection<T>,
        inParams: List<ExpressionInfo>
    ): Set<ViableCandidate<T>> {

        val viable = mutableSetOf<ViableCandidate<T>>()

        loop@ for (decl in decls) {
            val res = probeCallArgs(decl.params, decl.defaultParamsCount, inParams, decl.astNode.location, breakOnMiss = true);
            val conversions = res.map { it.getOrElse { continue@loop; } };
            viable += ViableCandidate(decl, conversions)
        }

        if (viable.isEmpty()) return emptySet()

        viable.removeIf {
            for (candB in viable) {
                if (it === candB) continue

                if (isBetterCandidateThan(candB, it, inParams.size)) {
                    return@removeIf true
                }
            }
            return@removeIf false
        }
        return viable
    }

    override fun probeCallArgs(declParams: List<SemanticType>, defaultCount: Int, inParams: List<ExpressionInfo>, location: SourceLocation?, breakOnMiss: Boolean): List<OpResult<ConversionSequence>> {
        if (inParams.size < (declParams.size - defaultCount) || inParams.size > declParams.size) return listOf(ErrorHelper.argsCountMiss(declParams.size, inParams.size, location))

        val seqs = mutableListOf<OpResult<ConversionSequence>>()

        for (i in inParams.indices) {
            val inParam = inParams[i]
            val declType = declParams[i]

            val seq = findImplicitCastSeq(inParam.type, inParam.valueCategory, declType, getRefValueCategory(declType), inParam.isNullConstant)
            if (seq.isEmpty()) {

                seqs += ErrorHelper.cannotConvert(inParam.type, declType, inParam.location)
                if (breakOnMiss) return seqs;
            } else if (seq.size > 1) {
                seqs += ErrorHelper.ambiguousConversion(inParam.type, declType, inParam.location)
            } else seqs += OpResult.success(seq.first());
        }
        return seqs;
    }

    private fun <T> isBetterCandidateThan(a: ViableCandidate<T>, b: ViableCandidate<T>, numArgs: Int): Boolean {
        var hasBetter = false

        for (i in 0 until numArgs) {
            val cmp = compareSequences(a.sequences[i], b.sequences[i])
            if (cmp < 0) return false
            if (cmp > 0) hasBetter = true
        }

        return hasBetter
    }


//    private fun resolveOverloads(scope: Scope, name: String, params: List<SemanticType>): List<DeclSymbol.FunctionDecl> {
//
//    }

    override fun findImplicitCastSeq(
        fromType: SemanticType, fromVC: ValueCategory,
        toType: SemanticType,
        toVC: ValueCategory,
        isNullPointerConstant: Boolean
    ): Collection<ConversionSequence> {

        when (toType) {
            is SemanticType.Reference -> {
                val underlying = toType.pointee

                if (!toType.isConst) {
                    if (fromVC == ValueCategory.LVALUE && types.removeRef(fromType) === underlying) {
                        val seq = findStdConversionSeq(fromType, fromVC, underlying, fromVC, isNullPointerConstant)
                                ?: IdentityConversionSequence(underlying)
                        seq.bindsToTemporary = false
                        return setOf(seq)
                    }
                    return emptySet()
                } else {
                    if (fromVC == ValueCategory.LVALUE && types.removeRef(fromType) === underlying) {
                        val seq = findStdConversionSeq(fromType, fromVC, underlying, fromVC, isNullPointerConstant)
                                ?: IdentityConversionSequence(underlying)
                        seq.bindsToTemporary = false
                        return setOf(seq)
                    }

                    var seq: Set<ConversionSequence> = setOfNotNull(
                        findStdConversionSeq(
                            fromType,
                            fromVC,
                            underlying,
                            ValueCategory.PRVALUE,
                            isNullPointerConstant
                        )
                    )

                    if (seq.isEmpty()) {
                        seq = findUserConversionsSeq(fromType, fromVC, underlying, ValueCategory.PRVALUE)
                    }

                    seq.forEach {
                        it.bindsToTemporary = true
                    }
                    return seq
                }
            }

            is SemanticType.RValueReference -> {
                val underlying = toType.pointee

                if (fromVC == ValueCategory.LVALUE) {
                    return emptySet()
                }

                if (fromVC == ValueCategory.XVALUE && types.removeRef(fromType) == underlying) {
                    val seq = findStdConversionSeq(fromType, fromVC, underlying, fromVC, isNullPointerConstant)
                            ?: IdentityConversionSequence(underlying)
                    seq.bindsToTemporary = false
                    return setOf(seq)
                }

                var seq: Set<ConversionSequence> = setOfNotNull(
                    findStdConversionSeq(
                        fromType,
                        fromVC,
                        underlying,
                        ValueCategory.PRVALUE,
                        isNullPointerConstant
                    )
                )
                if (seq.isEmpty()) {
                    seq = findUserConversionsSeq(fromType, fromVC, underlying, toVC)
                }

                seq.forEach {
                    it.bindsToTemporary = true
                }
                return seq
            }

            else -> {
                var seq: Set<ConversionSequence> = setOfNotNull(
                    findStdConversionSeq(
                        fromType,
                        fromVC,
                        toType,
                        ValueCategory.PRVALUE,
                        isNullPointerConstant
                    )
                )
                if (seq.isEmpty()) {
                    seq = findUserConversionsSeq(fromType, fromVC, toType, toVC)
                }
                seq.forEach {
                    it.bindsToTemporary = false
                }
                return seq
            }
        }
    }

    fun findUserConversionsSeq(
        fromType: SemanticType, fromVC: ValueCategory,
        toType: SemanticType,
        toVC: ValueCategory,
    ): Set<ConversionSequence> {

        fun findCtorConv(toType: SemanticType.Declared): Set<ConversionSequence> {
            val ctors = toType.decl.scope.getConstructors();
            val bestCtors = findBestMatch(ctors.overloads, listOf(ExpressionInfo(fromType, fromVC, false)))
            val scs2 = findStdConversionSeq(types.dropCV(toType), ValueCategory.PRVALUE, toType, toVC, false)
                    ?: return emptySet();

            return bestCtors.mapNotNullTo(mutableSetOf()) {
                val seq = it.sequences[0] as? StdConversionSequence ?: return@mapNotNullTo null
                UserConversionSequence(seq, it.decl, scs2, toType, seq.bindsToTemporary || scs2.bindsToTemporary)
            }
        }

        fun findOperatorConv(fromType: SemanticType.Declared): Set<ConversionSequence> {

            val ops = fromType.decl.scope.lookupLocal(CONVERSION_OP_FUN_PREFIX, true).getOrNull() as? DeclSymbol.OperatorFunctionDecl
            val bestOps = findBestMatch(listOfNotNull(ops), listOf(ExpressionInfo(fromType, fromVC, false)))

            return bestOps.mapNotNullTo(mutableSetOf()) {
                val seq = it.sequences[0] as? StdConversionSequence ?: return@mapNotNullTo null
                val scs2 = findStdConversionSeq(it.decl.returnType, ValueCategory.PRVALUE, fromType, toVC, false)
                        ?: return@mapNotNullTo null;
                UserConversionSequence(seq, it.decl, scs2, toType, seq.bindsToTemporary || scs2.bindsToTemporary)
            }
        }

        return when (fromType) {
            !is SemanticType.Declared if toType is SemanticType.Declared -> findCtorConv(toType)
            is SemanticType.Declared if toType !is SemanticType.Declared -> findOperatorConv(fromType)
            is SemanticType.Declared if toType is SemanticType.Declared -> findCtorConv(toType) + findOperatorConv(fromType)
            else -> emptySet()
        }
    }


    fun findStdConversionSeq(
        fromType: SemanticType,
        fromVC: ValueCategory,
        toType: SemanticType,
        toVC: ValueCategory,
        isNullPointerConstant: Boolean
    ): StdConversionSequence? {
        if (fromType == toType && fromVC == toVC) return StdConversionSequence(emptyList(), toType)
        val isNullPtr =
            (fromType is SemanticType.Primitive && fromType.kind == PrimitiveTypeKind.NULLPTR) || isNullPointerConstant

        var currentType = fromType
        var currentVC = fromVC
        val seq = mutableListOf<ConversionStep>()
        when {
            currentType is SemanticType.Array && toType is SemanticType.Pointer -> {
                if (currentType.elementType == toType.pointee) {
                    if (currentVC == ValueCategory.PRVALUE) {
                        currentVC = ValueCategory.XVALUE
                        seq += ConversionStep(ConversionKind.TEMPORARY_MATERIALIZATION, currentVC, currentType)

                    }
                    currentType = types.decay(currentType)
                    currentVC = ValueCategory.PRVALUE
                    seq += ConversionStep(ConversionKind.ARRAY_TO_POINTER, currentVC, currentType)
                } else return null
            }

            currentType is SemanticType.Function && toType.isFunctionPointer() -> {
                currentType = types.getPointer(currentType)
                currentVC = ValueCategory.PRVALUE
                seq += ConversionStep(ConversionKind.FUNCTION_TO_POINTER, currentVC, currentType)
            }

            currentVC.isGLValue && toVC == ValueCategory.PRVALUE &&
                    currentType !is SemanticType.Function && currentType !is SemanticType.Array -> {
                if (!currentType.isComplete) return null

                currentVC = ValueCategory.PRVALUE
                currentType = types.removeRef(currentType)
                seq += ConversionStep(ConversionKind.LVALUE_TO_RVALUE, currentVC, currentType)
            }
        }

        if (currentType == toType && currentVC == toVC) return StdConversionSequence(seq, toType)

        var ignoreQualConv = false
        if (currentType is SemanticType.Primitive && toType is SemanticType.Primitive) {
            val fromKind = currentType.kind
            val toKind = toType.kind

            if (fromKind != toKind) {
                when {
                    fromKind.isInt && toKind.isInt -> {
                        seq += if (toKind == PrimitiveTypeKind.BOOL) {
                            currentType =
                                types.getPrimitive(PrimitiveTypeKind.BOOL, currentType.isConst, currentType.isVolatile);
                            ConversionStep(ConversionKind.INTEGRAL_TO_BOOLEAN, currentVC, currentType)
                        } else {
                            val promoted = target.promoteIntegralType(fromKind)
                            if (promoted == toKind) {
                                currentType = types.getPrimitive(promoted, currentType.isConst, currentType.isVolatile);
                                ConversionStep(ConversionKind.INTEGRAL_PROMOTION, currentVC, currentType)
                            } else {
                                currentType = types.getPrimitive(toKind, currentType.isConst, currentType.isVolatile);
                                ConversionStep(ConversionKind.INTEGRAL_CONVERSION, currentVC, currentType)
                            }
                        }
                    }

                    fromKind.isFloat && toKind.isFloat -> {
                        seq += if (fromKind == PrimitiveTypeKind.FLOAT && toKind == PrimitiveTypeKind.DOUBLE) {
                            currentType = types.getPrimitive(
                                PrimitiveTypeKind.DOUBLE,
                                currentType.isConst,
                                currentType.isVolatile
                            );
                            ConversionStep(ConversionKind.FLOAT_PROMOTION, currentVC, currentType)
                        } else {
                            currentType = types.getPrimitive(toKind, currentType.isConst, currentType.isVolatile);
                            ConversionStep(ConversionKind.FLOAT_CONVERSION, currentVC, currentType)
                        }
                    }

                    fromKind.isFloat && toKind.isInt -> {
                        seq += if (toKind == PrimitiveTypeKind.BOOL) {
                            currentType =
                                types.getPrimitive(PrimitiveTypeKind.BOOL, currentType.isConst, currentType.isVolatile);
                            ConversionStep(ConversionKind.FLOAT_TO_BOOLEAN, currentVC, currentType)
                        } else {
                            currentType = types.getPrimitive(toKind, currentType.isConst, currentType.isVolatile);
                            ConversionStep(ConversionKind.FLOAT_TO_INTEGRAL, currentVC, currentType)
                        }
                    }

                    fromKind.isInt && toKind.isFloat -> {
                        currentType = types.getPrimitive(toKind, currentType.isConst, currentType.isVolatile);
                        seq += ConversionStep(ConversionKind.INTEGRAL_TO_FLOAT, currentVC, currentType)
                    }

                    else -> return null
                }
                currentType = toType
            }
        } else if (isNullPtr && (toType is SemanticType.Pointer || toType is SemanticType.MemberPointer)) {
            currentType = toType
            ignoreQualConv = true
            seq += ConversionStep(ConversionKind.NULL_TO_POINTER, currentVC, currentType)
        } else if ((currentType is SemanticType.Pointer || currentType is SemanticType.MemberPointer) &&
            (toType is SemanticType.Primitive && toType.kind == PrimitiveTypeKind.BOOL)
        ) {
            currentType = types.getPrimitive(PrimitiveTypeKind.BOOL, currentType.isConst, currentType.isVolatile);
            seq += ConversionStep(ConversionKind.POINTER_TO_BOOLEAN, currentVC, currentType)

        } else if (currentType is SemanticType.Pointer && toType is SemanticType.Pointer) {
            if (toType.pointee is SemanticType.Primitive && toType.pointee.kind == PrimitiveTypeKind.VOID) {
                currentType = types.getPointer(types.void, currentType.isConst, currentType.isVolatile);
                seq += ConversionStep(ConversionKind.POINTER_TO_VOID, currentVC, currentType)
            }
        } else if (currentType != toType && !isQualificationConversion(currentType, toType)) {
            return null
        }

        if (currentType.isFunctionPointer() && toType.isFunctionPointer()) {
            val fromFun = currentType.pointee as SemanticType.Function
            val toFun = toType.pointee as SemanticType.Function

            if (fromFun.returnType == toFun.returnType && fromFun.params == toFun.params) {
                if (fromFun.qualifiers.isConst == toFun.qualifiers.isConst &&
                    fromFun.qualifiers.isVolatile == toFun.qualifiers.isVolatile &&
                    fromFun.qualifiers.refQualifier == toFun.qualifiers.refQualifier
                ) {

                    if (fromFun.qualifiers.isNoExcept && !toFun.qualifiers.isNoExcept) {
                        currentType = types.getPointer(toFun, currentType.isConst, currentType.isVolatile);
                        seq += ConversionStep(ConversionKind.FUNCTION_PTR_CONVERSION, currentVC, currentType)
                    } else if (fromFun.qualifiers.isNoExcept != toFun.qualifiers.isNoExcept) {
                        return null
                    }
                } else return null
            } else return null
        }

        if (!ignoreQualConv && isQualificationConversion(currentType, toType)) {
            currentType = toType
            seq += ConversionStep(ConversionKind.QUALIFICATION, currentVC, currentType)
        }

        if (currentType != toType) {
            return null
        }

        return StdConversionSequence(seq, toType)
    }

    fun decomposePointer(type: SemanticType): DecomposedPointer? {
        if (type !is SemanticType.Pointer) return null

        val levels = mutableListOf<CvQualifiers>()
        var current: SemanticType = type

        while (current is SemanticType.Pointer) {
            val pointee = current.pointee
            levels.add(CvQualifiers(pointee.isConst, pointee.isVolatile))
            current = pointee
        }

        return DecomposedPointer(levels, types.dropCV(current))
    }

    fun isQualificationConversion(from: SemanticType, to: SemanticType): Boolean {
        if (from === to) return false;
        val decompFrom = decomposePointer(from) ?: return false
        val decompTo = decomposePointer(to) ?: return false

        if (decompFrom.levels.size != decompTo.levels.size) return false
        if (decompFrom.baseType != decompTo.baseType) return false

        val n = decompTo.levels.size
        for (k in 0 until n) {
            val cvFrom = decompFrom.levels[k]
            val cvTo = decompTo.levels[k]

            if (!cvTo.containsAll(cvFrom)) {
                return false // Trying to remove cv
            }

            if (cvFrom != cvTo) {
                for (j in 0 until k) {
                    if (!decompTo.levels[j].isConst) {
                        return false // Ex: int** -> const int
                    }
                }
            }
        }

        return true
    }

    fun compareSequences(a: ConversionSequence, b: ConversionSequence): Int {
        if (a is UserConversionSequence != b is UserConversionSequence) {
            return if (a !is UserConversionSequence) 1 else -1 // SCS better than User-Defined
        }

        if (a is UserConversionSequence && b is UserConversionSequence) {
            // Check sub SCS
            val scs1Cmp = compareSequences(a.firstScs, b.firstScs)
            if (scs1Cmp != 0) return scs1Cmp
            return compareSequences(a.secondScs, b.secondScs)
        }

        a as StdConversionSequence
        b as StdConversionSequence

        val rankA = a.getRank()
        val rankB = b.getRank()

        if (rankA != rankB) {
            return rankA.cost.compareTo(rankB.cost)
        }

        //Less steps is better
        if (a.steps.size != b.steps.size) {
            val aHasQual = a.contains(ConversionKind.QUALIFICATION)
            val bHasQual = b.contains(ConversionKind.QUALIFICATION)

            // Sequence without qual conv is better
            if (aHasQual != bHasQual) {
                return if (!aHasQual) 1 else -1
            }
        }

        if (a.bindsToTemporary != b.bindsToTemporary) {
            return if (!a.bindsToTemporary) 1 else -1
        }

        return 0
    }

    override fun buildConversionSeq(base: ExpressionNode, seq: ConversionSequence): ExpressionNode {
        var node = when (seq) {
            is StdConversionSequence -> buildStdConversionNodes(base, seq)
            is UserConversionSequence -> {
                var node = buildStdConversionNodes(base, seq.firstScs);
                node = CallExpressionNode(null, ArgumentsNode(listOf(node), SourceLocation.EXPORTED), SourceLocation.EXPORTED);
                node.functionDecl = seq.method
                node.resolvedType = seq.method.returnType;
                node.valueCategory = getRefValueCategory(seq.method.returnType);
                buildStdConversionNodes(node, seq.secondScs)
            }

            else -> base
        }

        if (seq.bindsToTemporary) {
            node = ImplicitCastExpressionNode(
                ConversionKind.TEMPORARY_MATERIALIZATION,
                node,
                base.location
            ).apply { resolvedType = seq.outType; valueCategory = ValueCategory.XVALUE }
        }
        return node
    }

    override fun getRefValueCategory(returnType: SemanticType): ValueCategory {
        return when (returnType) {
            is SemanticType.Reference -> ValueCategory.LVALUE
            is SemanticType.RValueReference -> ValueCategory.XVALUE
            else -> ValueCategory.PRVALUE
        }
    }

    fun buildStdConversionNodes(base: ExpressionNode, seq: StdConversionSequence): ExpressionNode {
        var current = base;
        for (step in seq.steps) {
            current = ImplicitCastExpressionNode(step.kind, current, base.location).apply {
                resolvedType = step.newType; valueCategory = step.newVC
            }
        }
        return current
    }


    override fun buildConversion(
        fromExpr: ExpressionNode,
        to: SemanticType,
        toVC: ValueCategory
    ): OpResult<ExpressionNode> {
        val seq = findImplicitCastSeq(
            fromExpr.resolvedType!!,
            fromExpr.valueCategory!!,
            to,
            toVC,
            isNullPointerConstant(fromExpr)
        )
        if (seq.size > 1) {
            return OpResult.failure("Ambiguous cast from${fromExpr.resolvedType} to ${to}", fromExpr)
        } else if (seq.isEmpty()) {
            return OpResult.failure("No cast from ${fromExpr.resolvedType} to ${to}", fromExpr)
        }
        return OpResult.success(buildConversionSeq(fromExpr, seq.first()))
    }

    override fun warn(message: String, node: ASTNode?, location: SourceLocation?) {
        problems[ProblemLevel.WARNING]!! += SemanticProblem(message, ProblemLevel.WARNING, node, location, getStackTrace(options))
    }

    override fun error(message: String, node: ASTNode?, location: SourceLocation?) {
        problems[ProblemLevel.ERROR]!! += SemanticProblem(message, ProblemLevel.ERROR, node, location, getStackTrace(options))
    }

    override fun error(failure: OpResult.Failure, astNode: ASTNode?, location: SourceLocation?) {
        error(failure.message, astNode ?: failure.args.firstOrNull() as? ASTNode, failure.args.firstOrNull() as? SourceLocation)
    }

    private fun collectADLForSymbol(
        typeDecl: DeclSymbol,
        name: String,
        result: MutableSet<DeclSymbol.FunctionDecl>
    ) {
        var current: DeclSymbol? = typeDecl

        while (current is DeclSymbol.ClassDecl) {
            val overloads = current.scope.lookupLocal(name, true).getAsOrNull<DeclSymbol.FunctionOverloadSet>()
            overloads?.let { result.addAll(it.overloads.filter { !it.isMethod }) }

            current = current.parentSymbol
        }

        if (current is DeclSymbol.NamespaceDecl) {
            val overloads = current.scope.lookupLocal(name, true).getAsOrNull<DeclSymbol.FunctionOverloadSet>()

            overloads?.let { result.addAll(it.overloads) }
        }
    }

    override fun collectADLOverloads(name: String, vararg operands: ExpressionInfo?): List<DeclSymbol.FunctionDecl> {
        val result = mutableSetOf<DeclSymbol.FunctionDecl>()

        for (operand in operands) {
            if (operand == null) continue

            val baseType = getUnderlyingTypeForADL(operand.type)

            if (baseType is SemanticType.Declared) {
                collectADLForSymbol(baseType.decl, name, result)
            }
        }

        return result.toList()
    }

    override fun getUnderlyingTypeForADL(type: SemanticType): SemanticType {
        var curr = type
        while (true) {
            curr = when (curr) {
                is SemanticType.Reference -> curr.pointee
                is SemanticType.Pointer -> curr.pointee
                is SemanticType.Array -> curr.elementType
                else -> return curr
            }
        }
    }

    data class CvQualifiers(
        val isConst: Boolean = false,
        val isVolatile: Boolean = false
    ) {
        fun containsAll(other: CvQualifiers): Boolean {
            if (other.isConst && !this.isConst) return false
            if (other.isVolatile && !this.isVolatile) return false
            return true
        }
    }

    data class DecomposedPointer(
        val levels: List<CvQualifiers>,
        val baseType: SemanticType
    )

}