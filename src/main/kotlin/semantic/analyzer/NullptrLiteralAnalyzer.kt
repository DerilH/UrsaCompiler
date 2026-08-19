package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BooleanLiteralNode
import java.math.BigInteger

class NullptrLiteralAnalyzer : NodeAnalyzer<BooleanLiteralNode> {

    override fun analyze(node: BooleanLiteralNode, ctx: AnalyzeContext): ASTNode {
        node.resolvedType = ctx.types.nullptr
        node.evaluated = BigInteger("0")
        return node;
    }
}