package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.ast.QualifiedIdentifierNode

public class NamespaceDeclAnalyzer : NodeAnalyzer<NamespaceDeclarationNode> {
    override fun analyze(node: NamespaceDeclarationNode, ctx: AnalyzeContext): ASTNode {
        val decl: DeclSymbol.NamespaceDecl;
        var leaveCount = 0;
        try {

            if (node.isAnonymous) {
                decl = findOrCreate("<anonymous_${ctx.getNextAnonId()}>", ctx, false, node, true) ?: return node
                ctx.scope.addUsingDirective(decl.scope)
            } else {
                if (node.name is QualifiedIdentifierNode) {
                    for (qual in node.name.qualifiers) {
                        val ns = findOrCreate(qual.name, ctx, true, node, false) ?: return node
                        ctx.enterScope(ns);
                        leaveCount++;
                    }
                }

                decl = findOrCreate(node.name!!.name, ctx, false, node, false) ?: return node
            }

            return ctx.withScope(decl.scope) {
                for (statement in node.body.declarations) {
                    ctx.findAnalyzer(statement).analyze(statement, ctx)
                }
                node
            }
        } finally {
            repeat(leaveCount) {
                ctx.leaveScope()
            }
        }
    }

    private fun findOrCreate(
        name: String,
        ctx: AnalyzeContext,
        isNested: Boolean,
        node: NamespaceDeclarationNode,
        isAnonymous: Boolean
    ): DeclSymbol.NamespaceDecl? {
        val existingSymbol = ctx.scope.lookupLocal(name)

        val targetNamespace: DeclSymbol.NamespaceDecl? = when (existingSymbol) {
            is DeclSymbol.NamespaceDecl -> {
                if (!isNested) {
                    existingSymbol.declarations.add(node)
                }
                existingSymbol
            }

            null -> {
                val newSymbol = DeclSymbol.namespace(name, node, ctx.scope.ownerSymbol, isAnonymous)
                newSymbol.scope = Scope(parent = ctx.scope, ownerSymbol = newSymbol)

                ctx.scope.define(newSymbol)
                newSymbol
            }

            else -> {
                ctx.error("Redefinition of namespace '$name' as different kind")
                null
            }
        }
        return targetNamespace
    }
}
