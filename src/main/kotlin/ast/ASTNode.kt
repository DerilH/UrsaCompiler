package org.derilh.ast

import org.derilh.core.CharPrefix
import org.derilh.core.ClassType
import org.derilh.core.Keyword
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.Radix
import org.derilh.util.Util
import java.math.BigInteger
import kotlin.collections.plus

sealed interface ASTNode {
    val children: List<ASTNode>
}

data class IdentifierWithNamespaceNode(
    val namespace: List<IdentifierNode>,
    override val name: String,
    val isGlobal: Boolean
) : IdentifierNode(name) {
    override val children: List<ASTNode> get() = namespace
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentifierWithNamespaceNode) return false

        if (name != other.name) return false
        if (namespace != other.namespace) return false
        if (isGlobal != other.isGlobal) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode() // Учитываем хэш родительского класса
        result = 31 * result + namespace.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isGlobal.hashCode()
        return result
    }
}

open class IdentifierNode(open val name: String) : ASTNode {
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
}


class PrimitiveTypeNode(
    val kind: PrimitiveTypeKind,
    isConst: Boolean = false,
    isVolatile: Boolean = false
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

class DeclaredTypeNode(val typeName: IdentifierNode, isConst: Boolean, isVolatile: Boolean) : TypeNode(
    isConst,
    isVolatile
) {
    val classDeclaration: ClassDeclarationNode? = null

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

//class AutoTypeNode(isConst: Boolean, isVolatile: Boolean) : TypeNode(isConst, isVolatile) {
//    override val children: List<ASTNode> get() = emptyList()
//    override fun toString(): String = "AutoTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
//}
//
//class DeclTypeTypeNode(isConst: Boolean, isVolatile: Boolean) : TypeNode(isConst, isVolatile) {
//    override val children: List<ASTNode> get() = emptyList()
//    override fun toString(): String = "DeclTypeTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
//}

//class DeclTypeNode() : ASTNode {
//    override val children: List<ASTNode> get() = listOf(type)
//}

abstract class TypeNode(open val isConst: Boolean, val isVolatile: Boolean) : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
    override fun toString(): String = "${this.javaClass.simpleName}(isConst=$isConst, isVolatile=$isVolatile)"
    abstract override fun equals(other: Any?): Boolean;
    abstract override fun hashCode(): Int;
}

class MemberPointerType(val parentId: IdentifierNode, val type: TypeNode, isConst: Boolean, isVolatile: Boolean) :
    TypeNode(isConst, isVolatile) {
    var classDeclaration: ClassDeclarationNode? = null
    override val children: List<ASTNode> get() = listOf(parentId, type)
    override fun toString(): String =
        "MemberPointerTypeNode(parent=${parentId}, isConst=$isConst, isVolatile=$isVolatile)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemberPointerType) return false

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

class PointerTypeNode(val type: TypeNode, isConst: Boolean, isVolatile: Boolean) : TypeNode(isConst, isVolatile) {
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

class ReferenceTypeNode(val type: TypeNode, isConst: Boolean, isVolatile: Boolean) : TypeNode(isConst, isVolatile) {
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

class RValueReferenceTypeNode(val type: TypeNode, isConst: Boolean, isVolatile: Boolean) :
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

enum class RefQualifier {
    NONE,   //
    LVALUE, // &
    RVALUE  // &&
};

data class FunctionQualifiers(
    val isConst: Boolean,
    val isVolatile: Boolean,
    val refQualifier: RefQualifier,
    val isNoExcept: Boolean
) {
    override fun toString(): String {
        return "FunctionQualifiers(isConst=$isConst, isVolatile=$isVolatile, refQualifier=$refQualifier, isNoExcept=$isNoExcept)"
    }
}

class FunctionTypeNode(val returnType: TypeNode, val params: List<ParameterNode>, val qualifiers: FunctionQualifiers) :
    TypeNode(qualifiers.isConst, qualifiers.isVolatile) {
    override val children: List<ASTNode>
        get() = listOfNotNull(returnType) + params

    override fun toString(): String = "FunctionTypeNode(qualifiers=${qualifiers})"

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
    val elementType: TypeNode, val sizeExpression: ExpressionNode?, isConst: Boolean = false,
    isVolatile: Boolean = false
) : TypeNode(isConst, isVolatile) {
    override val children: List<ASTNode> get() = listOf(elementType) + (sizeExpression?.let { listOf(it) } ?: emptyList())
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

class ArrayAccessNode(val operand: ExpressionNode, val index: ExpressionNode? = null) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(operand, index)

    override fun toString(): String = "ArrayAccessNode"
}


enum class CastType {
    CSTYLE,
    FUNCTIONAL
}

data class TypeCastExpressionNode(
    val castType: CastType,
    val explicit: Boolean,
    val declaratorNode: DeclaratorNode,
    val operand: ExpressionNode
) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOf(declaratorNode, operand)

    override fun toString(): String = "TypeCastExpressionNode(castType=$castType, explicit=$explicit)"
}

data class ParameterNode(val declarator: DeclaratorNode) : ASTNode {
    override val children: List<ASTNode> get() = listOfNotNull(declarator)
    val name = declarator.id
    val type = declarator.type
}

data class CompoundStatementNode(
    val statements: List<ASTNode>
) : StatementNode() {
    override val children: List<ASTNode> get() = statements
}

open class DeclarationNode() : StatementNode() {}

class MemberInitializerNode(
    val id: IdentifierNode,
    val arguments: ArgumentsNode,
    val isBraced: Boolean
) : ASTNode {
    override val children: List<ASTNode> get() = listOf(id) + arguments
    override fun toString(): String = "MemberInitializerNode"
}

data class ConstructorDeclarationNode(
    val type: FunctionTypeNode,
    val memberInitializers: List<MemberInitializerNode>,
    val body: StatementNode
) : DeclarationNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(type) + body + memberInitializers

    override fun toString(): String = "ConstructorDeclarationNode()"
}

data class FunctionDeclarationNode(
    val declarator: DeclaratorNode,
    val body: StatementNode
) : DeclarationNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(declarator) + body

    override fun toString(): String = "FunctionDeclarationNode(name=${declarator.id})"

    val type = declarator.type as FunctionTypeNode
    val name = declarator.id!!
}


abstract class ExpressionNode(var type: TypeNode? = null) : ASTNode {
    var evaluated: Any? = null
    override val children: List<ASTNode> get() = emptyList()
}

object EmptyExpressionNode : ExpressionNode()

open class LiteralNode<T>(val value: T, type: TypeNode?) : ExpressionNode(type) {
}

class BooleanLiteralNode(value: Boolean) : LiteralNode<Boolean>(value, null) {
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
    val isSizeT: Boolean
) : LiteralNode<BigInteger>(value, null) {
    override fun toString(): String {
        return "IntLiteralNode('$value')"
    }

    companion object {
        fun constantValue(value: Int): IntLiteralNode {
            return IntLiteralNode(
                BigInteger.valueOf(value.toLong()),
                Radix.DECIMAL,
                false,
                false,
                false,
                false
            ).apply { type = PrimitiveTypeNode(PrimitiveTypeKind.INT) }
        }
    }
}

class FloatLiteralNode(value: String, val isDouble: Boolean, val isLong: Boolean) :
    LiteralNode<String>(value, PrimitiveTypeNode(PrimitiveTypeKind.FLOAT)) {
    override fun toString(): String {
        return "FloatLiteralNode('$value')"
    }
}

class StringLiteralNode(value: IntArray, var prefix: CharPrefix) : LiteralNode<IntArray>(value, null) {
    override fun toString(): String {
        return "StringLiteralNode('${Util.codePointsToUtf16Filtered(value)}', prefix=$prefix)"
    }
}

class CharLiteralNode(value: IntArray, val prefix: CharPrefix, var isMultiChar: Boolean = false) :
    LiteralNode<IntArray>(value, null) {
    override fun toString(): String {
        return "CharLiteralNode('${Util.codePointsToUtf16Filtered(value)}')"
    }

    var numericValue: ULong? = null;
}

class StringConcatExpressionNode(val literals: List<StringLiteralNode>) : ExpressionNode(null) {
    override val children: List<ASTNode>
        get() = literals

    override fun toString(): String {
        return "StringConcatExpressionNode"
    }
}

class ThisExpressionNode : ExpressionNode() {
    override fun toString(): String = "ThisExpressionNode"
}

class NewExpressionNode(
    val placementArgs: List<ExpressionNode>,
    val initializerList: InitializerListExpressionNode?,
    type: TypeNode,
) : ExpressionNode(type) {
    override val children: List<ASTNode>
        get() = placementArgs + type!! + listOfNotNull(initializerList)

    override fun toString(): String = "NewExpressionNode"
}

class SizeofExpressionNode(val expression: ASTNode) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOf(expression)

    override fun toString(): String = "SizeofExpressionNode"
}

