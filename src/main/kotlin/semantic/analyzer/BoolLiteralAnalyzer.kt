package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BooleanLiteralNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.core.PrimitiveTypeKind

class BoolLiteralAnalyzer : NodeAnalyzer<BooleanLiteralNode> {

    override fun analyze(node: BooleanLiteralNode, ctx: AnalyzeContext): ASTNode {
        node.resolvedType = ctx.types.bool
        return node;
    }
}