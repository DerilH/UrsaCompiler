package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.VariableDeclarationNode

private class VarDeclAnalyzer : NodeAnalyzer<VariableDeclarationNode> {
    override fun analyze(node: VariableDeclarationNode, ctx: AnalyzeContext): ASTNode {
        for(decl in node.declarations) {
            ctx.scope.define(decl)
            //TODO: analyze initializer
        }
        return node;
    }
}