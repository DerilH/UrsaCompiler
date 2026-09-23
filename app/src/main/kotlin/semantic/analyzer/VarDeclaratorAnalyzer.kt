package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdExpressionNode
import org.derilh.ast.VariableDeclarationNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType

class VarDeclaratorAnalyzer : NodeAnalyzer<VariableDeclarationNode> {
    override fun analyze(node: VariableDeclarationNode, ctx: AnalyzeContext): ASTNode {
        var init = node.declarator.init;
        var initType = if (init != null) {
            node.initializer = ctx.analyze(init, ctx.scope) as ExpressionNode
            node.initializer!!.resolvedType;
        } else null
        val canonInit = initType?.canonical
        init = node.initializer

        var varType = ctx.resolveType(node.declSpec, node.declarator, ctx.scope, null).getOrElse { ctx.error(it, node); return node }

        if (canonInit is SemanticType.OverloadSet) {
            val resolvedInfo = resolveOverloadSetAddress(overloadSet = canonInit, targetType = varType, initializerNode = node.initializer!!, ctx = ctx)

            initType = resolvedInfo.type
            node.initializer!!.resolvedType = resolvedInfo.type
            node.initializer!!.valueCategory = resolvedInfo.valueCategory
        }

        varType = ctx.resolveType(node.declSpec, node.declarator, ctx.scope, initType).getOrElse { ctx.error(it); return node }


        node.varDecl.type = varType
        node.varDecl.processed = true;


        if (!varType.isComplete) {
            ctx.error("Type ${varType.toDisplayString()} is incomplete", location = node.location)
        } else if (varType.hasUndeducedAuto) {
            if (init == null) {
                ctx.error("Declaration with 'auto' requires an initializer", node)
            } else ctx.error("Cannot initialize 'auto' with an expression", init)
            return node;
        } else if (!(varType isSame initType) && initType != null) {
            val seq = ctx.findImplicitCastSeq(initType, init!!.valueCategory!!, varType, ValueCategory.PRVALUE, ctx.isNullPointerConstant(init))
            if (seq.size == 1) {
                node.initializer = ctx.buildConversionSeq(init, seq.first())
            } else if (seq.size > 1) {
                ctx.error("Ambiguous cast", init)
            } else {
                ctx.error("Cannot initialize variable ${node.declarator.id?.toDisplayString()} of type ${varType.toDisplayString()} with ${initType.toDisplayString()}", init)
            }
        }

        return node;
    }

    fun resolveOverloadSetAddress(
        overloadSet: SemanticType.OverloadSet,
        targetType: SemanticType,
        initializerNode: ASTNode,
        ctx: AnalyzeContext
    ): ExpressionInfo {
        val canonTarget = targetType.canonical

        if (canonTarget is SemanticType.Auto) {
            if (overloadSet.overloads.size == 1) {
                val singleFunc = overloadSet.overloads.first()
                val funcPtrType = ctx.types.getPointer(singleFunc.signatureType)

                (initializerNode as? IdExpressionNode)?.decl = singleFunc
                return ExpressionInfo(funcPtrType, ValueCategory.PRVALUE, false)
            } else {
                ctx.error("Cannot deduce 'auto' type from overloaded function '${overloadSet.name}' (ambiguous overload set)", initializerNode)
                return ExpressionInfo(ctx.types.getError(), ValueCategory.PRVALUE, false)
            }
        }

        val unrefTarget = ctx.types.removeRef(canonTarget).canonical

        val targetFuncType = when (unrefTarget) {
            is SemanticType.Pointer -> unrefTarget.pointee.canonical as? SemanticType.Function
            is SemanticType.Function -> unrefTarget
            else -> null
        }

        if (targetFuncType == null) {
            ctx.error("Cannot initialize non-function type '$targetType' with function set '${overloadSet.name}'", initializerNode)
            return ExpressionInfo(ctx.types.getError(), ValueCategory.PRVALUE, false)
        }

        val matchingDecls = overloadSet.overloads.filter { decl ->
            decl.signatureType isSame targetFuncType
        }

        return when (matchingDecls.size) {
            1 -> {
                val selectedFunc = matchingDecls.first()

                val finalType = if (unrefTarget is SemanticType.Function && canonTarget is SemanticType.Reference) {
                    selectedFunc.signatureType
                } else {
                    ctx.types.getPointer(selectedFunc.signatureType)
                }

                val valueCategory = if (finalType.canonical is SemanticType.Function) ValueCategory.LVALUE else ValueCategory.PRVALUE

                (initializerNode as? IdExpressionNode)?.decl = selectedFunc

                ExpressionInfo(finalType, valueCategory, false)
            }

            0 -> {
                ctx.error("No overload for function '${overloadSet.name}' matches required type '$targetFuncType'", initializerNode)
                ExpressionInfo(ctx.types.getError(), ValueCategory.PRVALUE, false)
            }

            else -> {
                ctx.error("Ambiguous target signature for overload set '${overloadSet.name}'", initializerNode)
                ExpressionInfo(ctx.types.getError(), ValueCategory.PRVALUE, false)
            }
        }
    }
}