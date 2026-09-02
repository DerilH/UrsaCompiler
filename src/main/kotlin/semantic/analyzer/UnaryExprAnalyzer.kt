package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.UnaryExpressionNode
import org.derilh.core.OpResult
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.ValueCategory
import org.derilh.core.asSuccess
import org.derilh.core.getOrElse
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isLValueRef
import org.derilh.semantic.isPointer
import org.derilh.semantic.isPrimitive
import org.derilh.semantic.isRValueRef
import org.derilh.util.ErrorHelper

class UnaryExprAnalyzer : NodeAnalyzer<UnaryExpressionNode> {
    override fun analyze(node: UnaryExpressionNode, ctx: AnalyzeContext): ASTNode {
        node.operand = ctx.findAnalyzer(node.operand).analyze(node.operand, ctx) as ExpressionNode
        val type = node.operand.resolvedType;
        val vc = node.operand.valueCategory;
        if (type == null || vc == null) {
            ctx.error("Could not resolve type of operand", node.operand)
            return node;
        }
        val canonType = type.canonical

        val operandInfo = ExpressionInfo(type, vc, ctx.isNullPointerConstant(node.operand))

        if (canonType is SemanticType.Function && node.operator == Operator.AMP) {
            node.resolvedType = ctx.types.decay(canonType)
            node.valueCategory = ValueCategory.PRVALUE
        } else if (canonType.isPointer()) {
            val info = resolvePointerUnaryOpType(canonType as SemanticType.Pointer, node, ctx).getOrElse { ctx.error(it); return node; }
            node.resolvedType = info.type;
            node.valueCategory = info.valueCategory;
            node.operand = ctx.buildConversion(node.operand,info.type,info.valueCategory ).getOrElse { ctx.error(it); return node; }
            return node;
        } else {
            var isPrimitive = false;
            val scope = when {
                canonType.isPrimitive() || canonType.isRValueRef() && canonType.pointee.canonical.isPrimitive() || canonType.isLValueRef() && canonType.pointee.canonical.isPrimitive() -> {
                    isPrimitive = true; ctx.rootScope
                };
                canonType.isDeclared() -> {
                    ctx.scope
                }

                canonType.isRValueRef() && canonType.pointee.canonical.isDeclared() -> ctx.scope;
                canonType.isLValueRef() && canonType.pointee.canonical.isDeclared() -> ctx.scope;
                else -> {
                    ctx.error("Cannot apply operator ${node.operator} to type ${node.operand.resolvedType}", node)
                    return node;
                }
            }

            val additionalParam = if ((node.operator == Operator.INCREMENT || node.operator == Operator.DECREMENT) && !node.isPrefix) {
                ExpressionInfo(ctx.types.int, ValueCategory.PRVALUE, false)
            } else null
            val overloads = ctx.resolveOpOverloads(scope, node.operator, false, operandInfo, additionalParam)
            val result = ErrorHelper.checkViableSet(overloads, "${SemanticAnalyzer.OPERATOR_FUN_PREFIX}${node.operator}", node.location!!).getOrElse { ctx.error(it); return node; }

            node.functionDecl = result.decl;
            node.resolvedType = result.decl.returnType;
            node.valueCategory = ctx.getRefValueCategory(result.decl.returnType)
            //TODO: check if overload is valid for primitive (maybe not needed check for empty conversion sequence;
            //TODO: Add replacing of binary expression to overload call if not builtin overload
            node.operand = ctx.buildConversionSeq(node.operand, result.sequences.first());
        }

        return node;
    }

    fun resolvePointerUnaryOpType(
        operand: SemanticType.Pointer,
        node: UnaryExpressionNode,
        ctx: AnalyzeContext
    ): OpResult<ExpressionInfo> {
        val pointee = operand.pointee.canonical

        return when (node.operator) {
            Operator.POINTER -> {
                if (pointee.isPrimitive(PrimitiveTypeKind.VOID)) {
                    OpResult.failure("Cannot dereference 'void*'", node)
                } else if (!pointee.isComplete) {
                    OpResult.failure("Cannot dereference pointer to incomplete type '${pointee}'", node)
                } else {
                    ExpressionInfo(pointee, ValueCategory.LVALUE, false).asSuccess
                }
            }

            Operator.AMP -> {
                val t = ctx.types.getPointer(pointee = operand, isConst = false, isVolatile = false)
                ExpressionInfo(t, ValueCategory.PRVALUE, false).asSuccess
            }

            Operator.NOT -> {
                ExpressionInfo(ctx.types.bool, ValueCategory.PRVALUE, false).asSuccess
            }

            Operator.PLUS -> {
                ExpressionInfo(operand, ValueCategory.PRVALUE, false).asSuccess
            }

            Operator.INCREMENT, Operator.DECREMENT -> {
                if (operand.isConst) {
                    OpResult.failure("Cannot apply '${node.operator}' to const-qualified pointer", node)
                } else if (pointee.isPrimitive(PrimitiveTypeKind.VOID)) {
                    OpResult.failure("Arithmetic on a pointer to void is forbidden in standard C++", node)
                } else if (!pointee.isComplete) {
                    OpResult.failure("Arithmetic on a pointer to an incomplete type '${pointee}'", node)
                } else {
                    ExpressionInfo(ctx.types.bool, ValueCategory.LVALUE, false).asSuccess
                }
            }

            Operator.MINUS, Operator.BIT_NOT -> {
                OpResult.failure("Invalid argument type '${operand}' to unary expression '${node.operator}'", node)
            }

            else -> OpResult.failure("Cannot apply operatoe ${node.operator.value} to type ${node.operand.resolvedType}}", node)
        }
    }
}
