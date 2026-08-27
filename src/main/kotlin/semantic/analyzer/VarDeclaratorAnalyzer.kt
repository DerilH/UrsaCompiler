package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.VariableDeclaratorNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.semantic.SemanticType

class VarDeclaratorAnalyzer : NodeAnalyzer<VariableDeclaratorNode> {
    override fun analyze(node: VariableDeclaratorNode, ctx: AnalyzeContext): ASTNode {
        var init = node.initializer;
        val initType = if (init != null) {
            node.initializer = ctx.analyze(init, ctx.scope) as ExpressionNode
            node.initializer!!.resolvedType;
        } else null
        init = node.initializer

        val varType = ctx.resolveType(node.type, ctx.scope, initType).getOrElse {
            ctx.error(it,)
            return node
        }
        node.varDecl.type = varType
        node.varDecl.processed = true;


        if(varType is SemanticType.Declared && !varType.isComplete) {
            ctx.error("Type ${varType.decl.name} is incomplete", node.type)
        }
        else if(varType.hasUndeducedAuto) {
            if(init == null) {
                ctx.error("Declaration with 'auto' requires an initializer", node)
            } else ctx.error("Cannot initialize 'auto' with an expression", init)
            return node;
        } else if(varType !== initType && initType != null) {
            val seq = ctx.findImplicitCastSeq(initType, init!!.valueCategory!!, varType, ValueCategory.PRVALUE, ctx.isNullPointerConstant(init))
            if(seq.size == 1)  {
                node.initializer = ctx.buildConversionSeq(init, seq.first())
            } else if(seq.size > 1) {
                ctx.error("Ambiguous cast", init)
            } else {
                ctx.error("Cannot initialize variable ${node.id.toDisplayString()} of type ${node.type.resolvedType} with ${init.resolvedType}", init)
            }
        }

        return node;
    }
}