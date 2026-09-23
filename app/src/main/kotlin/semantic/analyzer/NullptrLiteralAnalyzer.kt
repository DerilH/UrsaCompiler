package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BooleanLiteralNode
import org.derilh.ast.NullptrLiteralNode
import java.math.BigInteger

class NullptrLiteralAnalyzer : NodeAnalyzer<NullptrLiteralNode> {

    override fun analyze(node: NullptrLiteralNode, ctx: AnalyzeContext): ASTNode {
        node.resolvedType = ctx.types.nullptr
        node.evaluated = BigInteger("0")
        return node;
    }
}