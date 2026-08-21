package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.DeclarationSequenceNode
import org.derilh.core.ifFailure

class AbstractDeclaratorAnalyzer : NodeAnalyzer<AbstractDeclaratorNode> {
    override fun analyze(node: AbstractDeclaratorNode, ctx: AnalyzeContext): ASTNode {
        ctx.resolveType(node.type, ctx.scope).ifFailure(ctx::error);
        return node;
    }
}