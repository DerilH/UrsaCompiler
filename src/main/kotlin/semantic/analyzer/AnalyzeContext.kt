package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdentifierNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.ast.TypeNode
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.TypeContext
import org.derilh.semantic.analyzer.ConversionSequence
import org.derilh.semantic.analyzer.ViableCandidate
import org.derilh.target.TargetInfo
import java.math.BigInteger

interface AnalyzeContext {
    var anonymousIdCounter: Int;
    val target: TargetInfo
    val scope: Scope
    val rootScope: Scope
    val types: TypeContext
    fun enterScope(owner: DeclSymbol)
    fun enterScope(owner: Scope)
    fun leaveScope()
    fun <T> withScope(scope: DeclSymbol, block: () -> T): T
    fun <T> withScope(scope: Scope, block: () -> T): T
    fun <T : ASTNode> findAnalyzer(node: T): NodeAnalyzer<T>
    fun warn(message: String, node: ASTNode? = null)
    fun error(message: String, node: ASTNode? = null)
    fun isSameType(first: SemanticType, second: SemanticType): Boolean
    fun resolveSymbols(node: IdentifierNode, currentScope: Scope): Set<DeclSymbol>
    fun resolveType(typeNode: TypeNode, currentScope: Scope, deduceType: SemanticType? = null, isByValue: Boolean = true): SemanticType?;
    fun findBinaryOverload(firstOp: TypeNode, secondOp: TypeNode, operator: Operator): DeclSymbol.FunctionDecl?
    fun resolveOpOverloads(scope: Scope, op: Operator, params: List<ExpressionInfo>): Set<ViableCandidate<DeclSymbol.OperatorFunctionDecl>>;
    fun buildConversionNodes(base: ExpressionNode, seq: ConversionSequence): ExpressionNode;

    fun getNextAnonId(): Int {
        return anonymousIdCounter++;
    }

    fun isNullPointerConstant(expr: ExpressionNode): Boolean {
        val type = (expr.resolvedType as? SemanticType.Primitive) ?: return false

        return if (type.kind == PrimitiveTypeKind.NULLPTR) {
            true
        } else ((expr.evaluated as? BigInteger)?.signum() == 0)
    }
}