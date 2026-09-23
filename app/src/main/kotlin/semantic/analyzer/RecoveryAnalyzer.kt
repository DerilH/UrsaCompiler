package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode

class RecoveryAnalyzer : NodeAnalyzer<ASTNode> {
    override fun analyze(node: ASTNode, ctx: AnalyzeContext): ASTNode {
        return node;
    }
}
