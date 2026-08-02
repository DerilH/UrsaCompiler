package org.derilh.ast

import org.derilh.core.Keyword
import org.derilh.core.Operator
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

enum class PrimitiveTypeKind {
    VOID, BOOL, CHAR, INT, FLOAT, DOUBLE
}

data class PrimitiveTypeNode(
    val kind: PrimitiveTypeKind,
    val isUnsigned: Boolean = false,
    val isShort: Boolean = false,
    val isLong: Boolean = false,
    val isLongLong: Boolean = false,
    var isConst: Boolean = false
) : TypeNode();

data class DeclaredTypeNode(val id: IdentifierNode) : TypeNode() {
    override val children: List<ASTNode> get() = listOf(id)
}

class AutoTypeNode : TypeNode() {
    override val children: List<ASTNode> get() = emptyList()
}

//class DeclTypeNode() : ASTNode {
//    override val children: List<ASTNode> get() = listOf(type)
//}

abstract class TypeNode : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
}

class PointerTypeNode(val type: TypeNode, val isConst: Boolean) : TypeNode() {
    override val children: List<ASTNode> get() = listOf(type)
}

class ReferenceTypeNode(val type: TypeNode, val isConst: Boolean) : TypeNode() {
    override val children: List<ASTNode> get() = listOf(type)
}

class RValueReferenceTypeNode(val type: TypeNode) : TypeNode() {
    override val children: List<ASTNode> get() = listOf(type)
}

class FunctionTypeNode(val returnType: TypeNode, val params: List<TypeNode>, ) : TypeNode() {}

class ArrayTypeNode(val elementType: TypeNode, val size: ExpressionNode?) : TypeNode() {
    override val children: List<ASTNode> get() = listOf(elementType) + (size?.let { listOf(it) } ?: emptyList())
}

class ArrayAccessNode(val operand: ExpressionNode, val index: ExpressionNode? = null) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(operand, index)
}

data class ParameterNode(
    val name: IdentifierNode,
    val type: TypeNode
) : ASTNode {
    override val children: List<ASTNode> get() = listOf(name, type)
}

data class CompoundStatementNode(
    val statements: List<ASTNode>
) : StatementNode() {
    override val children: List<ASTNode> get() = statements
}

open class DeclarationNode() : StatementNode() {}

data class MethodDeclarationNode(
    val returnType: TypeNode,
    val name: IdentifierNode,
    val params: CompoundStatementNode,
    val body: StatementNode
) : DeclarationNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(returnType) + name + params + body
}


open class ExpressionNode : ASTNode {
    override val children: List<ASTNode> get() = emptyList()
}

object EmptyExpressionNode : ExpressionNode()

open class ValueExpressionNode<T>(val value: T) : ExpressionNode() {
}

class BooleanExpressionNode(value: Boolean) : ValueExpressionNode<Boolean>(value) {
    override fun toString(): String {
        return "BooleanExpressionNode('$value')"
    }
}

class IntExpressionNode(value: Int) : ValueExpressionNode<Int>(value) {
    override fun toString(): String {
        return "IntExpressionNode('$value')"
    }
}

class FloatExpressionNode(value: Float) : ValueExpressionNode<Float>(value) {
    override fun toString(): String {
        return "FloatExpressionNode('$value')"
    }
}

class DoubleExpressionNode(value: Double) : ValueExpressionNode<Double>(value) {
    override fun toString(): String {
        return "DoubleExpressionNode('$value')"
    }
}

class LiteralExpressionNode(value: String) : ValueExpressionNode<String>(value) {
    override fun toString(): String {
        return "LiteralExpressionNode('$value')"
    }
}

class CharExpressionNode(value: Char) : ValueExpressionNode<Char>(value) {
    override fun toString(): String {
        return "CharExpressionNode('$value')"
    }
}

class ThisExpressionNode : ExpressionNode()

class NewExpressionNode(
    val placementArgs: List<ExpressionNode>,
    var type: TypeNode,
    val initializerList: InitializerListExpressionNode?
) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = placementArgs + type + listOfNotNull(initializerList)
}

class SizeofExpressionNode(val expression: ASTNode) : ExpressionNode() {
    override val children: List<ASTNode>
        get() = listOf(expression)
}

class IdExpressionNode(val id: IdentifierNode) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(id)

    override fun toString(): String {
        return "IdExpressionNode('$id')"
    }
}

class CallExpressionNode(
    val callable: ExpressionNode,
    val arguments: ArgumentsExpressionNode
) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(callable) + arguments
}

class ArgumentsExpressionNode(
    val arguments: List<ExpressionNode>
) : ExpressionNode() {
    override val children: List<ASTNode> get() = arguments
}

class InitializerListExpressionNode(
    val arguments: List<ExpressionNode>
) : ExpressionNode() {
    override val children: List<ASTNode> get() = arguments
}

class VariableDeclarationNode(
    val baseType: TypeNode,
    val declarations: List<ExpressionNode>
) : DeclarationNode() {
    override val children: List<ASTNode> get() = listOf(baseType) + declarations
}

class DeclaratorNode(val type: TypeNode, val identifier: IdentifierNode, val initializer: ExpressionNode?) : ASTNode {
    override val children: List<ASTNode>
        get() = listOfNotNull(type, identifier, initializer)
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

class BreakStatementNode : StatementNode();
class ContinueStatementNode : StatementNode();

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
}

data class UnaryExpressionNode(
    val operand: ExpressionNode,
    val operator: Operator,
    val isPrefix: Boolean
) : ExpressionNode() {
    override val children: List<ASTNode> get() = listOf(operand)
}

data class StructDeclarationNode(
    val name: IdentifierNode?,
    val members: CompoundStatementNode
) : DeclarationNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(name) + members
}

data class ClassDeclarationNode(
    val name: IdentifierNode?,
    val members: CompoundStatementNode
) : DeclarationNode() {
    override val children: List<ASTNode>
        get() = listOfNotNull(name) + members
}

data class AccessDeclarationNode(
    val access: Keyword
) : DeclarationNode() {
}

data class RootNode(
    val declarations: List<ASTNode>
) : ASTNode {
    override val children: List<ASTNode> get() = declarations
}