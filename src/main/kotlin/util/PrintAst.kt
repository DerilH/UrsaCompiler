package org.derilh.util

import org.derilh.ast.*

private fun ASTNode.label(): String =
    when (this) {
        is PrimitiveTypeNode -> "PrimitiveTypeNode(kind=$kind, isUnsigned=$isUnsigned, isShort=$isShort, isLong=$isLong, isLongLong=$isLongLong, isConst=$isConst)"
        is IdentifierWithNamespaceNode -> "IdentifierWithNamespaceNode(name=$name), isGlobal=$isGlobal"
        is IdentifierNode -> "IdentifierNode(name=$name)"
        is DeclaredTypeNode -> "BaseTypeNode"
        is AutoTypeNode -> "AutoTypeNode"
        is ParameterNode -> "ParameterNode"
        is CompoundStatementNode -> "BlockNode"
        is MethodDeclarationNode -> "MethodNode"
        is BinaryExpressionNode -> "BinaryExpressionNode(operator=${operator.value})"
        is ClassDeclarationNode -> "ClassNode"
        is IfStatementNode -> "IfStatementNode"
        is ForStatementNode -> "ForStatementNode"
        is WhileStatementNode -> "WhileStatementNode"
        is DoStatementNode -> "DoStatementNode"
        is AccessDeclarationNode -> "AccessDeclarationNode(access=${access.value})"
        is UnaryExpressionNode -> "UnaryExpressionNode(operator=${operator.value}, isPrefix = ${isPrefix})"
        is NewExpressionNode -> "NewExpressionNode"
        is SizeofExpressionNode -> "SizeofExpressionNode"
        is ArrayTypeNode -> "ArrayTypeNode"
        is PointerTypeNode -> "PointerTypeNode(isConst=${isConst})"
        is ReferenceTypeNode -> "ReferenceTypeNode(isConst=${isConst})"
        is RValueReferenceTypeNode -> "RValueReferenceTypeNode"
        is RootNode -> "RootNode"
        else -> this.javaClass.simpleName
    }


fun ASTNode.printTree(prefix: String = "", isLast: Boolean = true) {
    val connector = if (isLast) "└── " else "├── "
    println(prefix + connector + label())

    val childPrefix = prefix + if (isLast) "    " else "│   "
    children.forEachIndexed { index, child ->
        child.printTree(
            prefix = childPrefix,
            isLast = index == children.lastIndex
        )
    }
}