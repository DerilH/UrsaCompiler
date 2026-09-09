package org.derilh.ast.api

import org.derilh.core.AccessSpecifier
import org.derilh.core.SourceLocation

interface IASTNode {
    val children: List<IASTNode>
    val location: SourceLocation?
}

interface IIdentifierNode : IASTNode {
    val name: String
}

interface IQualifiedIdentifierNode : IIdentifierNode {
    val qualifiers: List<IIdentifierNode>
    val isGlobal: Boolean
}

//interface ITypeNode : IASTNode {
//    val isConst: Boolean
//    val isVolatile: Boolean
//    fun toDisplayString(): String
//}

//interface IPrimitiveTypeNode : ITypeNode {
//    val kind: org.derilh.core.PrimitiveTypeKind
//}
//
//interface IDeclaredTypeNode : ITypeNode {
//    val typeName: IIdentifierNode
//}
//
//interface IAutoTypeNode : ITypeNode
//
//interface IMemberPointerTypeNode : ITypeNode {
//    val parentId: IIdentifierNode
//    val type: ITypeNode
//}
//
//interface IPointerTypeNode : ITypeNode {
//    val type: ITypeNode
//}
//
//interface IReferenceTypeNode : ITypeNode {
//    val type: ITypeNode
//}
//
//interface IRValueReferenceTypeNode : ITypeNode {
//    val type: ITypeNode
//}
//
//interface IFunctionTypeNode : ITypeNode {
//    val returnType: ITypeNode
//    val params: List<IParameterNode>
//    val qualifiers: org.derilh.core.FunctionQualifiers
//}
//
//interface IArrayTypeNode : ITypeNode {
//    val elementType: ITypeNode
//    val sizeExpression: IExpressionNode?
//}

interface IExpressionNode : IASTNode {
    var valueCategory: org.derilh.core.ValueCategory
}

interface ILiteralNode<T> : IExpressionNode {
    val value: T
}

interface IBooleanLiteralNode : ILiteralNode<Boolean>
interface IIntLiteralNode : ILiteralNode<java.math.BigInteger> {
    val radix: org.derilh.core.Radix
    val isUnsigned: Boolean
    val isLong: Boolean
    val isLongLong: Boolean
    val isSizeT: Boolean
}

interface IFloatLiteralNode : ILiteralNode<String> {
    val isDouble: Boolean
    val isLong: Boolean
}

interface IStringLiteralNode : ILiteralNode<IntArray> {
    val prefix: org.derilh.core.CharPrefix
}

interface INullptrLiteralNode : ILiteralNode<Int>
interface ICharLiteralNode : ILiteralNode<IntArray> {
    val prefix: org.derilh.core.CharPrefix
    val isMultiChar: Boolean
}

interface IStringConcatExpressionNode : IExpressionNode {
    val literals: List<IStringLiteralNode>
}

interface IThisExpressionNode : IExpressionNode

interface INewExpressionNode : IExpressionNode {
    val placementArgs: List<IExpressionNode>?
    val initializerList: IExpressionNode?
//    val type: ITypeNode
}

interface ISizeofExpressionNode : IExpressionNode {
    val expression: IASTNode?
}

interface IIdExpressionNode : IExpressionNode {
    val id: IIdentifierNode
}

interface ICallExpressionNode : IExpressionNode {
    val callee: IExpressionNode?
    val arguments: IArgumentsNode
}

interface ICommaExpressionNode : IExpressionNode {
    val arguments: List<IExpressionNode>
}

interface IArgumentsNode : IASTNode {
    val arguments: List<IExpressionNode>
}

interface IInitializerListExpressionNode : IExpressionNode {
    val isBraced: Boolean;
    val arguments: List<IExpressionNode>
}

interface IStatementNode : IASTNode

interface ICompoundStatementNode : IStatementNode {
    val statements: List<IASTNode>
}

interface IClassBodyNode : IASTNode {
    val declarations: List<IASTNode>
}

interface INamespaceBodyNode : IASTNode {
    val declarations: List<IASTNode>
}

