package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BooleanLiteralNode
import org.derilh.ast.LinkageSpecificationNode

class LinkageSpecAnalyzer : NodeAnalyzer<LinkageSpecificationNode> {

    override fun analyze(node: LinkageSpecificationNode, ctx: AnalyzeContext): ASTNode {
        ctx.linkageStack.push(node.linkage)
        ctx.analyze(node.body, ctx.scope);
        ctx.linkageStack.pop()
        return node;
    }
}