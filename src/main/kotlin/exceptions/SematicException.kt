package org.derilh.exceptions

import org.derilh.ast.ASTNode

class SemanticException(val msg: String, val node: ASTNode? = null) : Exception("Semantic error -> $msg") {
}