package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.DeclarationSequenceNode
import org.derilh.core.ifFailure

class DeclarationSeqAnalyzer : NodeAnalyzer<DeclarationSequenceNode> {
    override fun analyze(node: DeclarationSequenceNode, ctx: AnalyzeContext): ASTNode {
//        ctx.resolveType(node.declSpec, ctx.scope).ifFailure(ctx::error);
        if(node.declarations.isEmpty()) ctx.error("Declaration sequence should contain at least one declaration")

        for (declarator in node.declarations) {
            ctx.analyze(declarator, ctx.scope)
        }
        return node;
    }
}