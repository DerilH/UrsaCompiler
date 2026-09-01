package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.AsmOperandNode
import org.derilh.ast.AsmStatementNode
import org.derilh.ast.CallExpressionNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.StringConcatExpressionNode
import org.derilh.ast.StringLiteralNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.core.ifFailure

class AsmStmtAnalyzer : NodeAnalyzer<AsmStatementNode> {
    override fun analyze(node: AsmStatementNode, ctx: AnalyzeContext): ASTNode {
        node.asmExr = ctx.analyze(node.asmExr, ctx.scope) as StringLiteralNode;
        node.inList.forEach { ctx.analyze(it,ctx.scope); }
        node.outList.forEach { ctx.analyze(it,ctx.scope); }
        val clobber = node.clobberList.map { ctx.analyze(it,ctx.scope) as StringLiteralNode }
        node.clobberList = clobber;


        return node;
    }
}

class AsmOperandAnalyzer : NodeAnalyzer<AsmOperandNode> {
    override fun analyze(node: AsmOperandNode, ctx: AnalyzeContext): ASTNode {
        val analyzedConstraint = ctx.analyze(node.constraint, ctx.scope)
        if (analyzedConstraint !is StringLiteralNode) {
            ctx.error("Constraint operand must be a string literal", node.constraint)
            return node
        }
        node.constraint = analyzedConstraint

        val expr = ctx.analyze(node.expr, ctx.scope) as ExpressionNode
        val constraintStr = analyzedConstraint.evaluated as String

        if (node.isInput) {
            val targetCategory = if (constraintStr.contains("m")) ValueCategory.LVALUE else ValueCategory.PRVALUE

            node.expr = ctx.buildConversion(expr, expr.resolvedType!!, targetCategory).getOrElse { ctx.error(it); return node }
        } else {
            if (expr.valueCategory!!.isRValue) {
                ctx.error("Cannot set rvalue to asm output operand", expr)
            } else if (expr.resolvedType!!.isConst) {
                ctx.error("Cannot write to read-only (const) lvalue in asm output operand", expr)
            }
            node.expr = expr
        }

        return node
    }
}