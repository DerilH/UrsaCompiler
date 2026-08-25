package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.core.ifFailure

public class NamespaceDeclAnalyzer : NodeAnalyzer<NamespaceDeclarationNode> {
    override fun analyze(node: NamespaceDeclarationNode, ctx: AnalyzeContext): ASTNode {
        node.nsDecl.processed = true;
        ctx.withScope(node.nsDecl.scope) {
            for (statement in node.body.declarations) {
                ctx.findAnalyzer(statement).analyze(statement, ctx)
            }
        }
        return node;
    }
}

fun findOrCreateNamespaceDef(
    name: String,
    ctx: AnalyzeContext,
    isAnonymous: Boolean
): DeclSymbol.NamespaceDecl {
    val symbols = ctx.scope.lookupLocal(name, true)
    val existingSymbol = symbols.firstOrNull()

    return when (existingSymbol) {
        is DeclSymbol.NamespaceDecl -> {
            existingSymbol
        }


        else -> {
            ctx.error("Redefinition of symbol '$name' as different kind")

            val newSymbol = DeclSymbol.namespace(name, ctx.scope.ownerSymbol, isAnonymous)
            newSymbol.scope = Scope(parent = ctx.scope, ownerSymbol = newSymbol)
            ctx.scope.define(newSymbol).ifFailure(ctx::error)
            newSymbol
        }
    }
}
