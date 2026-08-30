package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdentifierNode
import org.derilh.ast.TypeNode
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.OpResult
import org.derilh.core.SourceLocation
import org.derilh.core.ValueCategory
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.TypeContext
import org.derilh.semantic.analyzer.ConversionSequence
import org.derilh.semantic.analyzer.IdContext
import org.derilh.semantic.analyzer.ViableCandidate
import org.derilh.target.TargetInfo
import java.math.BigInteger

interface AnalyzeContext {
    var anonymousIdCounter: Int;
    val target: TargetInfo
    val scope: Scope
    val rootScope: Scope
    val types: TypeContext
    var idContext: IdContext;

    fun enterScope(owner: Scope)
    fun enterScope()
    fun leaveScope()
    fun <T> withScope(scope: Scope, block: () -> T): T
    fun enterRootScope(scope: GlobalScope)
    fun leaveRootScope()

    fun <T : ASTNode> findAnalyzer(node: T): NodeAnalyzer<T>
    fun <T : ASTNode> analyze(node: T, scope: Scope): ASTNode {
        return findAnalyzer(node).analyze(node, this);
    }

    fun warn(message: String, node: ASTNode? = null, location: SourceLocation? = null)
    fun error(message: String, node: ASTNode? = null, location: SourceLocation? = null)
    fun error(failure: OpResult.Failure, astNode: ASTNode? = null, location: SourceLocation? = null)
    fun isSameType(first: SemanticType, second: SemanticType): Boolean
    fun isSameOverloadFun(first: SemanticType.Function, second: SemanticType.Function): Boolean
    fun resolveSymbols(node: IdentifierNode, currentScope: Scope, processedOnly: Boolean = true): OpResult<DeclSymbol>
    fun resolveSymbolsLocal(node: IdentifierNode, currentScope: Scope, processedOnly: Boolean = true): OpResult<DeclSymbol>;
    fun resolveSymbolsLocal(name: String, currentScope: Scope, processedOnly: Boolean = true): OpResult<DeclSymbol>;
    fun resolveSymbolsUnqualified(node: String, currentScope: Scope, processedOnly: Boolean = true): OpResult<DeclSymbol>
    fun resolveType(typeNode: TypeNode, currentScope: Scope, deduceType: SemanticType? = null, isByValue: Boolean = true): OpResult<SemanticType>;
    fun findBinaryOverload(firstOp: TypeNode, secondOp: TypeNode, operator: Operator): DeclSymbol.FunctionDecl?
    fun resolveOpOverloads(scope: Scope, op: Operator, isBinary: Boolean, leftOperand: ExpressionInfo, rightOperand: ExpressionInfo? = null): Set<ViableCandidate<DeclSymbol.FunctionDecl>>;
    fun resolveOpOverloads(scope: Scope, op: String, isBinary: Boolean, leftOperand: ExpressionInfo, rightOperand: ExpressionInfo?): Set<ViableCandidate<DeclSymbol.FunctionDecl>>;
    fun buildConversionSeq(base: ExpressionNode, seq: ConversionSequence): ExpressionNode;
    fun findImplicitCastSeq(fromType: SemanticType, fromVC: ValueCategory, toType: SemanticType, toVC: ValueCategory, isNullPointerConstant: Boolean): Collection<ConversionSequence>
    fun getRefValueCategory(returnType: SemanticType): ValueCategory;
    fun getUnderlyingTypeForADL(type: SemanticType): SemanticType
    fun buildConversion(fromExpr: ExpressionNode, to: SemanticType, toVC: ValueCategory): OpResult<ExpressionNode>;
    fun probeCallArgs(declParams: List<SemanticType>, defaultCount: Int, inParams: List<ExpressionInfo>, location: SourceLocation?, breakOnMiss: Boolean): List<OpResult<ConversionSequence>>;
    fun collectADLOverloads(name: String, vararg operands: ExpressionInfo?): List<DeclSymbol.FunctionDecl>;
    fun <T : DeclSymbol.FunctionDecl> findBestMatch(decls: Collection<T>, inParams: List<ExpressionInfo>): Set<ViableCandidate<T>>;
    fun getNextAnonId(): Int {
        return anonymousIdCounter++;
    }

    fun getAnonClassName() = "<anonymous_class_${getNextAnonId()}>"
    fun getAnonNamespaceName() = "<anonymous_ns_${getNextAnonId()}>"


    fun isNullPointerConstant(expr: ExpressionNode): Boolean {
        val type = (expr.resolvedType as? SemanticType.Primitive) ?: return false

        return if (type.kind == PrimitiveTypeKind.NULLPTR) {
            true
        } else ((expr.evaluated as? BigInteger)?.signum() == 0)
    }
}