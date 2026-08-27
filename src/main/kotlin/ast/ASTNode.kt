package org.derilh.ast

import org.derilh.ast.api.*
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.GlobalScope
import org.derilh.analyzer.Scope
import org.derilh.core.AccessSpecifier
import org.derilh.core.ConversionKind
import org.derilh.core.CastMethod
import org.derilh.core.CharPrefix
import org.derilh.core.ClassType
import org.derilh.core.FunctionQualifiers
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.Radix
import org.derilh.core.SourceLocation
import org.derilh.core.ValueCategory
import org.derilh.semantic.SemanticType
import org.derilh.util.Util
import java.math.BigInteger
import kotlin.collections.count
import kotlin.collections.plus

interface IReturnableNode {
    var returnStatements: List<ReturnStatementNode>?
}

sealed interface ASTNode : IASTNode {
    override val children: List<ASTNode>
    override val location: SourceLocation?
}

data class QualifiedIdentifierNode(
    override val qualifiers: List<IdentifierNode>,
    override val name: String,
    override val isGlobal: Boolean,
    override val location: SourceLocation?
) : IdentifierNode(name, location), IQualifiedIdentifierNode {
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

open class IdentifierNode(override val name: String, override val location: SourceLocation?) : ASTNode, IIdentifierNode {
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
    override val kind: PrimitiveTypeKind,
    isConst: Boolean = false,
    isVolatile: Boolean = false,
    override val location: SourceLocation?
) : TypeNode(isConst, isVolatile), IPrimitiveTypeNode {
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

    override fun toDisplayString(): String {
        return "${qualifiersPrefix()}${kind}"
    }
}

class DeclaredTypeNode(override val typeName: IdentifierNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(
    isConst,
    isVolatile
), IDeclaredTypeNode {
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

    override fun toDisplayString(): String {
        return "${qualifiersPrefix()}${typeName.toDisplayString()}"
    }
}

class AutoTypeNode(isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(isConst, isVolatile), IAutoTypeNode {
    override val children: List<ASTNode> get() = emptyList()
    override fun toString(): String = "AutoTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
    override fun equals(other: Any?): Boolean {
        return false;
    }

    override fun hashCode(): Int = 1

    override fun toDisplayString(): String {
        return "${qualifiersPrefix()}auto"
    }
}

//class DeclTypeTypeNode(isConst: Boolean, isVolatile: Boolean) : TypeNode(isConst, isVolatile) {
//    override val children: List<ASTNode> get() = emptyList()
//    override fun toString(): String = "DeclTypeTypeNode(isConst=$isConst, isVolatile=$isVolatile)"
//}

//class DeclTypeNode() : ASTNode {
//    override val children: List<ASTNode> get() = listOf(type)
//}

sealed class TypeNode(override val isConst: Boolean, override val isVolatile: Boolean) : ASTNode, ITypeNode {
    override val children: List<ASTNode> get() = emptyList()
    var resolvedType: SemanticType? = null
    override fun toString(): String = "${this.javaClass.simpleName}(isConst=$isConst, isVolatile=$isVolatile)"
    abstract override fun equals(other: Any?): Boolean;
    abstract override fun hashCode(): Int;

    protected fun qualifiersPrefix(): String {
        val list = mutableListOf<String>()
        if (isConst) list.add("const")
        if (isVolatile) list.add("volatile")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }

    abstract override fun toDisplayString(): String
}

class MemberPointerTypeNode(override val parentId: IdentifierNode, override var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) :
    TypeNode(isConst, isVolatile), IMemberPointerTypeNode {
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

    override fun toDisplayString(): String {
        return "${qualifiersPrefix()}${parentId.toDisplayString()}::* ${type.toDisplayString()}"
    }
}

class PointerTypeNode(override var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(isConst, isVolatile), IPointerTypeNode {
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

    override fun toDisplayString(): String {
        return "${qualifiersPrefix()}${type.toDisplayString()}*"
    }
}

class ReferenceTypeNode(override var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) : TypeNode(isConst, isVolatile), IReferenceTypeNode {
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

    override fun toDisplayString(): String {
        return "${type.toDisplayString()}&"
    }
}

class RValueReferenceTypeNode(override var type: TypeNode, isConst: Boolean, isVolatile: Boolean, override val location: SourceLocation?) :
    TypeNode(isConst, isVolatile), IRValueReferenceTypeNode {
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

    override fun toDisplayString(): String {
        return "${type.toDisplayString()}&&"
    }
}

class FunctionTypeNode(override var returnType: TypeNode, override val params: List<ParameterNode>, override val qualifiers: FunctionQualifiers, override val location: SourceLocation?) :
    TypeNode(qualifiers.isConst, qualifiers.isVolatile), IFunctionTypeNode {
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

    override fun toDisplayString(): String {
        val paramsStr = params.joinToString(", ") { it.type.toDisplayString() }
        val qualifiersStr = StringBuilder()
        if (qualifiers.isConst) qualifiersStr.append(" const")
        if (qualifiers.isVolatile) qualifiersStr.append(" volatile")
        when (qualifiers.refQualifier) {
            org.derilh.core.RefQualifier.LVALUE -> qualifiersStr.append(" &")
            org.derilh.core.RefQualifier.RVALUE -> qualifiersStr.append(" &&")
            else -> {}
        }
        return "${returnType.toDisplayString()}($paramsStr)$qualifiersStr"
    }
}

class ArrayTypeNode(
    override var elementType: TypeNode, override val sizeExpression: ExpressionNode?, isConst: Boolean = false,
    isVolatile: Boolean = false, override val location: SourceLocation?
) : TypeNode(isConst, isVolatile), IArrayTypeNode {
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

    override fun toDisplayString(): String {
        val size = sizeExpression?.let { "[$it]" } ?: "[]"
        return "${qualifiersPrefix()}${elementType.toDisplayString()}$size"
    }
}

class ArrayAccessNode(override val operand: ExpressionNode, override val index: ExpressionNode? = null, override val location: SourceLocation?) : ExpressionNode(), IArrayAccessNode {
    override val children: List<ASTNode>
        get() = listOfNotNull(operand, index)

    override fun toString(): String = "ArrayAccessNode"
}

data class ImplicitCastExpressionNode(
    override val kind: ConversionKind,
    override var operand: ExpressionNode, override val location: SourceLocation?
) : ExpressionNode(), IImplicitCastExpressionNode {
    override val children: List<ASTNode>
        get() = listOf(operand)

    override fun toString(): String = "ImplicitCastExpressionNode(kind=$kind) \u001B[31mSEMA_GENERATED\u001B[0m"
}

data class TypeCastExpressionNode(
    override val castType: CastMethod,
    override val explicit: Boolean,
    override val declaratorNode: DeclaratorNode,
    override var operand: ExpressionNode, override val location: SourceLocation?
) : ExpressionNode(), ITypeCastExpressionNode {
    override val children: List<ASTNode>
        get() = listOf(declaratorNode, operand)

    override fun toString(): String = "TypeCastExpressionNode(castType=$castType, explicit=$explicit)"
}

data class ParameterNode(override val declarator: DeclaratorNode, override val location: SourceLocation?) : ASTNode, IParameterNode {
    override val children: List<ASTNode> get() = listOfNotNull(declarator)
    override val name = (declarator as? NamedDeclaratorNode)?.id
    override val type = declarator.type
    override var hasDefaultValue: Boolean = false;
}

class ClassBodyNode(override val declarations: List<ASTNode>, override val location: SourceLocation?) : ASTNode, IClassBodyNode {
    override val children: List<ASTNode> get() = declarations
    override fun toString(): String = "ClassBodyNode"
}

class NamespaceBodyNode(override val declarations: List<ASTNode>, override val location: SourceLocation?) : ASTNode, INamespaceBodyNode {
    override val children: List<ASTNode> get() = declarations
    override fun toString(): String = "ClassBodyNode"
}

class FunctionBodyNode(declarations: List<ASTNode>, override val location: SourceLocation?) : CompoundStatementNode(declarations, location), IFunctionBodyNode {
    override fun toString(): String {
        return "FunctionBodyNode"
    }
}

open class CompoundStatementNode(
    override val statements: List<ASTNode>,
    override val location: SourceLocation?
) : StatementNode(location), ICompoundStatementNode, IReturnableNode {
    override val children: List<ASTNode> get() = statements
    override var returnStatements: List<ReturnStatementNode>? = null
    override fun toString(): String = "CompoundStatementNode"
}

sealed class DeclarationNode(override val location: SourceLocation?) : StatementNode(location), IDeclarationNode
sealed class DefinitionNode(override val location: SourceLocation?) : StatementNode(location), IDefinitionNode

class MemberInitializerNode(
    override val id: IdentifierNode,
    override val arguments: ArgumentsNode,
    override val isBraced: Boolean, override val location: SourceLocation?
) : ASTNode, IMemberInitializerNode {
    override val children: List<ASTNode> get() = listOf(id) + arguments
    override fun toString(): String = "MemberInitializerNode"
}

class ConstructorDeclarationNode(
    override val type: FunctionTypeNode,
    override val location: SourceLocation?
) : DeclarationNode(location), IConstructorDeclarationNode {
    var overloadSet: DeclSymbol.FunctionOverloadSet? = null;
    lateinit var ctorDecl: DeclSymbol.ConstructorDecl;

    override val children: List<ASTNode>
        get() = listOfNotNull(type)
    override val defaultParamCount: Int
        get() {
            val type = type;
            return type.params.count { (it.declarator as? VariableDeclaratorNode)?.initializer != null }
        }

    override val isExplicit: Boolean = false //TODO: Add explicit modifier

    override fun toString(): String = "ConstructorDeclarationNode()"
}

class ConstructorDefinitionNode(
    override val type: FunctionTypeNode,
    override val memberInitializers: List<MemberInitializerNode>,
    override var body: StatementNode, override val location: SourceLocation?
) : DefinitionNode(location), IConstructorDefinitionNode {
    var overloadSet: DeclSymbol.FunctionOverloadSet? = null;
    lateinit var ctorDecl: DeclSymbol.ConstructorDecl;

    override val children: List<ASTNode>
        get() = listOfNotNull(type) + body + memberInitializers

    override val isExplicit: Boolean = false //TODO: Add explicit modifier
    override val defaultParamCount: Int
        get() {
            val type = type;
            return type.params.count { (it.declarator as? VariableDeclaratorNode)?.initializer != null }
        }

    override fun toString(): String = "ConstructorDefinitionNode()"
}

data class FunctionDefinitionNode(
    override val declarator: FunctionDeclaratorNode,
    override var body: FunctionBodyNode, override val location: SourceLocation?
) : DefinitionNode(location), IFunctionDefinitionNode {
    var overloadSet: DeclSymbol.FunctionOverloadSet? = null;
    lateinit var functionDecl: DeclSymbol.FunctionDecl;
    override val children: List<ASTNode>
        get() = listOfNotNull(declarator) + body

    override fun toString(): String = "FunctionDeclarationNode(name=${declarator.id})"

    var type: FunctionTypeNode
        get() = declarator.type as FunctionTypeNode
        set(value) {
            declarator.type = value
        }

    override val name = declarator.id
}


abstract class ExpressionNode(override var valueCategory: ValueCategory? = null) : ASTNode, IExpressionNode {
    var resolvedType: SemanticType? = null
    var evaluated: Any? = null

    override val children: List<ASTNode> get() = emptyList()
}

class RecoveryExpressionNode(override val location: SourceLocation?) : ExpressionNode() {
    override fun toString(): String = "RecoveryExpression"
}

object EmptyExpressionNode : ExpressionNode() {
    override val location: SourceLocation?
        get() = null
}

open class LiteralNode<T>(override val value: T, valueCategory: ValueCategory, override val location: SourceLocation?) : ExpressionNode(valueCategory), ILiteralNode<T> {
}

class BooleanLiteralNode(value: Boolean, location: SourceLocation?) : LiteralNode<Boolean>(value, ValueCategory.PRVALUE, location), IBooleanLiteralNode {
    override fun toString(): String {
        return "BooleanLiteralNode('$value')"
    }
}

class IntLiteralNode(
    value: BigInteger,
    override val radix: Radix,
    override val isUnsigned: Boolean,
    override val isLong: Boolean,
    override val isLongLong: Boolean,
    override val isSizeT: Boolean, location: SourceLocation?
) : LiteralNode<BigInteger>(value, ValueCategory.PRVALUE, location), IIntLiteralNode {
    override fun toString(): String {
        return "IntLiteralNode('$value')"
    }
}

class FloatLiteralNode(value: String, override val isDouble: Boolean, override val isLong: Boolean, location: SourceLocation?) :
    LiteralNode<String>(value, ValueCategory.PRVALUE, location), IFloatLiteralNode {
    override fun toString(): String {
        return "FloatLiteralNode('$value')"
    }
}

class StringLiteralNode(value: IntArray, override var prefix: CharPrefix, location: SourceLocation?) : LiteralNode<IntArray>(value, ValueCategory.LVALUE, location), IStringLiteralNode {
    override fun toString(): String {
        return "StringLiteralNode('${Util.codePointsToUtf16Filtered(value)}', prefix=$prefix)"
    }
}

class NullptrLiteralNode(location: SourceLocation?) : LiteralNode<Int>(0, ValueCategory.PRVALUE, location), INullptrLiteralNode {
    override fun toString(): String {
        return "NullptrLiteral"
    }
}

class CharLiteralNode(value: IntArray, override val prefix: CharPrefix, override var isMultiChar: Boolean = false, location: SourceLocation?) :
    LiteralNode<IntArray>(value, ValueCategory.PRVALUE, location), ICharLiteralNode {
    override fun toString(): String {
        return "CharLiteralNode('${Util.codePointsToUtf16Filtered(value)}')"
    }

    var numericValue: ULong? = null;
}

class StringConcatExpressionNode(override val literals: List<StringLiteralNode>, override val location: SourceLocation?) : ExpressionNode(), IStringConcatExpressionNode {
    override val children: List<ASTNode>
        get() = literals

    override fun toString(): String {
        return "StringConcatExpressionNode"
    }
}

class ThisExpressionNode(override val location: SourceLocation?) : ExpressionNode(), IThisExpressionNode {
    override fun toString(): String = "ThisExpressionNode"
}

class NewExpressionNode(
    override val placementArgs: List<ExpressionNode>,
    override val initializerList: InitializerListExpressionNode?,
    override val type: TypeNode, override val location: SourceLocation?,
) : ExpressionNode(), INewExpressionNode {


    override val children: List<ASTNode>
        get() = placementArgs + type + listOfNotNull(initializerList)

    override fun toString(): String = "NewExpressionNode"
}

class SizeofExpressionNode(override val expression: ASTNode, override val location: SourceLocation?) : ExpressionNode(), ISizeofExpressionNode {
    override val children: List<ASTNode>
        get() = listOf(expression)

    override fun toString(): String = "SizeofExpressionNode"
}

class IdExpressionNode(override val id: IdentifierNode, override val location: SourceLocation?) : ExpressionNode(ValueCategory.LVALUE), IIdExpressionNode {
    override val children: List<ASTNode> get() = listOf(id)
    var decl: DeclSymbol? = null;

    override fun toString(): String {
        return "IdExpressionNode('$id')"
    }
}

class CallExpressionNode(
    override val callable: ExpressionNode?,
    override val arguments: ArgumentsNode, override val location: SourceLocation?
) : ExpressionNode(), ICallExpressionNode {
    var functionDecl: DeclSymbol.FunctionDecl? = null
    override val children: List<ASTNode> get() = listOfNotNull(callable) + arguments
    override fun toString(): String = "CallExpressionNode"
}

class CommaExpressionNode(
    override val arguments: List<ExpressionNode>, override val location: SourceLocation?
) : ExpressionNode(), ICommaExpressionNode {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "CommaExpressionNode"
}

class ArgumentsNode(
    override val arguments: List<ExpressionNode>, override val location: SourceLocation?
) : ASTNode, IArgumentsNode {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "ArgumentsExpressionNode"
}

class InitializerListExpressionNode(
    override val arguments: List<ExpressionNode>, override val location: SourceLocation?
) : ExpressionNode(), IInitializerListExpressionNode {
    override val children: List<ASTNode> get() = arguments
    override fun toString(): String = "InitializerListExpressionNode"
}

class DeclarationSequenceNode(
    override val typeSpecifier: TypeNode,
    override val declarations: List<DeclaratorNode>, location: SourceLocation?
) : DeclarationNode(location), IDeclarationSequenceNode {
    override val children: List<ASTNode> get() = listOf(typeSpecifier) + declarations
    override fun toString(): String = "DeclarationSequenceNode"
}

class VariableDeclaratorNode(id: IdentifierNode, type: TypeNode, override var initializer: ExpressionNode?, location: SourceLocation?) : NamedDeclaratorNode(id, type, location), IVariableDeclaratorNode {
    lateinit var varDecl: DeclSymbol.VariableDecl;

    override val children: List<ASTNode>
        get() = super.children + listOfNotNull(initializer)

    override fun toString(): String {
        return "VariableDeclaratorNode"
    }
}

class FunctionDeclaratorNode(id: IdentifierNode, type: FunctionTypeNode, location: SourceLocation?) : NamedDeclaratorNode(id, type, location), IFunctionDeclaratorNode {
    var overloadSet: DeclSymbol.FunctionOverloadSet? = null;
    lateinit var functionDecl: DeclSymbol.FunctionDecl;
    override val defaultParamCount: Int
        get() {
            val type = type as FunctionTypeNode;
            return type.params.count { (it.declarator as? VariableDeclaratorNode)?.initializer != null }
        }

    override fun toString(): String {
        return "FunctionDeclaratorNode"
    }
}

sealed class NamedDeclaratorNode(override val id: IdentifierNode, type: TypeNode, location: SourceLocation?) : DeclaratorNode(type, location), INamedDeclaratorNode {
    override val children: List<ASTNode>
        get() = super.children + id
}

class AbstractDeclaratorNode(type: TypeNode, location: SourceLocation?) : DeclaratorNode(type, location), IAbstractDeclaratorNode

sealed class DeclaratorNode(override var type: TypeNode, override val location: SourceLocation?) : ASTNode, IDeclaratorNode {
    override val children: List<ASTNode>
        get() = listOfNotNull(type)
}

open class StatementNode(override val location: SourceLocation?) : ASTNode, IStatementNode {
    override val children: List<ASTNode> get() = emptyList()
}

class RecoveryStatementNode(location: SourceLocation?) : StatementNode(location) {
    override fun toString(): String = "RecoveryStatement"
}

object EmptyStatementNode : StatementNode(null)

data class ReturnStatementNode(
    override var expression: ExpressionNode?, override val location: SourceLocation?,
) : StatementNode(location), IReturnStatementNode {
    override val children: List<ASTNode> get() = listOfNotNull(expression)
}

data class IfStatementNode(
    override var condition: ExpressionNode,
    override var body: StatementNode,
    override var elseBody: StatementNode? = null, override val location: SourceLocation?
) : StatementNode(location), IIfStatementNode, IReturnableNode {
    lateinit var scope: Scope;
    lateinit var bodyScope: Scope;
    var elseBodyScope: Scope? = null;

    override var returnStatements: List<ReturnStatementNode>? = null
    override val children: List<ASTNode> get() = listOfNotNull(condition, body, elseBody)
}

class BreakStatementNode(location: SourceLocation?) : StatementNode(location), IBreakStatementNode {
    override fun toString(): String = "BreakStatementNode"
}

class ContinueStatementNode(location: SourceLocation?) : StatementNode(location), IContinueStatementNode {
    override fun toString(): String = "ContinueStatementNode"
}

class WhileStatementNode(override var condition: ASTNode, override val body: StatementNode, location: SourceLocation?) : StatementNode(location), IWhileStatementNode, IReturnableNode {
    override val children: List<ASTNode> get() = listOf(condition, body)
    override var returnStatements: List<ReturnStatementNode>? = null
}

class DoStatementNode(override val body: StatementNode, override var condition: ASTNode, location: SourceLocation?) : StatementNode(location), IDoStatementNode, IReturnableNode {
    override val children: List<ASTNode> get() = listOf(body, condition)
    override var returnStatements: List<ReturnStatementNode>? = null

}

class ForStatementNode(
    override var initializer: List<ASTNode>,
    override var condition: ExpressionNode,
    override var increment: List<ExpressionNode>,
    override var body: StatementNode, location: SourceLocation?
) : StatementNode(location), IForStatementNode, IReturnableNode {
    override val children: List<ASTNode> get() = initializer + condition + increment + body
    override var returnStatements: List<ReturnStatementNode>? = null
}


class MemberAccessExpressionNode(
    left: ExpressionNode,
    right: ExpressionNode,
    operator: Operator,
    location: SourceLocation?,
) : BinaryExpressionNode(left, right, operator, location), IMemberAccessExpressionNode {
    override val children: List<ASTNode> get() = emptyList()
    override fun toString(): String = "MemberAccessExpressionNode"
}


open class BinaryExpressionNode(
    override var left: ExpressionNode,
    override var right: ExpressionNode,
    override val operator: Operator,
    override val location: SourceLocation?,
) : ExpressionNode(), IBinaryExpressionNode {
    /**May be builtin or user defined**/
    var functionDecl: DeclSymbol.FunctionDecl? = null
    override val children: List<ASTNode> get() = listOf(left, right)
    override fun toString(): String = "BinaryExpressionNode(operator='${operator.value}')"
}

data class UnaryExpressionNode(
    override var operand: ExpressionNode,
    override val operator: Operator,
    override val isPrefix: Boolean, override val location: SourceLocation?
) : ExpressionNode(), IUnaryExpressionNode {
    /**May be builtin or user defined**/
    var functionDecl: DeclSymbol.FunctionDecl? = null
    override val children: List<ASTNode> get() = listOf(operand)
    override fun toString(): String = "UnaryExpressionNode(operator='${operator.value}', isPrefix=$isPrefix)"
}

class NamespaceDeclarationNode(
    override val name: IdentifierNode?,
    override val body: NamespaceBodyNode, location: SourceLocation?
) : DefinitionNode(location), INamespaceDeclarationNode {
    lateinit var nsDecl: DeclSymbol.NamespaceDecl;

    override val isAnonymous = name == null
    override val children: List<ASTNode>
        get() = listOfNotNull(name) + body

    override fun toString(): String {
        return "NamespaceDeclarationNode(name=$name)"
    }
}

class ClassDeclarationNode(
    override val name: IdentifierNode?,
    override val type: ClassType,
    location: SourceLocation?
) : DeclarationNode(location), IClassDeclarationNode {
    lateinit var classDecl: DeclSymbol.ClassDecl;

    override val children: List<ASTNode>
        get() = listOfNotNull(name)
}

class ClassDefinitionNode(
    override val name: IdentifierNode?,
    override val type: ClassType,
    override val body: ClassBodyNode,
    location: SourceLocation?
) : DefinitionNode(location), IClassDefinitionNode {
    lateinit var classDecl: DeclSymbol.ClassDecl;

    override val children: List<ASTNode>
        get() = listOfNotNull(name) + body

    override fun toString(): String {
        return "ClassDefinitionNode(type=$type)"
    }
}

class AccessSpecifierNode(
    override val specifier: AccessSpecifier, override val location: SourceLocation?
) : ASTNode, IAccessSpecifierNode {
    override fun toString(): String = "AccessSpecifierNode(type='${specifier}')"
    override val children: List<ASTNode> = emptyList();
}

data class RootNode(
    override val declarations: List<ASTNode>, override val location: SourceLocation = SourceLocation.EXPORTED
) : ASTNode, IRootNode {
    lateinit var scope: GlobalScope;
    override val children: List<ASTNode> get() = declarations
}