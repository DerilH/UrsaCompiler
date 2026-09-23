package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BooleanLiteralNode

class BoolLiteralAnalyzer : NodeAnalyzer<BooleanLiteralNode> {

    override fun analyze(node: BooleanLiteralNode, ctx: AnalyzeContext): ASTNode {
        node.resolvedType = ctx.types.bool
        node.evaluated = node.value
        return node;
    }
}