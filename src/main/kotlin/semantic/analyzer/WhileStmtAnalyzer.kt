package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.CompoundStatementNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.ast.StatementNode
import org.derilh.ast.WhileStatementNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.core.ifFailure

class WhileStmtAnalyzer : NodeAnalyzer<WhileStatementNode> {
    override fun analyze(node: WhileStatementNode, ctx: AnalyzeContext): ASTNode {
        val ownerFun = ctx.scope.findCurrentFunction()
        if (ownerFun == null) {
            ctx.error("While statement outside of function", node)
            return node;
        }
        node.condition = ctx.analyze(node.condition, ctx.scope) as ExpressionNode;
        if(node.condition.resolvedType == null) return node;
        node.condition = ctx.buildConversion(node.condition, ctx.types.bool, ValueCategory.PRVALUE).getOrElse { ctx.error(it); return node };

        val returns = mutableListOf<ReturnStatementNode>()
        ctx.withScope(node.scope) {
            node.body = ctx.analyze(node.body, ctx.scope) as StatementNode;

            val body = node.body;
            if (body !is CompoundStatementNode) {
                throw IllegalStateException("While statement body must be compound statement")
            } else if (body.returnStatements != null) {
                returns += body.returnStatements!!
            }
        }

        node.returnStatements = returns;
        return node;
    }
}