class IdExpressionNode(val id: IdentifierNode) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(id)

    override fun toString(): String {
        return "IdExpressionNode('$id')"
    }
}

class CallExpressionNode(
    val callable: ExpressionNode,
    val arguments: ArgumentsNode
) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(callable) + arguments
    override fun toString(): String = "CallExpressionNode"
}

class CommaExpressionNode(
    val arguments: List<ExpressionNode>
) : ExpressionNode() {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "CommaExpressionNode"
}

class ArgumentsNode(
    val arguments: List<ExpressionNode>
) : ASTNode {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "ArgumentsExpressionNode"
}

class InitializerListExpressionNode(
    val arguments: List<ExpressionNode>
) : ExpressionNode() {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "InitializerListExpressionNode"
}

class VariableDeclarationNode(
    val baseType: TypeNode,
    val declarations: List<DeclaratorNode>
) : DeclarationNode() {
    override val children: List<ASTNode> get() = listOf(baseType) + declarations
    override fun toString(): String = "VariableDeclarationNode"
}

class DeclaratorNode(val type: TypeNode, val id: IdentifierNode? = null, val initializer: ExpressionNode? = null) :
    ASTNode {
    override val children: List<ASTNode>
        get() = listOfNotNull(type, id, initializer)

    override fun toString(): String = "DeclaratorNode"
}

