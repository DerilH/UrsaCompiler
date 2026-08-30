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

class SemanticProblem(
    val msg: String,
    val level: ProblemLevel,
    val node: IASTNode? = null,
    val location: SourceLocation? = null,
    var trace: Array<StackTraceElement>? = null
) {
    override fun toString(): String {
        val loc = node?.location ?: location
        val locStr = if (loc == SourceLocation.EXPORTED) {
            "location: exported code"
        } else if (loc != null) {
            "location: $loc"
        } else {
            "location: unknown"
        }
        return "$level: $msg ($locStr)"
    }
}