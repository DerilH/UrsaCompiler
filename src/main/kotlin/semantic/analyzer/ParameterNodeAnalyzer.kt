package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ParameterNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.ast.VariableDeclarationNode
import org.derilh.core.getOrElse
import org.derilh.core.getOrNull

class ParameterNodeAnalyzer : NodeAnalyzer<ParameterNode> {
    override fun analyze(node: ParameterNode, ctx: AnalyzeContext): ASTNode {
        if (node.declarator?.id == null) return node;
        if (node.declarator.id is QualifiedIdentifierNode) {
            ctx.error("Qualified identifiers are not allowed as parameter names")
            return node
        }

        val init = node.declarator.init;
        if (init != null) {
            ctx.analyze(init, ctx.scope)
            node.hasDefaultValue = true;
        }
        node.decl.processed =true;
        (node.decl as DeclSymbol.VariableDecl).type = ctx.resolveType(node.declSpec,node.declarator, ctx.scope).getOrElse { ctx.error(it); return node }
        return node;
    }
}