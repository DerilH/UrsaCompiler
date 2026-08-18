package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.DeclaratorNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.VariableDeclarationNode

class DeclaratorAnalyzer : NodeAnalyzer<DeclaratorNode> {
    override fun analyze(node: DeclaratorNode, ctx: AnalyzeContext): ASTNode {
        val init = node.initializer;
        val initType = if (init != null) {
            node.initializer = ctx.findAnalyzer(init).analyze(init, ctx) as ExpressionNode
            node.initializer!!.resolvedType;
        } else null

        val t = ctx.resolveType(node.type, ctx.scope, initType);
        if(t == null) {
            return node;
        } else if(t.hasUndeducedAuto) {
            if(init == null) {
                ctx.error("Declaration with 'auto' requires an initializer")
            } else ctx.error("Cannot initialize 'auto' with an expression")
            return node;
        }
        return node;
    }
}