package org.derilh.exceptions

import org.derilh.ast.ASTNode
import org.derilh.lexer.SourceLocation
private fun getLocation(node: ASTNode?) = if(node?.location == SourceLocation.EXPORTED) {
    "location: exported code"
} else "location: ${node?.location}"

enum class ProblemLevel {
    WARNING,
    ERROR
}

class SemanticProblem(val msg: String, val level: ProblemLevel, val node: ASTNode? = null) {

}