package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.TypeNode

sealed class Symbol(
    val name: String,
    val parentSymbol: Symbol?,
    val astNode: ASTNode
) {
    val qualifiedName: String get() {
        if (parentSymbol == null || parentSymbol.name.isEmpty()) {
            return name // Глобальный scope
        }
        return "${parentSymbol.qualifiedName}::$name"
    }

    class NamespaceSymbol(
        name: String,
        parentSymbol: Symbol?,
        astNode: ASTNode,
        val scope: Scope
    ) : Symbol(name, parentSymbol, astNode)

    class VariableSymbol(
        name: String,
        val type: TypeNode,
        parentSymbol: Symbol?,
        astNode: ASTNode
    ) : Symbol(name, parentSymbol, astNode)

    class ClassSymbol(
        name: String,
        parentSymbol: Symbol?,
        astNode: ASTNode,
        val scope: Scope
    ) : Symbol(name, parentSymbol, astNode)
}