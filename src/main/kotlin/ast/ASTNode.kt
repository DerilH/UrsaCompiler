package org.derilh.ast

import org.derilh.analyzer.DeclSymbol
import org.derilh.core.ConversionKind
import org.derilh.core.CastMethod
import org.derilh.core.CharPrefix
import org.derilh.core.ClassType
import org.derilh.core.Keyword
import org.derilh.core.MethodQualifiers
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.Radix
import org.derilh.core.ValueCategory
import org.derilh.lexer.SourceLocation
import org.derilh.semantic.SemanticType
import org.derilh.util.Util
import java.math.BigInteger
import kotlin.collections.count
import kotlin.collections.plus

sealed interface ASTNode {
    val children: List<ASTNode>
    val location: SourceLocation?
}

data class QualifiedIdentifierNode(
    val qualifiers: List<IdentifierNode>,
    override val name: String,
    val isGlobal: Boolean,
    override val location: SourceLocation?
) : IdentifierNode(name, location) {
    override val children: List<ASTNode> get() = qualifiers
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QualifiedIdentifierNode) return false

        if (name != other.name) return false
        if (qualifiers != other.qualifiers) return false
        if (isGlobal != other.isGlobal) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + qualifiers.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isGlobal.hashCode()
        return result
    }

    override fun toDisplayString(): String {
        return qualifiers.joinToString { it.toDisplayString() } + "::" + name
    }
}

open class IdentifierNode(open val name: String, override val location: SourceLocation?) : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
    override fun toString(): String {
        return "IdentifierNode('$name')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentifierNode) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    open fun toDisplayString(): String {
        return name
    }
}


