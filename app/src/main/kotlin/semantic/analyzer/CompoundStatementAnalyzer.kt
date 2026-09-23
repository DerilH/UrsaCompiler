package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.CompoundStatementNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.IReturnableNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.semantic.SemanticType

class CompoundStatementAnalyzer : NodeAnalyzer<CompoundStatementNode> {
    override fun analyze(node: CompoundStatementNode, ctx: AnalyzeContext): ASTNode {
        val ownerFun = ctx.scope.findCurrentFunction()
        val returns = mutableListOf<ReturnStatementNode>()

        for (child in node.statements) {
            if (child is FunctionDefinitionNode) {
                ctx.error("Inner function are not supported yet", child)
                continue;
            }
            if (child is ClassDeclarationNode) {
                ctx.error("Inner classes are not supported yet", child)
                continue;
            }

            ctx.findAnalyzer(child).analyze(child, ctx)
            if (child is ReturnStatementNode) {
                returns += child
            } else if (child is IReturnableNode) {
                val rets = child.returnStatements;
                if (rets != null) {
                    returns += rets;
                }
            }
        }

        node.returnStatements = returns;
        return node;
    }
}