interface IFunctionBodyNode : ICompoundStatementNode
interface IAnonymousBlock : ICompoundStatementNode

interface IDeclarationNode : IStatementNode
interface IDefinitionNode : IStatementNode

interface IConstructorDeclarationNode : IFunctionDeclarationNode {
}

interface IConstructorDefinitionNode : IFunctionDefinitionNode {
    val memberInitializers: List<IMemberInitializerNode>
}

interface IFunctionDefinitionNode : IDefinitionNode {
    val declaration: IFunctionDeclarationNode
    val body: IFunctionBodyNode
    val name: IIdentifierNode
}

interface IMemberInitializerNode : IASTNode {
    val id: IIdentifierNode
    val arguments: IArgumentsNode
    val isBraced: Boolean
}

interface IParameterNode : IASTNode {
    var hasDefaultValue: Boolean;
}

//interface IDeclaratorNode : IASTNode {
//    val type: ITypeNode
//}

//interface INamedDeclaratorNode : IDeclaratorNode {
//    val id: IIdentifierNode
//}

interface IVariableDeclarationNode : IDeclarationNode {
}

interface IFunctionDeclarationNode : IDeclarationNode {
}

interface ITypeDefDeclarationNode : IDeclarationNode {
}

//interface IAbstractDeclaratorNode : IDeclaratorNode {
//    val initializer: IExpressionNode?
//}

interface IDeclarationSequenceNode : IDeclarationNode {
    val declarations: List<IDeclarationNode>
}

interface IAsmStatementNode : IStatementNode {
    val asmExr: IExpressionNode
    val inList: List<IAsmOperandNode>
    val outList: List<IAsmOperandNode>
    val clobberList: List<IExpressionNode>
}

interface IAsmOperandNode : IASTNode {
    val constraint: IExpressionNode
    val expr: IExpressionNode
    val isInput: Boolean
}


interface IReturnStatementNode : IStatementNode {
    val expression: IExpressionNode?
}

interface IIfStatementNode : IStatementNode {
    val condition: IExpressionNode
    val body: IStatementNode
    val elseBody: IStatementNode?
}

interface IBreakStatementNode : IStatementNode
interface IContinueStatementNode : IStatementNode

interface IWhileStatementNode : IStatementNode {
    val condition: IExpressionNode
    val body: IStatementNode
}

interface IDoStatementNode : IStatementNode {
    val body: IStatementNode
    val condition: IASTNode
}

interface IForStatementNode : IStatementNode {
    val initializer: List<IASTNode>
    val condition: IExpressionNode
    val increment: List<IExpressionNode>
    val body: IStatementNode?
}

interface IMemberAccessExpressionNode : IBinaryExpressionNode;


interface IBinaryExpressionNode : IExpressionNode {
    val left: IExpressionNode
    val right: IExpressionNode
    val operator: org.derilh.core.Operator
}

interface IUnaryExpressionNode : IExpressionNode {
    val operand: IExpressionNode
    val operator: org.derilh.core.Operator
    val isPrefix: Boolean
}

interface INamespaceDeclarationNode : IDefinitionNode {
    val name: IIdentifierNode?
    val body: INamespaceBodyNode
    val isAnonymous: Boolean
}

interface IClassDeclarationNode : IDeclarationNode {
    val name: IIdentifierNode?
    val type: org.derilh.core.ClassType
}

interface IClassDefinitionNode : IDefinitionNode {
    val name: IIdentifierNode?
    val type: org.derilh.core.ClassType
    val body: IClassBodyNode
}

interface IAccessSpecifierNode : IASTNode {
    val specifier: AccessSpecifier
}

interface IRootNode : IASTNode {
    val declarations: List<IASTNode>
}

interface IArrayAccessNode : IExpressionNode {
    val operand: IExpressionNode
    val index: IExpressionNode?
}

interface IImplicitCastExpressionNode : IExpressionNode {
    val kind: org.derilh.core.ConversionKind
    val operand: IExpressionNode
}

interface ITypeCastExpressionNode : IExpressionNode {
    val castType: org.derilh.core.CastMethod
    val explicit: Boolean
    val operand: IExpressionNode
}
