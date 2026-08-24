package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.core.getOrElse

class ReturnStmtAnalyzer : NodeAnalyzer<ReturnStatementNode> {

    override fun analyze(node: ReturnStatementNode, ctx: AnalyzeContext): ASTNode {
        val funcDecl = ctx.scope.findCurrentFunction()
        if (funcDecl == null) {
            ctx.error("Return statement outside of function", node)
            return node
        }


        val expr = node.expression ?: return node
        if(funcDecl is DeclSymbol.ConstructorDecl) {
            ctx.error("Constructor cannot return a value", node)
            return node
        }

        val returnExpr =ctx.analyze(expr, ctx.scope) as ExpressionNode;
        if(returnExpr.resolvedType == null || returnExpr.valueCategory == null) {return node}

        if(!funcDecl.returnType.hasUndeducedAuto) {
            node.expression =
                ctx.buildConversion(returnExpr, funcDecl.returnType, ctx.getRefValueCategory(funcDecl.returnType))
                    .getOrElse { ctx.error(it); return expr }
        }

        return node;
    }
}