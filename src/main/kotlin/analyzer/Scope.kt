package org.derilh.analyzer

import org.derilh.ast.DeclaratorNode
import org.derilh.ast.IdentifierNode
import org.derilh.exceptions.SemanticException

class Scope(val type: ScopeType, val parent: Scope?) {
    private val symbols = mutableMapOf<String, DeclaratorNode>()

    fun define(decl: DeclaratorNode) {
        //TODO add support for namespaces
        val name = decl.id?.name ?: return

        if (symbols.containsKey(name)) {
            throw SemanticException("Redeclaration of identifier '$name'")
        }
        symbols[name] = decl
    }

    fun resolve(name: IdentifierNode): DeclaratorNode? {
        return resolve(name.name)
    }

    fun resolve(name: String): DeclaratorNode? {
        //TODO add support for namespaces
        symbols[name]?.let { return it }

        return parent?.resolve(name)
    }
}

enum class ScopeType {
    ROOT,
    CLASS,
    STRUCT,
    FUNCTION,
    METHOD,
    BLOCK
}