class PrimitiveTypeNode(
    val kind: PrimitiveTypeKind,
    isConst: Boolean = false,
    isVolatile: Boolean = false,
    override val location: SourceLocation?
) : TypeNode(isConst, isVolatile) {
    override fun toString(): String {
        return "PrimitiveTypeNode(kind=$kind, isConst=$isConst, isVolatile=$isVolatile)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrimitiveTypeNode) return false


        if (kind !== other.kind) return false;
        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false
        return true
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class DeclaredTypeNode(val typeName: IdentifierNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(
    isConst,
    isVolatile
) {
    var classDeclaration: DeclSymbol.ClassDecl? = null

    override fun toString(): String =
        "DeclaredTypeNode(name=${classDeclaration?.name}, isConst=$isConst, isVolatile=$isVolatile)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeclaredTypeNode) return false

        if (classDeclaration !== other.classDeclaration) return false;
        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false
        return true
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(classDeclaration)
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class AutoTypeNode(isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(isConst, isVolatile) {
    override val children: List<ASTNode> get() = emptyList()
    override fun toString(): String = "AutoTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
    override fun equals(other: Any?): Boolean {
        return false;
    }

    override fun hashCode(): Int = 1
}

//class DeclTypeTypeNode(isConst: Boolean, isVolatile: Boolean) : TypeNode(isConst, isVolatile) {
//    override val children: List<ASTNode> get() = emptyList()
//    override fun toString(): String = "DeclTypeTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
//}

//class DeclTypeNode() : ASTNode {
//    override val children: List<ASTNode> get() = listOf(type)
//}

sealed class TypeNode(open val isConst: Boolean, val isVolatile: Boolean) : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
    var resolvedType: SemanticType? = null
    override fun toString(): String = "${this.javaClass.simpleName}(isConst=$isConst, isVolatile=$isVolatile)"
    abstract override fun equals(other: Any?): Boolean;
    abstract override fun hashCode(): Int;
}

class MemberPointerTypeNode(val parentId: IdentifierNode, var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) :
    TypeNode(isConst, isVolatile) {
    var classDeclaration: DeclSymbol.ClassDecl? = null
    override val children: List<ASTNode> get() = listOf(parentId, type)
    override fun toString(): String =
        "MemberPointerTypeNode(parent=${parentId}, isConst=$isConst, isVolatile=$isVolatile)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemberPointerTypeNode) return false

        if (classDeclaration !== other.classDeclaration) return false;
        if (type != other.type) return false
        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false
        return true
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(classDeclaration)
        result = 31 * result + parentId.hashCode()
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class PointerTypeNode(var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(isConst, isVolatile) {
    override val children: List<ASTNode> get() = listOf(type)
    override fun toString(): String = "PointerTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointerTypeNode) return false

        if (type != other.type) return false
        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class ReferenceTypeNode(var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(isConst, isVolatile) {
    override val children: List<ASTNode> get() = listOf(type)
    override fun toString(): String = "ReferenceTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceTypeNode) return false

        if (type != other.type) return false
        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class RValueReferenceTypeNode(var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) :
    TypeNode(isConst, isVolatile) {
    override val children: List<ASTNode> get() = listOf(type)
    override fun toString(): String = "RValueReferenceTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RValueReferenceTypeNode) return false

        if (type != other.type) return false
        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class FunctionTypeNode(var returnType: TypeNode, val params: List<ParameterNode>, val qualifiers: MethodQualifiers, override val location: SourceLocation?) :
    TypeNode(qualifiers.isConst, qualifiers.isVolatile) {
    override val children: List<ASTNode>
        get() = listOfNotNull(returnType) + params

    override fun toString(): String {
        val methodQual =
            "FunctionQualifiers(isConst=$isConst, isVolatile=$isVolatile, refQualifier=${qualifiers.refQualifier}, isNoExcept=${qualifiers.isNoExcept})"
        return "FunctionTypeNode(qualifiers=${methodQual})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FunctionTypeNode) return false

        if (returnType != other.returnType) return false
        if (params.size != other.params.size) return false
        for (i in params.indices) {
            if (params[i].type != other.params[i].type) return false
        }

        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = returnType.hashCode()
        for (param in params) {
            result = 31 * result + param.type.hashCode()
        }
        result = 31 * result + qualifiers.hashCode()
        return result
    }
}

class ArrayTypeNode(
    var elementType: TypeNode, val sizeExpression: ExpressionNode?, isConst: Boolean = false,
    isVolatile: Boolean = false, override val location: SourceLocation?
) : TypeNode(isConst, isVolatile) {
    override val children: List<ASTNode>
        get() = listOf(elementType) + (sizeExpression?.let { listOf(it) } ?: emptyList())

    override fun toString(): String = "ArrayTypeNode(isConst=$isConst, isVolatile=$isVolatile)"


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArrayTypeNode) return false

        if (elementType != other.elementType) return false

        if (sizeExpression?.evaluated != other.sizeExpression?.evaluated) return false

        if (isConst != other.isConst) return false
        if (isVolatile != other.isVolatile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elementType.hashCode()
        result = 31 * result + (sizeExpression?.hashCode() ?: 0)
        result = 31 * result + isConst.hashCode()
        result = 31 * result + isVolatile.hashCode()
        return result
    }
}

class ArrayAccessNode(val operand: ExpressionNode, val index: ExpressionNode? = null, override val location: SourceLocation?) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(operand, index)

    override fun toString(): String = "ArrayAccessNode"
}

data class ImplicitCastExpressionNode(
    val kind: ConversionKind,
    var operand: ExpressionNode, override val location: SourceLocation?
) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOf(operand)

    override fun toString(): String = "ImplicitCastExpressionNode(kind=$kind) \u001B[31mSEMA_GENERATED\u001B[0m"
}

data class TypeCastExpressionNode(
    val castType: CastMethod,
    val explicit: Boolean,
    val declaratorNode: DeclaratorNode,
    var operand: ExpressionNode, override val location: SourceLocation?
) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOf(declaratorNode, operand)

    override fun toString(): String = "TypeCastExpressionNode(castType=$castType, explicit=$explicit)"
}

data class ParameterNode(val declarator: DeclaratorNode, override val location: SourceLocation?) : ASTNode {
    override val children: List<ASTNode> get() = listOfNotNull(declarator)
    val name = (declarator as? NamedDeclaratorNode)?.id
    val type = declarator.type
}

class ClassBodyNode(declarations: List<ASTNode>, override val location: SourceLocation?) : CompoundStatementNode(declarations, location)
class FunctionBodyNode(declarations: List<ASTNode>, override val location: SourceLocation?) : CompoundStatementNode(declarations,location) {
    var resolveType: SemanticType? = null
    override fun toString(): String {
        return "FunctionBodyNode"
    }
}

class AnonymousBlock(declarations: List<ASTNode>, override val location: SourceLocation?) : CompoundStatementNode(declarations, location)

open class CompoundStatementNode(
    val statements: List<ASTNode>, override val location: SourceLocation?
) : StatementNode(location) {
    override val children: List<ASTNode> get() = statements
}

sealed class DeclarationNode(override val location: SourceLocation?) : StatementNode(location)
sealed class DefinitionNode(override val location: SourceLocation?) : StatementNode(location)

class MemberInitializerNode(
    val id: IdentifierNode,
    val arguments: ArgumentsNode,
    val isBraced: Boolean, override val location: SourceLocation?
) : ASTNode {
    override val children: List<ASTNode> get() = listOf(id) + arguments
    override fun toString(): String = "MemberInitializerNode"
}

class ConstructorDeclarationNode(
    val type: FunctionTypeNode,
    override val location: SourceLocation?
) : DeclarationNode(location) {
    override val children: List<ASTNode>
        get() = listOfNotNull(type)

    val isExplicit: Boolean = false //TODO: Add explicit modifier

    override fun toString(): String = "ConstructorDeclarationNode()"
}

class ConstructorDefinitionNode(
    val type: FunctionTypeNode,
    val memberInitializers: List<MemberInitializerNode>,
    val body: StatementNode, override val location: SourceLocation?
) : DefinitionNode(location) {
    override val children: List<ASTNode>
        get() = listOfNotNull(type) + body + memberInitializers

    val isExplicit: Boolean = false //TODO: Add explicit modifier

    override fun toString(): String = "ConstructorDefinitionNode()"
}

data class FunctionDefinitionNode(
    val declarator: FunctionDeclaratorNode,
    var body: FunctionBodyNode, override val location: SourceLocation?
) : DefinitionNode(location) {
    override val children: List<ASTNode>
        get() = listOfNotNull(declarator) + body

    override fun toString(): String = "FunctionDeclarationNode(name=${declarator.id})"

    var type: FunctionTypeNode
        get() = declarator.type as FunctionTypeNode
        set(value) {
            declarator.type = value
        }

    val name = declarator.id
}


abstract class ExpressionNode(var valueCategory: ValueCategory? = null) : ASTNode {
    var resolvedType: SemanticType? = null
    var evaluated: Any? = null

    override val children: List<ASTNode> get() = emptyList()
}

object EmptyExpressionNode : ExpressionNode() {
    override val location: SourceLocation?
        get() = null
}

open class LiteralNode<T>(val value: T, valueCategory: ValueCategory, override val location: SourceLocation?) : ExpressionNode(valueCategory) {
}

class BooleanLiteralNode(value: Boolean, location: SourceLocation?) : LiteralNode<Boolean>(value, ValueCategory.PRVALUE, location) {
    override fun toString(): String {
        return "BooleanLiteralNode('$value')"
    }
}

class IntLiteralNode(
    value: BigInteger,
    val radix: Radix,
    val isUnsigned: Boolean,
    val isLong: Boolean,
    val isLongLong: Boolean,
    val isSizeT: Boolean, location: SourceLocation?
) : LiteralNode<BigInteger>(value, ValueCategory.PRVALUE, location) {
    override fun toString(): String {
        return "IntLiteralNode('$value')"
    }
}

class FloatLiteralNode(value: String, val isDouble: Boolean, val isLong: Boolean, location: SourceLocation?) :
    LiteralNode<String>(value, ValueCategory.PRVALUE, location) {
    override fun toString(): String {
        return "FloatLiteralNode('$value')"
    }
}

class StringLiteralNode(value: IntArray, var prefix: CharPrefix, location: SourceLocation?) : LiteralNode<IntArray>(value, ValueCategory.LVALUE, location) {
    override fun toString(): String {
        return "StringLiteralNode('${Util.codePointsToUtf16Filtered(value)}', prefix=$prefix)"
    }
}

class NullptrLiteralNode(location: SourceLocation?) : LiteralNode<Int>(0, ValueCategory.PRVALUE, location) {
    override fun toString(): String {
        return "NullptrLiteral"
    }
}

class CharLiteralNode(value: IntArray, val prefix: CharPrefix, var isMultiChar: Boolean = false, location: SourceLocation?) :
    LiteralNode<IntArray>(value, ValueCategory.PRVALUE, location) {
    override fun toString(): String {
        return "CharLiteralNode('${Util.codePointsToUtf16Filtered(value)}')"
    }

    var numericValue: ULong? = null;
}

class StringConcatExpressionNode(val literals: List<StringLiteralNode>, override val location: SourceLocation?) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = literals

    override fun toString(): String {
        return "StringConcatExpressionNode"
    }
}

class ThisExpressionNode(override val location: SourceLocation?) : ExpressionNode() {
    override fun toString(): String = "ThisExpressionNode"
}

class NewExpressionNode(
    val placementArgs: List<ExpressionNode>,
    val initializerList: InitializerListExpressionNode?,
    val type: TypeNode, override val location: SourceLocation?,
) : ExpressionNode() {


    override val children: List<ASTNode>
        get() = placementArgs + type + listOfNotNull(initializerList)

    override fun toString(): String = "NewExpressionNode"
}

class SizeofExpressionNode(val expression: ASTNode, override val location: SourceLocation?) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOf(expression)

    override fun toString(): String = "SizeofExpressionNode"
}

class IdExpressionNode(val id: IdentifierNode, override val location: SourceLocation?) : ExpressionNode(ValueCategory.LVALUE) {
    override val children: List<ASTNode> get() = listOf(id)
    var decl: DeclSymbol? = null;

    override fun toString(): String {
        return "IdExpressionNode('$id')"
    }
}

class CallExpressionNode(
    val callable: ExpressionNode?,
    val arguments: ArgumentsNode, override val location: SourceLocation?
) : ExpressionNode() {
    var functionDecl: DeclSymbol.FunctionDecl? = null
    override val children: List<ASTNode> get() = listOfNotNull(callable) + arguments
    override fun toString(): String = "CallExpressionNode"
}

class CommaExpressionNode(
    val arguments: List<ExpressionNode>, override val location: SourceLocation?
) : ExpressionNode() {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "CommaExpressionNode"
}

class ArgumentsNode(
    val arguments: List<ExpressionNode>, override val location: SourceLocation?
) : ASTNode {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "ArgumentsExpressionNode"
}

class InitializerListExpressionNode(
    val arguments: List<ExpressionNode>, override val location: SourceLocation?
) : ExpressionNode() {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "InitializerListExpressionNode"
}

class DeclarationSequenceNode(
    val typeSpecifier: TypeNode,
    val declarations: List<DeclaratorNode>, location: SourceLocation?
) : DeclarationNode(location) {
    override val children: List<ASTNode> get() = listOf(typeSpecifier) + declarations
    override fun toString(): String = "DeclarationSequenceNode"
}



class VariableDeclaratorNode(id: IdentifierNode, type: TypeNode, var initializer: ExpressionNode?, location: SourceLocation?) : NamedDeclaratorNode(id, type, location) {
    override val children: List<ASTNode>
        get() = super.children + listOfNotNull(initializer)

    override fun toString(): String {
        return "VariableDeclaratorNode"
    }
}
class FunctionDeclaratorNode(id: IdentifierNode, type: FunctionTypeNode, location: SourceLocation?) : NamedDeclaratorNode(id, type, location) {
    val defaultParamCount: Int
        get() {
            val type = type as FunctionTypeNode;
            return type.params.count {(it.declarator as? VariableDeclaratorNode)?.initializer != null}
        }

    override fun toString(): String {
        return "FunctionDeclaratorNode"
    }
}
sealed class NamedDeclaratorNode(val id: IdentifierNode, type: TypeNode, location: SourceLocation?) : DeclaratorNode(type, location) {
    override val children: List<ASTNode>
        get() = super.children + id
}
class AbstractDeclaratorNode(type: TypeNode, location: SourceLocation?) : DeclaratorNode(type, location)

sealed class DeclaratorNode(var type: TypeNode, override val location: SourceLocation?) : ASTNode {
    override val children: List<ASTNode>
        get() = listOfNotNull(type)
}

open class StatementNode(override val location: SourceLocation?) : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
}

object EmptyStatementNode : StatementNode(null)

data class ReturnStatementNode(
    var expression: ExpressionNode, override val location: SourceLocation?,
) : StatementNode(location) {
    override val children: List<ASTNode> get() = listOf(expression)
}

data class IfStatementNode(
    var condition: ExpressionNode,
    var body: StatementNode,
    val elseBranch: StatementNode = EmptyStatementNode, override val location: SourceLocation?
) : StatementNode(location) {
    override val children: List<ASTNode> get() = listOf(condition, body, elseBranch)
}

class BreakStatementNode(location: SourceLocation?) : StatementNode(location) {
    override fun toString(): String = "BreakStatementNode"
}

class ContinueStatementNode(location: SourceLocation?) : StatementNode(location) {
    override fun toString(): String = "ContinueStatementNode"
}

class WhileStatementNode(var condition: ASTNode, val body: StatementNode, location: SourceLocation?) : StatementNode(location) {
    override val children: List<ASTNode> get() = listOf(condition, body)
}

class DoStatementNode(val body: StatementNode, var condition: ASTNode, location: SourceLocation?) : StatementNode(location) {
    override val children: List<ASTNode> get() = listOf(body, condition)
}

class ForStatementNode(
    var initializer: List<ASTNode>,
    var condition: ExpressionNode,
    var increment: List<ExpressionNode>,
    var body: StatementNode, location: SourceLocation?
) : StatementNode(location) {
    override val children: List<ASTNode> get() = initializer + condition + increment + body
}

class BinaryExpressionNode(
    var left: ExpressionNode,
    var right: ExpressionNode,
    val operator: Operator, override val location: SourceLocation?,
) : ExpressionNode() {
    var functionDecl: DeclSymbol.FunctionDecl? = null
    override val children: List<ASTNode> get() = listOf(left, right)
    override fun toString(): String = "BinaryExpressionNode(operator='${operator.value}')"
}

data class UnaryExpressionNode(
    var operand: ExpressionNode,
    val operator: Operator,
    val isPrefix: Boolean, override val location: SourceLocation?
) : ExpressionNode() {
    /**May be builtin or user defined**/
    var functionDecl: DeclSymbol.FunctionDecl? = null
    override val children: List<ASTNode> get() = listOf(operand)
    override fun toString(): String = "UnaryExpressionNode(operator='${operator.value}', isPrefix=$isPrefix)"
}

class NamespaceDeclarationNode(
    val name: IdentifierNode?,
    val body: CompoundStatementNode, location: SourceLocation?
) : DefinitionNode(location) {
    val isAnonymous = name == null
    override val children: List<ASTNode>
        get() = listOfNotNull(name) + body
}

class ClassDeclarationNode(
    val name: IdentifierNode?,
    val type: ClassType,
    location: SourceLocation?
) : DeclarationNode(location) {

    override val children: List<ASTNode>
        get() = listOfNotNull(name)
}

class ClassDefinitionNode(
    val name: IdentifierNode?,
    val type: ClassType,
    val body: ClassBodyNode,
    location: SourceLocation?
) : DefinitionNode(location) {

    override val children: List<ASTNode>
        get() = listOfNotNull(name) + body

    override fun toString(): String {
        return "ClassDefinitionNode(type=$type)"
    }
}

class AccessSpecifierNode(
    val access: Keyword, override val location: SourceLocation?
) : ASTNode {
    override fun toString(): String = "AccessSpecifierNode(type='${access.value}')"
    override val children: List<ASTNode> = emptyList();
}

data class RootNode(
    val declarations: List<ASTNode>, override val location: SourceLocation = SourceLocation.EXPORTED
) : ASTNode {
    override val children: List<ASTNode> get() = declarations
}