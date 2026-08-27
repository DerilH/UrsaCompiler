package org.derilh.exceptions

import org.derilh.ast.api.IASTNode
import org.derilh.core.SourceLocation

private fun getLocation(node: IASTNode?) = if(node?.location == SourceLocation.EXPORTED) {
    "location: exported code"
} else "location: ${node?.location}"

enum class ProblemLevel {
    WARNING,
    ERROR
}

class SemanticProblem(val msg: String, val level: ProblemLevel, val node: IASTNode? = null, var trace: Array<StackTraceElement>? = null) {

}