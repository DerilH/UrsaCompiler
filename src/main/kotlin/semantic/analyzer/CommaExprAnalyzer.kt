package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.CommaExpressionNode
import org.derilh.ast.ExpressionNode
import org.derilh.core.ValueCategory
import org.derilh.core.ifFailure

class CommaExprAnalyzer : NodeAnalyzer<CommaExpressionNode> {
    override fun analyze(node: CommaExpressionNode, ctx: AnalyzeContext): ASTNode {
        //TODO: add proper comma expr parsing
//        if(node.arguments.isEmpty()) {
//        lateinit var last: ExpressionNode;
//            ctx.error("Empty comma expression", node)
//            node.resolvedType = ctx.types.getError();
//            node.valueCategory = ValueCategory.PRVALUE;
//            return node;
//        }
//        for (arg in node.arguments) {
//            last = ctx.analyze(arg, ctx.scope) as ExpressionNode;
//        }
//
//
//        node.resolvedType = last.resolvedType;
//        node.valueCategory = last.valueCategory;
        return node;
    }
}