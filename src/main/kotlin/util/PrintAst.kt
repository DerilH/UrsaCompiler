package org.derilh.util

import org.derilh.ast.*

private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_RED = "\u001B[31m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_YELLOW = "\u001B[33m"
private const val ANSI_BLUE = "\u001B[34m"
private const val ANSI_CYAN = "\u001B[36m"
private const val ANSI_PURPLE = "\u001B[35m"

private fun ASTNode.color(): String =
    when (this) {
        is RootNode -> ANSI_PURPLE
        is DeclarationNode -> ANSI_BLUE
        is StatementNode -> ANSI_CYAN
        is ExpressionNode -> ANSI_YELLOW
        is TypeNode -> ANSI_GREEN
        is IdentifierNode -> ANSI_RED
        else -> ANSI_RESET
    }

fun ASTNode.printTree(prefix: String = "", isLast: Boolean = true) {
    val connector = if (isLast) "└── " else "├── "
    val nodeColor = color()
    var nodeString = this.toString();
    if(this is ExpressionNode) {
        nodeString += " ${ANSI_RED}RESOLVED_TYPE${ANSI_RESET}: ${ANSI_BLUE}${this.resolvedType}${ANSI_RESET}"
    }
    println(prefix + connector + nodeColor + nodeString + ANSI_RESET)

    val childPrefix = prefix + if (isLast) "    " else "│   "
    children.forEachIndexed { index, child ->
        child.printTree(
            prefix = childPrefix,
            isLast = index == children.lastIndex
        )
    }
}
