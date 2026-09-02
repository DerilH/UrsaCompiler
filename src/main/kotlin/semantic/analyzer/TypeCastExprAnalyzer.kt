package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.TypeCastExpressionNode
import org.derilh.core.ValueCategory

public class TypeCastExprAnalyzer : NodeAnalyzer<TypeCastExpressionNode> {
    override fun analyze(node: TypeCastExpressionNode, ctx: AnalyzeContext): ASTNode {
//        node.operand = ctx.findAnalyzer(node.operand).analyze(node.operand, ctx) as ExpressionNode
//        TODO path finding
//        val res = ctx.resolveCastPath(node.operand.resolvedType!!, node.declaratorNode.type.resolvedType!!)
//        if(res == null) {
//            ctx.error("Cannot cast ${node.operand.resolvedType} to ${node.declaratorNode.type}", node)
//        }
//        node.resolvedType = node.declaratorNode.type.resolvedType;
//        TODO("Type cast not yet implemented")
        node.operand = ctx.analyze(node.operand, ctx.scope) as ExpressionNode;
        ctx.analyze(node.declaratorNode, ctx.scope)
        node.resolvedType = node.declaratorNode.type.resolvedType;
        node.valueCategory = ValueCategory.PRVALUE;
        return node;
    }
}
