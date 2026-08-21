package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.ReturnStatementNode

class ReturnStmtAnalyzer : NodeAnalyzer<ReturnStatementNode> {

    override fun analyze(node: ReturnStatementNode, ctx: AnalyzeContext): ASTNode {
        node.expression = ctx.analyze(node.expression, ctx.scope) as ExpressionNode
        return node;
    }
}