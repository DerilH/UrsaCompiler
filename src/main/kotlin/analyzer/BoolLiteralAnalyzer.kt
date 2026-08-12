package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BooleanLiteralNode
import org.derilh.ast.CharLiteralNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.core.CharPrefix
import org.derilh.core.PrimitiveTypeKind
import org.derilh.exceptions.SemanticException
import org.derilh.target.TargetInfo

class BoolLiteralAnalyzer : NodeAnalyzer<BooleanLiteralNode> {

    override fun analyze(node: BooleanLiteralNode, ctx: AnalyzeContext): ASTNode {
        node.type = PrimitiveTypeNode(PrimitiveTypeKind.BOOL)
        return node;
    }
}