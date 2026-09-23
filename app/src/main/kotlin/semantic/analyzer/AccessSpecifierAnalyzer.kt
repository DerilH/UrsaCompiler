package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AccessSpecifierNode

class AccessSpecifierAnalyzer : NodeAnalyzer<AccessSpecifierNode> {
    override fun analyze(node: AccessSpecifierNode, ctx: AnalyzeContext): ASTNode {
        val classDecl = ctx.scope.findCurrentClass();

        if(classDecl == null) {
            ctx.error("Access specifier outside of class", node)
            return node;
        }


        classDecl.scope.setAccessSpecifier(node.specifier)
        return node;
    }

}
