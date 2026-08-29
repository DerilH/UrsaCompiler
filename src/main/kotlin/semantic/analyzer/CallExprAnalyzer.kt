package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.ArgumentsNode
import org.derilh.ast.CallExpressionNode
import org.derilh.ast.ExpressionNode
import org.derilh.core.getOrElse
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.isFunctionPointer

class CallExprAnalyzer : NodeAnalyzer<CallExpressionNode> {
    override fun analyze(node: CallExpressionNode, ctx: AnalyzeContext): ASTNode {
        lateinit var convArgs: List<ExpressionNode>;
        val callable = node.callable ?: return node;
        ctx.analyze(callable, ctx.scope);
        val callableType = callable.resolvedType!!;

        val argsInfo = node.arguments.arguments.map {
            ctx.analyze(it, ctx.scope)
            ExpressionInfo(it.resolvedType!!,it.valueCategory!!, ctx.isNullPointerConstant(it), it.location)
        }

        when {
            callableType.isFunctionPointer() -> {
                val params = (callableType.pointee as SemanticType.Function).params;
                convArgs = ctx.probeCallArgs(params,0, argsInfo,node.arguments.location!!, breakOnMiss = false).mapIndexed { index, result ->
                    val conv = result.getOrElse { ctx.error(it); return@analyze node }
                    ctx.buildConversionSeq(node.arguments.arguments[index], conv)
                }



//                var sameParams = params.size == node.arguments.arguments.size;
//                if (sameParams) {
//                    node.arguments.arguments.forEachIndexed { index, arg ->
//                        ctx.analyze(arg, ctx.scope)
//                        if (arg.resolvedType == null) {
//                            sameParams = false;
//                        } else {
//                            convArgs += ctx.buildConversion(arg, params[index], ValueCategory.PRVALUE).getOrElse {
//                                sameParams = false;
//                                ctx.error(it);
//                                return@forEachIndexed
//                            }
//                        }
//                    }
//                }
//                if (sameParams) {
//                    node.arguments = ArgumentsNode(convArgs, node.arguments.location);
//                }
            }
            else -> {
                ctx.error("Callable is not valid type ${callable.resolvedType}", callable)
                return node;
            }
        }

        node.arguments = ArgumentsNode(convArgs, node.arguments.location);
        return node;
    }

    fun isSameParams() {

    }
}
