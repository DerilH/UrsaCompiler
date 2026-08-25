package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.DeclarationSequenceNode
import org.derilh.ast.ParameterNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.ast.VariableDeclaratorNode
import org.derilh.core.ifFailure

class ParameterNodeAnalyzer : NodeAnalyzer<ParameterNode> {
    override fun analyze(node: ParameterNode, ctx: AnalyzeContext): ASTNode {
        if (node.name == null) return node;
        if (node.name is QualifiedIdentifierNode) {
            ctx.error("Qualified identifiers are not allowed as parameter names")
            return node
        }

        ctx.analyze(node.declarator, ctx.scope)

        val init = (node.declarator as? VariableDeclaratorNode)?.initializer;
        if (init != null) {
            ctx.findAnalyzer(init).analyze(init, ctx)
            node.hasDefaultValue = true;
        }
        return node;
    }
}