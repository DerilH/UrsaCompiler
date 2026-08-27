package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.core.OpResult
import org.derilh.core.ifFailure
import org.derilh.util.ErrorHelper

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
): OpResult<DeclSymbol.NamespaceDecl> {
    val result = ctx.scope.lookupLocal(name, true);

    return when (result) {

        is OpResult.Success<DeclSymbol> -> {
            when (result.value) {
                is DeclSymbol.NamespaceDecl -> {
                    OpResult.success(result.value as DeclSymbol.NamespaceDecl);
                }

                else -> {
                    ErrorHelper.alreadyDefined(DeclSymbol.namespace(name, ctx.scope.ownerSymbol, isAnonymous), result.value)
                }
            }
        }

        is OpResult.Failure -> {
            if (result.args[0] == LookResult.AMBIGUOUS) {
                result
            } else {
                val newSymbol = DeclSymbol.namespace(name, ctx.scope.ownerSymbol, isAnonymous)
                newSymbol.scope = Scope(parent = ctx.scope, ownerSymbol = newSymbol)
                ctx.scope.define(newSymbol).ifFailure(ctx::error)
                OpResult.success(newSymbol)
            }
        }
    }
}
