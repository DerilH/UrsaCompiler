package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.CallExpressionNode

class CallExprAnalyzer : NodeAnalyzer<CallExpressionNode> {
    override fun analyze(node: CallExpressionNode, ctx: AnalyzeContext): ASTNode {
        node.arguments;

        ctx.resolveOpOverloads()

    }
}
