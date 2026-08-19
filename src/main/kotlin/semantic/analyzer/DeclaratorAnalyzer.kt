package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.DeclaratorNode
import org.derilh.ast.ExpressionNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse

class DeclaratorAnalyzer : NodeAnalyzer<DeclaratorNode> {
    override fun analyze(node: DeclaratorNode, ctx: AnalyzeContext): ASTNode {
        var init = node.initializer;
        val initType = if (init != null) {
            node.initializer = ctx.findAnalyzer(init).analyze(init, ctx) as ExpressionNode
            node.initializer!!.resolvedType;
        } else null
        init = node.initializer

        val varType = ctx.resolveType(node.type, ctx.scope, initType).getOrElse {
            ctx.error(it)
            return node
        }

        if(varType.hasUndeducedAuto) {
            if(init == null) {
                ctx.error("Declaration with 'auto' requires an initializer")
            } else ctx.error("Cannot initialize 'auto' with an expression")
            return node;
        } else if(varType !== initType && initType != null) {
            val seq = ctx.findImplicitCastSeq(initType, init!!.valueCategory!!, varType, ValueCategory.PRVALUE, ctx.isNullPointerConstant(init))
            if(seq.size == 1)  {
                node.initializer = ctx.buildConversionNodes(init, seq.first())
            } else if(seq.size > 1) {
                ctx.error("Ambiguous cast", init)
            }
        }
        return node;
    }
}