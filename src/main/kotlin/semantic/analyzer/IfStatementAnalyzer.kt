package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.CompoundStatementNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IfStatementNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.ast.StatementNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse

class IfStatementAnalyzer : NodeAnalyzer<IfStatementNode> {
    override fun analyze(node: IfStatementNode, ctx: AnalyzeContext): ASTNode {
        val ownerFun = ctx.scope.findCurrentFunction()
        if (ownerFun == null) {
            ctx.error("If statement outside of function", node)
            return node;
        }
        node.condition = ctx.analyze(node.condition, ctx.scope) as ExpressionNode

        if (node.condition.resolvedType == null) return node;

        node.condition = ctx.buildConversion(node.condition, ctx.types.bool, ValueCategory.PRVALUE)
            .getOrElse { ctx.error(it); return node };


        val returns = mutableListOf<ReturnStatementNode>()
        ctx.withScope(node.scope) {
            ctx.withScope(node.bodyScope) {
                node.body = ctx.analyze(node.body, ctx.scope) as StatementNode;

                val body = node.body;
                if (body !is CompoundStatementNode) {
                    throw IllegalStateException("If statement body must be compound statement")
                }
                if (body.returnStatements != null) {
                    returns += body.returnStatements!!
                }
            }
            val elseBody = node.elseBody;
            if (elseBody != null) {
                ctx.withScope(node.elseBodyScope!!) {
                    if (elseBody !is CompoundStatementNode) {
                        throw IllegalStateException("Else body must be compound statement")
                    } else {
                        node.elseBody = ctx.analyze(elseBody, ctx.scope) as StatementNode;
                        if (elseBody.returnStatements != null) {
                            returns += elseBody.returnStatements!!
                        }
                    }
                }
            }
        }

        node.returnStatements = returns;

        return node;
    }
}