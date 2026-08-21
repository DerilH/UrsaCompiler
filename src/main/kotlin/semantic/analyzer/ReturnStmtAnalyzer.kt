package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.core.getOrElse

class ReturnStmtAnalyzer : NodeAnalyzer<ReturnStatementNode> {

    override fun analyze(node: ReturnStatementNode, ctx: AnalyzeContext): ASTNode {
        val symbol = ctx.scope.findCurrentFunction()
        if (symbol == null) {
            ctx.error("Return statement outside of function", node)
            return node
        }


        val returnExpr =ctx.analyze(node.expression, ctx.scope) as ExpressionNode;
        if(returnExpr.resolvedType == null || returnExpr.valueCategory == null) {return node}
        node.expression = ctx.buildConversion(returnExpr, symbol.returnType, ctx.getRefValueCategory(symbol.returnType)).getOrElse { ctx.error(it); return node.expression }

        return node;
    }
}