open class StatementNode : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
}

object EmptyStatementNode : StatementNode()

data class ReturnStatementNode(
    val expression: ExpressionNode,
) : StatementNode() {
    override val children: List<ASTNode> get() = listOf(expression)
}

data class IfStatementNode(
    var condition: ExpressionNode,
    var body: StatementNode,
    val elseBranch: StatementNode = EmptyStatementNode
) : StatementNode() {
    override val children: List<ASTNode> get() = listOf(condition, body, elseBranch)
}

class BreakStatementNode : StatementNode() {
    override fun toString(): String = "BreakStatementNode"
}

class ContinueStatementNode : StatementNode() {
    override fun toString(): String = "ContinueStatementNode"
}

data class WhileStatementNode(var condition: ASTNode, val body: StatementNode) : StatementNode() {
    override val children: List<ASTNode> get() = listOf(condition, body)
}

data class DoStatementNode(val body: StatementNode, var condition: ASTNode) : StatementNode() {
    override val children: List<ASTNode> get() = listOf(body, condition)
}

data class ForStatementNode(
    var initializer: List<ASTNode>,
    var condition: ExpressionNode,
    var increment: List<ExpressionNode>,
    var body: StatementNode
) : StatementNode() {
    override val children: List<ASTNode> get() = initializer + condition + increment + body
}

data class BinaryExpressionNode(
    val left: ExpressionNode,
    val right: ExpressionNode,
    val operator: Operator,
) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(left, right)
    override fun toString(): String = "BinaryExpressionNode(operator='${operator.value}')"
}

data class UnaryExpressionNode(
    val operand: ExpressionNode,
    val operator: Operator,
    val isPrefix: Boolean
) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(operand)
    override fun toString(): String = "UnaryExpressionNode(operator='${operator.value}', isPrefix=$isPrefix)"
}

data class NamespaceDeclarationNode(
    val name: IdentifierNode?,
    val members: CompoundStatementNode
) : DeclarationNode() {
    val isAnonymous = name == null
    override val children: List<ASTNode>
        get() = listOfNotNull(name) + members
}

data class ClassDeclarationNode(
    val name: IdentifierNode?,
    val type: ClassType,
    val members: CompoundStatementNode
) : DeclarationNode() {

    override val children: List<ASTNode>
        get() = listOfNotNull(name) + members
}

data class AccessDeclarationNode(
    val access: Keyword
) : DeclarationNode() {
    override fun toString(): String = "AccessDeclarationNode(access='${access.value}')"
}

data class RootNode(
    val declarations: List<ASTNode>
) : ASTNode {
    override val children: List<ASTNode> get() = declarations
}